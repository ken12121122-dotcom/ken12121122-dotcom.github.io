# Amin Pocket GBA AI Handoff

新 AI、Codex、ChatGPT 或 GitHub Actions 在修改程式前，必須先完整閱讀本檔。

最後人工驗證：2026-07-19，Asia/Taipei  
Repository：`ken12121122-dotcom/ken12121122-dotcom.github.io`  
Default branch：`main`

## 不可違反的發布閘門

以下規則高於任何單一功能任務、版本號、工作流或「先發再修」要求：

1. **功能分支不得直接或間接推送 `main`。**
2. **禁止使用 `workflow_run` 將功能分支的 APK、manifest 或 release 檔案自動寫入 `main`。**
3. Draft PR、成功編譯、parser 測試、lint、APK 產出或簽章成功，都不代表功能完成。
4. 任何必要測試失敗、跳過或未執行時，必須回報「未完成」，不得提高正式版本號。
5. `amin-vault/native-release-manifest.json` 只能由獨立、可審查、經使用者明確批准的發布變更更新。
6. 一般 CI 只能產生 `CI DEBUG ARTIFACT ONLY`，不得使用永久簽章，也不得成為正式更新來源。
7. 正式發布前，同一 commit 必須完成：單元測試、JavaScript 測試、Android lint、APK 檢查、模擬器安裝、launcher/deep-link 驗證及 instrumentation acceptance tests。
8. 涉及 Accessibility、手把、語音、藍牙或真機系統動作的功能，還必須完成實機閉環驗證。
9. 禁止用新增 Bridge 版本號掩蓋尚未解決的同一問題。
10. 未經使用者明確要求，不得移除、放大、縮小或重新排列既有可用 UI。

違反以上任一項時，立刻停止發布，只保留 Draft PR 與 CI-only artifact。

## 產品方向

Amin 不是單一 GBA App，也不是一次性語音 Demo。它是 Android 手機、電腦、遊戲手把、藍牙、Wi-Fi、投影與未來外接裝置之間的可擴充控制中介層。

第一個必須可靠完成的閉環：

1. 回到 Android 桌面；
2. 左右或上下滑動頁面；
3. 移動游標；
4. 點擊任意 App 並進入；
5. 保留既有 GBA、存檔保護、手把輸入與自動更新能力。

新增功能應優先接入共用 Action Core，不得各自建立互不相通的控制路徑。

## 現況

Amin Pocket GBA 是 Android 原生外殼、GitHub Pages 熱更新 Runtime、EmulatorJS/mGBA，以及 Android 無障礙全域控制盤組成的個人系統。

已在 Samsung SM-A5560、Android 15 實機驗證：

- APK：`0.9.2-bridge16`，versionCode `112`
- Runtime：`0.9.2-rc19`
- 有線遊戲手柄可由 Android 原生層收到按鍵與搖桿
- 手把設定頁可綁定，測試區可顯示原生輸入
- 實體手把可控制 GBA 遊戲
- 全域控制盤可使用游標模式與捲動模式

Android WebView 的 `navigator.getGamepads()` 可能是空的。不要因此判斷手把失敗，原生橋接才是目前的輸入真相來源。

## 正式版本來源

APK 權威檔案：`amin-vault/native-release-manifest.json`

- package：`com.amin.pocketgba`
- verified latest：`0.9.2-bridge16`
- verified code：`112`
- signer SHA-256：`3b9a3125b2cd19389c284e834c4ff9eb67caeecb647fe41897d923169f4152c7`
- 原生功能分支：`agent/amin-pocket-gba-universal-control-v01`

在使用者完成新的實機驗收並明確批准前，正式 manifest 必須維持 Bridge 16。

Runtime 權威檔案：`amin-vault/runtime-manifest.json`

- verified latest：`0.9.2-rc19`
- entry：`amin-vault/gba.html`
- JS、HTML、CSS 與映射修正可熱更新，不需重裝 APK

## 完成定義

功能只有在以下適用項目全部通過後，才能標示完成：

- 原始碼可編譯
- Java/JUnit 單元測試通過
- `node --test tests/*.test.mjs` 通過
- Android lint 通過
- APK package、version、activities、permissions 與 signing state 完成檢查
- APK 可安裝至 Android 35 模擬器
- launcher 與必要 deep links 可開啟
- instrumentation/acceptance tests 通過
- 實際使用者動作可端到端完成，不只 parser 或 UI 有反應
- GBA、存檔、手把、更新及 Accessibility 既有能力沒有退化
- release notes 只描述已驗證的功能

## 手把輸入路徑

```text
USB / 2.4G / 系統已配對藍牙手把
→ MainActivity KeyEvent / MotionEvent
→ gba-native-input.js / AMIN_NATIVE_INPUT
→ gba-controller-native-addon.js（設定與測試）
→ gba-controller-runtime.js（遊戲映射）
→ EmulatorJS gameManager.simulateInput
→ mGBA
```

已觀察到：

- `KEYCODE_BUTTON_1` 到 `KEYCODE_BUTTON_10`
- `AXIS_X`、`AXIS_Y`、`AXIS_Z`、`AXIS_RZ`
- `AXIS_HAT_X`、`AXIS_HAT_Y`

常見預設：

- A：BUTTON_2
- B：BUTTON_3
- Start：BUTTON_10
- Select：BUTTON_9
- L：BUTTON_5
- R：BUTTON_6
- 方向：DPAD、AXIS_X/Y 或 AXIS_HAT_X/Y

Controller profile 存於 localStorage：`amin-gba-controller-profile-v1`。

## 全域控制盤

主要檔案：

- `UniversalControlAccessibilityService.java`
- `UniversalControlSetupActivity.java`

Bridge 16 已完成：

- 浮動喚醒球，可拖曳與吸附邊緣
- 2 秒無操作淡化
- GBA 造型方向、A/B、L/R、Select/Start
- 游標位移 `2～64 dp`，預設 `16 dp`
- 8、16、32 dp 快速選項
- 長按方向鍵連續移動
- 長按 Select 切換游標與捲動模式
- 按鍵自動收合

AccessibilityService 使用手勢與浮動層，不讀取其他 App 內容。

## UI 約定

1. 白色 Android 原生控制中心：主要入口與版本管理
2. GBA Runtime：遊戲庫與模擬器
3. 黑色舊 Pocket OS：保留作封存或實驗入口，不是預設首頁

GBA 返回應回白色原生控制中心，不要回黑色舊首頁。

## 關鍵檔案

- `AGENTS.md`
- `amin-vault/native-release-manifest.json`
- `amin-vault/runtime-manifest.json`
- `android-native/app/src/main/java/com/amin/pocketgba/MainActivity.java`
- `android-native/app/src/main/java/com/amin/pocketgba/UniversalControlAccessibilityService.java`
- `amin-vault/gba-native-input.js`
- `amin-vault/gba-controller-native-addon.js`
- `amin-vault/gba-controller-runtime.js`
- `amin-vault/gba-controller.js`
- `amin-vault/gba-signal-lab.html`
- `amin-vault/ARCHITECTURE.md`
- `amin-vault/architecture.json`

## 修改規則

Runtime 問題優先修改 main 的 `amin-vault/` 並提升 Runtime 版本。

只有 Java、Manifest、原生 Activity、AccessibilityService 或 APK 內容改動才建立新 Bridge。新 Bridge 必須停留在 Draft PR 與 CI-only artifact，直到完整驗收與使用者批准。

不要直接把長歷史功能分支合併到 main。不要在未讀 manifest、未確認 CI、模擬器、實機與簽章前宣稱已發布。

CI 不得寫死 Bridge 版本。應從實際 APK 讀取 package、versionName、versionCode 與 launcher，再進行驗證。

## 已知限制

- Web Gamepad API 在 Android WebView 可能沒有裝置
- Signal Lab 的下載與分享按鈕曾無反應，複製可用
- 尚未做控制器命名、VID/PID/descriptor 與每裝置 profile
- 尚未做多控制器切換
- 尚未做 IG/FB 短影音自動模式
- 尚未做 App 內藍牙掃描與配對

## 建議下一步

1. 讓實體手把、全域虛擬按鍵與語音共用同一個 Action Core
2. Controller Lab：控制器命名、裝置識別、原始按鍵與 axis 監看
3. 每控制器獨立 profile 與自動套用
4. 短影音模式
5. 修復 Signal Lab 下載與分享

## 每次任務必須回報

- 使用者可見行為改了什麼
- 修改了哪些檔案
- 哪些檢查通過
- 哪些檢查失敗或未執行
- 是否改動正式版本
- 安全回退點

每次完成實機驗證或正式發布後，才同步更新本檔、兩份 manifest 與架構文件。
