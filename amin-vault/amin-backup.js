(() => {
  'use strict';

  const FORMAT = 'amin-gba-backup';
  const FORMAT_VERSION = 1;
  const ROM_DB_NAME = 'amin-pocket-rom-library';
  const encoder = new TextEncoder();
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

  function requestToPromise(request) {
    return new Promise((resolve, reject) => {
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error('IndexedDB 操作失敗。'));
    });
  }

  function transactionDone(transaction) {
    return new Promise((resolve, reject) => {
      transaction.oncomplete = resolve;
      transaction.onerror = () => reject(transaction.error || new Error('IndexedDB 交易失敗。'));
      transaction.onabort = () => reject(transaction.error || new Error('IndexedDB 交易已中止。'));
    });
  }

  async function listDatabases() {
    if (typeof indexedDB.databases !== 'function') {
      throw new Error('目前 WebView 不支援列舉全部 IndexedDB，無法保證存檔備份完整。');
    }
    const databases = await indexedDB.databases();
    return databases
      .filter(item => item?.name)
      .map(item => ({ name: item.name, version: item.version || 1 }));
  }

  function storeSchema(store) {
    const indexes = [];
    for (const name of store.indexNames) {
      const index = store.index(name);
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

  function binaryPath(counter, suffix = 'bin') {
    return `binary/${String(counter).padStart(8, '0')}.${suffix}`;
  }

  async function encodeValue(value, binaries, state) {
    if (value === undefined) return { $type: 'undefined' };
    if (value === null || typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
      return value;
    }
    if (typeof value === 'bigint') return { $type: 'bigint', value: value.toString() };
    if (value instanceof Date) return { $type: 'date', value: value.toISOString() };

    if (value instanceof Blob) {
      const path = binaryPath(++state.binaryCounter, value instanceof File ? 'file' : 'blob');
      binaries[path] = new Uint8Array(await value.arrayBuffer());
      return {
        $type: value instanceof File ? 'file' : 'blob',
        path,
        mimeType: value.type || 'application/octet-stream',
        name: value instanceof File ? value.name : undefined,
        lastModified: value instanceof File ? value.lastModified : undefined
      };
    }

    if (value instanceof ArrayBuffer) {
      const path = binaryPath(++state.binaryCounter, 'arraybuffer');
      binaries[path] = new Uint8Array(value);
      return { $type: 'arraybuffer', path };
    }

    if (ArrayBuffer.isView(value)) {
      const path = binaryPath(++state.binaryCounter, 'typedarray');
      binaries[path] = new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
      return {
        $type: 'typedarray',
        path,
        constructor: value.constructor.name,
        length: value.length
      };
    }

    if (value instanceof Map) {
      const entries = [];
      for (const [key, item] of value.entries()) {
        entries.push([
          await encodeValue(key, binaries, state),
          await encodeValue(item, binaries, state)
        ]);
      }
      return { $type: 'map', entries };
    }

    if (value instanceof Set) {
      const entries = [];
      for (const item of value.values()) entries.push(await encodeValue(item, binaries, state));
      return { $type: 'set', entries };
    }

    if (typeof value === 'object') {
      if (state.seen.has(value)) throw new Error('備份資料包含循環物件，無法序列化。');
      state.seen.add(value);
      if (Array.isArray(value)) {
        const items = [];
        for (const item of value) items.push(await encodeValue(item, binaries, state));
        state.seen.delete(value);
        return { $type: 'array', items };
      }
      const properties = {};
      for (const [key, item] of Object.entries(value)) {
        properties[key] = await encodeValue(item, binaries, state);
      }
      state.seen.delete(value);
      return { $type: 'object', properties };
    }

    throw new Error(`不支援的備份資料類型：${typeof value}`);
  }

  function decodeValue(value, files) {
    if (value === null || typeof value !== 'object' || !value.$type) return value;
    switch (value.$type) {
      case 'undefined': return undefined;
      case 'bigint': return BigInt(value.value);
      case 'date': return new Date(value.value);
      case 'blob': return new Blob([files[value.path]], { type: value.mimeType });
      case 'file': return new File([files[value.path]], value.name || 'restored.bin', {
        type: value.mimeType,
        lastModified: value.lastModified || Date.now()
      });
      case 'arraybuffer': return files[value.path].buffer.slice(
        files[value.path].byteOffset,
        files[value.path].byteOffset + files[value.path].byteLength
      );
      case 'typedarray': {
        const bytes = files[value.path];
        const buffer = bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
        const ctor = globalThis[value.constructor];
        return typeof ctor === 'function' ? new ctor(buffer) : new Uint8Array(buffer);
      }
      case 'map': return new Map(value.entries.map(([key, item]) => [decodeValue(key, files), decodeValue(item, files)]));
      case 'set': return new Set(value.entries.map(item => decodeValue(item, files)));
      case 'array': return value.items.map(item => decodeValue(item, files));
      case 'object': return Object.fromEntries(
        Object.entries(value.properties).map(([key, item]) => [key, decodeValue(item, files)])
      );
      default: throw new Error(`未知的備份資料類型：${value.$type}`);
    }
  }

  async function snapshotDatabase(info, binaries) {
    const db = await requestToPromise(indexedDB.open(info.name));
    try {
      const schema = [];
      const stores = [];
      for (const storeName of db.objectStoreNames) {
        const transaction = db.transaction(storeName, 'readonly');
        const store = transaction.objectStore(storeName);
        schema.push(storeSchema(store));
        const keys = await requestToPromise(store.getAllKeys());
        const values = await requestToPromise(store.getAll());
        await transactionDone(transaction);

        const records = [];
        for (let index = 0; index < values.length; index += 1) {
          const state = { binaryCounter: Object.keys(binaries).length, seen: new WeakSet() };
          const encodedKey = await encodeValue(keys[index], binaries, state);
          const encodedValue = await encodeValue(values[index], binaries, state);
          records.push({ key: encodedKey, value: encodedValue });
        }
        stores.push({ name: storeName, records });
      }
      return { name: db.name, version: db.version, schema, stores };
    } finally {
      db.close();
    }
  }

  async function romMetadata() {
    try {
      const db = await requestToPromise(indexedDB.open(ROM_DB_NAME));
      try {
        if (!db.objectStoreNames.contains('roms')) return [];
        const transaction = db.transaction('roms', 'readonly');
        const records = await requestToPromise(transaction.objectStore('roms').getAll());
        await transactionDone(transaction);
        return records.map(({ blob, ...record }) => record);
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
      if (key) result[key] = localStorage.getItem(key);
    }
    return result;
  }

  async function zipFiles(entries) {
    return new Promise((resolve, reject) => {
      if (!window.fflate?.zip) {
        reject(new Error('ZIP 元件尚未載入。'));
        return;
      }
      window.fflate.zip(entries, { level: 6 }, (error, data) => {
        if (error) reject(error);
        else resolve(data);
      });
    });
  }

  function downloadBytes(bytes, filename) {
    const url = URL.createObjectURL(new Blob([bytes], { type: 'application/zip' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 30000);
  }

  function backupFilename(full) {
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    return `AminGBA_${full ? 'Full' : 'Saves'}_${timestamp}.agbbackup`;
  }

  async function exportBackup() {
    const includeRoms = Boolean(includeRomsInput?.checked);
    exportButton.disabled = true;
    importButton.disabled = true;
    setStatus('正在掃描遊戲、存檔與設定…', 'working');

    try {
      window.AMIN_GBA_SAVE_GUARD?.flush?.('backup');
      await new Promise(resolve => setTimeout(resolve, 700));

      const infos = await listDatabases();
      const selected = includeRoms ? infos : infos.filter(info => info.name !== ROM_DB_NAME);
      const binaries = {};
      const databases = [];

      for (let index = 0; index < selected.length; index += 1) {
        setStatus(`正在備份資料庫 ${index + 1} / ${selected.length}：${selected[index].name}`, 'working');
        databases.push(await snapshotDatabase(selected[index], binaries));
      }

      const metadata = {
        format: FORMAT,
        formatVersion: FORMAT_VERSION,
        createdAt: new Date().toISOString(),
        origin: location.origin,
        runtimeVersion: localStorage.getItem('amin.runtime.version'),
        nativeVersion: window.AMIN_NATIVE_SHELL?.state?.capabilities?.nativeVersion || null,
        includeRoms,
        databaseCount: databases.length,
        binaryCount: Object.keys(binaries).length,
        romMetadata: includeRoms ? [] : await romMetadata(),
        databases
      };

      const entries = {
        'manifest.json': window.fflate.strToU8(JSON.stringify(metadata)),
        'local-storage.json': window.fflate.strToU8(JSON.stringify(localStorageSnapshot())),
        ...binaries
      };
      const zipped = await zipFiles(entries);
      downloadBytes(zipped, backupFilename(includeRoms));
      setStatus(`備份完成：${databases.length} 個資料庫${includeRoms ? '，包含 ROM' : '，不含 ROM 本體'}。`, 'success');
      window.dispatchEvent(new CustomEvent('amin-backup-exported', { detail: metadata }));
    } catch (error) {
      console.error('[Amin Backup Export]', error);
      setStatus(error.message || '備份失敗。', 'error');
    } finally {
      exportButton.disabled = false;
      importButton.disabled = false;
    }
  }

  async function readBackup(file) {
    if (!window.fflate?.unzip) throw new Error('ZIP 元件尚未載入。');
    const bytes = new Uint8Array(await file.arrayBuffer());
    const entries = await new Promise((resolve, reject) => {
      window.fflate.unzip(bytes, (error, files) => error ? reject(error) : resolve(files));
    });
    if (!entries['manifest.json'] || !entries['local-storage.json']) {
      throw new Error('這不是有效的 Amin GBA 備份。');
    }
    const manifest = JSON.parse(decoder.decode(entries['manifest.json']));
    if (manifest.format !== FORMAT || manifest.formatVersion !== FORMAT_VERSION) {
      throw new Error('備份格式或版本不相容。');
    }
    return {
      manifest,
      localStorageData: JSON.parse(decoder.decode(entries['local-storage.json'])),
      entries
    };
  }

  function createStores(db, schemas) {
    for (const schema of schemas) {
      if (db.objectStoreNames.contains(schema.name)) continue;
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

  async function openDatabaseForRestore(snapshot) {
    let db = await new Promise((resolve, reject) => {
      const request = indexedDB.open(snapshot.name);
      request.onupgradeneeded = () => createStores(request.result, snapshot.schema || []);
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error(`無法建立 ${snapshot.name}`));
    });

    const missing = (snapshot.schema || []).filter(schema => !db.objectStoreNames.contains(schema.name));
    if (!missing.length) return db;

    const version = db.version + 1;
    db.close();
    db = await new Promise((resolve, reject) => {
      const request = indexedDB.open(snapshot.name, version);
      request.onupgradeneeded = () => createStores(request.result, missing);
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error(`無法升級 ${snapshot.name}`));
    });
    return db;
  }

  async function restoreDatabase(snapshot, entries) {
    const db = await openDatabaseForRestore(snapshot);
    try {
      for (const storeSnapshot of snapshot.stores || []) {
        if (!db.objectStoreNames.contains(storeSnapshot.name)) continue;
        const transaction = db.transaction(storeSnapshot.name, 'readwrite');
        const store = transaction.objectStore(storeSnapshot.name);
        store.clear();
        for (const record of storeSnapshot.records || []) {
          const key = decodeValue(record.key, entries);
          const value = decodeValue(record.value, entries);
          if (store.keyPath === null) store.put(value, key);
          else store.put(value);
        }
        await transactionDone(transaction);
      }
    } finally {
      db.close();
    }
  }

  async function importBackup(file) {
    exportButton.disabled = true;
    importButton.disabled = true;
    setStatus('正在檢查備份檔…', 'working');

    try {
      const backup = await readBackup(file);
      const { manifest, localStorageData, entries } = backup;
      const confirmed = window.confirm(
        `這份備份建立於 ${new Date(manifest.createdAt).toLocaleString('zh-TW')}，包含 ${manifest.databaseCount} 個資料庫${manifest.includeRoms ? '與 ROM' : ''}。\n\n匯入會覆寫同名資料庫的現有內容，是否繼續？`
      );
      if (!confirmed) {
        setStatus('已取消匯入。');
        return;
      }

      for (let index = 0; index < manifest.databases.length; index += 1) {
        const database = manifest.databases[index];
        setStatus(`正在還原資料庫 ${index + 1} / ${manifest.databases.length}：${database.name}`, 'working');
        await restoreDatabase(database, entries);
      }

      for (const [key, value] of Object.entries(localStorageData || {})) {
        localStorage.setItem(key, value);
      }
      localStorage.setItem('amin-gba-last-restore', JSON.stringify({
        restoredAt: Date.now(),
        backupCreatedAt: manifest.createdAt,
        databaseCount: manifest.databaseCount,
        includeRoms: manifest.includeRoms
      }));

      setStatus('資料還原完成，即將重新載入遊戲庫。', 'success');
      window.dispatchEvent(new CustomEvent('amin-backup-imported', { detail: manifest }));
      setTimeout(() => location.reload(), 1200);
    } catch (error) {
      console.error('[Amin Backup Import]', error);
      setStatus(error.message || '備份匯入失敗。', 'error');
    } finally {
      exportButton.disabled = false;
      importButton.disabled = false;
      importInput.value = '';
    }
  }

  exportButton?.addEventListener('click', exportBackup);
  importButton?.addEventListener('click', () => importInput?.click());
  importInput?.addEventListener('change', event => {
    const [file] = event.target.files || [];
    if (file) importBackup(file);
  });

  window.AMIN_GBA_BACKUP = {
    export: exportBackup,
    importFile: importBackup,
    format: FORMAT,
    version: FORMAT_VERSION
  };
})();
