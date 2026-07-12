# Amin Pocket GBA 正式簽章契約

正式 APK 的更新身份由簽章金鑰決定，不是由檔名、版本號或 GitHub 帳號決定。

## 不可違反的規則

1. 正式版只使用一把永久 keystore。
2. keystore、密碼與 base64 內容不得提交到 Git。
3. 金鑰遺失後，既有安裝無法再由新的 APK 覆蓋更新。
4. 至少保留兩份加密離線備份，放在不同實體位置。
5. GitHub Secrets 只是建置副本，不是唯一備份。
6. Preview/debug APK 不可被當作正式更新鏈的起點。

## 一次性建立 keystore

在可信任的本機執行：

```bash
keytool -genkeypair \
  -keystore amin-pocket-gba-release.jks \
  -alias amin-pocket-gba \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -sigalg SHA256withRSA
```

請使用獨立、長且不可重複使用的密碼。不要把密碼放進命令歷史、README 或聊天截圖。

## 取得憑證指紋

```bash
keytool -list -v \
  -keystore amin-pocket-gba-release.jks \
  -alias amin-pocket-gba
```

保存 `SHA256` 憑證指紋。未來 `native-release-manifest.json` 的 `signerCertificateSha256` 必須與它完全一致。

## 建立 GitHub Secrets

將 keystore 編碼：

```bash
base64 -w 0 amin-pocket-gba-release.jks > amin-pocket-gba-release.jks.base64
```

macOS 可使用：

```bash
base64 < amin-pocket-gba-release.jks | tr -d '\n' > amin-pocket-gba-release.jks.base64
```

在 GitHub repository secrets 建立：

- `AMIN_KEYSTORE_BASE64`
- `AMIN_KEYSTORE_PASSWORD`
- `AMIN_KEY_ALIAS`
- `AMIN_KEY_PASSWORD`

設定完成後刪除未加密的臨時 base64 文字檔，或將它移入加密保管庫。

## 發布流程

1. 執行 `Release Amin Pocket GBA Android` workflow。
2. 工作流先驗證四個 Secrets。
3. 建置 `com.amin.pocketgba` 正式 APK。
4. 使用 `apksigner` 驗證 v2 簽章與憑證。
5. 產生 APK SHA-256 與簽章報告。
6. 先以 Draft/Prerelease 發布。
7. 在實機測試覆蓋升級、資料保留與回退後，才更新正式 release manifest。

## Release manifest 啟用條件

以下欄位全部正確前，`enabled` 必須維持 `false`：

- `packageId`
- `latestVersionName`
- `latestVersionCode`
- `apkUrl`
- `apkSha256`
- `signerCertificateSha256`
- `sizeBytes`
- `publishedAt`

啟用後，Native Update Center 仍會再次驗證 APK 的 SHA-256、package ID、versionCode 與簽章憑證。

## 災難復原演練

正式發布前至少做一次：

1. 從離線備份還原 keystore 到一台乾淨電腦。
2. 使用還原的 keystore 簽署測試 APK。
3. 確認憑證 SHA-256 與原始版本相同。
4. 刪除測試電腦上的明文 keystore 副本。

未完成此演練，不應把任何版本標記為正式穩定版。
