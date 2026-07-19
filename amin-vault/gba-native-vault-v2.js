(() => {
  'use strict';

  const DB_NAME = 'amin-pocket-rom-library';
  const DB_VERSION = 2;
  const STORE_NAME = 'roms';
  const MAX_ROM_BYTES = 64 * 1024 * 1024;
  const CHUNK_BYTES = 384 * 1024;
  const START_TIMEOUT_MS = 60000;

  let launchBusy = false;
  let loaderScript = null;
  let migrationPromise = null;

  const $ = id => document.getElementById(id);
  const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

  function bridge() {
    const value = window.AminNativeCartridgeVault;
    return value
      && typeof value.hasRom === 'function'
      && typeof value.beginImport === 'function'
      && typeof value.appendImport === 'function'
      && typeof value.finishImport === 'function'
      && typeof value.getRomUrl === 'function'
      && typeof value.getEngineBaseUrl === 'function'
      ? value
      : null;
  }

  function enabled() {
    return Boolean(bridge()?.getEngineBaseUrl?.());
  }

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

  function setVaultStatus(text, kind = '') {
    const target = $('romVaultStatus');
    if (!target) return;
    target.textContent = text;
    target.className = `storage-status ${kind}`.trim();
  }

  function parseNativeResult(raw, fallback = '原生卡匣庫作業失敗。') {
    let result;
    try {
      result = JSON.parse(raw || '{}');
    } catch {
      throw new Error(fallback);
    }
    if (result.ok === false) throw new Error(result.error || fallback);
    return result;
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
        if (!db.objectStoreNames.contains(STORE_NAME)) {
          db.createObjectStore(STORE_NAME, { keyPath: 'id' });
        }
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error('無法開啟遊戲庫。'));
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

  async function getAllRecords() {
    const db = await openDb();
    try {
      if (!db.objectStoreNames.contains(STORE_NAME)) return [];
      return await requestToPromise(
        db.transaction(STORE_NAME, 'readonly').objectStore(STORE_NAME).getAll()
      );
    } finally {
      db.close();
    }
  }

  function recordHasRom(record) {
    return record?.blob instanceof Blob
      && record.blob.size > 0
      && record.blob.size <= MAX_ROM_BYTES;
  }

  async function sha256Blob(blob) {
    const digest = await crypto.subtle.digest('SHA-256', await blob.arrayBuffer());
    return [...new Uint8Array(digest)]
      .map(value => value.toString(16).padStart(2, '0'))
      .join('');
  }

  function bytesToBase64(bytes) {
    let binary = '';
    const slice = 0x8000;
    for (let offset = 0; offset < bytes.length; offset += slice) {
      binary += String.fromCharCode(...bytes.subarray(offset, offset + slice));
    }
    return btoa(binary);
  }

  async function romIdentity(record) {
    if (/^[a-f0-9]{64}$/i.test(record?.id || '')) return String(record.id).toLowerCase();
    return sha256Blob(record.blob);
  }

  async function migrateRecord(record, { quiet = false } = {}) {
    const native = bridge();
    if (!native) throw new Error('這個 APK 尚未提供 Native Cartridge Vault v2。');
    if (!recordHasRom(record)) throw new Error('遊戲庫中找不到完整 ROM 本體。');

    const key = await romIdentity(record);
    if (native.hasRom(key)) {
      return {
        key,
        url: native.getRomUrl(key),
        migrated: false,
        byteLength: record.blob.size
      };
    }

    const started = parseNativeResult(
      native.beginImport(key, record.name || 'cartridge.gba', record.blob.size),
      '無法建立原生 ROM 搬移工作。'
    );
    const sessionId = started.sessionId;
    if (!sessionId) throw new Error('原生 ROM 搬移工作缺少 session。');

    try {
      for (let offset = 0; offset < record.blob.size; offset += CHUNK_BYTES) {
        const end = Math.min(record.blob.size, offset + CHUNK_BYTES);
        const bytes = new Uint8Array(await record.blob.slice(offset, end).arrayBuffer());
        parseNativeResult(
          native.appendImport(sessionId, bytesToBase64(bytes)),
          'ROM 分段寫入 Android 私有空間失敗。'
        );
        if (!quiet) {
          const percent = Math.max(1, Math.round((end / record.blob.size) * 100));
          setVaultStatus(`正在建立 Android 原生卡匣：${percent}%`, 'working');
        }
        await sleep(0);
      }
      const finished = parseNativeResult(
        native.finishImport(sessionId),
        'ROM 完整性驗證或原子寫入失敗。'
      );
      if (!finished.url || finished.sha256 !== key) {
        throw new Error('Android 原生卡匣驗證結果不完整。');
      }
      if (!quiet) {
        setVaultStatus(
          `原生卡匣建立完成：${Math.ceil(finished.byteLength / 1024 / 1024)} MB · SHA-256 已驗證`,
          'success'
        );
      }
      return { key, url: finished.url, migrated: true, byteLength: finished.byteLength };
    } catch (error) {
      try { native.cancelImport(sessionId); } catch {}
      throw error;
    }
  }

  async function migrateLibrary() {
    if (!enabled()) return { available: false, migrated: 0, total: 0 };
    if (migrationPromise) return migrationPromise;
    migrationPromise = (async () => {
      const records = await getAllRecords();
      let migrated = 0;
      let protectedCount = 0;
      for (const record of records) {
        if (!recordHasRom(record)) continue;
        try {
          const result = await migrateRecord(record, { quiet: true });
          if (result.migrated) migrated += 1;
          protectedCount += 1;
        } catch (error) {
          console.warn('[Native Cartridge Vault]', record?.id, error);
        }
      }
      setVaultStatus(
        protectedCount
          ? `Android 原生卡匣庫：${protectedCount} 款已保護${migrated ? `，本次搬移 ${migrated} 款` : ''}。`
          : '尚無可搬移的 ROM；下一次匯入會直接建立 Android 原生卡匣。',
        protectedCount ? 'success' : ''
      );
      return { available: true, migrated, total: protectedCount };
    })().finally(() => {
      migrationPromise = null;
    });
    return migrationPromise;
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

  function requestOrientation(mode) {
    if (!document.documentElement.dataset.nativeShell) return;
    const link = document.createElement('a');
    link.href = `amin://orientation?mode=${encodeURIComponent(mode)}`;
    link.hidden = true;
    document.body.appendChild(link);
    link.click();
    link.remove();
  }

  function resetEmulator() {
    loaderScript?.remove();
    loaderScript = null;
    $('game')?.replaceChildren();
    delete window.EJS_emulator;
    delete window.EJS_Runtime;
    delete window.EJS_ready;
    delete window.EJS_onGameStart;
    delete window.EJS_onExit;
  }

  function showPlayer(record) {
    requestOrientation('landscape');
    $('libraryView')?.classList.add('hidden');
    $('playerView')?.classList.remove('hidden');
    $('gameLoading')?.classList.remove('hidden');
    if ($('activeGameTitle')) {
      $('activeGameTitle').textContent = `正在從 Android 卡匣庫啟動 ${cleanName(record.name)}…`;
    }
    document.body.classList.add('playing');
  }

  function showFailure(message) {
    window.__AMIN_GBA_CORE_READY__ = false;
    requestOrientation('portrait');
    document.body.classList.remove('playing');
    $('playerView')?.classList.add('hidden');
    $('libraryView')?.classList.remove('hidden');
    $('gameLoading')?.classList.add('hidden');
    setStatus('啟動失敗', 'error');
    setImportStatus(`${message} ROM 與存檔原檔均未刪除。`, 'error');
  }

  function dispatchLaunch(status, detail = {}) {
    const payload = {
      status,
      at: Date.now(),
      runtimeVersion: localStorage.getItem('amin.runtime.version') || null,
      nativeVersion: window.AMIN_NATIVE_SHELL?.state?.capabilities?.nativeVersion || null,
      nativeVaultV2: true,
      ...detail
    };
    try { localStorage.setItem('amin.gba.lastLaunch', JSON.stringify(payload)); } catch {}
    dispatchEvent(new CustomEvent('amin-gba-launch-status', { detail: payload }));
  }

  async function reportFailure(record, message, stage = 'native-vault-v2') {
    dispatchLaunch('failed', {
      stage,
      romId: record?.id || null,
      message,
      transport: 'android-private-file',
      engine: 'apk-bundled-emulatorjs-4.2.3',
      core: 'gba/mgba'
    });
    try {
      await window.AMIN_ERROR_REPORTER?.enqueue?.({
        category: 'emulator-launch',
        stage,
        message,
        environment: {
          emulatorChannel: 'apk-bundled-4.2.3',
          romHashPrefix: /^[a-f0-9]{8,64}$/i.test(record?.id || '')
            ? String(record.id).slice(0, 12)
            : null,
          lastSuccessfulStage: stage
        }
      });
      await window.AMIN_ERROR_REPORTER?.sendQueue?.();
    } catch {}
  }

  function emulatorMessage() {
    const emulator = window.EJS_emulator;
    return String(
      emulator?.textElem?.innerText
      || $('game')?.querySelector('.ejs_error_text')?.innerText
      || $('game')?.innerText
      || ''
    ).trim();
  }

  async function waitForStart(state) {
    const startedAt = Date.now();
    while (Date.now() - startedAt < START_TIMEOUT_MS) {
      if (state.loaderError) throw new Error(state.loaderError);
      if (state.started || window.__AMIN_GBA_CORE_READY__ === true) return true;
      if (window.EJS_emulator?.failedToStart) {
        throw new Error(emulatorMessage() || '內建 mGBA 核心回報啟動失敗。');
      }
      await sleep(200);
    }
    throw new Error('內建 mGBA 核心在 60 秒內沒有完成啟動。');
  }

  async function launch(id) {
    if (!enabled() || launchBusy) return false;
    launchBusy = true;
    let record = null;
    try {
      record = await getRecord(id);
      if (!recordHasRom(record)) {
        throw new Error('舊遊戲庫已沒有完整 ROM，請重新匯入合法卡帶備份一次。');
      }

      setStatus('建立原生卡匣…');
      const cartridge = await migrateRecord(record);
      const native = bridge();
      const engineBase = native.getEngineBaseUrl();
      const romUrl = native.getRomUrl(cartridge.key);
      if (!engineBase || !romUrl) throw new Error('APK 本地引擎或 ROM 路由尚未準備完成。');

      resetEmulator();
      window.__AMIN_GBA_NATIVE_ENGINE_ACTIVE__ = true;
      window.__AMIN_GBA_CORE_READY__ = false;

      const saveKey = record.saveKey || record.id || cartridge.key;
      const stableName = `amin-${saveKey}`;
      window.AMIN_GBA_SAVE_GUARD?.setGame?.({
        saveKey,
        romId: record.id || cartridge.key,
        romName: record.name,
        stableName
      });

      showPlayer(record);
      setStatus('啟動本地 mGBA…');
      setImportStatus('ROM 與模擬器核心皆從 APK 私有空間讀取，不使用 CDN 或 Blob URL。', 'working');

      const state = { started: false, loaderError: '' };
      window.EJS_player = '#game';
      window.EJS_core = 'gba';
      window.EJS_pathtodata = engineBase;
      window.EJS_gameUrl = romUrl;
      window.EJS_gameName = stableName;
      window.EJS_gameID = numericGameId(saveKey);
      window.EJS_startOnLoaded = true;
      window.EJS_fullscreenOnLoaded = false;
      window.EJS_browserMode = 1;
      window.EJS_threads = false;
      window.EJS_forceLegacyCores = false;
      window.EJS_dontExtractRom = true;
      window.EJS_disableDatabases = false;
      window.EJS_disableLocalStorage = false;
      window.EJS_language = 'en-US';
      window.EJS_disableAutoLang = false;
      window.EJS_DEBUG_XX = false;
      window.EJS_askBeforeExit = true;
      window.EJS_fixedSaveInterval = 10000;

      window.EJS_ready = () => {
        dispatchLaunch('emulator-ready', {
          romId: record.id,
          engineBase,
          transport: 'android-private-file'
        });
      };
      window.EJS_onGameStart = () => {
        state.started = true;
        window.__AMIN_GBA_CORE_READY__ = true;
        $('gameLoading')?.classList.add('hidden');
        setTimeout(() => {
          window.AMIN_GBA_SAVE_GUARD?.restore?.().catch?.(() => {});
        }, 900);
      };
      window.EJS_onExit = () => {
        window.__AMIN_GBA_CORE_READY__ = false;
        window.AMIN_GBA_SAVE_GUARD?.flush?.('exit').catch?.(() => {});
        dispatchLaunch('emulator-exit', { romId: record.id });
      };

      dispatchLaunch('starting', {
        romId: record.id,
        cartridgeKey: cartridge.key,
        romUrl,
        engineBase,
        transport: 'android-private-file',
        engine: 'apk-bundled-emulatorjs-4.2.3',
        core: 'gba/mgba'
      });

      loaderScript = document.createElement('script');
      loaderScript.id = 'amin-native-vault-v2-loader';
      loaderScript.src = `${engineBase}loader.js`;
      loaderScript.async = true;
      loaderScript.onerror = () => {
        state.loaderError = 'APK 內建 EmulatorJS loader 無法讀取。';
      };
      document.body.appendChild(loaderScript);

      await waitForStart(state);
      $('gameLoading')?.classList.add('hidden');
      setStatus('本地 mGBA 運作中', 'active');
      setImportStatus(
        `啟動成功：ROM ${Math.ceil(cartridge.byteLength / 1024 / 1024)} MB 已由 Android 私有卡匣庫供應。`,
        'success'
      );
      dispatchLaunch('started', {
        romId: record.id,
        cartridgeKey: cartridge.key,
        transport: 'android-private-file'
      });
      return true;
    } catch (error) {
      const message = error?.message || emulatorMessage() || 'Native Cartridge Vault v2 啟動失敗。';
      await reportFailure(record, message);
      showFailure(message);
      return false;
    } finally {
      launchBusy = false;
    }
  }

  async function capturePlay(event) {
    if (!enabled()) return;
    const button = event.target.closest?.('[data-play]');
    if (!button) return;
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
    await launch(button.dataset.play);
  }

  window.addEventListener('click', capturePlay, true);
  $('romInput')?.addEventListener('change', () => {
    setTimeout(() => migrateLibrary().catch(() => {}), 1800);
    setTimeout(() => migrateLibrary().catch(() => {}), 6000);
  });
  addEventListener('amin-native-cartridge-imported', () => {
    setTimeout(() => migrateLibrary().catch(() => {}), 1200);
  });

  window.AMIN_NATIVE_CARTRIDGE_VAULT_V2 = {
    version: '2.0.0',
    available: enabled,
    migrateRecord,
    migrateLibrary,
    launch
  };

  if (enabled()) {
    document.documentElement.dataset.nativeCartridgeVault = 'v2';
    setTimeout(() => migrateLibrary().catch(error => {
      setVaultStatus(error?.message || '原生卡匣庫搬移失敗。', 'error');
    }), 1100);
  }
})();
