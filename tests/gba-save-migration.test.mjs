import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import test from 'node:test';
import vm from 'node:vm';

const source = await fs.readFile(new URL('../amin-vault/gba-save-migration.js', import.meta.url), 'utf8');

function makeHarness({ protectedBytes = 0, currentBytes = null } = {}) {
  const files = new Map();
  const directories = new Set(['/', '/data', '/data/saves']);
  const legacyPath = '/data/saves/Pokémon Emerald.srm';
  const currentPath = '/data/saves/amin-0123456789abcdef.srm';
  const legacyBytes = new Uint8Array([1, 3, 3, 7, 9, 4, 2]);
  files.set(legacyPath, legacyBytes);
  if (currentBytes) files.set(currentPath, new Uint8Array(currentBytes));

  let syncCalls = 0;
  let loadCalls = 0;
  let flushReason = null;
  let previousSawMigrated = false;
  const records = [];

  const fakeFs = {
    analyzePath(path) {
      return { exists: files.has(path) || directories.has(path) };
    },
    readdir(directory) {
      const prefix = directory === '/' ? '/' : `${directory}/`;
      const entries = ['.', '..'];
      for (const path of files.keys()) {
        if (!path.startsWith(prefix)) continue;
        const remainder = path.slice(prefix.length);
        if (remainder && !remainder.includes('/')) entries.push(remainder);
      }
      return entries;
    },
    readFile(path) {
      if (!files.has(path)) throw new Error(`missing ${path}`);
      return new Uint8Array(files.get(path));
    },
    writeFile(path, bytes) {
      files.set(path, new Uint8Array(bytes));
    },
    unlink(path) {
      if (!files.delete(path)) throw new Error(`missing ${path}`);
    },
    mkdir(path) {
      directories.add(path);
    },
    syncfs(populate, callback) {
      assert.equal(populate, false);
      syncCalls += 1;
      callback(null);
    }
  };

  const guard = {
    setGame() {},
    async getSummary() {
      return protectedBytes ? { byteLength: protectedBytes, updatedAt: 123 } : null;
    },
    async flush(reason) {
      flushReason = reason;
      return files.has(currentPath);
    }
  };

  const status = { textContent: '', className: '' };
  const windowObject = {
    AMIN_GBA_SAVE_GUARD: guard,
    EJS_emulator: {
      started: true,
      displayMessage() {},
      gameManager: {
        FS: fakeFs,
        getSaveFilePath: () => currentPath,
        loadSaveFiles: () => { loadCalls += 1; }
      }
    },
    EJS_onGameStart: async () => {
      previousSawMigrated = files.has(currentPath)
        && files.get(currentPath).join(',') === legacyBytes.join(',');
    }
  };
  windowObject.window = windowObject;

  const context = vm.createContext({
    window: windowObject,
    document: { getElementById: () => status },
    localStorage: { setItem: (key, value) => records.push([key, value]) },
    dispatchEvent: () => undefined,
    CustomEvent: class CustomEvent {
      constructor(type, options) {
        this.type = type;
        this.detail = options?.detail;
      }
    },
    Uint8Array,
    ArrayBuffer,
    console
  });

  vm.runInContext(source, context, { filename: 'gba-save-migration.js' });
  guard.setGame({
    saveKey: '0123456789abcdef',
    romId: 'rom-1',
    romName: 'Pokémon Emerald.gba',
    stableName: 'amin-0123456789abcdef'
  });

  return {
    guard,
    windowObject,
    files,
    legacyPath,
    currentPath,
    legacyBytes,
    status,
    records,
    get syncCalls() { return syncCalls; },
    get loadCalls() { return loadCalls; },
    get flushReason() { return flushReason; },
    get previousSawMigrated() { return previousSawMigrated; }
  };
}

test('legacy save is copied before the previous game-start handler and source remains', async () => {
  const harness = makeHarness({ currentBytes: new Uint8Array(8).fill(0xff) });

  await harness.windowObject.EJS_onGameStart({});

  assert.deepEqual(
    [...harness.files.get(harness.currentPath)],
    [...harness.legacyBytes]
  );
  assert.deepEqual(
    [...harness.files.get(harness.legacyPath)],
    [...harness.legacyBytes],
    'legacy source was deleted or changed'
  );
  assert.equal(harness.loadCalls, 1);
  assert.ok(harness.syncCalls >= 1);
  assert.equal(harness.flushReason, 'legacy-migration');
  assert.equal(harness.previousSawMigrated, true);
  assert.match(harness.status.textContent, /已搬回舊版存檔/);
  assert.match(harness.records.at(-1)?.[1] || '', /legacy-migrated/);
});

test('an existing protected stable vault prevents legacy overwrite', async () => {
  const current = new Uint8Array([8, 6, 7, 5, 3, 0, 9]);
  const harness = makeHarness({ protectedBytes: current.length, currentBytes: current });

  await harness.windowObject.EJS_onGameStart({});

  assert.deepEqual([...harness.files.get(harness.currentPath)], [...current]);
  assert.equal(harness.loadCalls, 0);
  assert.equal(harness.flushReason, null);
  assert.match(harness.records.at(-1)?.[1] || '', /stable-vault-already-protected/);
});
