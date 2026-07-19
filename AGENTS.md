# Amin Pocket GBA AI Handoff

新 AI、Codex 或 ChatGPT 請先讀這份，再修改程式。

最後人工驗證：2026-07-19，Asia/Taipei
Repository：`ken12121122-dotcom/ken12121122-dotcom.github.io`
Default branch：`main`

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
- latest：`0.9.2-bridge16`
- code：`112`
- signer SHA-256：`3b9a3125b2cd19389c284e834c4ff9eb67caeecb647fe41897d923169f4152c7`
- 原生功能分支：`agent/amin-pocket-gba-universal-control-v01`

Runtime 權威檔案：`amin-vault/runtime-manifest.json`

- latest：`0.9.2-rc19`
- entry：`amin-vault/gba.html`
- JS、HTML、CSS 與映射修正可熱更新，不需重裝 APK

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

- `amin-vault/native-release-manifest.json`
- `amin-vault/runtime-manifest.json`
- `android-native/app/src/main/java/com/amin/pocketgba/MainActivity.java`
- `amin-vault/gba-native-input.js`
- `amin-vault/gba-controller-native-addon.js`
- `amin-vault/gba-controller-runtime.js`
- `amin-vault/gba-controller.js`
- `amin-vault/gba-signal-lab.html`
- `amin-vault/ARCHITECTURE.md`
- `amin-vault/architecture.json`

## 修改規則

Runtime 問題優先修改 main 的 `amin-vault/` 並提升 Runtime 版本。

只有 Java、Manifest、原生 Activity、AccessibilityService 或 APK 內容改動才建立新 Bridge。發布前必須增加版本、通過 build 與 lint、檢查 package/version/signer，最後才更新 native release manifest。

不要直接把長歷史功能分支合併到 main。不要在未讀 manifest、未確認 CI 與簽章前宣稱已發布。

## 已知限制

- Web Gamepad API 在 Android WebView 可能沒有裝置
- Signal Lab 的下載與分享按鈕曾無反應，複製可用
- 尚未做控制器命名、VID/PID/descriptor 與每裝置 profile
- 尚未做多控制器切換
- 尚未做 IG/FB 短影音自動模式
- 尚未做 App 內藍牙掃描與配對

## 建議下一步

1. Controller Lab：控制器命名、裝置識別、原始按鍵與 axis 監看
2. 每控制器獨立 profile 與自動套用
3. 短影音模式
4. 修復 Signal Lab 下載與分享
5. 讓實體手把和全域虛擬按鍵共用同一個 action core

每次完成實機驗證或正式發布後，請同步更新本檔、兩份 manifest 與架構文件。
