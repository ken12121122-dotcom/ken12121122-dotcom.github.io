const CACHE_PREFIX = 'amin-vault-runtime-';
const BOOTSTRAP_CACHE = `${CACHE_PREFIX}bootstrap-v092`;
const META_CACHE = `${CACHE_PREFIX}meta`;
const ACTIVE_KEY = new URL('__amin_runtime_active__', self.registration.scope).href;
const MANIFEST_PATH = new URL('./runtime-manifest.json', self.registration.scope).pathname;

const BOOTSTRAP_ASSETS = [
  './',
  './styles.css',
  './console-gba.css',
  './app.js',
  './public.js',
  './three-shell.js',
  './three-boot.js',
  './config.js',
  './gba.html',
  './gba.css',
  './gba-controller-link.css',
  './gba-controller.html',
  './gba-controller.css',
  './gba-controller.js',
  './gba-controller-native-addon.js',
  './gba-native-input.js',
  './amin-native-shell.js',
  './amin-backup-v2.js',
  './amin-diagnostics.js',
  './gba-signal-lab.html',
  './gba-signal-lab.css',
  './gba-signal-lab.js',
  './gba-signal-native-addon.js',
  './gba-immersive.js',
  './gba-save-guard.js',
  './gba-controller-runtime.js',
  './gba.js',
  './runtime-updater.js',
  './runtime-manifest.json',
  './manifest.webmanifest',
  './icon.svg',
  './architecture.json',
  './ARCHITECTURE.md'
];

function assetUrl(asset) {
  return new URL(asset, self.registration.scope).href;
}

async function getActiveCacheName() {
  const meta = await caches.open(META_CACHE);
  const response = await meta.match(ACTIVE_KEY);
  return response ? response.text() : BOOTSTRAP_CACHE;
}

async function setActiveCacheName(cacheName) {
  const meta = await caches.open(META_CACHE);
  await meta.put(ACTIVE_KEY, new Response(cacheName, {
    headers: { 'content-type': 'text/plain; charset=utf-8' }
  }));
}

async function cacheAssetList(cacheName, assets, onProgress) {
  const cache = await caches.open(cacheName);
  for (let index = 0; index < assets.length; index += 1) {
    const url = assetUrl(assets[index]);
    const response = await fetch(url, {
      cache: 'reload',
      credentials: 'same-origin'
    });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${assets[index]}`);
    }
    await cache.put(url, response.clone());
    if (onProgress) await onProgress(index + 1, assets.length, assets[index]);
  }
}

async function postToClient(source, payload) {
  if (source && typeof source.postMessage === 'function') {
    source.postMessage(payload);
    return;
  }
  const clients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
  clients.forEach(client => client.postMessage(payload));
}

async function cleanupRuntimeCaches(activeCacheName) {
  const keys = await caches.keys();
  await Promise.all(keys.map(key => {
    if (!key.startsWith(CACHE_PREFIX)) return Promise.resolve(false);
    if (key === META_CACHE || key === activeCacheName) return Promise.resolve(false);
    return caches.delete(key);
  }));
}

function validateRuntimeManifest(manifest) {
  if (!manifest || manifest.format !== 'amin-runtime-manifest') {
    throw new Error('Invalid Amin runtime manifest.');
  }
  if (!manifest.runtimeVersion || !Array.isArray(manifest.assets) || !manifest.assets.length) {
    throw new Error('Runtime version or asset list is missing.');
  }
  const scopeUrl = new URL(self.registration.scope);
  manifest.assets.forEach(asset => {
    const url = new URL(asset, scopeUrl);
    if (url.origin !== scopeUrl.origin || !url.pathname.startsWith(scopeUrl.pathname)) {
      throw new Error(`Asset is outside the allowed runtime scope: ${asset}`);
    }
  });
  return manifest;
}

async function refreshRuntime(manifest, source) {
  const checked = validateRuntimeManifest(manifest);
  const safeVersion = String(checked.runtimeVersion).replace(/[^a-zA-Z0-9._-]/g, '_');
  const nextCacheName = `${CACHE_PREFIX}${safeVersion}`;

  try {
    await caches.delete(nextCacheName);
    await cacheAssetList(nextCacheName, checked.assets, (completed, total, asset) =>
      postToClient(source, {
        type: 'AMIN_RUNTIME_PROGRESS',
        runtimeVersion: checked.runtimeVersion,
        completed,
        total,
        asset
      })
    );

    await setActiveCacheName(nextCacheName);
    await cleanupRuntimeCaches(nextCacheName);
    await postToClient(source, {
      type: 'AMIN_RUNTIME_READY',
      runtimeVersion: checked.runtimeVersion,
      cacheName: nextCacheName
    });
  } catch (error) {
    await caches.delete(nextCacheName);
    await postToClient(source, {
      type: 'AMIN_RUNTIME_FAILED',
      runtimeVersion: checked.runtimeVersion,
      message: error && error.message ? error.message : 'Runtime update failed.'
    });
  }
}

async function networkFirstManifest(request, cache) {
  try {
    const response = await fetch(request, { cache: 'no-store' });
    if (response.ok) await cache.put(assetUrl('./runtime-manifest.json'), response.clone());
    return response;
  } catch (error) {
    const cached = await cache.match(assetUrl('./runtime-manifest.json'), { ignoreSearch: true });
    if (cached) return cached;
    throw error;
  }
}

self.addEventListener('install', event => {
  event.waitUntil((async () => {
    await cacheAssetList(BOOTSTRAP_CACHE, BOOTSTRAP_ASSETS);
    const active = await getActiveCacheName();
    if (!active || active === BOOTSTRAP_CACHE) await setActiveCacheName(BOOTSTRAP_CACHE);
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    const active = await getActiveCacheName();
    await cleanupRuntimeCaches(active);
    await self.clients.claim();
  })());
});

self.addEventListener('message', event => {
  const data = event.data || {};
  if (data.type !== 'AMIN_REFRESH_RUNTIME') return;
  event.waitUntil(refreshRuntime(data.manifest, event.source));
});

self.addEventListener('fetch', event => {
  if (event.request.method !== 'GET') return;
  const requestUrl = new URL(event.request.url);
  const scopeUrl = new URL(self.registration.scope);
  if (requestUrl.origin !== scopeUrl.origin || !requestUrl.pathname.startsWith(scopeUrl.pathname)) return;

  event.respondWith((async () => {
    const activeCacheName = await getActiveCacheName();
    const cache = await caches.open(activeCacheName);

    if (requestUrl.pathname === MANIFEST_PATH) {
      return networkFirstManifest(event.request, cache);
    }

    const cached = await cache.match(event.request, { ignoreSearch: true });
    if (cached) return cached;

    try {
      const response = await fetch(event.request);
      if (response.ok) await cache.put(event.request, response.clone());
      return response;
    } catch (error) {
      if (event.request.mode === 'navigate') {
        const fallback = await cache.match(assetUrl('./gba.html'), { ignoreSearch: true });
        if (fallback) return fallback;
      }
      throw error;
    }
  })());
});
