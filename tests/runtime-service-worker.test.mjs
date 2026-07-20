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
let networkGeneration = 'v1';
let networkFetches = 0;

const scope = 'https://example.test/amin-vault/';
const context = vm.createContext({
  URL,
  Request,
  Response,
  console,
  caches,
  fetch: async request => {
    networkFetches += 1;
    const url = new URL(typeof request === 'string' ? request : request.url);
    if (offline) throw new Error('offline');
    if (failPattern && url.pathname.includes(failPattern)) {
      return new Response('missing', { status: 404 });
    }
    return new Response(`asset:${networkGeneration}:${url.pathname}`, { status: 200 });
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

async function dispatchFetch(path, options = {}) {
  const handler = handlers.get('fetch');
  assert.ok(handler, 'missing fetch handler');
  let responsePromise;
  handler({
    request: new Request(new URL(path, scope), options),
    respondWith(promise) {
      responsePromise = Promise.resolve(promise);
    }
  });
  assert.ok(responsePromise, `fetch handler ignored ${path}`);
  return responsePromise;
}

async function activeCacheName() {
  const meta = await caches.open('amin-vault-runtime-meta');
  const marker = await meta.match(new URL('__amin_runtime_active__', scope).href);
  return marker ? marker.text() : null;
}

await dispatchExtendable('install');
await dispatchExtendable('activate');
const bootstrapCache = await activeCacheName();
assert.match(
  bootstrapCache,
  /^amin-vault-runtime-bootstrap-v092(?:-[a-z0-9._-]+)?$/,
  'service worker did not activate a versioned bootstrap cache'
);
assert.ok((await caches.keys()).includes(bootstrapCache), 'active bootstrap cache does not exist');

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

networkGeneration = 'v2-uncommitted';
const fetchesBeforeActiveRead = networkFetches;
const activeResponse = await dispatchFetch('./gba.html?online=1');
assert.match(await activeResponse.text(), /asset:v1:\/amin-vault\/gba\.html/);
assert.equal(networkFetches, fetchesBeforeActiveRead, 'active runtime unexpectedly fetched a newer network file');

const manifestResponse = await dispatchFetch('./runtime-manifest.json?check=1');
assert.match(await manifestResponse.text(), /asset:v2-uncommitted:\/amin-vault\/runtime-manifest\.json/);

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
const offlineResponse = await dispatchFetch('./gba.html?offline=1');
assert.equal(offlineResponse.status, 200);
assert.match(await offlineResponse.text(), /asset:v1:\/amin-vault\/gba\.html/);

console.log('runtime service worker tests passed');
