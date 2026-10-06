# PackageCanvas GEN2 知識庫來源 · Web build 20261001.1

Web 層更新。Android 殼層與 build workflow 沒有改動，不需要新 Bridge。

## 20261007.2 · 知識世界：狐狸是自由行動的助理

- **你待在家**（任務告示板前），不用走路。
- **狐狸自己活動**：
  - 有 Agent 在工作時，牠走到那棟建築門口盯著（頭上「…」）。
  - 沒事時在附近閒逛。
  - 走路會繞過建築和樹（`world-model.js` 的 `findPath`、`foxGoal`，有測試）。
- **按鍵**：
  - **A**：呼叫狐狸，牠跑回你身邊後可以聊天。
  - **十字鍵**：看看四周。
  - **B**：鏡頭回到狐狸。
  - **L／R**：切換追蹤的任務，鏡頭移到那棟建築。
  - **START › 任務清單**：選一筆按 A 打開（Agent 工作中就看戰鬥，等你就看內容和處理方式）。

- **狐狸會來找你**：讀到有流程開始等你（新的一步），或流程剛完成、停止時，狐狸會放下手邊的事跑回你旁邊，頭上冒「！」，然後告訴你發生什麼事。
  - 每一筆、每一步只叫一次。
  - 打開遊戲時，已經在等你的流程也會各叫一次。
- **跟狐狸說話**：按 A 呼叫狐狸，或 START 選單選「🦊 跟狐狸聊天」。
- **在 Amin App（Bridge 104 起）裡**：
  - 「🦊 跟我聊這件／聊天、交代新任務」會用 `AminGen2.openChat(編號)` 打開 App 的狐狸聊天。
  - AI 判斷你的意思，聊清楚範圍後開始流程，或處理等你的那一筆。
  - 網頁只是請 App 打開畫面，拿不到權杖，也不送任何指令。
- **一般瀏覽器或 Bridge 103**：沒有聊天選項，只能看內容和處理方式。
- **規則與測試**：規則是 `world-model.js` 的 `callouts`，測試在 `tests/packagecanvas-world.test.mjs`。

## 20261006.3 · 在 App 裡開啟核准

在 Amin Pocket GBA（Bridge 103 起）裡開 PackageCanvas 時，Agent 狀態面板中「等你處理」的流程下方會多兩個按鈕：
- 「🎙 用說的」：打開狐狸語音核准，直接從這一筆開始聊。
- 「✋ 核准頁」：打開 App 的「GEN2 待核准」，並標出這一筆。

網頁只是請 App 打開畫面，拿不到 GitHub 權杖，也不會送出任何指令；決定一律在 App 裡確認後才送出。一般瀏覽器沒有這兩個按鈕。

## 20261007.1 · 知識世界：Agent 鑰匙櫃

在知識世界裡把 GEN2 Agent 需要的鑰匙放進 gen2-knowledge 的 GitHub Actions secrets，不用到 GitHub 網頁操作。
- 入口：START 選單「🔑 Agent 鑰匙櫃」，或走進沒有工作的「自動化工坊」。
- 只能寫這三個名稱：`ANTHROPIC_API_KEY`、`GOOGLE_SERVICE_ACCOUNT_JSON`、`GEN2_CALENDAR_ID`。其他名稱一律拒絕。
- 存入前先檢查格式：
  - Claude key 必須以 `sk-ant-` 開頭，並先向 Claude 確認可用（只列模型，不花 token）。被拒絕就不存。
  - 服務帳號 JSON 必須是 `service_account`，含 email 與私密金鑰。存好後，狐狸會顯示並複製服務帳號 email，提醒你把行事曆共用給它，權限選「變更活動」。
  - 行事曆 ID 必須是 email 格式。
- 加密：值在頁面裡用 repo 的公開金鑰封裝（libsodium sealed box，GitHub 規定的格式），只送到 `api.github.com`。網頁不儲存、不顯示、讀不回來；GitHub 也只回傳名稱和更新日期。
- 授權：只用在鑰匙櫃裡貼的一次性 fine-grained token，限 gen2-knowledge 一個 repo：
  - 存鑰匙要 Secrets：Read and write
  - 「讓 Agent 接手」要 Actions：Read and write
  - 關閉鑰匙櫃就清掉，不寫入 localStorage
  - 畫布已存的 token 維持唯讀，鑰匙櫃不會拿它來寫入
- 鑰匙都設定好，而且有任務在等 Agent 時，可以按「讓 Agent 接手 #N」。它會在 main 上執行 Actions › GEN2 Agent。
- 加密元件：`packagecanvas/gen2-secrets.js`（BLAKE2b 與 sealed box）與 `packagecanvas/vendor/nacl-fast.min.js`（tweetnacl 1.0.3，public domain）。測試在 `tests/packagecanvas-secrets.test.mjs`：
  - 測試向量取自 RFC 7693 與 libsodium。
  - 另外在本機確認過：libsodium 能解開這裡封裝的值。

## 20261006.1 · 知識世界（Knowledge World）遊戲畫面

`packagecanvas/world.html`：GBA 掌機風格的遊戲畫面，顯示 GEN2 流程（gen2-run Issue）的真實進度。PackageCanvas「GEN2 知識庫來源」對話框裡的「🎮 知識世界」可以進入。
- 你是玩家，狐狸是夥伴。用螢幕上的十字鍵走動，A 互動，B 返回，L／R 切換追蹤的任務，START 開選單。
- 每個流程步驟發生在一棟建築（依 Knowledge World「建築＝能力」）：
  - 提供資料：任務告示板
  - 審查閘門：驗證殿堂
  - 寫入或執行類的 Skill：自動化工坊
  - 研究或蒐集類的 Skill：研究所
  - 其他 Skill：知識鍛造所
- Agent 執行中的步驟顯示成建築裡的自動戰鬥（⚔ 和血條）。你可以進去看，按 B 離開也會繼續。步驟完成時，戰鬥結束並跳出提示。
- 等你處理的地方頭上有黃色「！」。進去會看到待審內容與處理方式：
  - 在 Amin Pocket GBA（Bridge 103 起）裡：「🎙 跟狐狸用說的處理」「✋ 打開核准頁」，由 App 確認後送出。
  - 在一般瀏覽器：只能複製 `/gen2` 指令或開 GitHub Issue。網頁本身不送出任何指令。
- 角色卡：每個知識庫一隻知識生物（WF-TIME → KB-TIME）。三條經驗（🧠 知識、💼 工作、✨ 技能）與發展階段（L0 種子起），只從驗證過的結果計算：
  - 通過 OWNER 審查的閘門：選項要明確寫核准、通過或採用。「調整後重做」這類重試不算
  - 完成且最後一步回報成功的流程
  - 停止的流程不算
  - 成長從全部已完成的流程計算（另外分頁讀取），不只最近幾筆，所以舊的成長不會消失
- 沿用 PackageCanvas 的 GitHub token（同一個網站），需要 Issues 讀取權限。沒有 token 時用示範資料。
- 有進行中的任務時每 10 秒更新，沒有時每 60 秒；畫面關掉就暫停（Knowledge World V0：線上才運轉）。
- 規則在 `packagecanvas/world-model.js`，測試在 `tests/packagecanvas-world.test.mjs`。

## 20261003.2 · gen2-source 0.4：畫布上的流程執行狀態（步驟卡片＋動畫）

讀 GitHub 正本的畫布會即時顯示 GEN2 流程跑到哪裡：
- 新增群組「執行狀態（GitHub）」。每個流程執行（gen2-run Issue）一條橫向泳道：
  - 開頭一張執行卡，標題範例：`🟠 #7｜WF-TIME-001｜等待你核准`，從所屬 Workflow 節點拉線過來。
  - 後面每個步驟一張卡片，依 Workflow 定義連線；閘門選項（例如「退回」）標在線上，退回線用虛線。
- 卡片狀態：
  - 目前步驟：卡片放大並發光。等你核准／提供資料是橘色，Agent 執行中是藍色。
  - 已完成：✅，卡片縮小。
  - 尚未到達：⬜，半透明小卡。
- 跑到下一步時，亮點會沿著連線移到下一張卡片，下一張卡片再展開發光。
- 右上角（手機在底部）有「Agent 狀態」面板，列出進行中的執行：誰在處理（🔵 Agent／🟠 你）、正在做什麼、已經等了幾分鐘。點一列就跳到那張卡片；沒有進行中的執行時面板自動隱藏。
- 有進行中執行的 Workflow，標題前面會加 `▶`。
- 進行中的執行全部顯示；已結束的只顯示最近 5 筆。
- 更新頻率：有進行中執行時每 10 秒，沒有時每 60 秒；切回畫面時立即更新。只在畫面可見、目前開的是 GitHub 畫布、而且沒有開啟對話框時才更新。
- 「GEN2 知識庫來源 › GitHub 正本」可以關掉「即時顯示流程執行」。關掉後執行卡片與面板會移除。
- 卡片位置可以拖動，之後更新或重新讀取都會保留位置。
- 系統設定「減少動態效果」時不播放發光與亮點動畫。
- token 需要多開 **Issues: Read-only**。沒開的話，設定區會顯示「⚠ 讀不到執行狀態」，不影響讀取 MD。
- 畫布只負責顯示，不會送出任何決定。核准請用 Amin Pocket GBA 的「GEN2 待核准」或 GitHub Issue 留言。

## gen2-source 0.3：可執行流程定義（gen2-run）

Workflow MD 可以加一個 ```` ```gen2-run ```` JSON 區塊，把步驟定義成狀態機：
- `input`：OWNER 提供資料
- `skill`：Agent 執行 Skill 並回報結果；可另設 `outcomes`，依回報結果走不同分支（例如「已寫入」與「發現衝突」）
- `gate`：OWNER 從選項中選一個，每個選項有自己的 `next`
- 結束有兩種：`end`（完成）與 `cancel`（停止，不算完成）

`parseRunSpec`／`validateRunSpec` 會檢查以下項目：
- id 格式
- 步驟類型
- Skill 是否存在於同一個 KB
- 選項設定
- `next` 是否指到存在的步驟
- 每個步驟是否從起點走得到
- 流程是否走得到 `end`

架構檢查新增：
- `run_spec_invalid`（錯誤）：定義寫錯
- `run_spec_no_gate`（警告）：沒有任何 OWNER gate
- `workflow_not_runnable`（提示）：還沒定義 gen2-run

執行引擎、格式說明與 GitHub Actions 在私有 repo `gen2-knowledge`。

## 20261001.1：GitHub 正本來源

GitHub 私有 repo `gen2-knowledge` 是 GEN2 的正式版本，Drive 和手機資料夾只是鏡像。這次讓畫布可以直接讀 GitHub，所以畫布上看到的就是流程實際使用的版本。

- 「GEN2 資料夾」視窗改名為「GEN2 知識庫來源」，上方新增「GitHub 正本」區塊；下方「手機／本機資料夾」的功能不變。
- **讀取 main**：讀取 `kb/` 下所有 `.md`，固定在當下的 commit，並在提示與來源列顯示 commit 前 7 碼。
- **待審 PR 清單**：列出 repo 內所有開啟中的 PR（fork 來的 PR 不列），按「讀取」就把該分支畫成獨立畫布，方便核准前先看架構與檢查結果。
  - PR 畫布第一次建立時，沿用 main 畫布已排好的位置。
- **深層連結**：`/packagecanvas/?gen2ref=<分支>` 開啟後自動讀取該分支。之後 APK 的核准通知會用它直接開到對應的 PR 畫布。
- 用的是同一個 `gen2-source.js` 解析器，所以 GitHub、手機資料夾、gen2-knowledge 的 PR 檢查三者結果一致。
- 讀取時用 blob SHA 做快取（只在記憶體內），切換分支時只下載有變動的檔案。

### Token

- 每台裝置設定一次 Fine-grained personal access token：
  - 只授權 `gen2-knowledge`
  - Contents: Read-only
  - Pull requests: Read-only
- 存在本頁 `localStorage` 的 `packagecanvas-gen2-github-v1`，只送往 `https://api.github.com`，可以按「清除 token」移除。
- 注意：`ken12121122-dotcom.github.io` 底下所有頁面共用同一個 origin，同站其他頁面理論上讀得到這個 token。所以權限一定要限縮成單一 repo、唯讀，並設定到期日。之後 APK 狀態機改由原生 GitHub 登入提供 token 時，就不需要存在網頁裡。


## 使用者可見行為

- 標題列新增「GEN2 資料夾」按鈕。原有的「GEN2 投影」與其他選單位置不變。
- 按下後可選擇 GEN2 知識庫資料夾（例如 `第二代知識庫`）。只讀取 `.md` 檔，`09_待刪除`、`.obsidian` 會略過。
- 自動產生一張畫布：
  - **Group**：`GEN2｜規則與入口`、每個 `KB-XXX｜名稱`、`候選｜尚未正式採用`、`_KB_TEMPLATE｜範本`、`架構檢查`
  - **Node**：每份 MD 一個節點，另外 `RESOURCE_REFS` 表格中的每個 `RES-…` 各一個節點
  - **Relation**：Wiki Link、Workflow → Skill（主要流程）、BOM 登記、所屬 KB、KB_REGISTRY 登記、Skill/Workflow 引用 Resource
- 三種呈現方式沿用既有引擎：
  - 完整 MD 畫布：看全部內容
  - MD 關聯圖：看 Wiki Link 網
  - Group 架構圖：看 KB 之間的關係
- 架構檢查結果會自動打開，並放在「架構檢查」節點裡。有問題的節點底部會加上「⚠ 架構檢查」段落。檢查項目：
  - 缺 Skill
  - 缺 Workflow 檔
  - 缺 Resource（引用了但 RESOURCE_REFS 沒登記）
  - 斷裂 Wiki Link
  - KB_REGISTRY 與資料夾不一致（未登記的 KB、登記了卻沒有資料夾）
  - KB 缺 Contract／Resource Refs／固定子資料夾
  - Workflow 沒有 OWNER Gate
  - Workflow／Skill 未登記於 BOM
  - 非受控 MD 類型
  - 未被使用的 Skill
  - 孤立節點
- 重新讀取同一個資料夾時：
  - 更新同一張畫布，不另外新增
  - 保留手動排好的位置與視角
  - 讀取前先自動存一份「GEN2 重新讀取前」版本紀錄

## 資料夾來源

| 環境 | 來源 | 記住授權 |
|---|---|---|
| Amin Pocket GBA（Bridge 101 起） | `window.AminPackageCanvasFiles`（Android SAF，唯讀） | 是，可在清單中讀取或移除授權 |
| 電腦版 Chrome／Edge | `showDirectoryPicker({mode:'read'})` | 否，每次重新選擇 |
| 其他支援 `webkitdirectory` 的瀏覽器 | 資料夾上傳至本頁（不離開裝置） | 否 |
| 舊 PackageCanvas 薄殼 APK | 不支援 | — |

### Native bridge 契約（`AminPackageCanvasFiles`）

所有方法皆為同步呼叫，回傳 JSON 字串。

- `listFolders()` → `{ok, folders:[{folderId, name, grantedAt}]}`
- `pickFolder()` → `true/false`。使用者選完後，原生端呼叫 `window.__pcGen2FolderPicked(json)`，參數為 `{ok, folderId, name}`，或 `{ok:false, cancelled|error}`。
- `listMarkdown(folderId)` → `{ok, rootName, files:[{path, size, modified}], dirs:[path], truncated}`。`path` 是相對於所選資料夾的路徑，用 `/` 分隔。
- `readText(folderId, path)` → `{ok, text}`，每個檔案上限 2 MB。
- `forgetFolder(folderId)` → `true/false`

## 資料與邊界

- 知識庫內容只存在這台裝置瀏覽器的 IndexedDB，不會上傳，也不會 commit 到本公開 repo。GitHub 正本來源只讀取私有 repo，不寫入。
- 本 repo 是公開 repo，所以測試只使用合成 fixture：`tests/packagecanvas-gen2.test.mjs`。
- 沒有任何寫回 MD 或 Drive 的功能。

## 驗證（20261001.1）

- `node --test tests/*.test.mjs`：24/24 通過，新增 6 項測試，使用模擬的 GitHub API：
  - 讀到的節點和檢查結果與資料夾來源相同
  - 只連線 api.github.com，`.obsidian` 等點開頭資料夾會略過
  - PR 分支沿用 main 的排版；沒變動的 blob 不重新下載
  - fork PR 不列出
  - token 錯誤、無權限、不安全的 owner/ref 都會被擋下
  - 遞迴樹被 GitHub 截斷時改成逐層讀取，結果與完整讀取相同
  - PR 清單會讀完所有分頁
  - 重新讀取或 PR 新增了節點／Group 時，舊的 MD 關聯圖／Group 架構圖排版作廢重排（修正前切換圖面會出現 `Cannot read properties of undefined (reading 'x')`）
- Chromium 1366×860 與 390×844，api.github.com 以 Playwright 攔截模擬：
  - 沒有 token 時打開設定
  - 儲存後列出 PR #7
  - 讀取 main（13 MD）
  - 讀取 PR 分支（14 MD；錯誤數 6 → 4）
  - `?gen2ref=` 深層連結
  - 清除 token
  - 沒有 page error
- 手機資料夾的端到端測試重跑結果和 main 相同：畫布數 1、重新讀取前版本紀錄 1。
- 尚未完成：用真實 token 讀真實 `gen2-knowledge`（需要 OWNER 建立 token）、Android WebView 實機。

## 驗證（20260930.2）

- `node --test tests/*.test.mjs`：18/18 通過，其中 6 項是新的 GEN2 解析測試。
- Chromium 在 390×844、1280×900、360×740 三種尺寸，搭配模擬的 `AminPackageCanvasFiles` bridge 做端到端測試：
  - 選資料夾 → 讀取 14 份 MD → 產生 15 個節點、6 個 Group → 自動顯示檢查報告
  - 重新讀取：畫布數維持 1，並新增 1 份「GEN2 重新讀取前」版本紀錄
  - 沒有 page error，也沒有水平捲軸
- 尚未完成：
  - 實機 Android WebView
  - Amin Pocket GBA 真實 bridge
  - 真實 GEN2 資料夾的驗收

## 回退

20261001.1：還原這次提交即可，`index.html`、`mobile.css`、`gen2-source.js` 回到 20260930.2。裝置上的 token 可以在視窗中清除，或忽略不用。

20260930.2：

還原這次提交即可：刪除 `gen2-source.js`，並把 `index.html` 與 `mobile.css` 還原為 `cdf2e17`。

- 既有畫布與 IndexedDB 名稱不變。
- 已由 GEN2 資料夾建立的畫布仍可開啟。
