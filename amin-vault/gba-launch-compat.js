(() => {
  'use strict';

  const DB_NAME = 'amin-pocket-rom-library';
  const STORE_NAME = 'roms';
  const PRIMARY_DATA_PATH = 'https://cdn.emulatorjs.org/4.2.3/data/';
  const FALLBACK_DATA_PATH = 'https://cdn.emulatorjs.org/4.0.12/data/';
  const PRIMARY_CHANNEL = 'legacy-4.2.3';
  const FALLBACK_CHANNEL = 'legacy-4.0.12';
  const START_TIMEOUT_MS = 60000;

  let busy = false;
  let activeObjectUrl = null;
  let loaderScript = null;

  const $ = id => document.getElementById(id);
  const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

  function requestToPromise(request) {
    return new Promise((resolve, reject) => {
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error('IndexedDB 操作失敗。'));
    });
  }

  function openDb() {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, 2);
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error('無法開啟 ROM 遊戲庫。'));
    });
  }

  async function getRecord(id) {
    const db = await openDb();
    try {
      if (!db.objectStoreNames.contains(STORE_NAME)) return null;
      return await requestToPromise(
        db.transaction(STORE_NAME, 'readonly').objectStore(STORE_NAME).get(id)
      );
    } finally {
      db.close();
    }
  }

  function recordHasRom(record) {
    return record?.blob instanceof Blob && record.blob.size > 0;
  }

  function numericGameId(value) {
    let hash = 2166136261;
    for (const char of String(value || 'amin-gba')) {
      hash ^= char.charCodeAt(0);
      hash = Math.imul(hash, 16777619);
    }
    return Math.abs(hash | 0) || 1;
  }

  function cleanName(value) {
    return String(value || 'GBA Game')
      .split('/').pop()
      .split('\\').pop()
      .replace(/\.(gba|bin)$/i, '') || 'GBA Game';
  }

  function setBadge(text, kind = '') {
    const badge = $('gbaStatus');
    if (!badge) return;
    badge.textContent = text;
    badge.className = `gba-badge ${kind}`.trim();
  }

  function setImportStatus(text, kind = '') {
    const target = $('importStatus');
    if (!target) return;
    target.textContent = text;
    target.className = `storage-status ${kind}`.trim();
  }

  function requestOrientation(mode) {
    if (!document.documentElement.dataset.nativeShell) return;
    const link = document.createElement('a');
    link.href = `amin://orientation?mode=${encodeURIComponent(mode)}`;
    link.hidden = true;
    document.body.appendChild(link);
    link.click();
    link.remove();
  }

  function dispatchLaunch(status, detail = {}) {
    const payload = {
      status,
      at: Date.now(),
      runtimeVersion: localStorage.getItem('amin.runtime.version') || null,
      nativeVersion: window.AMIN_NATIVE_SHELL?.state?.capabilities?.nativeVersion || null,
      ...detail
    };
    try {
      localStorage.setItem('amin.gba.lastLaunch', JSON.stringify(payload));
    } catch {}
    dispatchEvent(new CustomEvent('amin-gba-launch-status', { detail: payload }));
  }

  async function reportFailure(channel, record, message, stage = 'failed') {
    dispatchLaunch(stage, {
      channel,
      romId: record?.id || null,
      message,
      transport: 'blob-url',
      core: 'mgba',
      forceLegacy: true,
      cacheDisabled: true
    });
    try {
      await window.AMIN_ERROR_REPORTER?.enqueue?.({
        category: 'emulator-launch',
        stage: `${channel}:${stage}`,
        message,
        environment: {
          emulatorChannel: channel,
          romHashPrefix: /^[a-f0-9]{8,64}$/i.test(record?.saveKey || record?.id || '')
            ? String(record.saveKey || record.id).slice(0, 12)
            : null,
          lastSuccessfulStage: stage
        }
      });
      await window.AMIN_ERROR_REPORTER?.sendQueue?.();
    } catch {}
  }

  function releaseObjectUrl() {
    if (!activeObjectUrl) return;
    try {
      URL.revokeObjectURL(activeObjectUrl);
    } catch {}
    activeObjectUrl = null;
  }

  function resetEmulatorGlobals() {
    loaderScript?.remove();
    loaderScript = null;
    $('game')?.replaceChildren();
    delete window.EJS_emulator;
    delete window.EJS_Runtime;
    delete window.EJS_ready;
    delete window.EJS_onGameStart;
    delete window.EJS_onExit;
  }

  function showPlayer(record, channel) {
    requestOrientation('landscape');
    $('libraryView')?.classList.add('hidden');
    $('playerView')?.classList.remove('hidden');
    $('gameLoading')?.classList.remove('hidden');
    if ($('activeGameTitle')) {
      $('activeGameTitle').textContent = `正在啟動 ${cleanName(record.name)}…`;
    }
    document.body.classList.add('playing');
    setBadge(channel === FALLBACK_CHANNEL ? '相容模式啟動…' : 'mGBA 啟動中…');
  }

  function showFailure(message) {
    requestOrientation('portrait');
    releaseObjectUrl();
    document.body.classList.remove('playing');
    $('playerView')?.classList.add('hidden');
    $('libraryView')?.classList.remove('hidden');
    $('gameLoading')?.classList.add('hidden');
    setBadge('啟動失敗', 'error');
    setImportStatus(`${message} ROM 與存檔均未刪除。`, 'error');
  }

  function deleteDatabase(name) {
    return new Promise(resolve => {
      try {
        const request = indexedDB.deleteDatabase(name);
        request.onsuccess = () => resolve(true);
        request.onerror = () => resolve(false);
        request.onblocked = () => resolve(false);
      } catch {
        resolve(false);
      }
    });
  }

  async function clearTransientEmulatorCaches() {
    // Save states live in EmulatorJS-states and are intentionally preserved.
    await Promise.all([
      deleteDatabase('EmulatorJS-core'),
      deleteDatabase('EmulatorJS-roms'),
      deleteDatabase('EmulatorJS-bios'),
      deleteDatabase('EmulatorJS-Cache')
    ]);
  }

  function currentEmulatorMessage() {
    const emulator = window.EJS_emulator;
    const candidates = [
      emulator?.textElem?.innerText,
      $('game')?.querySelector('.ejs_error_text')?.innerText,
      $('game')?.querySelector('.ejs_loading_text')?.innerText,
      $('game')?.innerText
    ];
    return candidates.map(value => String(value || '').trim()).find(Boolean) || '';
  }

  async function waitForStart(state) {
    const startedAt = Date.now();
    while (Date.now() - startedAt < START_TIMEOUT_MS) {
      const emulator = window.EJS_emulator;
      if (state.loaderError) throw new Error(state.loaderError);
      if (state.started || emulator?.started) return emulator;
      if (emulator?.failedToStart) {
        throw new Error(currentEmulatorMessage() || 'EmulatorJS 回報核心啟動失敗。');
      }
      await sleep(200);
    }
    throw new Error(`mGBA 在 ${Math.round(START_TIMEOUT_MS / 1000)} 秒內沒有完成啟動。`);
  }

  function selectedChannel() {
    return new URLSearchParams(location.search).get('compat') === '4012'
      ? FALLBACK_CHANNEL
      : PRIMARY_CHANNEL;
  }

  function channelDataPath(channel) {
    return channel === FALLBACK_CHANNEL ? FALLBACK_DATA_PATH : PRIMARY_DATA_PATH;
  }

  async function retryWithFallback(record, message) {
    await reportFailure(PRIMARY_CHANNEL, record, message, 'retry-fallback');
    const next = new URL('./gba.html', location.href);
    next.searchParams.set('native', '1');
    next.searchParams.set('compat', '4012');
    next.searchParams.set('compatAutoplay', record.id);
    next.searchParams.delete('autoplay');
    next.searchParams.delete('ejs');
    next.searchParams.delete('retry');
    location.replace(next.href);
  }

  async function play(id) {
    if (busy) return;
    busy = true;
    let record = null;
    const channel = selectedChannel();
    try {
      record = await getRecord(id);
      if (!recordHasRom(record)) {
        throw new Error('遊戲庫裡找不到 ROM 本體，請重新匯入一次。');
      }

      await clearTransientEmulatorCaches();
      resetEmulatorGlobals();
      releaseObjectUrl();

      const stableIdentity = record.saveKey || record.id;
      const stableName = `amin-${stableIdentity}`;
      activeObjectUrl = URL.createObjectURL(record.blob);

      window.AMIN_GBA_SAVE_GUARD?.setGame?.({
        saveKey: stableIdentity,
        romId: record.id,
        romName: record.name,
        stableName
      });

      showPlayer(record, channel);
      setImportStatus(
        channel === FALLBACK_CHANNEL
          ? '正在使用舊版相容快照與 Legacy mGBA 核心啟動。'
          : '正在使用原始 ROM、Legacy mGBA 核心與全新暫存啟動。',
        'working'
      );

      const state = { started: false, loaderError: '' };
      const dataPath = channelDataPath(channel);

      window.EJS_player = '#game';
      window.EJS_core = 'mgba';
      window.EJS_pathtodata = dataPath;
      window.EJS_gameUrl = activeObjectUrl;
      window.EJS_gameName = stableName;
      window.EJS_gameID = numericGameId(stableIdentity);
      window.EJS_color = '#8da2ff';
      window.EJS_backgroundColor = '#05070b';
      window.EJS_volume = 0.65;
      window.EJS_startOnLoaded = true;
      window.EJS_fullscreenOnLoaded = false;
      window.EJS_browserMode = 1;
      window.EJS_forceLegacyCores = true;
      window.EJS_threads = false;
      window.EJS_disableDatabases = true;
      window.EJS_disableLocalStorage = false;
      window.EJS_dontExtractRom = true;
      window.EJS_CacheLimit = 0;
      window.EJS_cacheConfig = {
        enabled: false,
        cacheMaxSizeMB: 0,
        cacheMaxAgeMins: 0
      };
      window.EJS_language = 'en-US';
      window.EJS_disableAutoLang = false;
      window.EJS_DEBUG_XX = false;
      window.EJS_askBeforeExit = true;
      window.EJS_fixedSaveInterval = 7000;

      window.EJS_ready = () => {
        dispatchLaunch('emulator-ready', {
          channel,
          romId: record.id,
          core: 'mgba',
          transport: 'blob-url',
          forceLegacy: true,
          cacheDisabled: true
        });
      };
      window.EJS_onGameStart = () => {
        state.started = true;
        $('gameLoading')?.classList.add('hidden');
      };
      window.EJS_onExit = () => {
        dispatchLaunch('emulator-exit', { channel, romId: record.id });
      };

      dispatchLaunch('starting', {
        channel,
        romId: record.id,
        core: 'mgba',
        dataPath,
        transport: 'blob-url',
        originalRomBytes: record.blob.size,
        forceLegacy: true,
        cacheDisabled: true
      });

      loaderScript = document.createElement('script');
      loaderScript.id = 'amin-compat-emulatorjs-loader';
      loaderScript.src = `${dataPath}loader.js`;
      loaderScript.async = true;
      loaderScript.onerror = () => {
        state.loaderError = `無法下載 EmulatorJS loader：${dataPath}`;
      };
      document.body.appendChild(loaderScript);

      await waitForStart(state);
      $('gameLoading')?.classList.add('hidden');
      setBadge('mGBA 運作中', 'active');
      setImportStatus(
        `${channel === FALLBACK_CHANNEL ? '相容快照' : 'Legacy 4.2.3'} 啟動成功；原始 ROM 未修改，核心快取已隔離。`,
        'success'
      );
      dispatchLaunch('started', {
        channel,
        romId: record.id,
        core: 'mgba',
        transport: 'blob-url',
        forceLegacy: true,
        cacheDisabled: true
      });
    } catch (error) {
      const message = error?.message || currentEmulatorMessage() || '遊戲啟動失敗。';
      if (record?.id && channel === PRIMARY_CHANNEL) {
        await retryWithFallback(record, message);
        return;
      }
      await reportFailure(channel, record, message, 'failed');
      showFailure(message);
    } finally {
      busy = false;
    }
  }

  async function onPlayClick(event) {
    const button = event.target.closest?.('[data-play]');
    if (!button) return;
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
    await play(button.dataset.play);
  }

  // Window capture runs before the older document-level reliability listener.
  window.addEventListener('click', onPlayClick, true);
  addEventListener('pagehide', releaseObjectUrl);
  addEventListener('beforeunload', releaseObjectUrl);

  const params = new URLSearchParams(location.search);
  const compatAutoplay = params.get('compatAutoplay');
  if (compatAutoplay) {
    const clean = new URL(location.href);
    clean.searchParams.delete('compatAutoplay');
    history.replaceState(null, '', clean.href);
    setTimeout(() => play(compatAutoplay), 650);
  }

  window.AMIN_GBA_COMPAT_LAUNCHER = {
    version: '1.0.1',
    play,
    clearTransientCaches: clearTransientEmulatorCaches,
    primaryChannel: PRIMARY_CHANNEL,
    fallbackChannel: FALLBACK_CHANNEL
  };
  dispatchEvent(new CustomEvent('amin-gba-compat-ready'));
})();
