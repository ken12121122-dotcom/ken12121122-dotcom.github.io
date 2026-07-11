(() => {
  'use strict';

  const DB_NAME = 'amin-pocket-rom-library';
  const DB_VERSION = 1;
  const STORE_NAME = 'roms';
  const MAX_ROM_BYTES = 64 * 1024 * 1024;
  const MAX_ARCHIVE_BYTES = 160 * 1024 * 1024;
  const ROM_PATTERN = /\.(gba|bin)$/i;
  let activeObjectUrl = null;

  const $ = id => document.getElementById(id);
  const statusBadge = $('gbaStatus');
  const storageText = $('storageStatus');
  const importText = $('importStatus');
  const libraryList = $('romLibrary');
  const emptyState = $('emptyLibrary');
  const playerView = $('playerView');
  const libraryView = $('libraryView');
  const gameTitle = $('activeGameTitle');

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

  function openDb() {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, DB_VERSION);
      request.onupgradeneeded = () => {
        const db = request.result;
        if (!db.objectStoreNames.contains(STORE_NAME)) {
          const store = db.createObjectStore(STORE_NAME, { keyPath: 'id' });
          store.createIndex('lastPlayedAt', 'lastPlayedAt');
          store.createIndex('addedAt', 'addedAt');
        }
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

  async function putRom(record) {
    const db = await openDb();
    try {
      await new Promise((resolve, reject) => {
        const transaction = db.transaction(STORE_NAME, 'readwrite');
        transaction.objectStore(STORE_NAME).put(record);
        transaction.oncomplete = resolve;
        transaction.onerror = () => reject(transaction.error || new Error('無法儲存遊戲。'));
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

  async function createRomId(name, size, modified = 0) {
    const raw = new TextEncoder().encode(`${name}|${size}|${modified}`);
    const digest = await crypto.subtle.digest('SHA-256', raw);
    return [...new Uint8Array(digest)].slice(0, 12).map(byte => byte.toString(16).padStart(2, '0')).join('');
  }

  function numericGameId(id) {
    let hash = 2166136261;
    for (const char of id) {
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

  async function renderLibrary() {
    try {
      const roms = await getAllRoms();
      emptyState.classList.toggle('hidden', roms.length > 0);
      libraryList.innerHTML = roms.map(record => `
        <article class="rom-card">
          <div class="rom-icon">GBA</div>
          <div class="rom-info">
            <strong>${escapeHtml(cleanGameName(record.name))}</strong>
            <span>${escapeHtml(record.name)} · ${formatBytes(record.size)}</span>
            <small>${record.sourcePack ? `來源：${escapeHtml(record.sourcePack)} · ` : ''}${record.lastPlayedAt ? `最近遊玩：${new Date(record.lastPlayedAt).toLocaleString('zh-TW')}` : `匯入：${new Date(record.addedAt).toLocaleString('zh-TW')}`}</small>
          </div>
          <div class="rom-actions">
            <button data-play="${escapeHtml(record.id)}">開始</button>
            <button class="secondary" data-delete="${escapeHtml(record.id)}">移除</button>
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
    const id = await createRomId(normalizedName, blob.size, modified);
    const existing = await getRom(id);
    await putRom({
      id,
      name: normalizedName,
      size: blob.size,
      type: blob.type || 'application/octet-stream',
      blob,
      sourcePack,
      addedAt: existing?.addedAt || Date.now(),
      lastPlayedAt: existing?.lastPlayedAt || null
    });
    return { imported: true, duplicate: Boolean(existing) };
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

    setImportStatus(`正在解開「${file.name}」並尋找 GBA 遊戲…`, 'working');
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

        setImportStatus(`正在加入：${file.name}`, 'working');
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
      setImportStatus(`完成：處理 ${imported} 款${duplicates ? `，其中 ${duplicates} 款已存在並更新` : ''}${skipped ? `，略過 ${skipped} 個不支援或過大的檔案` : ''}。`, imported ? 'success' : '');
    } catch (error) {
      setStatus('匯入失敗', 'error');
      setImportStatus(error.message || '合集匯入失敗。', 'error');
    } finally {
      $('romInput').value = '';
    }
  }

  function showPlayer(record) {
    libraryView.classList.add('hidden');
    playerView.classList.remove('hidden');
    gameTitle.textContent = cleanGameName(record.name);
    document.body.classList.add('playing');
  }

  async function playRom(id) {
    const record = await getRom(id);
    if (!record?.blob) {
      window.alert('找不到這個遊戲檔案，請重新匯入。');
      return;
    }

    setStatus('載入 mGBA…');
    record.lastPlayedAt = Date.now();
    await putRom(record);
    await requestPersistentStorage();
    showPlayer(record);

    if (activeObjectUrl) URL.revokeObjectURL(activeObjectUrl);
    activeObjectUrl = URL.createObjectURL(record.blob);

    window.EJS_player = '#game';
    window.EJS_core = 'mgba';
    window.EJS_pathtodata = 'https://cdn.emulatorjs.org/stable/data/';
    window.EJS_gameUrl = activeObjectUrl;
    window.EJS_gameName = cleanGameName(record.name);
    window.EJS_gameID = numericGameId(record.id);
    window.EJS_color = '#8da2ff';
    window.EJS_backgroundColor = '#05070b';
    window.EJS_volume = 0.65;
    window.EJS_startOnLoaded = true;
    window.EJS_fullscreenOnLoaded = false;
    window.EJS_browserMode = matchMedia('(pointer: coarse)').matches ? 'mobile' : 'desktop';
    window.EJS_askBeforeExit = true;
    window.EJS_fixedSaveInterval = 10000;
    window.EJS_cacheConfig = {
      enabled: true,
      cacheMaxSizeMB: 1024,
      cacheMaxAgeMins: 43200
    };

    const script = document.createElement('script');
    script.src = 'https://cdn.emulatorjs.org/stable/data/loader.js';
    script.async = true;
    script.onload = () => setStatus('mGBA 已啟動', 'active');
    script.onerror = () => {
      setStatus('核心載入失敗', 'error');
      window.alert('無法載入 EmulatorJS。請確認網路後重新開啟 GBA 遊戲中心。');
    };
    document.body.appendChild(script);
  }

  $('romInput').addEventListener('change', event => importFiles(event.target.files));
  $('persistButton').addEventListener('click', async () => {
    const granted = await requestPersistentStorage();
    await updateStorageStatus();
    window.alert(granted ? '已要求瀏覽器保留本機遊戲資料。' : '瀏覽器沒有授予持久儲存，但仍可使用遊戲庫。');
  });
  $('backToLibrary').addEventListener('click', () => {
    if (window.confirm('離開目前遊戲並回到遊戲庫？請先在模擬器內完成存檔。')) {
      location.replace('./gba.html');
    }
  });

  libraryList.addEventListener('click', async event => {
    const playButton = event.target.closest('[data-play]');
    if (playButton) {
      try {
        await playRom(playButton.dataset.play);
      } catch (error) {
        setStatus('啟動失敗', 'error');
        window.alert(error.message || '遊戲啟動失敗。');
      }
      return;
    }

    const deleteButton = event.target.closest('[data-delete]');
    if (deleteButton && window.confirm('從此瀏覽器移除這個 ROM？遊戲存檔可能仍由模擬器另外保存。')) {
      await deleteRom(deleteButton.dataset.delete);
      await renderLibrary();
    }
  });

  addEventListener('beforeunload', () => {
    if (activeObjectUrl) URL.revokeObjectURL(activeObjectUrl);
  });

  renderLibrary();
})();
