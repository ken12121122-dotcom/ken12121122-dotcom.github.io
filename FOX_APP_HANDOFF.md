# FOX App（狐狸）｜新對話交接檔

更新日期：2026-09-14
Repository：`ken12121122-dotcom/ken12121122-dotcom.github.io`
主分支：`main`
目前階段：**P0（資料層＋前端 UI 骨架）已完成並經 GitHub Actions CI 驗證通過、已合併進 main；尚未有可安裝的 APK，也尚未經任何實機／視覺驗收。**

---

## 1. 核心連結

### GitHub

- Repository
  https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io

- FOX App 專案
  https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io/tree/main/fox-app

- 入口 `MainActivity.kt`
  https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io/blob/main/fox-app/app/src/main/java/com/fox/app/MainActivity.kt

- CI Workflow
  https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io/blob/main/.github/workflows/fox-app-ci.yml

- GitHub Actions 執行紀錄
  https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io/actions/workflows/fox-app-ci.yml

- P0 合併紀錄（PR #147，squash 進 main 的 commit）
  https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io/commit/0571d63

### Drive 知識庫（架構規格的唯一權威來源）

開工前**必讀**這三份，用標題搜尋讀取（不要寫死 file ID，文件曾被改版重建過，ID 會變）：

- `KB-APP-001-知識庫對App資料契約` — Drive 三分法（Node/Edge/Content）怎麼對應 App 資料層
- `KB-APP-002-App本機知識庫架構設計` — Room 三張核心表＋兩張新表、7 模組優先度表（P0～P2）、搜尋策略分階段
- `FOX_SCHEMA` — 文件必要欄位、寫入規則、人工核准規則（治理底層規則，App 解析器要遵守）

---

## 2. 背景與核心架構決策

**Google Drive 知識庫是 Canonical Source；FOX App 是它的 Knowledge Runtime。** App 只負責讀、解析、索引、搜尋、關聯、操作，不建立第二套知識庫。畫面不直接讀 Google Drive——本機 Room 資料庫才是平常查詢來源，讓離線也能搜尋。

資料流：

```text
Google Drive .md 文件
 ├── frontmatter ───────► Node
 ├── 動詞:: [[目標]] ────► Edge
 └── Markdown Sections ─► Content
 ↓
 Parser / Validator
 ↓
 本機 Knowledge DB（Room）
 ├── 搜尋
 ├── Graph
 └── Skill
 ↓
 UI
```

寫入路徑明確晚於讀取：**P0 是 Drive → App 唯讀**。App 要回寫知識庫是 P1／P2 以後的事，且仍受 `KB_COORDINATOR`／OWNER 治理流程約束，不是 Claude Code 能自己開的權限。

---

## 3. 已完成項目（P0）

### A. Room KnowledgeDB

五張表 + FTS4 搜尋索引：`nodes`／`edges`／`contents`／`sync_state`／`app_usage_log`／`node_search`（FTS4）。

```text
fox-app/app/src/main/java/com/fox/app/data/db/
  NodeEntity.kt / EdgeEntity.kt / ContentEntity.kt
  SyncStateEntity.kt / AppUsageLogEntity.kt / NodeSearchFtsEntity.kt
  NodeDao.kt (+EdgeDao/ContentDao/NodeSearchFtsDao/SyncStateDao/AppUsageLogDao)
  Converters.kt / FoxDatabase.kt
```

### B. KbParser

frontmatter → Node，`關係:: [[目標]]` → Edge，`## 段落` → Content。缺欄位列為 gap，不猜值。已用真實 KB-APP-001 內容寫單元測試。

```text
fox-app/app/src/main/java/com/fox/app/data/parser/KbParser.kt
fox-app/app/src/main/java/com/fox/app/data/parser/ParsedDocument.kt
fox-app/app/src/test/java/com/fox/app/data/parser/KbParserTest.kt
```

### C. KbValidator

檢查 FOX_SCHEMA 的列舉值／範圍，`errors` 擋入庫、`warnings` 不擋。

```text
fox-app/app/src/main/java/com/fox/app/data/validator/KbValidator.kt
```

### D. DriveAdapter（唯讀）

Google Sign-In（`drive.readonly` 範圍）＋ Drive v3 REST（OkHttp 直接呼叫，沒用重量級 google-api-client）。

```text
fox-app/app/src/main/java/com/fox/app/data/drive/DriveAdapter.kt
fox-app/app/src/main/java/com/fox/app/data/drive/DriveAuthTokenProvider.kt
fox-app/app/src/main/java/com/fox/app/data/drive/GoogleDriveAdapter.kt
```

### E. SyncRepository ＋ WorkManager

Drive → Parser → Validator → Room，依 `sync_state` 的 `remote_modified_time` 比對，沒變動的檔案跳過。

```text
fox-app/app/src/main/java/com/fox/app/data/sync/SyncRepository.kt
fox-app/app/src/main/java/com/fox/app/data/sync/SyncWorker.kt
```

### F. Compose UI

Home（搜尋入口、待處理事項、最近節點、統計、手動同步）／Search（FTS）／Node Detail（frontmatter＋內文段落）／Edge Navigation（點關係邊跳到對應節點）。

```text
fox-app/app/src/main/java/com/fox/app/ui/home/
fox-app/app/src/main/java/com/fox/app/ui/search/
fox-app/app/src/main/java/com/fox/app/ui/detail/
fox-app/app/src/main/java/com/fox/app/ui/FoxNavHost.kt
fox-app/app/src/main/java/com/fox/app/MainActivity.kt
```

### G. CI

```text
Android Gradle Plugin 8.7.3
Kotlin / Compose compiler 2.2.20
KSP 2.2.20-2.0.4（+ ksp.useKSP2=false，見第 7 節）
Gradle 8.9
JDK 17
compileSdk 35 / targetSdk 35 / minSdk 26
```

`fox-app-ci.yml` 在 push/PR 觸及 `fox-app/**` 時跑 `:app:testDebugUnitTest` + `:app:assembleDebug`，PR #147 已實測通過。

---

## 4. 已驗證與未驗證分界

| 項目 | 狀態 | 說明 |
|---|---|---|
| Room DB schema | 已完成 | 未經真實資料長期使用驗證 |
| KbParser | 已完成＋單元測試通過 | 只測過 KB-APP-001 一份真實文件 |
| KbValidator | 已完成 | 未接真實資料跑過完整驗證流程 |
| DriveAdapter | 已完成程式 | **未經實際 Google 帳號登入測試**，OAuth 流程只寫了程式，沒人真的登入過 |
| SyncRepository | 已完成程式 | **未經實機執行**，只有 CI 的 debug build 過，沒有真的跑過一次同步 |
| Compose UI | 已完成程式 | **沒有人看過實際畫面**，未經視覺／互動驗收 |
| Gradle build（assembleDebug／testDebugUnitTest） | 已在 GitHub Actions 驗證通過 | PR #147，4 輪修正後才過（見第 7 節） |
| 可安裝 APK | 未完成 | CI 目前沒有把建出的 APK 存成 artifact，需要補這一步 |
| P1 GraphEngine（關係圖、Chain Focus） | 未開始 | |
| P2 SkillRuntime | 未開始 | |
| 正式簽章 Release APK | 未完成 | 目前只有 debug signing |
| 回寫知識庫（App → Drive） | 未開始，且刻意延後 | P0 明確唯讀 |

---

## 5. 現在真正卡在哪裡

程式不是卡在「架構沒想清楚」——KB-APP-001／002 的架構決策都已經落實成程式碼，CI 也證明程式碼是對的、編得過、單元測試過得了。

現在卡在：**從來沒有人真的打開過這個 App。** 沒有 APK、沒有畫面截圖、沒有登入過 Google 帳號、沒有跑過一次真的同步。所有「已完成」都只到「程式碼正確」這一層，還沒有到「東西真的能用」這一層。

---

## 6. 下一步執行順序

### 第一優先：產出可安裝的 debug APK — 已完成，但安裝方式待確認

CI 已經會在每次 push 到 `main` 時建置 debug APK，並發布成 GitHub Release（tag `fox-app-latest-debug`，見 `fox-app-ci.yml` 最後一步）。第一版 APK（2026-09-14）是用這條路徑產出後，透過 `SendUserFile` 直接交給 OWNER 的。

**但 OWNER 事後說明：「我的apk採本地直接更新，不需要下載」**——也就是說 OWNER 安裝／更新 APK 的實際方式是本地直接更新，不是透過下載連結。這代表：

- 未來要交付新版 APK 時，**不要預設走「等 CI → 下載 Release asset → SendUserFile」這條路**，先跟 OWNER 確認這次要用哪種方式。
- 「本地直接更新」的具體機制（ADB push？USB 傳檔手動安裝？類似 Amin Pocket GBA 的 `runtime-updater.js`／`native-release-manifest.json` 那種機制？）尚未問清楚，見 Drive KB 的 `MEM-CLAUDECODE-005-FOX App APK安裝方式為本地直接更新` 候選文件。
- `fox-app-ci.yml` 的 GitHub Release 發布步驟目前**保留著**（沒有拿掉），因為不確定 OWNER 是否仍需要它作為備援；是否要移除待 OWNER／KB_COORDINATOR 決定。

### 第二優先：實機驗收

1. 打開 App，確認 Home 畫面渲染正常（沒有 crash、沒有排版跑掉）。
2. 按「立即同步 Drive」——此時會觸發 Google Sign-In，**第一次會需要 OWNER 用自己的 Google 帳號登入並授權 `drive.readonly`**。
3. 確認同步結果：掃描到幾份文件、有沒有失敗、失敗原因是什麼。
4. 進 Search／Node Detail／Edge Navigation，確認畫面跟資料對得起來。
5. 把發現的問題（crash log、畫面截圖、行為跟預期不符的地方）回報到新對話。

### 第三優先：依實機回饋修 bug

不要预先猜測會出什麼問題——等實機回饋，照真實錯誤修。

### 第四優先：開始 P1（GraphEngine）

關係圖 Navigation Engine：預設顯示分類彙總，點擊展開，支援沿關係鏈路逐層展開（呼應既有 Chain Focus 構想）。細節見 `KB-APP-002` 的「Graph 定位」章節。

### 第五優先（較後）：正式簽章 Release

比照 `android-native` 的模式：建立 keystore、存進 GitHub Actions Secrets、建立 GitHub Release，避免每次 debug build 簽章不一致導致無法覆蓋安裝。

---

## 7. 重要風險與邊界

### A. 這個開發環境本身沒有 Google Maven 的網路權限

Claude Code 在這個 repo 上工作用的 sandbox，其網路政策明確擋掉 `dl.google.com`（Android Gradle Plugin、所有 AndroidX／Compose／Room 套件的來源）。這代表：

- **本地端完全沒辦法跑 `gradle assembleDebug`**，唯一能驗證建置是否成功的地方是 GitHub Actions CI。
- 每次改動都得走「push → 開/更新 PR → 等 CI → 看 log 找錯誤 → 再修」這個迴圈，沒辦法本地快速反覆試。
- PR #147 的 4 輪修正全部是這樣一輪一輪抓出來的：Kotlin/Compose-compiler 版本不存在 → 漏了 `gradle.properties` → KSP2 崩潰（已用 `ksp.useKSP2=false` 繞過，不是根本解，未來升級 Room／KSP 版本時應該重新測試拿掉這個 flag 是否還需要）→ `SyncWorker.kt` import 了不存在的 `androidx.work.Result`（正確應為巢狀類別 `ListenableWorker.Result`）。

### B. Debug APK 沒有固定簽章

跟 `android-native` 完全一樣的風險：GitHub Actions 每次在乾淨 runner 上建置的 debug APK，簽章可能不一致，覆蓋安裝可能被拒絕，必須先解除安裝舊版才能裝新版。

### C. P0 是唯讀，App 目前不能寫回任何東西

即使 UI 上以後加了「編輯」按鈕，寫回 Drive 這件事本身要先有 P1／P2 的正式設計＋ KB_COORDINATOR／OWNER 核准，Claude Code 不能自己決定開放這個能力。

### D. Drive 檔案沒有原地更新

所有改版都是 trash 舊檔＋建立新檔，file ID 會變。App 的 `DriveAdapter.listMarkdownFiles()` 是用 query 搜尋（`mimeType`／`fileExtension`），不是寫死 ID，這點沒問題；但任何人（包含未來的 Claude Code session）手動去 Drive 對照文件時，也要記得用標題搜尋，不要用舊的 file ID。

---

## 8. 關鍵檔案索引

```text
# 資料層
fox-app/app/src/main/java/com/fox/app/data/db/
fox-app/app/src/main/java/com/fox/app/data/parser/
fox-app/app/src/main/java/com/fox/app/data/validator/
fox-app/app/src/main/java/com/fox/app/data/drive/
fox-app/app/src/main/java/com/fox/app/data/sync/

# UI
fox-app/app/src/main/java/com/fox/app/ui/
fox-app/app/src/main/java/com/fox/app/MainActivity.kt
fox-app/app/src/main/java/com/fox/app/FoxApplication.kt

# 測試
fox-app/app/src/test/java/com/fox/app/data/parser/KbParserTest.kt

# 建置設定
fox-app/build.gradle
fox-app/app/build.gradle
fox-app/gradle.properties
fox-app/settings.gradle

# CI
.github/workflows/fox-app-ci.yml

# 交接
FOX_APP_HANDOFF.md（本檔）
```

---

## 9. 新對話直接貼上的啟動指令

```text
請讀取 GitHub repository：
ken12121122-dotcom/ken12121122-dotcom.github.io

先完整讀取根目錄的 FOX_APP_HANDOFF.md，並以它作為唯一進度基準，不要重做已完成項目。

開工前，用標題搜尋（不要寫死 file ID）讀取 Drive 知識庫的：
KB-APP-001-知識庫對App資料契約
KB-APP-002-App本機知識庫架構設計
FOX_SCHEMA
確認架構決策有沒有更新。

目前任務：[在這裡填入這次對話的具體任務]

規則：
1. 這個 sandbox 沒有 dl.google.com 的網路權限，本地端跑不了 Gradle build，只能靠 GitHub Actions CI 驗證，且每次改動都要走 push/PR → CI → 看 log 的迴圈。
2. P0 是 Drive → App 唯讀，不要自己加上寫回 Drive 的功能。
3. 遇到規格缺口要誠實記錄，不要瞎猜；FOX_SCHEMA 的寫入規則同樣適用於 App 程式碼本身的假設。
4. 修改後要詳實回報：改了哪些檔案、commit SHA、CI 結果、未完成項目。
5. 不要聲稱驗證過實機行為，除非真的有人回報過實機結果。
```

---

## 10. 一句話交接

> **FOX App P0（Room 資料層＋Drive 唯讀同步＋Compose UI 骨架）已完成並經 GitHub Actions CI 真實驗證通過、合併進 main；但目前沒有可安裝的 APK，也完全沒有人實機打開過這個 App——下一步是把 CI 建出的 APK 存成 artifact 讓 OWNER 真正裝上手機試用。**
