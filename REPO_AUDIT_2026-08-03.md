# Repo 完整性稽核報告（2026-08-03）

稽核者：Claude（受使用者 ken12121122@gmail.com 要求檢查專案本身有沒有問題）
稽核範圍：`main` 分支原始碼、`amin-vault/*.json` manifest、簽章、GitHub commit 記錄、PR 歷史、已發布的 `amin-vault/releases/*.apk`

**任何新對話在修改本專案前，應先讀完本檔，避免重做同一輪調查。**

---

## 1. 嚴重問題：`main` 分支缺少目前正式版的大部分原始碼

### 現象

比對 GitHub PR 歷史發現一個重複出現的模式：

- Bridge 18～23 的每一個「release」PR（#14、#15、#16、#18、#19、#20、#21、#22、#23、#24、#25、#26、#27）狀態全部是 **`closed` 但 `merged: false`**——也就是全部沒有真正合併就被關閉。
- 但 `main` 上同時間有 `github-actions[bot]` 直接推上去的 commit（例如 `8558286 release: publish signed Bridge 23`），只改了 `amin-vault/native-release-manifest.json` 並丟進已簽名的 `.apk` 二進位檔，**完全沒有附上對應原始碼**。
- PR #26（`release: Bridge 23 Amin Control API v1`）的說明文字自己承認：
  > "the release was published through the permanent-signing pipeline rather than merged into the Bridge 22 source branch."

### 證據：實際被裝到手機上的功能，原始碼不在 main

把 `amin-vault/releases/Amin-Pocket-GBA-v0.9.2-bridge23.apk` 解開看 `classes.dex`，確認手機上會裝的正式版裡真的存在下列類別：

```
AminInputGateway
AminControlApiConfig / AminControlApiService / AminControlApiActivity
ExecutionResult
FloatingVoiceController
```

但這些類別的 `.java` 原始碼在 `main` 分支完全找不到（`git log --all --diff-filter=A` 也查不到曾經加入過）。原始碼其實都在孤立分支上，從未合併：

```
release/bridge18-voice
release/bridge19-voice-catalog
release/bridge20-floating-voice-fix
release/bridge21-separate-floating-voice
release/bridge22-independent-floating-toggles
release/bridge23-amin-control-api-v1   ← 目前正式版原始碼所在
```

`release/bridge23-amin-control-api-v1` 上實際存在、但 `main` 沒有的 Java 檔案（共約 34 個，節錄較關鍵的）：

```
UniversalControlAccessibilityService.java   1136 行（AGENTS.md 有列為關鍵檔案，main 上其實不存在）
UniversalControlSetupActivity.java           464 行（AGENTS.md 有列為關鍵檔案，main 上其實不存在）
FloatingVoiceController.java                 795 行
VoiceCommandActivity.java / Catalog*.java
AminControlHttpServer.java                   390 行（本機 REST + WebSocket 伺服器本體）
AminControlApiActivity.java / Service.java / Config.java
AminInputGateway.java / AminActionDispatcher.java / AminActionValidator.java
AminAutomationActivity.java / AminAutomationReceiver.java
NativeCartridgeVaultBridge.java / LaunchGateActivity.java
```

### 為什麼這是嚴重問題

1. 直接違反專案自己在 `AGENTS.md` 訂的發布閘門規則第 1、2、5、6 條（功能分支不得直接推 main；manifest 只能由可審查、經批准的變更更新；CI 只能產生 CI-only artifact）。這個違規**重複發生至少 6 次**（Bridge 18～23）。
2. `AGENTS.md` 裡列的「關鍵檔案」清單本身已經過期／錯誤——它列的 `UniversalControlAccessibilityService.java` 等檔案在 `main` 上根本不存在。
3. 任何人（包含未來的 AI）如果只看 `main` 就動手修改「正式版」，會在不知情的狀況下漏掉語音控制、全域控制盤、Control API 整包功能，非常容易做出破壞性回歸。
4. `main` 上的 CI（`.github/workflows/build-amin-pocket-gba-android.yml` 的「Validate APK components」步驟）從未檢查過這些新元件，因為它根本不知道這些檔案存在，等於這幾版「正式版」從 Bridge 18 起就沒有被 `main` 上的 CI 真正驗證過。

### 好消息

- 孤立分支**沒有被刪除**，原始碼還在（`origin/release/bridge23-amin-control-api-v1` 等仍可 fetch），不是完全遺失，只是沒進 `main`。
- APK 簽章確認一致：解開 APK 用 `openssl x509` 算出的 signer SHA-256 指紋（`3b9a3125b2cd19389c284e834c4ff9eb67caeecb647fe41897d923169f4152c7`）跟 `native-release-manifest.json` 記錄的完全相符，代表建置者確實持有正式簽章金鑰，不是被冒充。

### 尚未做的後續（使用者已表示「先不處理，自己評估」，不要自動執行）

- 選項 A：把 `release/bridge18-voice` 到 `release/bridge23-amin-control-api-v1` 的原始碼真正合併回 `main`，讓 repo 內容與手機上的正式版一致。
- 選項 B：至少更新 `AGENTS.md`／`amin-vault/architecture.json` 的關鍵檔案清單，標注哪些檔案其實只存在於孤立分支。
- 在使用者明確要求前，不要自行合併任何 `release/bridge*` 分支到 `main`。

---

## 2. 程式碼品質評估（在孤立分支上看到的實際實作）

雖然流程有問題，但**實際程式碼品質本身相當紮實**，不是隨便寫的：

### Amin Control API（本機 REST + WebSocket，`AminControlApiConfig.java` / `AminControlHttpServer.java`）

- Token 用 `SecureRandom` 產生 32 bytes，比對時用 `MessageDigest.isEqual`（常數時間比對，防 timing attack）。
- API 開關與 LAN 模式**預設都是關閉**；沒開 LAN 模式時只接受 loopback（127.0.0.1）連線。
- LAN 白名單預設只允許私有網段 CIDR（`192.168.0.0/16`、`10.0.0.0/8`、`172.16.0.0/12`）。
- 有 port range clamp（1024–65535）與速率限制（1–600 req/min，可調）。
- 請求處理順序正確：先檢查來源位址是否在白名單內，再檢查 token，避免 side-channel。

### APK 自動更新（`NativeUpdateActivity.java`，`android-native/app/src/main/java/com/amin/pocketgba/`）

- 只信任 HTTPS，且網域白名單限定 `ken12121122-dotcom.github.io`、`github.com` 及其 CDN 子網域。
- 下載後需同時通過四項驗證才會顯示「可安裝」：SHA-256、套件名稱（packageId）、versionCode、**簽章憑證指紋**。
- 仍會導向 Android 系統安裝確認畫面，不是靜默安裝（正確做法；Android 本來就不允許非 device-owner 靜默安裝）。
- 有下載大小上限（APK 250MB／manifest 512KB），避免被灌爆儲存空間。

### Runtime 熱更新（`amin-vault/runtime-updater.js` + `amin-vault/sw.js`）

- 每一筆 manifest 裡列的資源 URL，都會檢查 origin 與 scope 是否落在允許範圍內，避免惡意/被竄改的 manifest 夾帶跨網域資源。
- Service Worker 用「新版本先完整下載到新 cache，成功才切換 active cache」的方式做原子式更新，下載失敗會保留舊版本正常運作（不會讓使用者卡在半殘版本）。

**結論：更新機制與新控制 API 本身的資安設計是合格的個人專案水準以上；核心問題出在發布流程治理，不是程式碼本身。**

---

## 3. 其他觀察到的小問題

- 有幾個長期沒關閉、狀態不明的 open PR：#10（`feat: add Amin read-only MCP v0.1`）、#12（`Draft: Voice Command v1 push-to-talk scaffold`）、#17（`Fix floating bubble voice command execution`）。
- `AMIN_POCKET_GBA_HANDOFF.md`（根目錄）內容還停留在 v0.9.0（2026-07-12），早已被 `AGENTS.md` 取代，容易讓新對話誤讀成最新狀態。

---

## 4. 本次稽核方法（供之後複查）

```text
1. 讀 README.md / AGENTS.md / native-release-manifest.json / runtime-manifest.json 建立基準認知
2. grep 全 repo 搜尋 release notes 提到的關鍵字（AminInputGateway、WebSocket、CIDR…）在原始碼中是否存在
3. unzip 開 amin-vault/releases/Amin-Pocket-GBA-v0.9.2-bridge23.apk，strings classes.dex 比對類別是否存在於已發布 APK
4. git log --all --diff-filter=A 確認這些類別是否曾經被 commit 進本地可見的歷史
5. 用 GitHub MCP（list_pull_requests / pull_request_read）查 PR 狀態，找出 merged:false 但功能已上 main 的落差
6. git fetch 對應 release/bridge* 分支，確認原始碼是否還存在、內容是否合理（非只是空殼）
7. openssl x509 驗證 APK 簽章指紋是否與 manifest 聲稱的一致
```
