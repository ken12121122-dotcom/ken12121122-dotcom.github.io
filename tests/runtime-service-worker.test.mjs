import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

class MemoryCache {
  constructor() {
    this.entries = new Map();
  }

  keyOf(request) {
    const raw = typeof request === 'string' ? request : request.url;
    return new URL(raw).href;
  }

  async put(request, response) {
    this.entries.set(this.keyOf(request), response.clone());
  }

  async match(request, options = {}) {
    const requested = new URL(this.keyOf(request));
    for (const [key, response] of this.entries) {
      const candidate = new URL(key);
      const same = options.ignoreSearch
        ? candidate.origin === requested.origin && candidate.pathname === requested.pathname
        : candidate.href === requested.href;
      if (same) return response.clone();
    }
    return undefined;
  }
}

class MemoryCacheStorage {
  constructor() {
    this.stores = new Map();
  }

  async open(name) {
    if (!this.stores.has(name)) this.stores.set(name, new MemoryCache());
    return this.stores.get(name);
  }

  async keys() {
    return [...this.stores.keys()];
  }

  async delete(name) {
    return this.stores.delete(name);
  }
}

const handlers = new Map();
const caches = new MemoryCacheStorage();
const posted = [];
let failPattern = null;
let offline = false;

const scope = 'https://example.test/amin-vault/';
const context = vm.createContext({
  URL,
  Request,
  Response,
  console,
  caches,
  fetch: async request => {
    const url = new URL(typeof request === 'string' ? request : request.url);
    if (offline) throw new Error('offline');
    if (failPattern && url.pathname.includes(failPattern)) {
      return new Response('missing', { status: 404 });
    }
    return new Response(`asset:${url.pathname}`, { status: 200 });
  },
  self: {
    registration: { scope },
    clients: {
      claim: async () => undefined,
      matchAll: async () => []
    },
    skipWaiting: async () => undefined,
    addEventListener(type, handler) {
      handlers.set(type, handler);
    }
  }
});

const source = await fs.readFile(new URL('../amin-vault/sw.js', import.meta.url), 'utf8');
vm.runInContext(source, context, { filename: 'amin-vault/sw.js' });

async function dispatchExtendable(type, extra = {}) {
  const handler = handlers.get(type);
  assert.ok(handler, `missing ${type} handler`);
  let task;
  handler({
    ...extra,
    waitUntil(promise) {
      task = Promise.resolve(promise);
    }
  });
  if (task) await task;
}

async function activeCacheName() {
  const meta = await caches.open('amin-vault-runtime-meta');
  const marker = await meta.match(new URL('__amin_runtime_active__', scope).href);
  return marker ? marker.text() : null;
}

await dispatchExtendable('install');
await dispatchExtendable('activate');
assert.equal(await activeCacheName(), 'amin-vault-runtime-bootstrap-v091');

const client = {
  postMessage(message) {
    posted.push(message);
  }
};

const v1Manifest = {
  format: 'amin-runtime-manifest',
  runtimeVersion: '1.0.0',
  assets: ['./gba.html', './runtime-manifest.json']
};

await dispatchExtendable('message', {
  data: { type: 'AMIN_REFRESH_RUNTIME', manifest: v1Manifest },
  source: client
});
assert.equal(await activeCacheName(), 'amin-vault-runtime-1.0.0');
assert.ok(posted.some(message => message.type === 'AMIN_RUNTIME_READY' && message.runtimeVersion === '1.0.0'));

const v1Cache = await caches.open('amin-vault-runtime-1.0.0');
assert.ok(await v1Cache.match(new URL('./gba.html', scope).href));

failPattern = 'missing.js';
const v2Manifest = {
  format: 'amin-runtime-manifest',
  runtimeVersion: '2.0.0',
  assets: ['./gba.html', './missing.js']
};

await dispatchExtendable('message', {
  data: { type: 'AMIN_REFRESH_RUNTIME', manifest: v2Manifest },
  source: client
});
assert.equal(await activeCacheName(), 'amin-vault-runtime-1.0.0', 'failed update replaced the active runtime');
assert.ok(posted.some(message => message.type === 'AMIN_RUNTIME_FAILED' && message.runtimeVersion === '2.0.0'));
assert.ok(!(await caches.keys()).includes('amin-vault-runtime-2.0.0'), 'failed staging cache was not deleted');

failPattern = null;
offline = true;
const fetchHandler = handlers.get('fetch');
let responsePromise;
fetchHandler({
  request: new Request(new URL('./gba.html?offline=1', scope)),
  respondWith(promise) {
    responsePromise = Promise.resolve(promise);
  }
});
const offlineResponse = await responsePromise;
assert.equal(offlineResponse.status, 200);
assert.match(await offlineResponse.text(), /asset:\/amin-vault\/gba\.html/);

console.log('runtime service worker tests passed');
