import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const [gba, guard, migration, html, manifest, gradle, backupRules, extractionRules, bridge] = await Promise.all([
  readFile(new URL('../amin-vault/gba.js', import.meta.url), 'utf8'),
  readFile(new URL('../amin-vault/gba-save-guard.js', import.meta.url), 'utf8'),
  readFile(new URL('../amin-vault/gba-save-migration.js', import.meta.url), 'utf8'),
  readFile(new URL('../amin-vault/gba.html', import.meta.url), 'utf8'),
  readFile(new URL('../amin-vault/runtime-manifest.json', import.meta.url), 'utf8'),
  readFile(new URL('../android-native/app/build.gradle', import.meta.url), 'utf8'),
  readFile(new URL('../android-native/app/src/main/res/xml/backup_rules.xml', import.meta.url), 'utf8'),
  readFile(new URL('../android-native/app/src/main/res/xml/data_extraction_rules.xml', import.meta.url), 'utf8'),
  readFile(new URL('../android-native/app/src/main/java/com/amin/pocketgba/NativeSaveBridge.java', import.meta.url), 'utf8')
]);

test('ROM identity stays stable while launch transport uses an object URL', () => {
  assert.match(gba, /crypto\.subtle\.digest\('SHA-256'/);
  assert.match(gba, /saveKey/);
  assert.match(gba, /URL\.createObjectURL\(record\.blob\)/);
  assert.match(gba, /window\.EJS_gameUrl = activeObjectUrl/);
  assert.match(gba, /window\.EJS_gameName = stableBaseName/);
  assert.match(gba, /window\.EJS_gameID = numericGameId\(record\.saveKey\)/);
  assert.match(gba, /URL\.revokeObjectURL\(activeObjectUrl\)/);
  assert.match(gba, /EMULATORJS_VERSION = '4\.2\.3'/);
  assert.doesNotMatch(gba, /new File\(\[record\.blob\], stableFileName/);
});

test('save exit waits for explicit IDBFS synchronization and verification', () => {
  assert.match(guard, /fs\.syncfs\(populate/);
  assert.match(guard, /await syncFileSystem\(false\)/);
  assert.match(guard, /await flushCartridgeSave\('exit'\)/);
  assert.match(guard, /兩層存檔都寫入失敗/);
  assert.match(guard, /restored-from-vault/);
  assert.match(guard, /amin-save-verified/);
});

test('legacy name-based saves migrate before blank stable saves are protected', () => {
  assert.match(migration, /legacyBaseName/);
  assert.match(migration, /findLegacyPath/);
  assert.match(migration, /isLikelyBlankSave/);
  assert.match(migration, /stable-vault-already-protected/);
  assert.match(migration, /blank-stable-save-removed/);
  assert.match(migration, /fs\.readdir\(directory\)/);
  assert.match(migration, /fs\.readFile\(legacyPath\)/);
  assert.match(migration, /fs\.writeFile\(currentPath, bytes\)/);
  assert.match(migration, /await syncFileSystem\(fs\)/);
  assert.match(migration, /flush\?\.\('legacy-migration'\)/);
  assert.match(migration, /legacy-migrated/);
  assert.match(
    migration,
    /window\.EJS_onGameStart = async event => \{\s*await migrateLegacySaveIfNeeded\(\);\s*if \(typeof previousOnGameStart/
  );
  assert.doesNotMatch(migration, /unlink\(legacyPath\)/);
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

test('Preview migration UI explains the safe side-by-side flow', () => {
  assert.match(html, /id="saveVaultStatus"/);
  assert.match(html, /id="verifySaveVaultButton"/);
  assert.match(html, /gba-save-migration\.js/);
  assert.match(html, /三層存檔保護/);
  assert.match(html, /從 Preview 3 安全搬到 Preview 4/);
  assert.match(html, /確認進度正確後，才移除 Preview 3/);
});

test('RC5 manifest includes launcher and save persistence capabilities', () => {
  assert.match(manifest, /"runtimeVersion": "0\.9\.2-rc5"/);
  assert.match(manifest, /"save-vault-v1"/);
  assert.match(manifest, /"native-save-vault-v1"/);
  assert.match(manifest, /"legacy-save-migration-v1"/);
  assert.match(manifest, /"rom-object-url-v2"/);
  assert.match(manifest, /"emulatorjs-pinned-4\.2\.3"/);
  assert.match(manifest, /"\.\/gba-save-migration\.js"/);
});

test('Preview 5 package installs beside broken Preview 4', () => {
  assert.match(gradle, /AMIN_VERSION_CODE/);
  assert.match(gradle, /versionCode aminVersionCode/);
  assert.match(gradle, /applicationIdSuffix '\.preview095'/);
  assert.match(gradle, /versionNameSuffix aminPreviewSuffix/);
  assert.match(gradle, /Amin Pocket GBA Preview 0\.9\.2 P5/);
});

test('Preview 5 is pinned to the RC5 launcher runtime', () => {
  assert.match(gradle, /3984eb99cfe28789032abefd5cb1ce6d9329ac36/);
  assert.match(gradle, /preview=5/);
});
