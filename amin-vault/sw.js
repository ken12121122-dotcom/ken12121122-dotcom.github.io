const CACHE='amin-vault-v084';
const ASSETS=[
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
  './gba-immersive.js',
  './gba-save-guard.js',
  './gba-controller-runtime.js',
  './gba.js',
  './manifest.webmanifest',
  './icon.svg',
  './architecture.json',
  './ARCHITECTURE.md'
];

self.addEventListener('install',event=>{
  event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(ASSETS)));
  self.skipWaiting();
});

self.addEventListener('activate',event=>{
  event.waitUntil(
    caches.keys().then(keys=>Promise.all(keys.filter(key=>key!==CACHE).map(key=>caches.delete(key))))
  );
  self.clients.claim();
});

self.addEventListener('fetch',event=>{
  if(event.request.method!=='GET') return;
  event.respondWith(
    fetch(event.request).then(response=>{
      const copy=response.clone();
      caches.open(CACHE).then(cache=>cache.put(event.request,copy));
      return response;
    }).catch(()=>caches.match(event.request).then(hit=>hit||caches.match('./')))
  );
});
