# Amin Pocket GBA AI Handoff

新 AI、Codex、ChatGPT 或 GitHub Actions 在修改程式前，必須先完整閱讀本檔。

最後人工驗證：2026-10-04（Bridge 103 實機驗收），2026-10-06 正式發布，Asia/Taipei  
Repository：`ken12121122-dotcom/ken12121122-dotcom.github.io`  
Default branch：`main`

## 這個 repo 的定位（給任何 AI）

這個 repository 是使用者的**整合工作環境**之一，不是只服務 Amin Pocket GBA 這一件事。使用者的帳號底下還掛著許多跟這個 GBA 專案完全無關的技能／助理（例如消防防護計畫、門禁管理、資安體系、工程管理、標準工時蒐集、職安體系等），對應他本人橫跨軍旅、消防、氣象、建築、產線管理、倉管、職安主管等多段職涯背景。**看到這些不相關的東西是正常的，不代表環境設定錯誤或這個 repo 用錯地方，不需要因此質疑使用者、要求他解釋為什麼提到無關內容。**

具體規則：
- `amin-vault/`、`amin-wiki/`、`AGENTS.md` 本身描述的技術內容，範圍就是 Amin Pocket GBA 專案，維持原本的邊界，不要把不相關的個人職涯內容硬塞進去。
- 如果使用者要求記錄、整理跟 GBA 無關的個人知識（職涯經歷、其他領域專業），這是合理需求，應該協助他在這個 repo 裡開一個**平行的、獨立的新節點/資料夾**（不是塞進 `amin-wiki/`），而不是拒絕或表示困惑。
- 不確定要放哪裡時，直接問使用者要開在哪個路徑，不要質疑「這個是不是問錯地方了」。

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

正式版：APK `0.11.22-rc14-bridge103`（versionCode `199`），2026-10-06 正式發布。

OWNER 2026-10-03／2026-10-04 實機驗收 Bridge 102／103（紀錄見 PR #169）：
- GEN2 待核准畫面：提供資料、核准、退回（必須寫說明）、停止流程
- ntfy 推播以 `amin-gen2://run/編號` 開啟流程
- 狐狸語音核准：讀回指令，口頭確認後送出
- 對狐狸說「有什麼要我處理的」進入語音核准
- GBA、存檔、手把、全域控制盤與 PackageCanvas 卡片沒有退化
- 2026-10-06：PackageCanvas 網頁 `20261006.3` 的「🎙 用說的」與「✋ 核准頁」按鈕（`AminGen2`）可分別打開 App 的語音核准與 GEN2 待核准頁

Bridge 103 尚未實機確認：
- 背景 15 分鐘檢查的通知
- 語音說「等一下」取消

Bridge 16 時已在 Samsung SM-A5560、Android 15 實機驗證（Runtime `0.9.2-rc19`）：
- 有線遊戲手柄可由 Android 原生層收到按鍵與搖桿
- 手把設定頁可綁定，測試區可顯示原生輸入
- 實體手把可控制 GBA 遊戲
- 全域控制盤可使用游標模式與捲動模式

Android WebView 的 `navigator.getGamepads()` 可能是空的。不要因此判斷手把失敗，原生橋接才是目前的輸入真相來源。

## 正式版本來源

APK 權威檔案：`amin-vault/native-release-manifest.json`

- package：`com.amin.pocketgba`
- verified latest：`0.11.22-rc14-bridge103`（channel `release`）
- verified code：`199`
- signer SHA-256：`3b9a3125b2cd19389c284e834c4ff9eb67caeecb647fe41897d923169f4152c7`
- 原生發布分支：`release/android`。PR 合併後由 `android-release.yml` 建置、永久簽章，並寫入 main 的 manifest。
  - `channel: candidate` 只能用 workflow_dispatch 發候選版。
  - 推到 `release/android` 的必須是正式通道。

在使用者完成新的實機驗收並明確批准前，正式 manifest 必須維持 Bridge 103。

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

## PackageCanvas（GEN2 知識架構工作台）

PackageCanvas 是 OWNER 的 GEN2 知識架構視覺化工作台，與 GBA 遊戲 Runtime 分開。

- 網頁：`packagecanvas/index.html`、`packagecanvas/gen2-source.js`、`packagecanvas/mobile.css`
- 正式網址：`https://ken12121122-dotcom.github.io/packagecanvas/`
- 網頁層修改推到 main 的 GitHub Pages 即生效，不需要提升 `runtime-manifest.json` 或 Bridge 版本；Amin Pocket GBA 的 WebView 只攔截 ROM 與模擬器引擎路徑，`/packagecanvas/` 直接讀 Pages。
- 手機入口規劃：Amin Pocket GBA 白色控制中心的「PackageCanvas」卡片，開啟獨立 Activity 載入上述網址，不併入 AMIN WIKI、不共用 FOX KnowledgeProfile。
- 知識庫來源（web 層）：GitHub 私有 repo `ken12121122-dotcom/gen2-knowledge` 的 `kb/` 是正本（OWNER 2026-10-01 決定：GPT／Claude 的工作直接送 GitHub PR，不再寫 Drive）；畫布以唯讀 token 經 `api.github.com` 讀取 main 或待審 PR 分支，`?gen2ref=<分支>` 可直接開啟；GitHub 畫布讀取 gen2-run Issue（有進行中執行每 10 秒、否則每 60 秒），以步驟卡片發光、連線亮點與 Agent 狀態面板顯示流程執行狀態（唯讀）。`packagecanvas/world.html`（知識世界）以 GBA 風格遊戲畫面顯示同一份流程進度（唯讀；決定經 `AminGen2` 交給 App 確認後送出）。唯一的寫入是知識世界的「Agent 鑰匙櫃」：只能把 `ANTHROPIC_API_KEY`、`GOOGLE_SERVICE_ACCOUNT_JSON`、`GEN2_CALENDAR_ID` 加密（sealed box）後寫進 gen2-knowledge 的 Actions secrets，並可觸發 GEN2 Agent workflow。它只用 OWNER 當場貼的一次性 token，不儲存任何 token 或金鑰；不得擴大可寫的名稱，也不得改用畫布已存的唯讀 token。手機資料夾保留作離線鏡像。
- 原生能力只有唯讀 bridge `AminPackageCanvasFiles`（選資料夾、列出已授權資料夾、列出 `.md`、讀取文字、移除授權），只對 `ken12121122-dotcom.github.io/packagecanvas/` 開放；不得加入寫檔、刪檔或任意網域。Bridge 103 起另有 `AminGen2`（`isAvailable`、`openRun(issue, mode)`），只能請 App 開啟某一筆 GEN2 流程的原生核准頁或語音畫面；網頁拿不到 GitHub 權杖、不能送出任何 `/gen2` 指令，決定一律在原生畫面確認後送出。
- 這個 bridge 屬於 APK 變更，受本檔「不可違反的發布閘門」約束：新 Bridge 先停在 Draft PR 與 CI-only artifact，完成模擬器、實機閉環驗收與 OWNER 批准後才可更新正式 manifest。
- GEN2 解析（frontmatter、Wiki Link、BOM、Registry、Resource、架構檢查）全部在網頁層，改規則只改 `gen2-source.js`，不改 APK。
- 獨立 APK `android/packagecanvas/`（`tw.amin.packagecanvas`）在 GBA 入口通過實機驗收後停用；原始碼與 workflow 保留作回退，不再新增功能，也不得再建立第二套 Android 專案。
- 隱私：本 repo 是公開 repo。GEN2 知識庫內容（含 KB-USER 個人資料）不得 commit 到本 repo；測試只用合成 fixture。未來 GitHub 端的知識庫同步、核准紀錄與 Skill 執行必須放在 OWNER 另建的私有 repo。
- 狀態（2026-10-06）：`AminPackageCanvasFiles` 與控制中心 PackageCanvas 卡片自 Bridge 101 起包含在 App 裡，已隨 Bridge 103 正式發布。OWNER 2026-10-03 回報 PackageCanvas 正常。`AminGen2` 的「🎙 用說的」與「✋ 核准頁」都已於 2026-10-06 實機確認。

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
