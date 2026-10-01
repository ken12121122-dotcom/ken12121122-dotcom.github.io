# PackageCanvas GEN2 知識庫來源 · Web build 20261001.1

Web 層更新。Android 殼層與 build workflow 沒有改動，不需要新 Bridge。

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

- `node --test tests/*.test.mjs`：21/21 通過，新增 3 項 GitHub 來源測試，使用模擬的 GitHub API：
  - 讀到的節點和檢查結果與資料夾來源相同
  - 只連線 api.github.com，`.obsidian` 等點開頭資料夾會略過
  - PR 分支沿用 main 的排版；沒變動的 blob 不重新下載
  - fork PR 不列出
  - token 錯誤、無權限、不安全的 owner/ref 都會被擋下
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
