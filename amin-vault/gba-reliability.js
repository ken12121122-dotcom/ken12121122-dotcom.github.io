(() => {
  'use strict';

  const DB_NAME = 'amin-pocket-rom-library';
  const DB_VERSION = 2;
  const STORE_NAME = 'roms';
  const ROM_MIRROR_CACHE = 'amin-rom-vault-v1';
  const MIRROR_PATH = '/amin-vault/__amin_rom_vault__/';
  const PRIMARY_DATA_PATH = 'https://cdn.emulatorjs.org/4.2.3/data/';
  const FALLBACK_DATA_PATH = 'https://cdn.emulatorjs.org/stable/data/';
  const LAUNCH_TIMEOUT_MS = 30000;
  const MIRROR_RELOAD_KEY = 'amin.rom.mirror.reload';
  const LAST_LAUNCH_KEY = 'amin.gba.lastLaunch';

  let launchBusy = false;
  let activeObjectUrl = null;
  let mirrorTimer = null;
  let recoveryPromise = null;

  const $ = id => document.getElementById(id);
  const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

  function setStatus(text, kind = '') {
    const target = $('gbaStatus');
    if (!target) return;
    target.textContent = text;
    target.className = `gba-badge ${kind}`.trim();
  }

  function setImportStatus(text, kind = '') {
    const target = $('importStatus');
    if (!target) return;
    target.textContent = text;
    target.className = `storage-status ${kind}`.trim();
  }

  function recordLaunch(status, detail = {}) {
    const payload = {
      status,
      at: Date.now(),
      runtimeVersion: localStorage.getItem('amin.runtime.version') || null,
      nativeVersion: window.AMIN_NATIVE_SHELL?.state?.capabilities?.nativeVersion || null,
      ...detail
    };
    try {
      localStorage.setItem(LAST_LAUNCH_KEY, JSON.stringify(payload));
    } catch {}
    dispatchEvent(new CustomEvent('amin-gba-launch-status', { detail: payload }));
  }

  function requestNativeOrientation(mode) {
    if (!document.documentElement.dataset.nativeShell) return;
    const link = document.createElement('a');
    link.href = `amin://orientation?mode=${encodeURIComponent(mode)}`;
    link.hidden = true;
    document.body.appendChild(link);
    link.click();
    link.remove();
  }

  function releaseObjectUrl() {
    if (!activeObjectUrl) return;
    URL.revokeObjectURL(activeObjectUrl);
    activeObjectUrl = null;
  }

  function requestToPromise(request) {
    return new Promise((resolve, reject) => {
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error('IndexedDB 操作失敗。'));
    });
  }

  function openDb() {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, DB_VERSION);
      request.onupgradeneeded = () => {
        const db = request.result;
        let store;
        if (!db.objectStoreNames.contains(STORE_NAME)) {
          store = db.createObjectStore(STORE_NAME, { keyPath: 'id' });
        } else {
          store = request.transaction.objectStore(STORE_NAME);
        }
        if (!store.indexNames.contains('lastPlayedAt')) store.createIndex('lastPlayedAt', 'lastPlayedAt');
        if (!store.indexNames.contains('addedAt')) store.createIndex('addedAt', 'addedAt');
        if (!store.indexNames.contains('saveKey')) store.createIndex('saveKey', 'saveKey', { unique: false });
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error('無法開啟遊戲庫。'));
    });
  }

  async function getAllRecords() {
    const db = await openDb();
    try {
      return await requestToPromise(db.transaction(STORE_NAME, 'readonly').objectStore(STORE_NAME).getAll());
    } finally {
      db.close();
    }
  }

  async function getRecord(id) {
    const db = await openDb();
    try {
      return await requestToPromise(db.transaction(STORE_NAME, 'readonly').objectStore(STORE_NAME).get(id));
    } finally {
      db.close();
    }
  }

  async function putRecord(record) {
    const db = await openDb();
    try {
      await new Promise((resolve, reject) => {
        const transaction = db.transaction(STORE_NAME, 'readwrite');
        transaction.objectStore(STORE_NAME).put(record);
        transaction.oncomplete = resolve;
        transaction.onerror = () => reject(transaction.error || new Error('無法修復遊戲資料。'));
        transaction.onabort = () => reject(transaction.error || new Error('遊戲資料修復已中止。'));
      });
    } finally {
      db.close();
    }
  }

  function mirrorUrl(id) {
    return new URL(`${MIRROR_PATH}${encodeURIComponent(id)}`, location.origin).href;
  }

  function headerValue(value) {
    return encodeURIComponent(String(value ?? ''));
  }

  function decodeHeader(value, fallback = '') {
    if (!value) return fallback;
    try { return decodeURIComponent(value); } catch { return fallback; }
  }

  function recordHasBlob(record) {
    return record?.blob instanceof Blob && record.blob.size > 0;
  }

  async function mirrorRecord(record) {
    if (!('caches' in window) || !recordHasBlob(record) || !record?.id) return false;
    const cache = await caches.open(ROM_MIRROR_CACHE);
    const headers = new Headers({
      'content-type': record.type || record.blob.type || 'application/octet-stream',
      'x-amin-rom-id': headerValue(record.id),
      'x-amin-rom-name': headerValue(record.name || 'game.gba'),
      'x-amin-save-key': headerValue(record.saveKey || ''),
      'x-amin-added-at': String(Number(record.addedAt || Date.now())),
      'x-amin-last-played-at': String(Number(record.lastPlayedAt || 0)),
      'x-amin-source-pack': headerValue(record.sourcePack || ''),
      'x-amin-original-modified-at': String(Number(record.originalModifiedAt || 0)),
      'x-amin-rom-size': String(record.blob.size)
    });
    await cache.put(mirrorUrl(record.id), new Response(record.blob, { headers }));
    return true;
  }

  async function readMirror(id) {
    if (!('caches' in window) || !id) return null;
    const cache = await caches.open(ROM_MIRROR_CACHE);
    const response = await cache.match(mirrorUrl(id));
    if (!response) return null;
    const blob = await response.blob();
    if (!blob.size) return null;
    const headers = response.headers;
    return {
      id,
      saveKey: decodeHeader(headers.get('x-amin-save-key')) || id,
      saveIdentityVersion: 1,
      name: decodeHeader(headers.get('x-amin-rom-name'), 'game.gba'),
      size: Number(headers.get('x-amin-rom-size')) || blob.size,
      type: headers.get('content-type') || blob.type || 'application/octet-stream',
      blob,
      sourcePack: decodeHeader(headers.get('x-amin-source-pack')) || null,
      addedAt: Number(headers.get('x-amin-added-at')) || Date.now(),
      lastPlayedAt: Number(headers.get('x-amin-last-played-at')) || null,
      originalModifiedAt: Number(headers.get('x-amin-original-modified-at')) || null,
      recoveredFromMirrorAt: Date.now()
    };
  }

  async function listMirroredIds() {
    if (!('caches' in window)) return [];
    const cache = await caches.open(ROM_MIRROR_CACHE);
    const keys = await cache.keys();
    return keys
      .map(request => {
        const url = new URL(request.url);
        if (!url.pathname.startsWith(MIRROR_PATH)) return null;
        return decodeURIComponent(url.pathname.slice(MIRROR_PATH.length));
      })
      .filter(Boolean);
  }

  async function deleteMirror(id) {
    if (!('caches' in window) || !id) return false;
    const cache = await caches.open(ROM_MIRROR_CACHE);
    return cache.delete(mirrorUrl(id));
  }

  async function recoverAndMirrorLibrary() {
    if (recoveryPromise) return recoveryPromise;
    recoveryPromise = (async () => {
      const records = await getAllRecords();
      const byId = new Map(records.map(record => [String(record.id), record]));
      let mirrored = 0;
      let recovered = 0;

      for (const record of records) {
        if (recordHasBlob(record)) {
          if (await mirrorRecord(record).catch(() => false)) mirrored += 1;
          continue;
        }
        const backup = await readMirror(record.id).catch(() => null);
        if (backup) {
          await putRecord({ ...record, ...backup });
          recovered += 1;
        }
      }

      const mirroredIds = await listMirroredIds().catch(() => []);
      for (const id of mirroredIds) {
        if (byId.has(String(id))) continue;
        const backup = await readMirror(id).catch(() => null);
        if (!backup) continue;
        await putRecord(backup);
        recovered += 1;
      }

      const detail = { mirrored, recovered, total: records.length };
      dispatchEvent(new CustomEvent('amin-rom-vault-ready', { detail }));
      return detail;
    })().finally(() => {
      recoveryPromise = null;
    });
    return recoveryPromise;
  }

  function scheduleMirror(delay = 900) {
    clearTimeout(mirrorTimer);
    mirrorTimer = setTimeout(() => {
      recoverAndMirrorLibrary().catch(error => {
        console.warn('[Amin ROM Vault]', error);
      });
    }, delay);
  }

  function numericGameId(value) {
    let hash = 2166136261;
    for (const char of String(value)) {
      hash ^= char.charCodeAt(0);
      hash = Math.imul(hash, 16777619);
    }
    return Math.abs(hash | 0) || 1;
  }

  function cleanName(name) {
    return String(name || 'GBA Game').split('/').pop().split('\\').pop().replace(/\.(gba|bin)$/i, '') || 'GBA Game';
  }

  function showPlayer(record) {
    requestNativeOrientation('landscape');
    $('libraryView')?.classList.add('hidden');
    $('playerView')?.classList.remove('hidden');
    $('gameLoading')?.classList.remove('hidden');
    if ($('activeGameTitle')) $('activeGameTitle').textContent = `正在啟動 ${cleanName(record.name)}…`;
    document.body.classList.add('playing');
  }

  function showLibraryError(message) {
    requestNativeOrientation('portrait');
    releaseObjectUrl();
    document.body.classList.remove('playing');
    $('playerView')?.classList.add('hidden');
    $('libraryView')?.classList.remove('hidden');
    $('gameLoading')?.classList.add('hidden');
    setStatus('啟動失敗', 'error');
    setImportStatus(message, 'error');
  }

  async function waitForEmulatorReady(timeoutMs) {
    const startedAt = Date.now();
    while (Date.now() - startedAt < timeoutMs) {
      const emulator = window.EJS_emulator;
      if (emulator?.failedToStart) {
        throw new Error('mGBA 核心回報啟動失敗。');
      }
      if (emulator?.started && emulator?.gameManager) return emulator;
      if ($('game')?.querySelector('canvas') && emulator?.gameManager) return emulator;
      await sleep(250);
    }
    throw new Error('mGBA 核心在 30 秒內沒有完成啟動。');
  }

  function loaderChannel() {
    const params = new URLSearchParams(location.search);
    return params.get('ejs') === 'stable' ? 'stable' : 'pinned';
  }

  async function retryWithStable(id, reason) {
    recordLaunch('retry-stable', { romId: id, reason });
    const next = new URL('./gba.html', location.href);
    next.searchParams.set('native', '1');
    next.searchParams.set('autoplay', id);
    next.searchParams.set('ejs', 'stable');
    next.searchParams.set('retry', '1');
    location.replace(next.href);
  }

  async function reliablePlay(id) {
    if (launchBusy) return;
    launchBusy = true;
    let record;
    try {
      await recoverAndMirrorLibrary().catch(() => null);
      record = await getRecord(id);
      if (!recordHasBlob(record)) {
        const recovered = await readMirror(id);
        if (recovered) {
          record = { ...record, ...recovered };
          await putRecord(record);
        }
      }
      if (!recordHasBlob(record)) {
        throw new Error('找不到 ROM 本體。請重新匯入一次，之後 Amin 會建立第二份鏡像。');
      }

      await mirrorRecord(record).catch(() => false);
      record.lastPlayedAt = Date.now();
      await putRecord(record);
      await navigator.storage?.persist?.().catch(() => false);

      const channel = loaderChannel();
      const dataPath = channel === 'stable' ? FALLBACK_DATA_PATH : PRIMARY_DATA_PATH;
      const stableName = `amin-${record.saveKey || record.id}`;

      releaseObjectUrl();
      activeObjectUrl = URL.createObjectURL(record.blob);
      window.AMIN_GBA_SAVE_GUARD?.setGame?.({
        saveKey: record.saveKey || record.id,
        romId: record.id,
        romName: record.name,
        stableName
      });

      showPlayer(record);
      setStatus(channel === 'stable' ? '備援啟動中…' : '載入 mGBA…');
      recordLaunch('starting', { romId: record.id, channel, dataPath });

      window.EJS_player = '#game';
      window.EJS_core = 'gba';
      window.EJS_pathtodata = dataPath;
      window.EJS_gameUrl = activeObjectUrl;
      window.EJS_gameName = stableName;
      window.EJS_gameID = numericGameId(record.saveKey || record.id);
      window.EJS_color = '#8da2ff';
      window.EJS_backgroundColor = '#05070b';
      window.EJS_volume = 0.65;
      window.EJS_startOnLoaded = true;
      window.EJS_fullscreenOnLoaded = false;
      window.EJS_browserMode = matchMedia('(pointer: coarse)').matches ? 'mobile' : 'desktop';
      window.EJS_askBeforeExit = true;
      window.EJS_fixedSaveInterval = 5000;
      window.EJS_disableAutoLang = true;
      window.EJS_language = 'zh-TW';
      window.EJS_DEBUG_XX = false;
      delete window.EJS_dontExtractRom;
      delete window.EJS_cacheConfig;

      const script = document.createElement('script');
      script.id = 'amin-reliable-emulatorjs-loader';
      script.src = `${dataPath}loader.js`;
      script.async = true;
      script.onerror = () => {
        recordLaunch('loader-error', { romId: record.id, channel, src: script.src });
      };
      document.body.appendChild(script);

      await waitForEmulatorReady(LAUNCH_TIMEOUT_MS);
      $('gameLoading')?.classList.add('hidden');
      setStatus(`mGBA 4.2.3 運作中`, 'active');
      setImportStatus(`已從 ${channel === 'stable' ? '官方穩定備援' : '固定 4.2.3'} 通道啟動。ROM 已建立雙層本機副本。`, 'success');
      recordLaunch('started', { romId: record.id, channel });
    } catch (error) {
      const channel = loaderChannel();
      const message = error?.message || '遊戲啟動失敗。';
      if (record?.id && channel === 'pinned') {
        await retryWithStable(record.id, message);
        return;
      }
      recordLaunch('failed', { romId: record?.id || id, channel, message });
      showLibraryError(`${message} 已保留 ROM 與存檔，可在「系統診斷」查看錯誤。`);
    } finally {
      launchBusy = false;
    }
  }

  async function handlePlayClick(event) {
    const button = event.target.closest?.('[data-play]');
    if (!button) return;
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
    await reliablePlay(button.dataset.play);
  }

  function handleDeleteClick(event) {
    const button = event.target.closest?.('[data-delete]');
    if (!button) return;
    const id = button.dataset.delete;
    setTimeout(async () => {
      const record = await getRecord(id).catch(() => null);
      if (!record) await deleteMirror(id).catch(() => false);
    }, 1200);
  }

  async function maybeRecoverAndReload() {
    const result = await recoverAndMirrorLibrary().catch(error => {
      console.warn('[Amin ROM Vault]', error);
      return { recovered: 0 };
    });
    if (!result.recovered) return;
    const previous = Number(sessionStorage.getItem(MIRROR_RELOAD_KEY) || 0);
    if (Date.now() - previous < 10000) return;
    sessionStorage.setItem(MIRROR_RELOAD_KEY, String(Date.now()));
    const next = new URL(location.href);
    next.searchParams.set('romRecovered', String(result.recovered));
    location.replace(next.href);
  }

  function showQueryMessages() {
    const params = new URLSearchParams(location.search);
    const recovered = Number(params.get('romRecovered') || 0);
    if (recovered > 0) {
      setImportStatus(`已從第二層 ROM 鏡像修復 ${recovered} 款遊戲。`, 'success');
    }
    const launchError = params.get('launchError');
    if (launchError) setImportStatus(launchError, 'error');
  }

  function maybeAutoplay() {
    const id = new URLSearchParams(location.search).get('autoplay');
    if (!id) return;
    setTimeout(() => reliablePlay(id), 450);
  }

  document.addEventListener('click', handlePlayClick, true);
  document.addEventListener('click', handleDeleteClick, false);
  $('romInput')?.addEventListener('change', () => {
    scheduleMirror(1200);
    setTimeout(() => scheduleMirror(0), 4000);
    setTimeout(() => scheduleMirror(0), 10000);
  });
  addEventListener('amin-native-cartridge-imported', () => scheduleMirror(1800));
  addEventListener('amin-save-verified', () => scheduleMirror(300));
  addEventListener('pagehide', releaseObjectUrl);
  addEventListener('beforeunload', releaseObjectUrl);

  const libraryObserver = new MutationObserver(() => scheduleMirror(1400));
  if ($('romLibrary')) libraryObserver.observe($('romLibrary'), { childList: true, subtree: true });

  window.AMIN_ROM_VAULT = {
    version: '1.0.0',
    recover: recoverAndMirrorLibrary,
    mirrorAll: recoverAndMirrorLibrary,
    readMirror,
    deleteMirror,
    play: reliablePlay
  };

  showQueryMessages();
  maybeRecoverAndReload().finally(maybeAutoplay);
})();
