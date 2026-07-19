(() => {
  'use strict';

  const DB_NAME = 'amin-pocket-rom-library';
  const DB_VERSION = 2;
  const STORE_NAME = 'roms';
  const ROM_MIRROR_CACHE = 'amin-rom-vault-v1';
  const MIRROR_PATH = '/amin-vault/__amin_rom_vault__/';
  const PRIMARY_DATA_PATH = 'https://cdn.emulatorjs.org/4.2.3/data/';
  const FALLBACK_DATA_PATH = 'https://cdn.emulatorjs.org/stable/data/';
  const LAUNCH_TIMEOUT_MS = 45000;
  const MIRROR_RELOAD_KEY = 'amin.rom.mirror.reload';
  const LAST_LAUNCH_KEY = 'amin.gba.lastLaunch';
  const GBA_HEADER_SIZE = 0xC0;
  const GBA_MAX_ROM_BYTES = 32 * 1024 * 1024;

  let launchBusy = false;
  let mirrorTimer = null;
  let recoveryPromise = null;
  let activeLaunch = null;

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
        const store = db.objectStoreNames.contains(STORE_NAME)
          ? request.transaction.objectStore(STORE_NAME)
          : db.createObjectStore(STORE_NAME, { keyPath: 'id' });
        if (!store.indexNames.contains('lastPlayedAt')) store.createIndex('lastPlayedAt', 'lastPlayedAt');
        if (!store.indexNames.contains('addedAt')) store.createIndex('addedAt', 'addedAt');
        if (!store.indexNames.contains('saveKey')) store.createIndex('saveKey', 'saveKey', { unique: false });
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error('無法開啟遊戲庫。'));
    });
  }

  async function withStore(mode, operation) {
    const db = await openDb();
    try {
      const transaction = db.transaction(STORE_NAME, mode);
      const result = operation(transaction.objectStore(STORE_NAME), transaction);
      if (mode === 'readonly') return await result;
      await new Promise((resolve, reject) => {
        transaction.oncomplete = resolve;
        transaction.onerror = () => reject(transaction.error || new Error('遊戲資料庫寫入失敗。'));
        transaction.onabort = () => reject(transaction.error || new Error('遊戲資料庫作業中止。'));
      });
      return result;
    } finally {
      db.close();
    }
  }

  const getAllRecords = () => withStore('readonly', store => requestToPromise(store.getAll()));
  const getRecord = id => withStore('readonly', store => requestToPromise(store.get(id)));
  const putRecord = record => withStore('readwrite', store => store.put(record));

  function mirrorUrl(id) {
    return new URL(`${MIRROR_PATH}${encodeURIComponent(id)}`, location.origin).href;
  }

  function headerValue(value) {
    return encodeURIComponent(String(value ?? ''));
  }

  function decodeHeader(value, fallback = '') {
    if (!value) return fallback;
    try {
      return decodeURIComponent(value);
    } catch {
      return fallback;
    }
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
    return keys.map(request => {
      const url = new URL(request.url);
      if (!url.pathname.startsWith(MIRROR_PATH)) return null;
      return decodeURIComponent(url.pathname.slice(MIRROR_PATH.length));
    }).filter(Boolean);
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
      const knownIds = new Set(records.map(record => String(record.id)));
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

      for (const id of await listMirroredIds().catch(() => [])) {
        if (knownIds.has(String(id))) continue;
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
      recoverAndMirrorLibrary().catch(error => console.warn('[Amin ROM Vault]', error));
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
    return String(name || 'GBA Game')
      .split('/').pop()
      .split('\\').pop()
      .replace(/\.(gba|bin)$/i, '') || 'GBA Game';
  }

  function gbaHeaderChecksum(header) {
    let checksum = 0;
    for (let offset = 0xA0; offset <= 0xBC; offset += 1) {
      checksum = (checksum - header[offset]) & 0xFF;
    }
    return (checksum - 0x19) & 0xFF;
  }

  async function createCompatibleRomFile(record, stableName) {
    if (!recordHasBlob(record)) throw new Error('ROM 本體不存在。');
    if (record.blob.size < GBA_HEADER_SIZE) throw new Error('ROM 檔案過小，不是完整的 GBA 映像。');
    if (record.blob.size > GBA_MAX_ROM_BYTES) {
      throw new Error(`ROM 大小 ${record.blob.size} bytes 超過標準 GBA 32 MiB 上限。`);
    }

    const header = new Uint8Array(await record.blob.slice(0, GBA_HEADER_SIZE).arrayBuffer());
    const before = {
      fixedValue: header[0xB2],
      unitCode: header[0xB3],
      deviceType: header[0xB4],
      checksum: header[0xBD],
      expectedChecksum: gbaHeaderChecksum(header)
    };
    let repaired = false;

    if (header[0xB2] !== 0x96) {
      header[0xB2] = 0x96;
      repaired = true;
    }
    if (header[0xB3] !== 0) {
      header[0xB3] = 0;
      repaired = true;
    }
    if (header[0xB4] !== 0) {
      header[0xB4] = 0;
      repaired = true;
    }
    for (let offset = 0xB5; offset <= 0xBB; offset += 1) {
      if (header[offset] !== 0) {
        header[offset] = 0;
        repaired = true;
      }
    }
    if (header[0xBE] !== 0 || header[0xBF] !== 0) {
      header[0xBE] = 0;
      header[0xBF] = 0;
      repaired = true;
    }
    const correctedChecksum = gbaHeaderChecksum(header);
    if (header[0xBD] !== correctedChecksum) {
      header[0xBD] = correctedChecksum;
      repaired = true;
    }

    const parts = repaired
      ? [header, record.blob.slice(GBA_HEADER_SIZE)]
      : [record.blob];
    const file = new File(parts, `${stableName}.gba`, {
      type: 'application/octet-stream',
      lastModified: Number(record.originalModifiedAt || record.addedAt || Date.now())
    });
    return {
      file,
      repaired,
      before,
      after: {
        fixedValue: header[0xB2],
        unitCode: header[0xB3],
        deviceType: header[0xB4],
        checksum: header[0xBD]
      }
    };
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
    document.body.classList.remove('playing');
    $('playerView')?.classList.add('hidden');
    $('libraryView')?.classList.remove('hidden');
    $('gameLoading')?.classList.add('hidden');
    setStatus('啟動失敗', 'error');
    setImportStatus(message, 'error');
  }

  function currentEmulatorError() {
    const emulator = window.EJS_emulator;
    const text = emulator?.textElem?.innerText
      || $('game')?.querySelector('.ejs_error_text')?.innerText
      || $('game')?.querySelector('.ejs_loading_text')?.innerText
      || '';
    return String(text).trim();
  }

  function waitForEmulatorReady(timeoutMs, state) {
    return new Promise((resolve, reject) => {
      const startedAt = Date.now();
      const timer = setInterval(() => {
        const emulator = window.EJS_emulator;
        if (state.started || (emulator?.started && emulator?.gameManager)) {
          clearInterval(timer);
          resolve(emulator);
          return;
        }
        if (emulator?.failedToStart) {
          clearInterval(timer);
          const detail = currentEmulatorError();
          reject(new Error(detail ? `EmulatorJS：${detail}` : 'EmulatorJS 在載入階段失敗。'));
          return;
        }
        if (Date.now() - startedAt >= timeoutMs) {
          clearInterval(timer);
          reject(new Error(`mGBA 在 ${Math.round(timeoutMs / 1000)} 秒內沒有完成啟動。`));
        }
      }, 200);
    });
  }

  function loaderChannel() {
    return new URLSearchParams(location.search).get('ejs') === 'stable' ? 'stable' : 'pinned';
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

  function removePreviousLoader() {
    document.getElementById('amin-reliable-emulatorjs-loader')?.remove();
    delete window.EJS_emulator;
    delete window.EJS_Runtime;
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
      const compatibleRom = await createCompatibleRomFile(record, stableName);

      window.AMIN_GBA_SAVE_GUARD?.setGame?.({
        saveKey: record.saveKey || record.id,
        romId: record.id,
        romName: record.name,
        stableName
      });

      showPlayer(record);
      setStatus(channel === 'stable' ? '備援啟動中…' : '載入 mGBA…');
      setImportStatus(
        compatibleRom.repaired
          ? '已偵測到改版 ROM 標頭異常，正在使用記憶體修正版啟動；原始 ROM 不會被修改。'
          : 'ROM 標頭檢查通過，正在啟動 mGBA。',
        compatibleRom.repaired ? 'working' : ''
      );

      const launchState = { started: false, ready: false };
      activeLaunch = launchState;
      window.EJS_ready = () => {
        launchState.ready = true;
        recordLaunch('emulator-ready', {
          romId: record.id,
          channel,
          headerRepaired: compatibleRom.repaired
        });
      };
      window.EJS_onGameStart = () => {
        launchState.started = true;
        $('gameLoading')?.classList.add('hidden');
      };
      window.EJS_onExit = () => {
        recordLaunch('emulator-exit', { romId: record.id, channel });
      };

      removePreviousLoader();
      window.EJS_player = '#game';
      window.EJS_core = 'gba';
      window.EJS_pathtodata = dataPath;
      window.EJS_gameUrl = compatibleRom.file;
      window.EJS_gameName = stableName;
      window.EJS_gameID = numericGameId(record.saveKey || record.id);
      window.EJS_color = '#8da2ff';
      window.EJS_backgroundColor = '#05070b';
      window.EJS_volume = 0.65;
      window.EJS_startOnLoaded = true;
      window.EJS_fullscreenOnLoaded = false;
      window.EJS_browserMode = 'mobile';
      window.EJS_askBeforeExit = true;
      window.EJS_fixedSaveInterval = 7000;
      window.EJS_disableAutoLang = true;
      window.EJS_language = 'zh-CN';
      window.EJS_DEBUG_XX = true;
      window.EJS_forceLegacyCores = false;
      delete window.EJS_dontExtractRom;
      delete window.EJS_cacheConfig;

      recordLaunch('starting', {
        romId: record.id,
        channel,
        dataPath,
        transport: 'File',
        fileSize: compatibleRom.file.size,
        headerRepaired: compatibleRom.repaired,
        headerBefore: compatibleRom.before,
        headerAfter: compatibleRom.after
      });

      const script = document.createElement('script');
      script.id = 'amin-reliable-emulatorjs-loader';
      script.src = `${dataPath}loader.js`;
      script.async = true;
      script.onerror = () => {
        const message = `EmulatorJS loader 無法下載：${script.src}`;
        recordLaunch('loader-error', { romId: record.id, channel, message });
      };
      document.body.appendChild(script);

      await waitForEmulatorReady(LAUNCH_TIMEOUT_MS, launchState);
      $('gameLoading')?.classList.add('hidden');
      setStatus('mGBA 運作中', 'active');
      setImportStatus(
        `${compatibleRom.repaired ? '已套用記憶體標頭修正；' : ''}遊戲已透過 File 模式啟動，ROM 原檔與存檔身分保持不變。`,
        'success'
      );
      recordLaunch('started', {
        romId: record.id,
        channel,
        transport: 'File',
        headerRepaired: compatibleRom.repaired
      });
    } catch (error) {
      const channel = loaderChannel();
      const message = error?.message || '遊戲啟動失敗。';
      const emulatorMessage = currentEmulatorError();
      if (record?.id && channel === 'pinned') {
        await retryWithStable(record.id, emulatorMessage || message);
        return;
      }
      recordLaunch('failed', {
        romId: record?.id || id,
        channel,
        message,
        emulatorMessage,
        transport: 'File'
      });
      showLibraryError(`${emulatorMessage || message} ROM 與存檔均已保留。`);
    } finally {
      launchBusy = false;
      activeLaunch = null;
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
    if (id) setTimeout(() => reliablePlay(id), 450);
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

  const libraryObserver = new MutationObserver(() => scheduleMirror(1400));
  if ($('romLibrary')) libraryObserver.observe($('romLibrary'), { childList: true, subtree: true });

  window.AMIN_ROM_VAULT = {
    version: '1.1.0',
    recover: recoverAndMirrorLibrary,
    mirrorAll: recoverAndMirrorLibrary,
    readMirror,
    deleteMirror,
    inspectAndPrepareRom: createCompatibleRomFile,
    play: reliablePlay
  };

  showQueryMessages();
  maybeRecoverAndReload().finally(maybeAutoplay);
})();
