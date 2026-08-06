# Release process

來源：[`AGENTS.md`](../../AGENTS.md)、[`amin-vault/native-release-manifest.json`](../../amin-vault/native-release-manifest.json)、[`amin-vault/runtime-manifest.json`](../../amin-vault/runtime-manifest.json)、`git log`

## 發布閘門（`AGENTS.md` 不可違反規則，摘要）

1. 功能分支不得直接或間接推送 `main`。
2. 不得用 `workflow_run` 把功能分支的 APK/manifest/release 檔案自動寫入 `main`。
3. Draft PR、編譯成功、parser 測試、lint、APK 產出或簽章成功都不算功能完成。
4. 任何必要測試失敗/跳過/未執行，必須回報「未完成」，不得提高正式版本號。
5. `amin-vault/native-release-manifest.json` 只能由獨立、可審查、經使用者明確批准的發布變更更新。
6. 一般 CI 只能產生 `CI DEBUG ARTIFACT ONLY`，不得永久簽章，不得成為正式更新來源。
7. 正式發布前同一 commit 必須跑完：單元測試、JS 測試、Android lint、APK 檢查、模擬器安裝、launcher/deep-link 驗證、instrumentation acceptance tests。
8. 涉及 Accessibility、手把、語音、藍牙或真機系統動作的功能，還需要實機閉環驗證。
9. 不得用新增 Bridge 版本號掩蓋未解決的同一問題。
10. 未經使用者明確要求，不得移除/放大/縮小/重新排列既有可用 UI。

判斷「要不要開新 Bridge」：Java、Manifest、原生 Activity、AccessibilityService 或 APK 內容改動才需要新 Bridge，且新 Bridge 必須停在 Draft PR + CI-only artifact，直到完整驗收與使用者批准。Runtime（JS/HTML/CSS/映射）問題優先改 `amin-vault/`、提升 Runtime 版本即可，不必開新 Bridge。

## ⚠️ 矛盾：文件標註的「正式版本」跟 manifest 內容不一致

這是本 wiki 建立時（見 [`log.md`](../log.md)）第一次 lint 就抓到的問題，記錄下來但**不要自行修正 raw 來源或 manifest**，這屬於發布流程，只能由使用者核准的發布動作處理：

| 來源 | 標註日期 | 說法 |
|---|---|---|
| `AGENTS.md` | 2026-07-19 | 「在使用者完成新的實機驗收並明確批准前，正式 manifest 必須維持 Bridge 16」；verified latest = `0.9.2-bridge16` / code `112` |
| `amin-vault/ARCHITECTURE.md` | 2026-07-19 | 同上，Bridge 16 / rc19 為目前驗證狀態 |
| `README.md` | 對應 Bridge 16 | Android APK: `0.9.2-bridge16` / code `112` |
| `amin-vault/native-release-manifest.json` | `publishedAt: 2026-07-22` | `latestVersionName: "0.9.2-bridge23"`，`latestVersionCode: 119`，release notes 提到新增 Amin Control API v1 |
| `git log`（`main` 分支提交） | 到 2026-08-06 為止 | 已有 `Bridge 17` 到 `Bridge 23` 的「release: publish signed/verified Bridge N」提交 |

可能的解讀（不確定，需要使用者或下一次 ingest 確認）：
- 要嘛是 Bridge 17-23 已經完成使用者實機驗收與批准，只是 `AGENTS.md`／`ARCHITECTURE.md`／`README.md` 這三份文件的「最後人工驗證」欄位沒有跟著更新（最可能）；
- 要嘛是 manifest 在沒有完整走過 `AGENTS.md` 規則 5-8 的情況下被更新了，違反了發布閘門。

**行動建議**：下次有人力核對時，先確認 Bridge 17-23 是否真的完成了 `AGENTS.md` 規則 7-8 要求的測試與實機驗證，再決定是把三份文件更新到 Bridge 23，還是回滾 manifest。這不是 wiki 能單方面決定的事。

## 簽章與正式發布驗收（`android-native/` 文件）

來源：[`android-native/RELEASE_SIGNING.md`](../../android-native/RELEASE_SIGNING.md)、[`android-native/PERMANENT_SIGNING_IDENTITY.md`](../../android-native/PERMANENT_SIGNING_IDENTITY.md)、[`android-native/UPDATE_BRIDGE_SIGNING.md`](../../android-native/UPDATE_BRIDGE_SIGNING.md)、[`android-native/RC092_DEVICE_ACCEPTANCE.md`](../../android-native/RC092_DEVICE_ACCEPTANCE.md)

這四份文件全部最後一次改動都在初始提交 `1a9c84d`（2026-07-19），之後沒有再更新，內容對應到 Bridge 1 / rc2-rc6 那個時期，比 `AGENTS.md`／`ARCHITECTURE.md` 標註的 Bridge 16 現況還早。

`RELEASE_SIGNING.md` 定義的契約重點：正式版只能用一把永久 keystore；`native-release-manifest.json` 的 `packageId`／`latestVersionName`／`latestVersionCode`／`apkUrl`／`apkSha256`／`signerCertificateSha256`／`sizeBytes`／`publishedAt` 全部正確前 `enabled` 必須是 `false`；正式發布前至少要做過一次「從離線備份還原 keystore」的災難復原演練。

`RC092_DEVICE_ACCEPTANCE.md` 是一份逐項勾選的實機驗收表，涵蓋安裝並存、Runtime 更新、手把回歸、卡匣匯入、備份還原、診斷報告、離線救援、Update Center、簽章復原、權限中心。它記錄的狀態是「自動化驗收完成，實體裝置與正式簽章尚未執行」，多數實體裝置項目仍是 `[ ]`，最後一行明寫「才能將 release manifest 設為 `enabled: true`」。這份表格本身是 Bridge 1/rc2 時期的快照，**不能直接當成 Bridge 17-23 是否完成驗收的證據**——但它示範了這個專案原本預期的驗收粒度（逐項勾選、實機才算數），可以用來對照未來若拿到 Bridge 17-23 的驗收紀錄時該長什麼樣子。

## ⚠️ 矛盾：`PERMANENT_SIGNING_IDENTITY.md` 記錄的簽章指紋跟其他所有來源不一致

`PERMANENT_SIGNING_IDENTITY.md` 寫「Signer certificate SHA-256: `aff1bab8f364e1d0f248c6242da1e07a7114778a7347b4390f179948290c256e`」，並聲稱這是 Bridge 1（code 97）起的永久簽章身分。但這個指紋**只出現在這一份文件裡**；其餘每一個提到簽章指紋的來源都是 `3b9a3125b2cd19389c284e834c4ff9eb67caeecb647fe41897d923169f4152c7`：

| 來源 | 指紋 |
|---|---|
| `android-native/PERMANENT_SIGNING_IDENTITY.md` | `aff1bab8...c256e`（唯一不同的一份） |
| `AGENTS.md` | `3b9a3125...52c7` |
| `amin-vault/ARCHITECTURE.md` | `3b9a3125...52c7` |
| `amin-vault/native-release-manifest.json`（`signerCertificateSha256`） | `3b9a3125...52c7` |
| `amin-vault/architecture.json`（`signer_certificate_sha256`） | `3b9a3125...52c7` |
| `.github/workflows/publish-amin-pocket-gba-bridge2-update.yml`、`-bridge15-update.yml`、`-bridge16-update.yml`、`build-amin-pocket-gba-bridge17.yml` | 都用 `grep -qi '3b9a3125...'` 驗證簽章 |

可能的解讀（不確定，wiki 不替使用者下結論）：
- `PERMANENT_SIGNING_IDENTITY.md` 記的可能是打字或複製貼上錯誤，實際一直都是 `3b9a3125...`；
- 或者 Bridge 1 當時真的用了 `aff1bab8...` 這把 key 簽章，後來（Bridge 2 起，從 CI workflow 看是從 Bridge 2 就已經是 `3b9a3125...`）換成另一把 `3b9a3125...` 的永久 key，但沒有任何 raw 來源記錄這次金鑰更替——而 `RELEASE_SIGNING.md` 自己的規則 1 明講「正式版只使用一把永久 keystore」，如果真的换过 key，這本身就違反了專案自訂的簽章契約。

**行動建議**：這需要人工確認 `PERMANENT_SIGNING_IDENTITY.md` 是否只是文件錯誤；如果不是，代表 Bridge 1→Bridge 2 之間有一次未被記錄的金鑰更替，應該視為需要向使用者澄清的簽章鏈完整性問題，不是 wiki 能自行判定或修改 raw 來源解決的事。

## 版本歷史（來自 git log，Bridge 2 → Bridge 23）

`git log`（oldest→newest 摘要）顯示的發布序列，可作為時間軸參考：

```text
Bridge 2 ... Bridge 9   （更早的歷史，未在本次 ingest 範圍內詳讀）
Bridge 10  code 106  Native Cartridge Vault v2
Bridge 11  code 107  Universal Control v0.1
Bridge 12  code 108  Universal Control entry fix
Bridge 13  code 109  Universal Control v0.2
Bridge 14  code 110  GBA-style controls
Bridge 15  code 111  fixed D-pad
Bridge 16  code 112  control modes and tuning        ← AGENTS.md/ARCHITECTURE.md/README.md 標註的「現況」
Bridge 17  code 113  voice command update
Bridge 18  verified  voice update
Bridge 19  verified  voice command catalog
Bridge 20  verified  floating voice fix
Bridge 21  verified  separate floating voice controls
Bridge 22  verified  independent floating controls
Bridge 23  code 119  Amin Control API v1              ← native-release-manifest.json 標註的「現況」
```

## Runtime manifest 現況

`runtime-manifest.json`：`runtimeVersion: 0.9.2-rc19`，`entryPoint: ./gba.html`，`minimumNativeVersion: 0.9.0`。`optionalCapabilities` 清單很長（native-gamepad、save-vault、backup-v2、diagnostics-v1 等），代表 Runtime 本身用 capability flag 做漸進增強，而不是單一版本號綁死所有功能。
