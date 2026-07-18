(() => {
  'use strict';

  const MAX_SAVE_BYTES = 2 * 1024 * 1024;
  let gameContext = null;
  let migrationAttempted = false;

  function normalizeBytes(value) {
    if (!value) return null;
    if (value instanceof Uint8Array) return new Uint8Array(value);
    if (value instanceof ArrayBuffer) return new Uint8Array(value.slice(0));
    if (ArrayBuffer.isView(value)) {
      return new Uint8Array(value.buffer.slice(value.byteOffset, value.byteOffset + value.byteLength));
    }
    return null;
  }

  function isLikelyBlankSave(bytes) {
    if (!bytes?.byteLength) return true;
    const first = bytes[0];
    if (first !== 0x00 && first !== 0xff) return false;
    for (let index = 1; index < bytes.length; index += 1) {
      if (bytes[index] !== first) return false;
    }
    return true;
  }

  function legacyBaseName(name) {
    const raw = String(name || '').split('/').pop().split('\\').pop();
    const withoutExtension = raw.replace(/\.(gba|bin|zip)$/i, '').trim();
    const invalidCharacters = /[#<$+%>!`&*'|{}/\\?"=@:^\r\n]/ig;
    return withoutExtension.replace(invalidCharacters, '').trim();
  }

  function pathParts(path) {
    const slash = path.lastIndexOf('/');
    const directory = slash >= 0 ? path.slice(0, slash) || '/' : '/';
    const filename = slash >= 0 ? path.slice(slash + 1) : path;
    const dot = filename.lastIndexOf('.');
    const extension = dot >= 0 ? filename.slice(dot + 1) : 'srm';
    return { directory, filename, extension };
  }

  function findLegacyPath(fs, currentPath) {
    const base = legacyBaseName(gameContext?.romName);
    if (!base) return null;

    const { directory, filename, extension } = pathParts(currentPath);
    const expected = `${base}.${extension}`;
    if (expected.toLocaleLowerCase('en-US') === filename.toLocaleLowerCase('en-US')) return null;

    let entries;
    try {
      entries = fs.readdir(directory);
    } catch {
      return null;
    }

    const match = entries.find(entry =>
      !['.', '..'].includes(entry) &&
      entry.toLocaleLowerCase('en-US') === expected.toLocaleLowerCase('en-US')
    );
    return match ? `${directory === '/' ? '' : directory}/${match}` : null;
  }

  function syncFileSystem(fs) {
    return new Promise((resolve, reject) => {
      if (!fs || typeof fs.syncfs !== 'function') return resolve(false);
      try {
        fs.syncfs(false, error => error ? reject(error) : resolve(true));
      } catch (error) {
        reject(error);
      }
    });
  }

  function updateStatus(text, kind = '') {
    const target = document.getElementById('saveVaultStatus');
    if (!target) return;
    target.textContent = text;
    target.className = `storage-status ${kind}`.trim();
  }

  function recordMigration(status, detail = {}) {
    const payload = {
      status,
      at: Date.now(),
      saveKey: gameContext?.saveKey || null,
      romName: gameContext?.romName || null,
      ...detail
    };
    try {
      localStorage.setItem('amin-gba-save-migration', JSON.stringify(payload));
    } catch {}
    dispatchEvent(new CustomEvent('amin-save-migration', { detail: payload }));
  }

  async function migrateLegacySaveIfNeeded() {
    if (migrationAttempted || !gameContext?.saveKey) return false;
    migrationAttempted = true;

    const emulator = window.EJS_emulator;
    const gameManager = emulator?.gameManager;
    const fs = gameManager?.FS;
    if (!emulator?.started || !gameManager || !fs) return false;

    const protectedSummary = await window.AMIN_GBA_SAVE_GUARD?.getSummary?.(gameContext.saveKey);
    if (protectedSummary?.byteLength) {
      recordMigration('stable-vault-already-protected', {
        byteLength: protectedSummary.byteLength,
        updatedAt: protectedSummary.updatedAt
      });
      return false;
    }

    const currentPath = gameManager.getSaveFilePath();
    if (!currentPath) return false;

    let currentBytes = null;
    try {
      if (fs.analyzePath(currentPath).exists) {
        currentBytes = normalizeBytes(fs.readFile(currentPath));
        if (currentBytes?.byteLength && !isLikelyBlankSave(currentBytes)) {
          recordMigration('stable-save-already-present', {
            currentPath,
            byteLength: currentBytes.byteLength
          });
          return false;
        }
      }
    } catch {}

    const legacyPath = findLegacyPath(fs, currentPath);
    if (!legacyPath) {
      if (currentBytes?.byteLength && isLikelyBlankSave(currentBytes)) {
        try {
          fs.unlink(currentPath);
          await syncFileSystem(fs);
          recordMigration('blank-stable-save-removed', {
            currentPath,
            byteLength: currentBytes.byteLength
          });
        } catch {}
      } else {
        recordMigration('legacy-not-found', { currentPath });
      }
      return false;
    }

    let bytes;
    try {
      bytes = normalizeBytes(fs.readFile(legacyPath));
    } catch (error) {
      recordMigration('legacy-read-failed', { legacyPath, error: error?.message || 'unknown' });
      return false;
    }

    if (!bytes?.byteLength || bytes.byteLength > MAX_SAVE_BYTES || isLikelyBlankSave(bytes)) {
      recordMigration('legacy-invalid-save', {
        legacyPath,
        byteLength: bytes?.byteLength || 0
      });
      return false;
    }

    try {
      const slash = currentPath.lastIndexOf('/');
      const directory = slash > 0 ? currentPath.slice(0, slash) : '/';
      let current = '';
      for (const part of directory.split('/')) {
        if (!part) continue;
        current += `/${part}`;
        if (!fs.analyzePath(current).exists) fs.mkdir(current);
      }

      if (fs.analyzePath(currentPath).exists) fs.unlink(currentPath);
      fs.writeFile(currentPath, bytes);
      gameManager.loadSaveFiles();
      await syncFileSystem(fs);
      const protectedSave = await window.AMIN_GBA_SAVE_GUARD?.flush?.('legacy-migration');
      if (protectedSave === false) throw new Error('三層存檔驗證未完成');

      recordMigration('legacy-migrated', {
        legacyPath,
        currentPath,
        byteLength: bytes.byteLength
      });
      updateStatus(
        `已搬回舊版存檔：${bytes.byteLength} bytes，並轉入永久保險庫。`,
        'success'
      );
      emulator.displayMessage?.('已找回舊版遊戲存檔');
      return true;
    } catch (error) {
      recordMigration('legacy-migration-failed', {
        legacyPath,
        currentPath,
        error: error?.message || 'unknown'
      });
      updateStatus(`舊版存檔搬家失敗：${error?.message || '未知錯誤'}`, 'error');
      return false;
    }
  }

  const guard = window.AMIN_GBA_SAVE_GUARD;
  if (!guard) return;

  const originalSetGame = guard.setGame.bind(guard);
  guard.setGame = context => {
    gameContext = context ? { ...context } : null;
    migrationAttempted = false;
    return originalSetGame(context);
  };

  const previousOnGameStart = window.EJS_onGameStart;
  window.EJS_onGameStart = async event => {
    await migrateLegacySaveIfNeeded();
    if (typeof previousOnGameStart === 'function') await previousOnGameStart(event);
  };

  guard.migrateLegacy = migrateLegacySaveIfNeeded;
  guard.migrationVersion = '1.1.0';
})();
