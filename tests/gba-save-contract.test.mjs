import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const [gba, guard, html, gradle, backupRules, extractionRules, bridge] = await Promise.all([
  readFile(new URL('../amin-vault/gba.js', import.meta.url), 'utf8'),
  readFile(new URL('../amin-vault/gba-save-guard.js', import.meta.url), 'utf8'),
  readFile(new URL('../amin-vault/gba.html', import.meta.url), 'utf8'),
  readFile(new URL('../android-native/app/build.gradle', import.meta.url), 'utf8'),
  readFile(new URL('../android-native/app/src/main/res/xml/backup_rules.xml', import.meta.url), 'utf8'),
  readFile(new URL('../android-native/app/src/main/res/xml/data_extraction_rules.xml', import.meta.url), 'utf8'),
  readFile(new URL('../android-native/app/src/main/java/com/amin/pocketgba/NativeSaveBridge.java', import.meta.url), 'utf8')
]);

test('ROM identity is content-based and stable across names and updates', () => {
  assert.match(gba, /crypto\.subtle\.digest\('SHA-256'/);
  assert.match(gba, /saveKey/);
  assert.match(gba, /new File\(\[record\.blob\], stableFileName/);
  assert.match(gba, /window\.EJS_gameName = stableBaseName/);
  assert.match(gba, /window\.EJS_gameID = numericGameId\(record\.saveKey\)/);
  assert.doesNotMatch(gba, /URL\.createObjectURL\(record\.blob\)/);
});

test('save exit waits for explicit IDBFS synchronization and verification', () => {
  assert.match(guard, /fs\.syncfs\(populate/);
  assert.match(guard, /await syncFileSystem\(false\)/);
  assert.match(guard, /await flushCartridgeSave\('exit'\)/);
  assert.match(guard, /兩層存檔都寫入失敗/);
  assert.match(guard, /restored-from-vault/);
  assert.match(guard, /amin-save-verified/);
});

test('native vault is bounded atomic and keeps a prior generation', () => {
  assert.match(bridge, /MAX_SAVE_BYTES = 2 \* 1024 \* 1024/);
  assert.match(bridge, /SAFE_KEY/);
  assert.match(bridge, /output\.getFD\(\)\.sync\(\)/);
  assert.match(bridge, /current\.renameTo\(backup\)/);
  assert.match(bridge, /unchanged/);
  assert.match(bridge, /sha256/);
});

test('Android backup includes only the app-private save vault', () => {
  assert.match(backupRules, /<include domain="file" path="gba-saves\/" \/>/);
  assert.match(extractionRules, /<cloud-backup/);
  assert.match(extractionRules, /<device-transfer>/);
  assert.equal((extractionRules.match(/path="gba-saves\/"/g) || []).length, 2);
});

test('UI explains save protection and preview build points to RC3 runtime', () => {
  assert.match(html, /id="saveVaultStatus"/);
  assert.match(html, /id="verifySaveVaultButton"/);
  assert.match(html, /三層存檔/);
  assert.match(gradle, /versionCode 95/);
  assert.match(gradle, /versionNameSuffix '-preview4'/);
  assert.match(gradle, /1907f4d4d2e6877442b3a1a775eac17761ad9d9f/);
});
