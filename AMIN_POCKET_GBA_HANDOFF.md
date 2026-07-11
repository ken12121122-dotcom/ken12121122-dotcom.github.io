# Amin Pocket GBA v0.9.0｜新對話交接檔

更新日期：2026-07-12  
Repository：`ken12121122-dotcom/ken12121122-dotcom.github.io`  
主分支：`main`  
目前階段：**Android 原生手把橋接已完成程式與 APK 建置，等待實體手把按鈕 1–4／Start／Select／L／R 的最終原生訊號驗證與遊戲內驗收。**

---

## 1. 核心連結

### GitHub

- Repository  
  https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io

- Android 原生專案  
  https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io/tree/main/android-native

- Android 原生入口 `MainActivity.java`  
  https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io/blob/main/android-native/app/src/main/java/com/amin/pocketgba/MainActivity.java

- Android 建置 Workflow  
  https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io/blob/main/.github/workflows/build-amin-pocket-gba-android.yml

- GitHub Actions 下載／查看 APK 建置  
  https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io/actions/workflows/build-amin-pocket-gba-android.yml

- CI 驗證 PR #1  
  https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io/pull/1

### 線上頁面

- Pokémon GBA 遊戲中心  
  https://ken12121122-dotcom.github.io/amin-vault/gba.html?v=090

- 手把按鈕與搖桿設定  
  https://ken12121122-dotcom.github.io/amin-vault/gba-controller.html?v=090

- 原始手把訊號接收器  
  https://ken12121122-dotcom.github.io/amin-vault/gba-signal-lab.html?v=090

- Pocket OS 主頁  
  https://ken12121122-dotcom.github.io/amin-vault/?v=090

---

## 2. 問題來源與已完成診斷

使用者的 USB 手把在 Android Chrome 被辨識為：

```text
USB Gamepad
Vendor: 0810
Product: 0001
mapping: ""
16 buttons
4 axes
```

網頁原始訊號報告確認：

```text
有收到：button 12、13、14、15
有收到：axis 0、1、2、3
沒有收到：button 0～11
沒有收到：keyboard event
```

實際意義：

- `button 12～15` 是十字方向鍵。
- `axis 0～3` 是左右搖桿。
- 實體按鈕 1、2、3、4、Start、Select、L、R 沒有進入 Chrome Gamepad API。
- 純網頁 JavaScript 無法映射「不存在於瀏覽器中的訊號」。

因此專案已從純 PWA 修正方向，升級成：

```text
實體 USB／Bluetooth 手把
→ Android Activity.dispatchKeyEvent(KeyEvent)
→ Android Activity.dispatchGenericMotionEvent(MotionEvent)
→ WebView.evaluateJavascript
→ window.AMIN_NATIVE_INPUT
→ 自訂手把 Profile
→ EmulatorJS gameManager.simulateInput
→ mGBA
```

---

## 3. 已完成項目

### A. GBA 網頁遊戲中心

- 支援匯入 `.gba`、`.bin`。
- 支援匯入包含多個 ROM 的 `.zip`。
- ROM 存在瀏覽器／WebView IndexedDB，不上傳 GitHub、Supabase 或 Google Drive。
- 使用 EmulatorJS stable + mGBA。
- 第一次啟動核心需要網路。
- 不提供、不託管 Pokémon ROM 或 BIOS。

主要檔案：

```text
amin-vault/gba.html
amin-vault/gba.css
amin-vault/gba.js
```

### B. 橫向沉浸式全螢幕

- 按下「開始」時要求全螢幕。
- 嘗試鎖定 `landscape`。
- 隱藏瀏覽器／Android 系統列。
- 顯示載入遮罩與旋轉提示。
- 遊戲畫面占滿可用螢幕。

主要檔案：

```text
amin-vault/gba-immersive.js
amin-vault/gba.css
```

### C. 存檔保護

已修正原本「離開按鈕直接重新載入，可能沒讓存檔落盤」的問題。

目前流程：

```text
遊戲內儲存
→ 強制呼叫 saveSaveFiles()
→ 觸發 EmulatorJS exit
→ 等待存檔寫入
→ 回遊戲庫
```

並在以下情況嘗試補存：

- 按離開
- App／頁面切到背景
- Android Activity `onPause()`

主要檔案：

```text
amin-vault/gba-save-guard.js
```

### D. Web 原始訊號接收器

可紀錄：

- Gamepad button down／up
- Gamepad axes
- KeyboardEvent
- PointerEvent
- Android／瀏覽器返回行為
- 裝置 ID、mapping、按鈕數、軸數
- JSON 匯出

主要檔案：

```text
amin-vault/gba-signal-lab.html
amin-vault/gba-signal-lab.css
amin-vault/gba-signal-lab.js
```

### E. Android 原生輸入橋接

Android App 已建立，原生層直接攔截：

- `KeyEvent`
- `MotionEvent`
- Gamepad source
- Joystick source
- D-pad source
- `KEYCODE_BUTTON_A～MODE`
- `KEYCODE_BUTTON_1～16`
- `DPAD_UP／DOWN／LEFT／RIGHT／CENTER`
- `AXIS_X／Y／Z／RZ`
- `AXIS_HAT_X／HAT_Y`
- Trigger／Brake／Gas axes

原生事件會送入 WebView：

```text
AMIN_NATIVE_INPUT.receiveKey(...)
AMIN_NATIVE_INPUT.receiveMotion(...)
AMIN_NATIVE_INPUT.receiveDevice(...)
```

主要檔案：

```text
android-native/app/src/main/java/com/amin/pocketgba/MainActivity.java
amin-vault/gba-native-input.js
```

### F. 遊戲內自訂控制橋接

目前 GBA 邏輯控制：

```text
A      logicalIndex 8
B      logicalIndex 0
Start  logicalIndex 3
Select logicalIndex 2
L      logicalIndex 10
R      logicalIndex 11
Up     logicalIndex 4
Down   logicalIndex 5
Left   logicalIndex 6
Right  logicalIndex 7
```

預設需求映射：

```text
實體按鈕 2 → GBA A
實體按鈕 3 → GBA B
實體按鈕 1 → 無功能
實體按鈕 4 → 無功能
十字鍵 → 方向
左搖桿 → 方向
右搖桿 → 無功能
Start → GBA Start
Select → GBA Select
L1 → GBA L
R1 → GBA R
```

目前同時支援三種來源：

```text
Gamepad API binding
NATIVE_KEY binding
NATIVE_AXIS binding
```

主要檔案：

```text
amin-vault/gba-controller-runtime.js
amin-vault/gba-controller.html
amin-vault/gba-controller.js
amin-vault/gba-controller-native-addon.js
```

### G. Android App 外殼

已完成：

- WebView 載入 GBA 遊戲中心
- User-Agent 加入 `AminPocketGBA/0.9.0`
- 橫向固定與沉浸式全螢幕
- 螢幕保持開啟
- Android 檔案選擇器
- 多檔案選取
- `.gba`／`.bin`／`.zip` MIME 接收
- WebView DOM Storage／IndexedDB
- HTTPS only
- 外部網址交給系統瀏覽器
- WebView render process crash 後重建
- Android 返回鍵接入安全離開／存檔流程
- App pause 時補存
- App 圖示與主題
- ROM 與模擬器資料排除雲端備份／裝置轉移

Android App 載入：

```text
https://ken12121122-dotcom.github.io/amin-vault/gba.html?native=1&v=090
```

### H. GitHub Actions APK 建置

目前主分支建置設定：

```text
Android Gradle Plugin 8.7.3
Gradle 8.9
JDK 17
compileSdk 35
targetSdk 35
minSdk 26
versionName 0.9.0
versionCode 90
```

APK artifact 名稱：

```text
Amin-Pocket-GBA-v0.9.0-debug
```

PR #1 的說明已記錄：GitHub Actions 成功完成 APK build 與 artifact upload。PR 只用來做 CI 驗證，最後關閉且沒有 merge；相同穩定工具鏈修改已直接套入 `main`。

---

## 4. 已驗證與未驗證分界

| 項目 | 狀態 | 說明 |
|---|---|---|
| GBA ROM 匯入 | 已完成 | `.gba`／`.bin`／`.zip` |
| mGBA 啟動 | 已完成 | EmulatorJS web core |
| 橫向全螢幕 | 已完成 | Web + Android shell |
| 遊戲內存檔防護 | 已完成程式 | 仍需長時間實機回歸測試 |
| Web 十字鍵 | 已驗證 | button 12～15 |
| Web 左右搖桿 | 已驗證 | axis 0～3 |
| Web 按鈕 1～4 | 已確認失敗 | Android Chrome 沒交給網頁 |
| Android APK 建置 | 已完成 | debug APK artifact |
| Android App 啟動 | 已做過使用者端測試 | User-Agent 可辨識為 `AminPocketGBA/0.9.0` |
| Android 原生 KeyEvent 程式 | 已完成 | 尚缺該手把的實際 keyName 報告 |
| Android 原生 MotionEvent 程式 | 已完成 | 尚缺 App 內原生 JSON 驗收 |
| 按鈕 2 → GBA A | 未最終驗證 | 程式已有預設映射 |
| 按鈕 3 → GBA B | 未最終驗證 | 程式已有預設映射 |
| Start／Select／L／R | 未最終驗證 | 等原生訊號報告 |
| 正式簽章 Release APK | 未完成 | 目前只有 debug signing |
| App 自動更新 | 未完成 | 尚無 release manifest／固定簽章 |
| 存檔雲端同步 | 未完成 | 目前只在裝置 WebView storage |
| ROM 雲端同步 | 不做 | ROM 保持本機 |

---

## 5. 現在真正卡在哪裡

程式不是卡在「沒有寫按鍵映射」。映射與 Android 原生攔截層都已經寫好。

現在卡在最後一個實機證據：

> 這支 Vendor 0810／Product 0001 手把，在 Android 原生 Activity 中按下實體 1、2、3、4 時，實際送出的 `keyName`、`keyCode` 與 `scanCode` 是什麼？

必須從 **Android App 內的原生報告** 取得，不是從一般 Chrome 的 Web JSON 取得。

下一份正確報告應該是：

```text
amin-gba-native-signal-日期時間.json
```

而不是：

```text
amin-gba-signal-日期時間.json
```

前者代表 Android `KeyEvent`／`MotionEvent`；後者仍只是瀏覽器 Gamepad API。

---

## 6. 下一步執行順序

### 第一優先：原生訊號實機驗收

1. 安裝並打開 `Amin-Pocket-GBA-v0.9.0-debug.apk`。
2. 確認是在 APK 裡，不是一般 Chrome。
3. 進入「📡 收集手把原始訊號」。
4. 依序按：1、2、3、4、Start、Select、L、R。
5. 左搖桿推上下左右。
6. 下載 **Android 原生 JSON**。
7. 將 JSON 上傳到新對話。

### 第二優先：依真實 keyName 修正最終映射

根據 JSON 對照：

```text
實體按鈕 2 → 收到的實際 KEYCODE／scanCode → GBA A
實體按鈕 3 → 收到的實際 KEYCODE／scanCode → GBA B
```

若手把實體數字與 Android `KEYCODE_BUTTON_1～4` 並不一致，以實測為準，不再猜測。

### 第三優先：遊戲內完整驗收

至少測：

- A 確認
- B 返回
- Start
- Select
- L／R
- 十字鍵
- 左搖桿
- 右搖桿確實無功能
- 遊戲內存檔
- 離開 App
- 再次打開並讀取存檔

### 第四優先：建立可長期更新的 APK

目前 CI 是 debug APK。要避免未來每次重裝或簽章不一致，需完成：

1. 建立正式 Android signing keystore。
2. 用 GitHub Actions Secrets 保存簽章資訊。
3. 建立 GitHub Release。
4. 發布固定簽章 APK。
5. 增加 `update-manifest.json` 或 GitHub Releases API 版本檢查。
6. APK 原生程式更新時提示下載新版。
7. 純網頁 UI／JS 更新維持 GitHub Pages 即時更新。

---

## 7. 重要風險與邊界

### A. Chrome PWA 與 Android App 資料不是同一份

Android WebView 有自己的 App sandbox。Chrome 裡已匯入的 ROM／存檔不會自動出現在 APK 裡，反向也一樣。

### B. Debug APK 更新風險

GitHub Actions 每次在乾淨 runner 建置 debug APK，若 debug signing key 不一致，Android 可能拒絕覆蓋安裝。若為了安裝新版而先解除安裝舊版，舊 App 的 ROM／存檔可能一起消失。

因此在大量使用之前，應優先完成固定 release signing。

### C. App 目前仍依賴 GitHub Pages

APK 是原生外殼，但內容主要載入遠端：

```text
GitHub Pages GBA UI
EmulatorJS CDN
fflate CDN
```

優點：UI／JS 修改可不重裝 APK。  
限制：第一次開啟與核心未快取時需要網路。

### D. 更新分成兩層

```text
GitHub Pages 的 HTML／CSS／JS
→ 使用者重新開啟 App 即可取得新版

Android MainActivity／Manifest／原生橋接
→ 必須重新 build APK 並安裝更新
```

### E. 版權邊界

- Repository 與 APK 不包含 Pokémon ROM。
- 不包含 BIOS。
- 僅允許使用者匯入自己合法取得的備份。

---

## 8. 關鍵檔案索引

```text
# Web GBA
amin-vault/gba.html
amin-vault/gba.css
amin-vault/gba.js

# 全螢幕與保存
amin-vault/gba-immersive.js
amin-vault/gba-save-guard.js

# Web / Native 輸入
amin-vault/gba-native-input.js
amin-vault/gba-controller-runtime.js
amin-vault/gba-controller.html
amin-vault/gba-controller.js
amin-vault/gba-controller-native-addon.js

# 診斷
amin-vault/gba-signal-lab.html
amin-vault/gba-signal-lab.css
amin-vault/gba-signal-lab.js
amin-vault/gba-signal-native-addon.js

# Android
android-native/app/src/main/java/com/amin/pocketgba/MainActivity.java
android-native/app/src/main/AndroidManifest.xml
android-native/app/build.gradle
android-native/build.gradle
android-native/README.md

# CI
.github/workflows/build-amin-pocket-gba-android.yml
```

---

## 9. 新對話直接貼上的啟動指令

```text
請讀取 GitHub repository：
ken12121122-dotcom/ken12121122-dotcom.github.io

先完整讀取根目錄的 AMIN_POCKET_GBA_HANDOFF.md，並以它作為唯一進度基準，不要重做已完成項目。

目前任務是 Amin Pocket GBA v0.9.0 的 Android 原生手把橋接最終驗收：
1. 先檢查 main 分支最新狀態與 GitHub Actions。
2. 我會提供 amin-gba-native-signal-*.json。
3. 根據 Android 原生 keyName、keyCode、scanCode 修正實體按鈕映射。
4. 需求固定：實體 2=A、實體 3=B、1/4 無功能、十字鍵與左搖桿控制方向、右搖桿無功能。
5. 修改後必須詳實回報：改了哪些檔案、commit SHA、驗收結果、未完成項目。
6. 不要聲稱看得到我本機檔案；只能讀我上傳的檔案與 GitHub。
7. 不要提供或散布 Pokémon ROM／BIOS。
8. 若沒有完成實機驗收，必須明確寫未完成，不要用漂亮話包裝。
```

---

## 10. 一句話交接

> **v0.9.0 已經完成 PWA 模擬器、全螢幕、存檔保護、訊號診斷、Android 原生 KeyEvent／MotionEvent 橋接與 debug APK 建置；目前只差從 APK 內取得這支實體手把的 Android 原生按鈕 JSON，完成最終映射與遊戲內驗收。**
