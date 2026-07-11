(() => {
  'use strict';

  const DB_NAME = 'amin-pocket-rom-library';
  const DB_VERSION = 1;
  const STORE_NAME = 'roms';
  const MAX_ROM_BYTES = 64 * 1024 * 1024;
  let activeObjectUrl = null;

  const $ = id => document.getElementById(id);
  const statusBadge = $('gbaStatus');
  const storageText = $('storageStatus');
  const libraryList = $('romLibrary');
  const emptyState = $('emptyLibrary');
  const playerView = $('playerView');
  const libraryView = $('libraryView');
  const gameTitle = $('activeGameTitle');

  function setStatus(text, kind = '') {
    statusBadge.textContent = text;
    statusBadge.className = `gba-badge ${kind}`.trim();
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

  async function createRomId(file) {
    const raw = new TextEncoder().encode(`${file.name}|${file.size}|${file.lastModified}`);
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

  function cleanGameName(name) {
    return name.replace(/\.(gba|zip)$/i, '').trim() || 'GBA Game';
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
            <small>${record.lastPlayedAt ? `最近遊玩：${new Date(record.lastPlayedAt).toLocaleString('zh-TW')}` : `匯入：${new Date(record.addedAt).toLocaleString('zh-TW')}`}</small>
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

  async function importFiles(fileList) {
    const files = [...fileList];
    if (!files.length) return;
    setStatus('正在匯入…');
    let imported = 0;

    for (const file of files) {
      if (!/\.(gba|zip)$/i.test(file.name)) {
        setStatus('格式不支援', 'error');
        window.alert(`「${file.name}」不是 .gba 或 .zip 檔案。`);
        continue;
      }
      if (file.size > MAX_ROM_BYTES) {
        setStatus('檔案過大', 'error');
        window.alert(`「${file.name}」超過 64 MB，未匯入。`);
        continue;
      }

      const id = await createRomId(file);
      const existing = await getRom(id);
      await putRom({
        id,
        name: file.name,
        size: file.size,
        type: file.type || 'application/octet-stream',
        blob: file,
        addedAt: existing?.addedAt || Date.now(),
        lastPlayedAt: existing?.lastPlayedAt || null
      });
      imported += 1;
    }

    await requestPersistentStorage();
    await renderLibrary();
    if (imported) setStatus(`已匯入 ${imported} 款`, 'active');
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
