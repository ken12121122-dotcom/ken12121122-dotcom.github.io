# FOX 知識庫（狐狸）｜新對話交接檔

更新日期：2026-09-18
Repository：`ken12121122-dotcom/ken12121122-dotcom.github.io`
主分支：`main`
目前階段：**架構變更 — FOX 已從獨立 App 改為 Amin Pocket GBA 的內部功能（`:foxknowledge` library 模組），P0（資料層＋Compose UI 骨架）本身內容不變，但尚未有任何一版「合併後的單一 APK」經過真機驗收。**

---

## 0. 2026-09-18 架構變更：FOX 不再是獨立 App

**這是一次會影響前面所有段落既有連結／檔案路徑的變更，請先讀這一節。**

FOX 原本依照 `KB-APP-001`／`KB-APP-002` 的決策，是一個完全獨立的 App（`fox-app/`，applicationId `com.fox.app`），跟 OWNER 每天在用的 Amin Pocket GBA（`android-native/`，applicationId `com.amin.pocketgba`）是兩個不同的 APK、兩個圖示。OWNER 在對話中確認這不是他要的：他要的是「FOX 知識庫是 Amin Pocket GBA 裡的新功能」，不是另一個 App，這個落差一直沒人跟他確認過。

處理方式：**把 `fox-app/app/` 整個搬進 `android-native/foxknowledge/`，改成 android-native 這個 Gradle 專案底下的 library 模組**（`com.android.library`，不再有自己的 `applicationId`），`:app`（純 Java，維持不變）只多一行 `implementation project(':foxknowledge')` 依賴，跟控制中心首頁一張新卡片。合併後只有一個 APK、一個圖示、一個 applicationId（`com.amin.pocketgba`）。

同時因為 OWNER 要求，這次一併補上了 FOX 原本就缺的「Sign in with Google」登入按鈕（P0 原本就漏做，不是因為合併才出現的缺口）——但**這個登入按鈕要真的能用，需要 OWNER 在 Google Cloud Console 用 `com.amin.pocketgba` 正式簽章的 SHA-1 申請 OAuth client ID、下載 `google-services.json`**，這一步 Claude Code 沒有帳號權限做，見第 7 節。

具體技術變更（供快速核對，細節見各章節）：

| 項目 | 合併前 | 合併後 |
|---|---|---|
| Gradle 專案 | `fox-app/`（獨立 root，`:app`） | `android-native/foxknowledge/`（`android-native` 底下的 library 模組） |
| applicationId | `com.fox.app` | 無（library），最終隨 `:app` 打包成 `com.amin.pocketgba` |
| Application 類別 | `FoxApplication`（`Application` 子類，手動 DI） | `FoxDependencies`（純 Kotlin 單例，不碰 `AminPocketApplication`） |
| 進入畫面 | `MainActivity`（LAUNCHER） | `FoxKnowledgeActivity`（非 LAUNCHER，由 `ControlCenterActivity` 的新卡片 `startActivity` 開啟） |
| 首頁入口 | 桌面另一個 App 圖示 | Amin Pocket GBA 控制中心「管理」區塊新增「📚 FOX 知識庫」卡片 |
| CI | `fox-app-ci.yml`（已刪除） | `foxknowledge-ci.yml`（驗證 `android-native/**`，不含 push-to-main 自動發布，比照 `AGENTS.md` 的 CI-only 規則） |
| Google 登入 | 無登入 UI，只讀已登入帳號 | `HomeScreen` 新增登入／登出按鈕，未登入時停用同步按鈕 |

---

## 1. 核心連結

### GitHub

- Repository
  https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io

- FOX 知識庫模組（library，非獨立 App）
  https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io/tree/main/android-native/foxknowledge

- 進入畫面 `FoxKnowledgeActivity.kt`
  https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io/blob/main/android-native/foxknowledge/src/main/java/com/fox/app/FoxKnowledgeActivity.kt

- 控制中心首頁入口（新卡片）`ControlCenterActivity.java`
  https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io/blob/main/android-native/app/src/main/java/com/amin/pocketgba/ControlCenterActivity.java

- CI Workflow
  https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io/blob/main/.github/workflows/foxknowledge-ci.yml

### Drive 知識庫（架構規格的唯一權威來源）

開工前**必讀**這三份，用標題搜尋讀取（不要寫死 file ID，文件曾被改版重建過，ID 會變）：

- `KB-APP-001-知識庫對App資料契約` — Drive 三分法（Node/Edge/Content）怎麼對應 App 資料層
- `KB-APP-002-App本機知識庫架構設計` — Room 三張核心表＋兩張新表、7 模組優先度表（P0～P2）、搜尋策略分階段。**注意：這份文件寫的仍是「FOX 是獨立 App」的舊架構，跟本檔第 0 節的「合併進 Amin Pocket GBA」現況不一致，尚未同步更新——資料層／Room schema 本身的決策仍然有效，只有「獨立 App vs. 合併功能」這一項已經被 OWNER 在對話中口頭推翻，需要另外找時間回寫 Drive KB 修正這份文件。**
- `FOX_SCHEMA` — 文件必要欄位、寫入規則、人工核准規則（治理底層規則，App 解析器要遵守）

---

## 2. 背景與核心架構決策

**Google Drive 知識庫是 Canonical Source；FOX 是它的 Knowledge Runtime，現在是 Amin Pocket GBA 裡的一個功能模組，不是獨立 App。** 這個模組只負責讀、解析、索引、搜尋、關聯、操作，不建立第二套知識庫。畫面不直接讀 Google Drive——本機 Room 資料庫才是平常查詢來源，讓離線也能搜尋。

資料流：

```text
Google Drive .md 文件
 ├── frontmatter ───────► Node
 ├── 動詞:: [[目標]] ────► Edge
 └── Markdown Sections ─► Content
 ↓
 Parser / Validator
 ↓
 本機 Knowledge DB（Room，:foxknowledge 模組內）
 ├── 搜尋
 ├── Graph
 └── Skill
 ↓
 UI（由 Amin Pocket GBA 控制中心進入）
```

寫入路徑明確晚於讀取：**P0 是 Drive → App 唯讀**。回寫知識庫是 P1／P2 以後的事，且仍受 `KB_COORDINATOR`／OWNER 治理流程約束，不是 Claude Code 能自己開的權限。

---

## 3. 已完成項目（P0 資料層，內容不變；路徑已隨合併更新）

### A. Room KnowledgeDB

五張表 + FTS4 搜尋索引：`nodes`／`edges`／`contents`／`sync_state`／`app_usage_log`／`node_search`（FTS4）。

```text
android-native/foxknowledge/src/main/java/com/fox/app/data/db/
  NodeEntity.kt / EdgeEntity.kt / ContentEntity.kt
  SyncStateEntity.kt / AppUsageLogEntity.kt / NodeSearchFtsEntity.kt
  NodeDao.kt (+EdgeDao/ContentDao/NodeSearchFtsDao/SyncStateDao/AppUsageLogDao)
  Converters.kt / FoxDatabase.kt
```

### B. KbParser

frontmatter → Node，`關係:: [[目標]]` → Edge，`## 段落` → Content。缺欄位列為 gap，不猜值。已用真實 KB-APP-001 內容寫單元測試。

```text
android-native/foxknowledge/src/main/java/com/fox/app/data/parser/KbParser.kt
android-native/foxknowledge/src/main/java/com/fox/app/data/parser/ParsedDocument.kt
android-native/foxknowledge/src/test/java/com/fox/app/data/parser/KbParserTest.kt
```

### C. KbValidator

檢查 FOX_SCHEMA 的列舉值／範圍，`errors` 擋入庫、`warnings` 不擋。

```text
android-native/foxknowledge/src/main/java/com/fox/app/data/validator/KbValidator.kt
```

### D. DriveAdapter（唯讀）＋ Google 登入（2026-09-18 新增）

Google Sign-In（`drive.readonly` 範圍）＋ Drive v3 REST（OkHttp 直接呼叫，沒用重量級 google-api-client）。

```text
android-native/foxknowledge/src/main/java/com/fox/app/data/drive/DriveAdapter.kt
android-native/foxknowledge/src/main/java/com/fox/app/data/drive/DriveAuthTokenProvider.kt
android-native/foxknowledge/src/main/java/com/fox/app/data/drive/GoogleDriveAdapter.kt
android-native/foxknowledge/src/main/java/com/fox/app/data/drive/FoxGoogleSignInClient.kt   ← 新增：2026-09-18 補的登入按鈕用的 GoogleSignInClient
```

`HomeScreen.kt` 現在有真正可以按的「使用 Google 帳號登入」／「登出」按鈕，未登入時「立即同步 Drive」會被停用並顯示提示文字。**但實際登入會失敗（`ApiException` code 10／DEVELOPER_ERROR），直到 OWNER 在 Google Cloud Console 幫 `com.amin.pocketgba` 申請 OAuth client 並放進 `google-services.json`** — 見第 7 節，這一步不是程式問題。

### E. SyncRepository ＋ WorkManager

Drive → Parser → Validator → Room，依 `sync_state` 的 `remote_modified_time` 比對，沒變動的檔案跳過。排程改為在使用者第一次打開 FOX 知識庫畫面（`FoxKnowledgeActivity.onCreate()`）才註冊，不會拖慢 Amin Pocket GBA 本身的啟動路徑。

```text
android-native/foxknowledge/src/main/java/com/fox/app/data/sync/SyncRepository.kt
android-native/foxknowledge/src/main/java/com/fox/app/data/sync/SyncWorker.kt
```

### F. Compose UI

Home（搜尋入口、Google 登入狀態、待處理事項、最近節點、統計、手動同步）／Search（FTS）／Node Detail（frontmatter＋內文段落）／Edge Navigation（點關係邊跳到對應節點）。

```text
android-native/foxknowledge/src/main/java/com/fox/app/ui/home/
android-native/foxknowledge/src/main/java/com/fox/app/ui/search/
android-native/foxknowledge/src/main/java/com/fox/app/ui/detail/
android-native/foxknowledge/src/main/java/com/fox/app/ui/FoxNavHost.kt
android-native/foxknowledge/src/main/java/com/fox/app/FoxKnowledgeActivity.kt   ← 原 MainActivity.kt，改名＋不再是 LAUNCHER
android-native/foxknowledge/src/main/java/com/fox/app/FoxDependencies.kt        ← 原 FoxApplication.kt，改成不依賴 Application 子類的單例
```

### G. Amin Pocket GBA 端的進入點（新增）

```text
android-native/app/src/main/java/com/amin/pocketgba/ControlCenterActivity.java
```

`buildUi()` 的「管理」區塊新增一張「📚 FOX 知識庫」卡片，`onClick` 是 `startActivity(new Intent(this, com.fox.app.FoxKnowledgeActivity.class))`。這是這次合併對 `android-native` 既有、受 `AGENTS.md` 嚴格閘門保護的程式碼**唯一**的改動——新增一張卡片，沒有改動任何既有卡片的行為。

### H. CI

```text
Android Gradle Plugin 8.7.3
Kotlin / Compose compiler 2.2.20
KSP 2.2.20-2.0.4（+ ksp.useKSP2=false，見第 7 節，現在寫在 android-native/gradle.properties）
Gradle 8.9
JDK 17
compileSdk 35 / targetSdk 35 / minSdk 26
```

`foxknowledge-ci.yml`（取代已刪除的 `fox-app-ci.yml`）在 push-to-main／PR 觸及 `android-native/**` 時跑 `:foxknowledge:testDebugUnitTest` + `:app:testDebugUnitTest` + `:app:assembleDebug`，只上傳 CI-only debug artifact，**沒有**自動發布 GitHub Release——比照 `AGENTS.md` 對 android-native 的規則，一般 CI 不能自動變成發布來源，這跟 `fox-app-ci.yml` 原本會自動發 `fox-app-latest-debug` Release 不一樣。

---

## 4. 已驗證與未驗證分界

| 項目 | 狀態 | 說明 |
|---|---|---|
| Room DB schema | 已完成 | 未經真實資料長期使用驗證 |
| KbParser | 已完成＋單元測試通過 | 只測過 KB-APP-001 一份真實文件 |
| KbValidator | 已完成 | 未接真實資料跑過完整驗證流程 |
| DriveAdapter | 已完成程式 | **未經實際 Google 帳號登入測試** |
| Google 登入按鈕（2026-09-18 新增） | 程式已寫 | **無法實際登入成功，OWNER 尚未在 Google Cloud Console 申請 OAuth client** |
| SyncRepository | 已完成程式 | **未經實機執行**，沒有真的跑過一次同步 |
| Compose UI | 已完成程式 | **沒有人看過實際畫面**，未經視覺／互動驗收 |
| **合併進 android-native 的建置**（:app 依賴 :foxknowledge） | 程式已寫，**尚未送過一次 CI** | 這是這次變更最大的未知數：Kotlin/Compose/KSP 第一次被加進 android-native 的建置，理論分析顯示版本相容（見 PR 說明），但只有真的跑過 GitHub Actions 才算數 |
| 控制中心新卡片 | 程式已寫 | 未經真機點擊驗證 |
| Amin Pocket GBA 既有功能（GBA、存檔、手把、更新、權限中心） | 理論上不受影響（只新增一張卡片，沒改任何既有程式） | **仍必須實機重新驗證零退化**，這是 `AGENTS.md` 的硬性要求，不能因為「理論上沒改到」就跳過 |
| 可安裝 APK（合併後單一版本） | 未產出 | 需要新的一輪 CI 才有 |
| P1 GraphEngine（關係圖、Chain Focus） | 未開始 | |
| P2 SkillRuntime | 未開始 | |
| 正式簽章 Release APK | 未完成 | 目前只有 debug signing |
| 回寫知識庫（App → Drive） | 未開始，且刻意延後 | P0 明確唯讀 |

---

## 5. 現在真正卡在哪裡

跟合併之前一樣：**從來沒有人真的打開過這個功能**，現在又多了一層——合併後的建置本身也還沒送過 CI。沒有 APK、沒有畫面截圖、沒有登入過 Google 帳號、沒有跑過一次真的同步，而且合併這件事本身（Kotlin/Compose 第一次進到 android-native 的建置系統）也還沒被驗證過能不能編譯過。

---

## 6. 下一步執行順序

### 第一優先：讓合併後的建置在 CI 跑過一次

Draft PR 推上去後，看 `foxknowledge-ci.yml` 有沒有綠燈（`:foxknowledge:testDebugUnitTest`、`:app:testDebugUnitTest`、`:app:assembleDebug`）。第一次大概率會踩到 fox-app P0 開發時同樣類型的坑（plugin 版本、KSP2 crash 之類），照 CI log 一輪一輪修，這是這個 sandbox 沒有 `dl.google.com` 網路權限、跑不了本地 Gradle 的既定限制。

### 第二優先：把合併後的單一 debug APK 交給 OWNER 試裝

CI 綠燈後，用跟這次一樣的方式（`SendUserFile`）把 `android-native/app/build/outputs/apk/debug/app-debug.apk` 交給 OWNER——這次只會有**一個** APK、一個圖示（`com.amin.pocketgba`），覆蓋安裝到 OWNER 手機上原本的 Amin Pocket GBA 應該就會直接更新，不需要先解除安裝。

### 第三優先：OWNER 申請 Google OAuth client（Claude Code 做不到的部分）

Google 登入按鈕程式已經寫好，但要真的能用，OWNER 需要：

1. 在 Google Cloud Console 建立／使用一個專案。
2. 用 `com.amin.pocketgba` 的正式簽章 SHA-1（`amin-vault/native-release-manifest.json` 提到的 signer SHA-256 是簽章雜湊，OAuth 這裡要的是同一份簽章憑證的 SHA-1，需要另外算）申請 Android OAuth client ID。
3. 下載對應的 `google-services.json`，放進 `android-native/app/`（或告訴 Claude Code 內容，由 Claude Code 放進去並接上 Gradle 的 `google-services` plugin）。

在這步完成前，登入按鈕會穩定重現 `ApiException` code 10（DEVELOPER_ERROR），這是**預期中**的失敗，不是 bug。

### 第四優先：實機驗收

1. 打開 App，確認控制中心跟以前一樣、新卡片有出現。
2. 點「📚 FOX 知識庫」，確認能打開、沒有 crash。
3. 按「使用 Google 帳號登入」（OAuth client 設定好之後）。
4. 按「立即同步 Drive」，確認同步結果。
5. 進 Search／Node Detail／Edge Navigation，確認畫面跟資料對得起來。
6. **逐項重新確認 Amin Pocket GBA 既有能力零退化**：GBA 遊戲、存檔、手把、原生更新檢查、權限中心——這是 `AGENTS.md` 的硬性要求。

### 第五優先：依實機回饋修 bug，開始 P1（GraphEngine）

跟合併之前的規劃一樣，不重複。

---

## 7. 重要風險與邊界

### A. 這個開發環境本身沒有 Google Maven 的網路權限

跟 fox-app 獨立開發時期一樣，這個 sandbox 的網路政策明確擋掉 `dl.google.com`，本地端完全沒辦法跑 `gradle assembleDebug`，只能靠 GitHub Actions CI 驗證，每次改動都得走「push → 開/更新 PR → 等 CI → 看 log 找錯誤 → 再修」的迴圈。這次合併把 Kotlin/Compose/KSP 第一次加進 android-native 的建置系統，理論分析（AGP/SDK 版本一致、android-native 原本沒有衝突的 Kotlin/Compose/Room 版本）顯示應該低風險，但沒有實際跑過 CI 前不能保證。

### B. Application 類別衝突已經在程式層面解掉，但沒有實跑驗證過

一個 process 只能有一個 `Application` 子類，`android-native` 已經有 `AminPocketApplication`。這次把 FOX 原本的 `FoxApplication`（`Application` 子類）整個改寫成 `FoxDependencies`（普通 Kotlin 單例，靠 `Context.applicationContext` 取得，不繼承 `Application`），`AminPocketApplication` 完全沒被動到。這個设计在程式碼審閱上看起来是對的，但因為本地端跑不了 Gradle，**還沒有實際編譯／執行過確認沒有遺漏的呼叫點**。

### C. Debug APK 沒有固定簽章

跟 `android-native` 原本就有的風險一樣：GitHub Actions 每次在乾淨 runner 上建置的 debug APK，簽章可能不一致，覆蓋安裝可能被拒絕，必須先解除安裝舊版才能裝新版——但因為合併後 applicationId 沒變（還是 `com.amin.pocketgba`），如果簽章配置沒變，理論上這次應該可以直接覆蓋安裝到 OWNER 現有的 Amin Pocket GBA 上面，這點也需要實機確認。

### D. P0 是唯讀，App 目前不能寫回任何東西

即使 UI 上以後加了「編輯」按鈕，寫回 Drive 這件事本身要先有 P1／P2 的正式設計＋ KB_COORDINATOR／OWNER 核准，Claude Code 不能自己決定開放這個能力。

### E. Drive 檔案沒有原地更新

所有改版都是 trash 舊檔＋建立新檔，file ID 會變。`DriveAdapter.listMarkdownFiles()` 是用 query 搜尋（`mimeType`／`fileExtension`），不是寫死 ID，這點沒問題；但任何人（包含未來的 Claude Code session）手動去 Drive 對照文件時，也要記得用標題搜尋，不要用舊的 file ID。

### F. Drive KB 的 `KB-APP-002` 還沒同步更新（新增於 2026-09-18）

Drive 知識庫裡的 `KB-APP-002-App本機知識庫架構設計` 仍然寫著「FOX 是獨立 App」的舊架構決策，跟本檔第 0 節的現況不一致。這份文件的 Room schema／資料層決策本身沒有錯、不用改，但「獨立 App vs. 合併進 Amin Pocket GBA」這一條架構決策需要另外找時間，依照 FOX 的治理流程（`decision_candidate` → OWNER 核准 → 回寫 Drive KB）補一份修正文件，不能只改這份 repo 內的交接檔就算數。

---

## 8. 關鍵檔案索引

```text
# FOX 知識庫模組（library，位於 android-native 底下）
android-native/foxknowledge/src/main/java/com/fox/app/data/db/
android-native/foxknowledge/src/main/java/com/fox/app/data/parser/
android-native/foxknowledge/src/main/java/com/fox/app/data/validator/
android-native/foxknowledge/src/main/java/com/fox/app/data/drive/
android-native/foxknowledge/src/main/java/com/fox/app/data/sync/
android-native/foxknowledge/src/main/java/com/fox/app/ui/
android-native/foxknowledge/src/main/java/com/fox/app/FoxKnowledgeActivity.kt
android-native/foxknowledge/src/main/java/com/fox/app/FoxDependencies.kt
android-native/foxknowledge/build.gradle

# 測試
android-native/foxknowledge/src/test/java/com/fox/app/data/parser/KbParserTest.kt

# Amin Pocket GBA 端的進入點
android-native/app/src/main/java/com/amin/pocketgba/ControlCenterActivity.java

# 建置設定（合併後統一在 android-native 底下）
android-native/build.gradle
android-native/settings.gradle
android-native/gradle.properties
android-native/app/build.gradle

# CI
.github/workflows/foxknowledge-ci.yml

# 交接
FOX_APP_HANDOFF.md（本檔）
AMIN_POCKET_GBA_HANDOFF.md（Amin Pocket GBA 本身的交接檔，也要同步這次的變更）
```

---

## 9. 新對話直接貼上的啟動指令

```text
請讀取 GitHub repository：
ken12121122-dotcom/ken12121122-dotcom.github.io

先完整讀取根目錄的 FOX_APP_HANDOFF.md，並以它作為唯一進度基準，不要重做已完成項目。
注意：FOX 已經不是獨立 App，是 android-native/foxknowledge 這個 library 模組，合併進 Amin Pocket GBA 了——不要照舊版交接檔或 Drive KB-APP-002 的「獨立 App」架構去理解現況。

開工前，用標題搜尋（不要寫死 file ID）讀取 Drive 知識庫的：
KB-APP-001-知識庫對App資料契約
KB-APP-002-App本機知識庫架構設計（注意：這份文件的「獨立 App」架構部分已過時，見 FOX_APP_HANDOFF.md 第 0、7-F 節）
FOX_SCHEMA
確認架構決策有沒有更新。

目前任務：[在這裡填入這次對話的具體任務]

規則：
1. 這個 sandbox 沒有 dl.google.com 的網路權限，本地端跑不了 Gradle build，只能靠 GitHub Actions CI 驗證，且每次改動都要走 push/PR → CI → 看 log 的迴圈。
2. P0 是 Drive → App 唯讀，不要自己加上寫回 Drive 的功能。
3. android-native 受 AGENTS.md 的嚴格發布閘門保護：功能分支不得推 main，CI-only artifact，實機驗收前不得宣稱完成。FOX 現在是 android-native 的一部分，同樣受這套規則約束。
4. 遇到規格缺口要誠實記錄，不要瞎猜；FOX_SCHEMA 的寫入規則同樣適用於 App 程式碼本身的假設。
5. 修改後要詳實回報：改了哪些檔案、commit SHA、CI 結果、未完成項目。
6. 不要聲稱驗證過實機行為，除非真的有人回報過實機結果。
```

---

## 10. 一句話交接

> **FOX 已從獨立 App（`fox-app/`，`com.fox.app`）改成 Amin Pocket GBA 內部的功能模組（`android-native/foxknowledge/`，作為 library 併入 `com.amin.pocketgba`），同時補上了原本缺的 Google 登入按鈕；但這次合併後的建置本身還沒送過一次 CI，也完全沒有人在真機上打開過合併後的版本——下一步是先讓 CI 綠燈，再把單一合併版 APK 交給 OWNER 真機驗收，並確認 Amin Pocket GBA 既有功能零退化。**
