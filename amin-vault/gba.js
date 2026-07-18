(() => {
  'use strict';

  const DB_NAME = 'amin-pocket-rom-library';
  const DB_VERSION = 2;
  const STORE_NAME = 'roms';
  const MAX_ROM_BYTES = 64 * 1024 * 1024;
  const MAX_ARCHIVE_BYTES = 160 * 1024 * 1024;
  const ROM_PATTERN = /\.(gba|bin)$/i;
  const EMULATORJS_VERSION = '4.2.3';
  const EMULATOR_DATA_PATH = `https://cdn.emulatorjs.org/${EMULATORJS_VERSION}/data/`;

  let activeObjectUrl = null;
  let emulatorLoaderScript = null;

  const $ = id => document.getElementById(id);
  const statusBadge = $('gbaStatus');
  const storageText = $('storageStatus');
  const importText = $('importStatus');
  const libraryList = $('romLibrary');
  const emptyState = $('emptyLibrary');
  const playerView = $('playerView');
  const libraryView = $('libraryView');
  const gameTitle = $('activeGameTitle');

  function requestNativeOrientation(mode) {
    if (!document.documentElement.dataset.nativeShell) return;
    const link = document.createElement('a');
    link.href = `amin://orientation?mode=${encodeURIComponent(mode)}`;
    link.hidden = true;
    document.body.appendChild(link);
    link.click();
    link.remove();
  }

  function setStatus(text, kind = '') {
    statusBadge.textContent = text;
    statusBadge.className = `gba-badge ${kind}`.trim();
  }

  function setImportStatus(text, kind = '') {
    importText.textContent = text;
    importText.className = `storage-status ${kind}`.trim();
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function releaseActiveObjectUrl() {
    if (!activeObjectUrl) return;
    URL.revokeObjectURL(activeObjectUrl);
    activeObjectUrl = null;
  }

  function resetPlayerAfterFailure(message) {
    releaseActiveObjectUrl();
    emulatorLoaderScript?.remove();
    emulatorLoaderScript = null;
    requestNativeOrientation('portrait');
    document.body.classList.remove('playing');
    playerView.classList.add('hidden');
    libraryView.classList.remove('hidden');
    setStatus('啟動失敗', 'error');
    window.alert(message || '遊戲啟動失敗。');
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
      request.onerror = () => reject(request.error || new Error('無法開啟本機遊戲庫。'));
    });
  }

  function requestToPromise(request) {
    return new Promise((resolve, reject) => {
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error('資料讀取失敗。'));
    });
  }

  async function getAllRoms() {
    const db = await openDb();
    try {
      const transaction = db.transaction(STORE_NAME, 'readonly');
      const records = await requestToPromise(transaction.objectStore(STORE_NAME).getAll());
      return records.sort((a, b) => (b.lastPlayedAt || b.addedAt) - (a.lastPlayedAt || a.addedAt));
    } finally {
      db.close();
    }
  }

  async function getRom(id) {
    const db = await openDb();
    try {
      const transaction = db.transaction(STORE_NAME, 'readonly');
      return await requestToPromise(transaction.objectStore(STORE_NAME).get(id));
    } finally {
      db.close();
    }
  }

  async function getRomBySaveKey(saveKey) {
    if (!saveKey) return null;
    const db = await openDb();
    try {
      const transaction = db.transaction(STORE_NAME, 'readonly');
      const store = transaction.objectStore(STORE_NAME);
      if (!store.indexNames.contains('saveKey')) return null;
      return await requestToPromise(store.index('saveKey').get(saveKey));
    } finally {
      db.close();
    }
  }

  async function putRom(record) {
    const db = await openDb();
    try {
      await new Promise((resolve, reject) => {
        const transaction = db.transaction(STORE_NAME, 'readwrite');
        transaction.objectStore(STORE_NAME).put(record);
        transaction.oncomplete = resolve;
        transaction.onerror = () => reject(transaction.error || new Error('無法儲存遊戲。'));
        transaction.onabort = () => reject(transaction.error || new Error('遊戲資料交易已中止。'));
      });
    } finally {
      db.close();
    }
  }

  async function deleteRom(id) {
    const db = await openDb();
    try {
      await new Promise((resolve, reject) => {
        const transaction = db.transaction(STORE_NAME, 'readwrite');
        transaction.objectStore(STORE_NAME).delete(id);
        transaction.oncomplete = resolve;
        transaction.onerror = () => reject(transaction.error || new Error('無法刪除遊戲。'));
      });
    } finally {
      db.close();
    }
  }

  function formatBytes(bytes) {
    if (!Number.isFinite(bytes) || bytes <= 0) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB'];
    const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
    return `${(bytes / (1024 ** index)).toFixed(index ? 1 : 0)} ${units[index]}`;
  }

  async function hashBlob(blob) {
    const digest = await crypto.subtle.digest('SHA-256', await blob.arrayBuffer());
    return [...new Uint8Array(digest)].map(value => value.toString(16).padStart(2, '0')).join('');
  }

  function numericGameId(value) {
    let hash = 2166136261;
    for (const char of String(value)) {
      hash ^= char.charCodeAt(0);
      hash = Math.imul(hash, 16777619);
    }
    return Math.abs(hash | 0) || 1;
  }

  function baseName(path) {
    return String(path || '').split('/').pop().split('\\').pop();
  }

  function cleanGameName(name) {
    return baseName(name).replace(/\.(gba|bin|zip)$/i, '').trim() || 'GBA Game';
  }

  async function ensureSaveIdentity(record) {
    if (!record?.blob) return record;
    if (record.saveKey && /^[a-f0-9]{64}$/i.test(record.saveKey)) return record;
    const saveKey = await hashBlob(record.blob);
    const updated = {
      ...record,
      saveKey,
      saveIdentityVersion: 1
    };
    await putRom(updated);
    return updated;
  }

  async function requestPersistentStorage() {
    if (!navigator.storage?.persist) return false;
    try {
      return await navigator.storage.persist();
    } catch {
      return false;
    }
  }

  async function updateStorageStatus() {
    if (!navigator.storage?.estimate) {
      storageText.textContent = '此瀏覽器未提供儲存空間估算。';
      return;
    }
    try {
      const { usage = 0, quota = 0 } = await navigator.storage.estimate();
      const persisted = navigator.storage.persisted ? await navigator.storage.persisted() : false;
      storageText.textContent = `本機使用 ${formatBytes(usage)} / ${formatBytes(quota)} · ${persisted ? '持久儲存已啟用' : '可能由瀏覽器清理'}`;
    } catch {
      storageText.textContent = '暫時無法讀取儲存空間狀態。';
    }
  }

  function saveLabel(record) {
    if (!record.saveUpdatedAt) return '存檔：尚未建立保險副本';
    const at = new Date(record.saveUpdatedAt).toLocaleString('zh-TW');
    const bytes = record.saveByteLength ? ` · ${record.saveByteLength} bytes` : '';
    return `存檔：已保護 ${at}${bytes}`;
  }

  async function renderLibrary() {
    try {
      let roms = await getAllRoms();
      const migrated = [];
      for (const record of roms) {
        migrated.push(await ensureSaveIdentity(record));
      }
      roms = migrated;

      emptyState.classList.toggle('hidden', roms.length > 0);
      libraryList.innerHTML = roms.map(record => `
        <article class="rom-card">
          <div class="rom-icon">GBA</div>
          <div class="rom-info">
            <strong>${escapeHtml(cleanGameName(record.name))}</strong>
            <span>${escapeHtml(record.name)} · ${formatBytes(record.size)}</span>
            <small>${record.sourcePack ? `來源：${escapeHtml(record.sourcePack)} · ` : ''}${record.lastPlayedAt ? `最近遊玩：${new Date(record.lastPlayedAt).toLocaleString('zh-TW')}` : `匯入：${new Date(record.addedAt).toLocaleString('zh-TW')}`}</small>
            <small class="save-protection-line">${escapeHtml(saveLabel(record))}</small>
          </div>
          <div class="rom-actions">
            <button data-play="${escapeHtml(record.id)}">開始</button>
            <button class="secondary" data-delete="${escapeHtml(record.id)}">移除 ROM</button>
          </div>
        </article>
      `).join('');
      setStatus(roms.length ? `${roms.length} 款遊戲` : '等待匯入', roms.length ? 'active' : '');
      await updateStorageStatus();
    } catch (error) {
      setStatus('遊戲庫錯誤', 'error');
      libraryList.innerHTML = `<p class="gba-message error">${escapeHtml(error.message)}</p>`;
    }
  }

  async function storeRomBlob({ name, blob, modified = 0, sourcePack = null }) {
    if (!ROM_PATTERN.test(name)) return { imported: false, reason: 'unsupported' };
    if (!blob.size || blob.size > MAX_ROM_BYTES) return { imported: false, reason: 'size' };

    const normalizedName = baseName(name);
    const saveKey = await hashBlob(blob);
    const existing = await getRomBySaveKey(saveKey);
    const id = existing?.id || saveKey.slice(0, 24);
    await putRom({
      id,
      saveKey,
      saveIdentityVersion: 1,
      name: normalizedName,
      size: blob.size,
      type: blob.type || 'application/octet-stream',
      blob,
      sourcePack,
      addedAt: existing?.addedAt || Date.now(),
      lastPlayedAt: existing?.lastPlayedAt || null,
      saveUpdatedAt: existing?.saveUpdatedAt || null,
      saveByteLength: existing?.saveByteLength || null,
      originalModifiedAt: modified || null
    });
    return { imported: true, duplicate: Boolean(existing), id, saveKey };
  }

  function unzipArchive(file) {
    return new Promise(async (resolve, reject) => {
      if (!window.fflate?.unzip) {
        reject(new Error('ZIP 解壓縮元件尚未載入，請重新整理後再試。'));
        return;
      }
      try {
        const buffer = new Uint8Array(await file.arrayBuffer());
        window.fflate.unzip(buffer, {
          filter: entry => ROM_PATTERN.test(entry.name)
        }, (error, entries) => {
          if (error) reject(error);
          else resolve(entries || {});
        });
      } catch (error) {
        reject(error);
      }
    });
  }

  async function importArchive(file) {
    if (file.size > MAX_ARCHIVE_BYTES) {
      throw new Error(`合集 ZIP 超過 ${formatBytes(MAX_ARCHIVE_BYTES)}，未匯入。`);
    }

    setImportStatus(`正在解開「${file.name}」並建立永久遊戲身分…`, 'working');
    const entries = await unzipArchive(file);
    const romEntries = Object.entries(entries).filter(([name]) => ROM_PATTERN.test(name));
    if (!romEntries.length) {
      throw new Error('這個 ZIP 裡沒有找到 `.gba` 或 `.bin` 遊戲。');
    }

    let imported = 0;
    let duplicates = 0;
    let skipped = 0;
    for (let index = 0; index < romEntries.length; index += 1) {
      const [path, bytes] = romEntries[index];
      setImportStatus(`正在加入 ${index + 1} / ${romEntries.length}：${baseName(path)}`, 'working');
      const blob = new Blob([bytes], { type: 'application/octet-stream' });
      const result = await storeRomBlob({
        name: path,
        blob,
        modified: file.lastModified,
        sourcePack: file.name
      });
      if (result.imported) {
        imported += 1;
        if (result.duplicate) duplicates += 1;
      } else {
        skipped += 1;
      }
    }

    return { imported, duplicates, skipped, total: romEntries.length };
  }

  async function importFiles(fileList) {
    const files = [...fileList];
    if (!files.length) return;
    setStatus('正在匯入…');
    let imported = 0;
    let duplicates = 0;
    let skipped = 0;

    try {
      for (const file of files) {
        if (/\.zip$/i.test(file.name)) {
          const result = await importArchive(file);
          imported += result.imported;
          duplicates += result.duplicates;
          skipped += result.skipped;
          continue;
        }

        if (!ROM_PATTERN.test(file.name)) {
          skipped += 1;
          continue;
        }

        setImportStatus(`正在建立永久身分：${file.name}`, 'working');
        const result = await storeRomBlob({
          name: file.name,
          blob: file,
          modified: file.lastModified
        });
        if (result.imported) {
          imported += 1;
          if (result.duplicate) duplicates += 1;
        } else {
          skipped += 1;
        }
      }

      await requestPersistentStorage();
      await renderLibrary();
      setStatus(imported ? `已加入 ${imported} 款` : '沒有新增遊戲', imported ? 'active' : '');
      setImportStatus(`完成：處理 ${imported} 款${duplicates ? `，其中 ${duplicates} 款依 ROM 內容辨識為同一遊戲` : ''}${skipped ? `，略過 ${skipped} 個不支援或過大的檔案` : ''}。`, imported ? 'success' : '');
    } catch (error) {
      setStatus('匯入失敗', 'error');
      setImportStatus(error.message || '合集匯入失敗。', 'error');
    } finally {
      $('romInput').value = '';
    }
  }

  function showPlayer(record) {
    requestNativeOrientation('landscape');
    libraryView.classList.add('hidden');
    playerView.classList.remove('hidden');
    gameTitle.textContent = cleanGameName(record.name);
    document.body.classList.add('playing');
  }

  async function returnToLibrary() {
    const saved = await window.AMIN_GBA_SAVE_GUARD?.flush?.('leave-game');
    if (saved === false) {
      const leaveAnyway = window.confirm('存檔尚未完成驗證，仍要回遊戲庫嗎？');
      if (!leaveAnyway) return;
    }
    requestNativeOrientation('portrait');
    releaseActiveObjectUrl();
    const next = new URL('./gba.html', location.href);
    next.searchParams.set('native', '1');
    next.searchParams.set('saveReturn', String(Date.now()));
    location.replace(next.href);
  }

  async function playRom(id) {
    if (emulatorLoaderScript || window.EJS_emulator) {
      throw new Error('模擬器已經在執行，請先回到遊戲庫後再開啟。');
    }

    let record = await getRom(id);
    if (!record?.blob) {
      window.alert('找不到這個遊戲檔案，請重新匯入。');
      return;
    }
    record = await ensureSaveIdentity(record);

    setStatus('載入 mGBA…');
    record.lastPlayedAt = Date.now();
    await putRom(record);
    await requestPersistentStorage();

    const stableBaseName = `amin-${record.saveKey}`;
    releaseActiveObjectUrl();
    activeObjectUrl = URL.createObjectURL(record.blob);

    window.AMIN_GBA_SAVE_GUARD?.setGame?.({
      saveKey: record.saveKey,
      romId: record.id,
      romName: record.name,
      stableName: stableBaseName
    });

    showPlayer(record);

    window.EJS_player = '#game';
    window.EJS_core = 'mgba';
    window.EJS_pathtodata = EMULATOR_DATA_PATH;
    window.EJS_gameUrl = activeObjectUrl;
    window.EJS_gameName = stableBaseName;
    window.EJS_gameID = numericGameId(record.saveKey);
    window.EJS_color = '#8da2ff';
    window.EJS_backgroundColor = '#05070b';
    window.EJS_volume = 0.65;
    window.EJS_startOnLoaded = true;
    window.EJS_fullscreenOnLoaded = false;
    window.EJS_browserMode = matchMedia('(pointer: coarse)').matches ? 'mobile' : 'desktop';
    window.EJS_askBeforeExit = true;
    window.EJS_fixedSaveInterval = 5000;
    window.EJS_dontExtractRom = true;
    window.EJS_DEBUG_XX = false;
    window.EJS_cacheConfig = {
      enabled: true,
      cacheMaxSizeMB: 1024,
      cacheMaxAgeMins: 43200
    };

    const script = document.createElement('script');
    emulatorLoaderScript = script;
    script.id = 'amin-emulatorjs-loader';
    script.src = `${EMULATOR_DATA_PATH}loader.js`;
    script.async = true;
    script.onload = () => setStatus(`mGBA ${EMULATORJS_VERSION} 已載入`, 'active');
    script.onerror = () => {
      resetPlayerAfterFailure('無法載入 EmulatorJS 4.2.3。請確認網路後再試。');
    };
    document.body.appendChild(script);
  }

  $('romInput').addEventListener('change', event => importFiles(event.target.files));
  $('persistButton').addEventListener('click', async () => {
    const granted = await requestPersistentStorage();
    await updateStorageStatus();
    window.alert(granted ? '已要求瀏覽器保留本機遊戲資料。' : '瀏覽器沒有授予持久儲存，但 Amin 原生存檔保險庫仍會保護遊戲存檔。');
  });
  $('backToLibrary').addEventListener('click', () => {
    if (window.confirm('離開目前遊戲並回到遊戲庫？系統會先驗證三層存檔。')) {
      returnToLibrary();
    }
  });

  libraryList.addEventListener('click', async event => {
    const playButton = event.target.closest('[data-play]');
    if (playButton) {
      try {
        await playRom(playButton.dataset.play);
      } catch (error) {
        resetPlayerAfterFailure(error.message || '遊戲啟動失敗。');
      }
      return;
    }

    const deleteButton = event.target.closest('[data-delete]');
    if (deleteButton && window.confirm('只移除 ROM？Amin 存檔保險庫會保留，未來重新匯入相同 ROM 可找回進度。')) {
      await deleteRom(deleteButton.dataset.delete);
      await renderLibrary();
    }
  });

  addEventListener('amin-save-verified', async event => {
    const detail = event.detail || {};
    if (!detail.romId) return;
    const record = await getRom(detail.romId).catch(() => null);
    if (!record) return;
    record.saveUpdatedAt = detail.updatedAt || Date.now();
    record.saveByteLength = detail.byteLength || null;
    record.saveSha256 = detail.sha256 || null;
    record.saveNativeProtected = Boolean(detail.native);
    await putRom(record).catch(() => {});
  });

  addEventListener('pagehide', releaseActiveObjectUrl);
  addEventListener('beforeunload', releaseActiveObjectUrl);

  renderLibrary();
})();
