(() => {
  'use strict';

  const FORMAT = 'amin-gba-backup';
  const FORMAT_VERSION = 2;
  const ROM_DB_NAME = 'amin-pocket-rom-library';
  const LOCAL_STORAGE_PREFIXES = ['amin', 'EJS_', 'emulatorjs', 'mgba'];
  const decoder = new TextDecoder();

  const $ = id => document.getElementById(id);
  const exportButton = $('exportBackupButton');
  const importButton = $('importBackupButton');
  const importInput = $('backupImportInput');
  const includeRomsInput = $('backupIncludeRoms');
  const status = $('backupStatus');

  function setStatus(text, kind = '') {
    if (!status) return;
    status.textContent = text;
    status.className = `storage-status ${kind}`.trim();
  }

  function requestResult(request, fallback = 'IndexedDB 操作失敗。') {
    return new Promise((resolve, reject) => {
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error(fallback));
    });
  }

  function transactionResult(transaction) {
    return new Promise((resolve, reject) => {
      transaction.oncomplete = () => resolve();
      transaction.onerror = () => reject(transaction.error || new Error('IndexedDB 交易失敗。'));
      transaction.onabort = () => reject(transaction.error || new Error('IndexedDB 交易已中止。'));
    });
  }

  function deleteDatabase(name) {
    return new Promise((resolve, reject) => {
      const request = indexedDB.deleteDatabase(name);
      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error || new Error(`無法清除資料庫 ${name}`));
      request.onblocked = () => reject(new Error(`資料庫 ${name} 仍被使用中，請關閉遊戲後再匯入。`));
    });
  }

  async function listDatabases() {
    if (typeof indexedDB.databases !== 'function') {
      throw new Error('此 WebView 無法列舉全部 IndexedDB，為避免產生不完整備份，已停止匯出。');
    }
    return (await indexedDB.databases())
      .filter(database => database?.name)
      .map(database => ({ name: database.name, version: database.version || 1 }));
  }

  function readStoreSchema(store) {
    const indexes = [];
    for (const indexName of store.indexNames) {
      const index = store.index(indexName);
      indexes.push({
        name: index.name,
        keyPath: index.keyPath,
        unique: index.unique,
        multiEntry: index.multiEntry
      });
    }
    return {
      name: store.name,
      keyPath: store.keyPath,
      autoIncrement: store.autoIncrement,
      indexes
    };
  }

  function binaryName(state, extension) {
    state.binaryCounter += 1;
    return `binary/${String(state.binaryCounter).padStart(8, '0')}.${extension}`;
  }

  async function encodeValue(value, binaryFiles, state) {
    if (value === undefined) return { $type: 'undefined' };
    if (value === null || ['string', 'number', 'boolean'].includes(typeof value)) return value;
    if (typeof value === 'bigint') return { $type: 'bigint', value: value.toString() };
    if (value instanceof Date) return { $type: 'date', value: value.toISOString() };

    if (value instanceof Blob) {
      const path = binaryName(state, value instanceof File ? 'file' : 'blob');
      binaryFiles[path] = new Uint8Array(await value.arrayBuffer());
      return {
        $type: value instanceof File ? 'file' : 'blob',
        path,
        mimeType: value.type || 'application/octet-stream',
        name: value instanceof File ? value.name : null,
        lastModified: value instanceof File ? value.lastModified : null
      };
    }

    if (value instanceof ArrayBuffer) {
      const path = binaryName(state, 'arraybuffer');
      binaryFiles[path] = new Uint8Array(value.slice(0));
      return { $type: 'arraybuffer', path };
    }

    if (ArrayBuffer.isView(value)) {
      const path = binaryName(state, 'typedarray');
      binaryFiles[path] = new Uint8Array(value.buffer.slice(value.byteOffset, value.byteOffset + value.byteLength));
      return { $type: 'typedarray', path, constructor: value.constructor.name };
    }

    if (state.seen.has(value)) throw new Error('資料包含循環參照，無法備份。');
    state.seen.add(value);

    try {
      if (Array.isArray(value)) {
        const items = [];
        for (const item of value) items.push(await encodeValue(item, binaryFiles, state));
        return { $type: 'array', items };
      }
      if (value instanceof Map) {
        const entries = [];
        for (const [key, item] of value.entries()) {
          entries.push([
            await encodeValue(key, binaryFiles, state),
            await encodeValue(item, binaryFiles, state)
          ]);
        }
        return { $type: 'map', entries };
      }
      if (value instanceof Set) {
        const entries = [];
        for (const item of value.values()) entries.push(await encodeValue(item, binaryFiles, state));
        return { $type: 'set', entries };
      }

      const properties = {};
      for (const [key, item] of Object.entries(value)) {
        properties[key] = await encodeValue(item, binaryFiles, state);
      }
      return { $type: 'object', properties };
    } finally {
      state.seen.delete(value);
    }
  }

  function requireBinary(files, path) {
    const bytes = files[path];
    if (!(bytes instanceof Uint8Array)) throw new Error(`備份缺少二進位資料：${path}`);
    return bytes;
  }

  function decodeValue(value, files) {
    if (value === null || typeof value !== 'object' || !value.$type) return value;
    switch (value.$type) {
      case 'undefined': return undefined;
      case 'bigint': return BigInt(value.value);
      case 'date': return new Date(value.value);
      case 'blob': return new Blob([requireBinary(files, value.path)], { type: value.mimeType });
      case 'file': return new File([requireBinary(files, value.path)], value.name || 'restored.bin', {
        type: value.mimeType,
        lastModified: value.lastModified || Date.now()
      });
      case 'arraybuffer': {
        const bytes = requireBinary(files, value.path);
        return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
      }
      case 'typedarray': {
        const bytes = requireBinary(files, value.path);
        const buffer = bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
        const constructor = globalThis[value.constructor];
        return typeof constructor === 'function' ? new constructor(buffer) : new Uint8Array(buffer);
      }
      case 'array': return value.items.map(item => decodeValue(item, files));
      case 'map': return new Map(value.entries.map(([key, item]) => [decodeValue(key, files), decodeValue(item, files)]));
      case 'set': return new Set(value.entries.map(item => decodeValue(item, files)));
      case 'object': return Object.fromEntries(
        Object.entries(value.properties).map(([key, item]) => [key, decodeValue(item, files)])
      );
      default: throw new Error(`未知備份資料類型：${value.$type}`);
    }
  }

  async function snapshotDatabase(databaseInfo, binaryFiles, state) {
    const db = await requestResult(indexedDB.open(databaseInfo.name), `無法開啟資料庫 ${databaseInfo.name}`);
    try {
      const schema = [];
      const stores = [];

      for (const storeName of db.objectStoreNames) {
        const transaction = db.transaction(storeName, 'readonly');
        const completed = transactionResult(transaction);
        const store = transaction.objectStore(storeName);
        schema.push(readStoreSchema(store));

        const [keys, values] = await Promise.all([
          requestResult(store.getAllKeys()),
          requestResult(store.getAll())
        ]);
        await completed;
        if (keys.length !== values.length) throw new Error(`資料庫 ${databaseInfo.name}/${storeName} 鍵值數量不一致。`);

        const records = [];
        for (let index = 0; index < values.length; index += 1) {
          records.push({
            key: await encodeValue(keys[index], binaryFiles, state),
            value: await encodeValue(values[index], binaryFiles, state)
          });
        }
        stores.push({ name: storeName, records });
      }

      return { name: db.name, version: db.version, schema, stores };
    } finally {
      db.close();
    }
  }

  async function readRomMetadata() {
    try {
      const db = await requestResult(indexedDB.open(ROM_DB_NAME));
      try {
        if (!db.objectStoreNames.contains('roms')) return [];
        const transaction = db.transaction('roms', 'readonly');
        const completed = transactionResult(transaction);
        const records = await requestResult(transaction.objectStore('roms').getAll());
        await completed;
        return records.map(record => ({
          id: record.id,
          name: record.name,
          size: record.size,
          type: record.type,
          sourcePack: record.sourcePack,
          addedAt: record.addedAt,
          lastPlayedAt: record.lastPlayedAt
        }));
      } finally {
        db.close();
      }
    } catch {
      return [];
    }
  }

  function localStorageSnapshot() {
    const result = {};
    for (let index = 0; index < localStorage.length; index += 1) {
      const key = localStorage.key(index);
      if (!key || !LOCAL_STORAGE_PREFIXES.some(prefix => key.startsWith(prefix))) continue;
      result[key] = localStorage.getItem(key);
    }
    return result;
  }

  function zipEntries(entries) {
    return new Promise((resolve, reject) => {
      if (!window.fflate?.zip || !window.fflate?.strToU8) {
        reject(new Error('ZIP 元件尚未載入。'));
        return;
      }
      window.fflate.zip(entries, { level: 6 }, (error, bytes) => error ? reject(error) : resolve(bytes));
    });
  }

  function triggerDownload(bytes, filename) {
    const url = URL.createObjectURL(new Blob([bytes], { type: 'application/zip' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 30000);
  }

  async function exportBackup() {
    const includeRoms = Boolean(includeRomsInput?.checked);
    exportButton.disabled = true;
    importButton.disabled = true;
    setStatus('正在要求模擬器寫回存檔…', 'working');

    try {
      window.AMIN_GBA_SAVE_GUARD?.flush?.('backup');
      await new Promise(resolve => setTimeout(resolve, 900));

      const databaseInfos = await listDatabases();
      const selected = includeRoms
        ? databaseInfos
        : databaseInfos.filter(database => database.name !== ROM_DB_NAME);
      const binaryFiles = {};
      const state = { binaryCounter: 0, seen: new WeakSet() };
      const databases = [];

      for (let index = 0; index < selected.length; index += 1) {
        setStatus(`正在備份 ${index + 1} / ${selected.length}：${selected[index].name}`, 'working');
        databases.push(await snapshotDatabase(selected[index], binaryFiles, state));
      }

      const manifest = {
        format: FORMAT,
        formatVersion: FORMAT_VERSION,
        createdAt: new Date().toISOString(),
        origin: location.origin,
        runtimeVersion: localStorage.getItem('amin.runtime.version'),
        nativeVersion: window.AMIN_NATIVE_SHELL?.state?.capabilities?.nativeVersion || null,
        includeRoms,
        databaseCount: databases.length,
        databaseNames: databases.map(database => database.name),
        binaryCount: state.binaryCounter,
        localStoragePrefixes: LOCAL_STORAGE_PREFIXES,
        romMetadata: includeRoms ? [] : await readRomMetadata(),
        databases
      };

      const entries = {
        'manifest.json': window.fflate.strToU8(JSON.stringify(manifest)),
        'local-storage.json': window.fflate.strToU8(JSON.stringify(localStorageSnapshot())),
        ...binaryFiles
      };
      const archive = await zipEntries(entries);
      const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
      triggerDownload(archive, `AminGBA_${includeRoms ? 'Full' : 'Saves'}_${timestamp}.agbbackup`);
      setStatus(`備份完成：${databases.length} 個資料庫${includeRoms ? '，包含 ROM' : '，不含 ROM 本體'}。`, 'success');
      dispatchEvent(new CustomEvent('amin-backup-exported', { detail: manifest }));
    } catch (error) {
      console.error('[Amin Backup Export]', error);
      setStatus(error.message || '備份失敗。', 'error');
    } finally {
      exportButton.disabled = false;
      importButton.disabled = false;
    }
  }

  async function unzipBackup(file) {
    if (!window.fflate?.unzip) throw new Error('ZIP 元件尚未載入。');
    const bytes = new Uint8Array(await file.arrayBuffer());
    const files = await new Promise((resolve, reject) => {
      window.fflate.unzip(bytes, (error, entries) => error ? reject(error) : resolve(entries));
    });
    if (!files['manifest.json'] || !files['local-storage.json']) throw new Error('這不是有效的 Amin GBA 備份。');

    const manifest = JSON.parse(decoder.decode(files['manifest.json']));
    if (manifest.format !== FORMAT || manifest.formatVersion !== FORMAT_VERSION) {
      throw new Error(`備份格式不相容，目前需要 v${FORMAT_VERSION}。`);
    }
    if (!Array.isArray(manifest.databases)) throw new Error('備份缺少資料庫清單。');
    return {
      manifest,
      localStorageData: JSON.parse(decoder.decode(files['local-storage.json'])),
      files
    };
  }

  function createSchema(db, schemas) {
    for (const schema of schemas || []) {
      const options = {};
      if (schema.keyPath !== null && schema.keyPath !== undefined) options.keyPath = schema.keyPath;
      if (schema.autoIncrement) options.autoIncrement = true;
      const store = db.createObjectStore(schema.name, options);
      for (const index of schema.indexes || []) {
        store.createIndex(index.name, index.keyPath, {
          unique: Boolean(index.unique),
          multiEntry: Boolean(index.multiEntry)
        });
      }
    }
  }

  async function restoreDatabase(snapshot, files) {
    await deleteDatabase(snapshot.name);
    const version = Math.max(1, Number(snapshot.version) || 1);
    const db = await new Promise((resolve, reject) => {
      const request = indexedDB.open(snapshot.name, version);
      request.onupgradeneeded = () => createSchema(request.result, snapshot.schema);
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error(`無法重建資料庫 ${snapshot.name}`));
      request.onblocked = () => reject(new Error(`資料庫 ${snapshot.name} 被其他頁面占用。`));
    });

    try {
      for (const storeSnapshot of snapshot.stores || []) {
        if (!db.objectStoreNames.contains(storeSnapshot.name)) throw new Error(`資料庫缺少物件儲存區 ${storeSnapshot.name}`);
        const transaction = db.transaction(storeSnapshot.name, 'readwrite');
        const completed = transactionResult(transaction);
        const store = transaction.objectStore(storeSnapshot.name);

        for (const record of storeSnapshot.records || []) {
          const key = decodeValue(record.key, files);
          const value = decodeValue(record.value, files);
          if (store.keyPath === null) store.put(value, key);
          else store.put(value);
        }
        await completed;
      }
    } finally {
      db.close();
    }
  }

  async function importBackup(file) {
    exportButton.disabled = true;
    importButton.disabled = true;
    setStatus('正在檢查備份完整性…', 'working');

    try {
      const backup = await unzipBackup(file);
      const { manifest, localStorageData, files } = backup;
      const confirmation = window.confirm(
        `備份日期：${new Date(manifest.createdAt).toLocaleString('zh-TW')}\n`
        + `資料庫：${manifest.databaseCount} 個\n`
        + `ROM：${manifest.includeRoms ? '包含' : '不包含'}\n\n`
        + '匯入會完整取代備份中同名的資料庫。請先關閉正在執行的遊戲。是否繼續？'
      );
      if (!confirmation) {
        setStatus('已取消匯入。');
        return;
      }

      for (let index = 0; index < manifest.databases.length; index += 1) {
        const database = manifest.databases[index];
        setStatus(`正在還原 ${index + 1} / ${manifest.databases.length}：${database.name}`, 'working');
        await restoreDatabase(database, files);
      }

      for (const [key, value] of Object.entries(localStorageData || {})) {
        if (LOCAL_STORAGE_PREFIXES.some(prefix => key.startsWith(prefix))) localStorage.setItem(key, value);
      }
      localStorage.setItem('amin-gba-last-restore', JSON.stringify({
        restoredAt: Date.now(),
        backupCreatedAt: manifest.createdAt,
        databaseCount: manifest.databaseCount,
        includeRoms: manifest.includeRoms,
        formatVersion: manifest.formatVersion
      }));

      setStatus('還原完成，即將重新載入遊戲庫。', 'success');
      dispatchEvent(new CustomEvent('amin-backup-imported', { detail: manifest }));
      setTimeout(() => location.reload(), 1200);
    } catch (error) {
      console.error('[Amin Backup Import]', error);
      setStatus(error.message || '備份匯入失敗。', 'error');
    } finally {
      exportButton.disabled = false;
      importButton.disabled = false;
      if (importInput) importInput.value = '';
    }
  }

  exportButton?.addEventListener('click', exportBackup);
  importButton?.addEventListener('click', () => importInput?.click());
  importInput?.addEventListener('change', event => {
    const file = event.target.files?.[0];
    if (file) importBackup(file);
  });

  window.AMIN_GBA_BACKUP = {
    export: exportBackup,
    importFile: importBackup,
    inspect: unzipBackup,
    format: FORMAT,
    version: FORMAT_VERSION
  };
})();
