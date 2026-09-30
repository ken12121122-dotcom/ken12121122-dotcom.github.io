# PackageCanvas GEN2 資料夾 · Web build 20260930.2

Web 層更新。Android 殼層與 build workflow 在這次提交中沒有改動。

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

- 知識庫內容只存在這台裝置瀏覽器的 IndexedDB，不會上傳，也不會 commit 到 GitHub。
- 本 repo 是公開 repo，所以測試只使用合成 fixture：`tests/packagecanvas-gen2.test.mjs`。
- 沒有任何寫回 MD 或 Drive 的功能。

## 驗證

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

還原這次提交即可：刪除 `gen2-source.js`，並把 `index.html` 與 `mobile.css` 還原為 `cdf2e17`。

- 既有畫布與 IndexedDB 名稱不變。
- 已由 GEN2 資料夾建立的畫布仍可開啟。
