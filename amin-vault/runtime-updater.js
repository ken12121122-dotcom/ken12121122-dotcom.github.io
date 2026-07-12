(() => {
  'use strict';

  const MANIFEST_URL = './runtime-manifest.json';
  const STORAGE_KEY = 'amin.runtime.version';
  const RELOAD_KEY = 'amin.runtime.reloaded';
  const UPDATE_TIMEOUT_MS = 120000;

  const badge = document.getElementById('gbaStatus');

  function setBadge(text, kind = '') {
    if (!badge) return;
    badge.textContent = text;
    badge.className = `gba-badge ${kind}`.trim();
  }

  function emit(type, detail) {
    window.dispatchEvent(new CustomEvent(type, { detail }));
  }

  function parseVersion(value) {
    return String(value || '')
      .replace(/^[^0-9]*/, '')
      .split(/[.+-]/)
      .slice(0, 3)
      .map(part => Number.parseInt(part, 10) || 0);
  }

  function compareVersions(left, right) {
    const a = parseVersion(left);
    const b = parseVersion(right);
    for (let index = 0; index < 3; index += 1) {
      if (a[index] > b[index]) return 1;
      if (a[index] < b[index]) return -1;
    }
    return 0;
  }

  function nativeVersion() {
    const match = navigator.userAgent.match(/AminPocketGBA\/([^\s]+)/i);
    return match ? match[1].replace(/-debug$/i, '') : null;
  }

  function validateManifest(manifest) {
    if (!manifest || manifest.format !== 'amin-runtime-manifest') {
      throw new Error('更新清單格式不正確。');
    }
    if (!manifest.runtimeVersion || !Array.isArray(manifest.assets) || !manifest.assets.length) {
      throw new Error('更新清單缺少版本或資源。');
    }

    for (const asset of manifest.assets) {
      const url = new URL(asset, location.href);
      if (url.origin !== location.origin || !url.pathname.startsWith(new URL('./', location.href).pathname)) {
        throw new Error(`更新清單包含不允許的資源：${asset}`);
      }
    }
    return manifest;
  }

  async function fetchManifest() {
    const response = await fetch(`${MANIFEST_URL}?t=${Date.now()}`, {
      cache: 'no-store',
      credentials: 'same-origin'
    });
    if (!response.ok) throw new Error(`無法取得更新清單（HTTP ${response.status}）。`);
    return validateManifest(await response.json());
  }

  async function registerWorker() {
    if (!('serviceWorker' in navigator)) return null;
    const registration = await navigator.serviceWorker.register('./sw.js', { scope: './' });
    try {
      await registration.update();
    } catch {
      // The installed worker can still serve the last working runtime while offline.
    }
    return navigator.serviceWorker.ready;
  }

  function requestRuntimeRefresh(registration, manifest) {
    return new Promise((resolve, reject) => {
      let finished = false;
      const timer = setTimeout(() => finish(new Error('更新下載逾時，已保留舊版。')), UPDATE_TIMEOUT_MS);

      function finish(error, result) {
        if (finished) return;
        finished = true;
        clearTimeout(timer);
        navigator.serviceWorker.removeEventListener('message', onMessage);
        if (error) reject(error);
        else resolve(result);
      }

      function onMessage(event) {
        const data = event.data || {};
        if (data.runtimeVersion !== manifest.runtimeVersion) return;

        if (data.type === 'AMIN_RUNTIME_PROGRESS') {
          setBadge(`更新 ${data.completed}/${data.total}`, 'active');
          emit('amin-runtime-progress', data);
          return;
        }
        if (data.type === 'AMIN_RUNTIME_READY') {
          finish(null, data);
          return;
        }
        if (data.type === 'AMIN_RUNTIME_FAILED') {
          finish(new Error(data.message || '更新下載失敗，已保留舊版。'));
        }
      }

      navigator.serviceWorker.addEventListener('message', onMessage);
      const worker = registration.active || registration.waiting || registration.installing || navigator.serviceWorker.controller;
      if (!worker) {
        finish(new Error('更新服務尚未啟動。'));
        return;
      }
      worker.postMessage({ type: 'AMIN_REFRESH_RUNTIME', manifest });
    });
  }

  function reloadIntoVersion(version) {
    const alreadyReloaded = sessionStorage.getItem(RELOAD_KEY);
    if (alreadyReloaded === version) return;
    sessionStorage.setItem(RELOAD_KEY, version);
    const url = new URL(location.href);
    url.searchParams.set('runtime', version);
    location.replace(url.toString());
  }

  async function start() {
    const installedVersion = localStorage.getItem(STORAGE_KEY);
    const initialBadge = badge?.textContent || '';
    if (!installedVersion && initialBadge === '初始化') setBadge('檢查更新…');

    let registration = null;
    try {
      registration = await registerWorker();
      const manifest = await fetchManifest();
      const appVersion = nativeVersion();

      if (appVersion && manifest.minimumNativeVersion
          && compareVersions(appVersion, manifest.minimumNativeVersion) < 0) {
        setBadge('需要更新 APK', 'error');
        emit('amin-runtime-native-required', {
          installedNativeVersion: appVersion,
          minimumNativeVersion: manifest.minimumNativeVersion,
          runtimeVersion: manifest.runtimeVersion
        });
        return;
      }

      if (installedVersion === manifest.runtimeVersion) {
        emit('amin-runtime-ready', {
          runtimeVersion: manifest.runtimeVersion,
          updated: false,
          offline: false
        });
        return;
      }

      if (!registration) {
        throw new Error('此環境不支援離線更新服務。');
      }

      setBadge(installedVersion ? `下載 v${manifest.runtimeVersion}` : '準備離線內容…', 'active');
      await requestRuntimeRefresh(registration, manifest);
      localStorage.setItem(STORAGE_KEY, manifest.runtimeVersion);
      setBadge('更新完成', 'active');
      emit('amin-runtime-ready', {
        runtimeVersion: manifest.runtimeVersion,
        previousVersion: installedVersion,
        updated: true,
        offline: false
      });

      if (installedVersion) reloadIntoVersion(manifest.runtimeVersion);
    } catch (error) {
      const fallbackVersion = localStorage.getItem(STORAGE_KEY);
      if (fallbackVersion) {
        setBadge(`離線 · v${fallbackVersion}`);
        emit('amin-runtime-ready', {
          runtimeVersion: fallbackVersion,
          updated: false,
          offline: true,
          error: error.message
        });
      } else {
        setBadge('更新檢查失敗', 'error');
        emit('amin-runtime-error', { message: error.message });
      }
      console.warn('[Amin Runtime Updater]', error);
    }
  }

  start();
})();
