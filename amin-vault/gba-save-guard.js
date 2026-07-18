(() => {
  'use strict';

  const VAULT_DB = 'amin-gba-save-vault';
  const VAULT_VERSION = 1;
  const VAULT_STORE = 'saves';
  const EXIT_DELAY_MS = 900;
  const MAX_SAVE_BYTES = 2 * 1024 * 1024;

  let gameContext = null;
  let exiting = false;
  let lastFlushAt = 0;
  let flushChain = Promise.resolve(true);

  const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

  function emulatorReady() {
    return Boolean(
      window.EJS_emulator &&
      window.EJS_emulator.started &&
      window.EJS_emulator.gameManager
    );
  }

  function normalizeBytes(value) {
    if (!value) return null;
    if (value instanceof Uint8Array) return new Uint8Array(value);
    if (value instanceof ArrayBuffer) return new Uint8Array(value.slice(0));
    if (ArrayBuffer.isView(value)) {
      return new Uint8Array(value.buffer.slice(value.byteOffset, value.byteOffset + value.byteLength));
    }
    return null;
  }

  function showSaveMessage(text) {
    const loading = document.getElementById('gameLoading');
    const title = document.getElementById('activeGameTitle');
    if (title) title.textContent = text;
    loading?.classList.remove('hidden');
  }

  function updateVaultStatus(text, kind = '') {
    const target = document.getElementById('saveVaultStatus');
    if (!target) return;
    target.textContent = text;
    target.className = `storage-status ${kind}`.trim();
  }

  function recordSaveStatus(status, detail = '') {
    const payload = {
      status,
      detail,
      at: Date.now(),
      gameId: window.EJS_gameID || null,
      gameName: window.EJS_gameName || null,
      saveKey: gameContext?.saveKey || null,
      romId: gameContext?.romId || null
    };
    try {
      localStorage.setItem('amin-gba-save-status', JSON.stringify(payload));
    } catch {}
    dispatchEvent(new CustomEvent('amin-save-status', { detail: payload }));
  }

  function openVaultDb() {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(VAULT_DB, VAULT_VERSION);
      request.onupgradeneeded = () => {
        const db = request.result;
        if (!db.objectStoreNames.contains(VAULT_STORE)) {
          const store = db.createObjectStore(VAULT_STORE, { keyPath: 'key' });
          store.createIndex('updatedAt', 'updatedAt');
          store.createIndex('romId', 'romId');
        }
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error('無法開啟 Amin 存檔保險庫。'));
    });
  }

  function requestResult(request) {
    return new Promise((resolve, reject) => {
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error('存檔資料庫操作失敗。'));
    });
  }

  async function getIndexedSave(key) {
    const db = await openVaultDb();
    try {
      return await requestResult(db.transaction(VAULT_STORE, 'readonly').objectStore(VAULT_STORE).get(key));
    } finally {
      db.close();
    }
  }

  async function putIndexedSave(record) {
    const db = await openVaultDb();
    try {
      await new Promise((resolve, reject) => {
        const transaction = db.transaction(VAULT_STORE, 'readwrite');
        transaction.objectStore(VAULT_STORE).put(record);
        transaction.oncomplete = resolve;
        transaction.onerror = () => reject(transaction.error || new Error('無法寫入 Amin 存檔保險庫。'));
        transaction.onabort = () => reject(transaction.error || new Error('存檔交易已中止。'));
      });
    } finally {
      db.close();
    }
  }

  async function sha256(bytes) {
    const digest = await crypto.subtle.digest(
      'SHA-256',
      bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength)
    );
    return [...new Uint8Array(digest)].map(value => value.toString(16).padStart(2, '0')).join('');
  }

  function bytesToBase64(bytes) {
    let binary = '';
    const chunkSize = 0x8000;
    for (let offset = 0; offset < bytes.length; offset += chunkSize) {
      binary += String.fromCharCode(...bytes.subarray(offset, offset + chunkSize));
    }
    return btoa(binary);
  }

  function base64ToBytes(value) {
    if (!value) return null;
    const binary = atob(value);
    const bytes = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
    return bytes;
  }

  function nativeBridgeAvailable() {
    return Boolean(
      window.AminNativeSaveVault &&
      typeof window.AminNativeSaveVault.putSave === 'function' &&
      typeof window.AminNativeSaveVault.getSave === 'function'
    );
  }

  function readNativeInfo(key) {
    if (!nativeBridgeAvailable()) return null;
    try {
      return JSON.parse(window.AminNativeSaveVault.getSaveInfo(key) || '{}');
    } catch {
      return null;
    }
  }

  function readNativeSave(key) {
    if (!nativeBridgeAvailable()) return null;
    try {
      return base64ToBytes(window.AminNativeSaveVault.getSave(key));
    } catch {
      return null;
    }
  }

  function writeNativeSave(key, bytes) {
    if (!nativeBridgeAvailable()) return { ok: false, skipped: true };
    try {
      return JSON.parse(window.AminNativeSaveVault.putSave(key, bytesToBase64(bytes)) || '{}');
    } catch (error) {
      return { ok: false, error: error?.message || '原生存檔橋接失敗' };
    }
  }

  async function putVaultSave(bytes, source = 'unknown') {
    const normalized = normalizeBytes(bytes);
    if (!gameContext?.saveKey || !normalized?.byteLength) return null;
    if (normalized.byteLength > MAX_SAVE_BYTES) throw new Error('GBA 存檔大小異常，已停止寫入。');

    const updatedAt = Date.now();
    const hash = await sha256(normalized);
    const data = normalized.buffer.slice(normalized.byteOffset, normalized.byteOffset + normalized.byteLength);
    const record = {
      key: gameContext.saveKey,
      romId: gameContext.romId || null,
      romName: gameContext.romName || null,
      stableName: gameContext.stableName || null,
      data,
      byteLength: normalized.byteLength,
      sha256: hash,
      updatedAt,
      source,
      schemaVersion: 1
    };

    const indexedResult = putIndexedSave(record)
      .then(() => ({ ok: true }))
      .catch(error => ({ ok: false, error }));
    const nativeResult = writeNativeSave(gameContext.saveKey, normalized);
    const indexed = await indexedResult;

    if (!indexed.ok && !nativeResult.ok) {
      throw indexed.error || new Error(nativeResult.error || '兩層存檔都寫入失敗。');
    }

    const detail = {
      saveKey: gameContext.saveKey,
      romId: gameContext.romId,
      byteLength: normalized.byteLength,
      sha256: hash,
      updatedAt,
      indexedDb: indexed.ok,
      native: Boolean(nativeResult.ok),
      nativeBackup: Boolean(nativeResult.hasBackup),
      source
    };
    recordSaveStatus('verified', JSON.stringify(detail));
    updateVaultStatus(
      `最近保護：${new Date(updatedAt).toLocaleString('zh-TW')} · ${normalized.byteLength} bytes · ${nativeResult.ok ? '原生＋IndexedDB' : 'IndexedDB'}`,
      'success'
    );
    dispatchEvent(new CustomEvent('amin-save-verified', { detail }));
    return record;
  }

  async function getBestVaultSave(key) {
    if (!key) return null;
    const indexed = await getIndexedSave(key).catch(() => null);
    const nativeInfo = readNativeInfo(key);
    const nativeBytes = nativeInfo?.exists ? readNativeSave(key) : null;

    if (nativeBytes?.byteLength && (!indexed || Number(nativeInfo.updatedAt) > Number(indexed.updatedAt || 0))) {
      const mirrored = {
        key,
        romId: gameContext?.romId || indexed?.romId || null,
        romName: gameContext?.romName || indexed?.romName || null,
        stableName: gameContext?.stableName || indexed?.stableName || null,
        data: nativeBytes.buffer.slice(nativeBytes.byteOffset, nativeBytes.byteOffset + nativeBytes.byteLength),
        byteLength: nativeBytes.byteLength,
        sha256: nativeInfo.sha256 || await sha256(nativeBytes),
        updatedAt: Number(nativeInfo.updatedAt) || Date.now(),
        source: 'native-recovery',
        schemaVersion: 1
      };
      await putIndexedSave(mirrored).catch(() => {});
      return { ...mirrored, bytes: nativeBytes };
    }

    if (indexed?.data) {
      const bytes = normalizeBytes(indexed.data);
      if (bytes?.byteLength) {
        if (!nativeBytes?.byteLength || Number(indexed.updatedAt) > Number(nativeInfo?.updatedAt || 0)) {
          writeNativeSave(key, bytes);
        }
        return { ...indexed, bytes };
      }
    }

    if (nativeBytes?.byteLength) {
      return {
        key,
        data: nativeBytes.buffer,
        bytes: nativeBytes,
        byteLength: nativeBytes.byteLength,
        updatedAt: Number(nativeInfo?.updatedAt) || Date.now(),
        sha256: nativeInfo?.sha256 || await sha256(nativeBytes),
        source: 'native-only'
      };
    }
    return null;
  }

  function ensureParentDirectories(fs, path) {
    const parts = path.split('/');
    let current = '';
    for (let index = 0; index < parts.length - 1; index += 1) {
      if (!parts[index]) continue;
      current += `/${parts[index]}`;
      if (!fs.analyzePath(current).exists) fs.mkdir(current);
    }
  }

  function readEmulatorSave() {
    if (!emulatorReady()) return null;
    try {
      return normalizeBytes(window.EJS_emulator.gameManager.getSaveFile(false));
    } catch {
      return null;
    }
  }

  function syncFileSystem(populate = false) {
    return new Promise((resolve, reject) => {
      if (!emulatorReady()) return resolve({ supported: false });
      const fs = window.EJS_emulator.gameManager.FS;
      if (!fs || typeof fs.syncfs !== 'function') return resolve({ supported: false });
      try {
        fs.syncfs(populate, error => {
          if (error) reject(error);
          else resolve({ supported: true });
        });
      } catch (error) {
        reject(error);
      }
    });
  }

  async function restoreVaultIfNeeded() {
    if (!emulatorReady() || !gameContext?.saveKey) return false;
    const gameManager = window.EJS_emulator.gameManager;
    const path = gameManager.getSaveFilePath();
    const existing = readEmulatorSave();

    if (existing?.byteLength) {
      await putVaultSave(existing, 'idbfs-primary');
      recordSaveStatus('primary-save-loaded', `${existing.byteLength} bytes`);
      return false;
    }

    const vaulted = await getBestVaultSave(gameContext.saveKey);
    if (!vaulted?.bytes?.byteLength) {
      recordSaveStatus('no-existing-save', gameContext.saveKey);
      updateVaultStatus('這款遊戲尚未建立可恢復的存檔。');
      return false;
    }

    const fs = gameManager.FS;
    ensureParentDirectories(fs, path);
    if (fs.analyzePath(path).exists) fs.unlink(path);
    fs.writeFile(path, vaulted.bytes);
    gameManager.loadSaveFiles();
    await syncFileSystem(false);
    recordSaveStatus('restored-from-vault', `${vaulted.bytes.byteLength} bytes`);
    updateVaultStatus(
      `已從 Amin 存檔保險庫恢復 ${vaulted.bytes.byteLength} bytes。`,
      'success'
    );
    window.EJS_emulator.displayMessage?.('已恢復 Amin 保險存檔');
    return true;
  }

  async function performFlush(reason) {
    if (!emulatorReady()) {
      recordSaveStatus('flush-skipped', `${reason}: emulator-not-ready`);
      return false;
    }

    const now = Date.now();
    if (!['exit', 'backup', 'manual', 'android-pause'].includes(reason) && now - lastFlushAt < 1200) {
      return true;
    }

    const gameManager = window.EJS_emulator.gameManager;
    try {
      gameManager.saveSaveFiles();
      await sleep(160);
      let bytes = readEmulatorSave();
      if (bytes?.byteLength) await putVaultSave(bytes, reason);

      await syncFileSystem(false);
      bytes = readEmulatorSave();
      if (bytes?.byteLength) await putVaultSave(bytes, `${reason}-synced`);

      lastFlushAt = Date.now();
      recordSaveStatus('flush-verified', `${reason}: ${bytes?.byteLength || 0} bytes`);
      return Boolean(bytes?.byteLength);
    } catch (error) {
      console.warn('Amin GBA save flush failed:', error);
      recordSaveStatus('flush-failed', error?.message || reason);
      updateVaultStatus(`存檔寫入失敗：${error?.message || reason}`, 'error');
      return false;
    }
  }

  function flushCartridgeSave(reason = 'manual') {
    flushChain = flushChain
      .catch(() => false)
      .then(() => performFlush(reason));
    return flushChain;
  }

  async function exitSafely() {
    if (exiting) return;
    const confirmed = window.confirm('離開遊戲並保存目前進度？系統會驗證 mGBA、IndexedDB 與原生保險庫。');
    if (!confirmed) return;

    exiting = true;
    showSaveMessage('正在驗證三層存檔…');
    const flushed = await flushCartridgeSave('exit');

    if (!flushed) {
      const leaveAnyway = window.confirm('存檔驗證沒有完成。仍要離開嗎？');
      if (!leaveAnyway) {
        exiting = false;
        document.getElementById('gameLoading')?.classList.add('hidden');
        return;
      }
    }

    try {
      if (emulatorReady() && typeof window.EJS_emulator.callEvent === 'function') {
        window.EJS_emulator.callEvent('exit');
      }
    } catch (error) {
      console.warn('Amin GBA exit event failed:', error);
      recordSaveStatus('exit-event-failed', error?.message || 'unknown');
    }

    await sleep(EXIT_DELAY_MS);
    try { screen.orientation?.unlock?.(); } catch {}
    try {
      if (document.fullscreenElement) await document.exitFullscreen();
    } catch {}

    const next = new URL('./gba.html', location.href);
    next.searchParams.set('native', '1');
    next.searchParams.set('saveReturn', String(Date.now()));
    location.replace(next.href);
  }

  function setGame(context) {
    if (!context?.saveKey) throw new Error('缺少穩定存檔識別碼。');
    gameContext = {
      saveKey: String(context.saveKey),
      romId: context.romId ? String(context.romId) : null,
      romName: context.romName ? String(context.romName) : null,
      stableName: context.stableName ? String(context.stableName) : null
    };
    recordSaveStatus('game-identity-ready', gameContext.saveKey);
  }

  async function getSummary(key = gameContext?.saveKey) {
    const record = await getBestVaultSave(key).catch(() => null);
    if (!record) return null;
    return {
      key,
      byteLength: record.byteLength || record.bytes?.byteLength || 0,
      updatedAt: record.updatedAt || 0,
      sha256: record.sha256 || '',
      native: Boolean(readNativeInfo(key)?.exists)
    };
  }

  const previousOnGameStart = window.EJS_onGameStart;
  window.EJS_onGameStart = async event => {
    try {
      if (typeof previousOnGameStart === 'function') await previousOnGameStart(event);
    } finally {
      await restoreVaultIfNeeded().catch(error => {
        recordSaveStatus('restore-failed', error?.message || 'unknown');
        updateVaultStatus(`存檔恢復失敗：${error?.message || '未知錯誤'}`, 'error');
      });
    }
  };

  const previousOnSaveUpdate = window.EJS_onSaveUpdate;
  window.EJS_onSaveUpdate = event => {
    try {
      if (typeof previousOnSaveUpdate === 'function') previousOnSaveUpdate(event);
    } finally {
      const bytes = normalizeBytes(event?.save);
      if (bytes?.byteLength) {
        putVaultSave(bytes, 'save-update').catch(error => {
          recordSaveStatus('save-update-failed', error?.message || 'unknown');
        });
      }
    }
  };

  const previousOnExit = window.EJS_onExit;
  window.EJS_onExit = event => {
    if (typeof previousOnExit === 'function') previousOnExit(event);
    recordSaveStatus('emulator-exited');
  };

  document.addEventListener('click', event => {
    const exitButton = event.target.closest('#backToLibrary');
    if (!exitButton) return;
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
    exitSafely();
  }, true);

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden' && !exiting) {
      flushCartridgeSave('background');
    }
  });

  addEventListener('pagehide', () => {
    if (!exiting) flushCartridgeSave('pagehide');
  });

  document.getElementById('verifySaveVaultButton')?.addEventListener('click', async () => {
    const summary = await getSummary();
    if (!summary) {
      updateVaultStatus('目前沒有可驗證的遊戲存檔。');
      return;
    }
    updateVaultStatus(
      `保險庫正常：${summary.byteLength} bytes · ${new Date(summary.updatedAt).toLocaleString('zh-TW')} · ${summary.native ? '含原生副本' : 'IndexedDB 副本'}`,
      'success'
    );
  });

  window.AMIN_GBA_SAVE_GUARD = {
    setGame,
    flush: flushCartridgeSave,
    restore: restoreVaultIfNeeded,
    getSummary,
    exitSafely,
    version: '1.0.0'
  };
})();
