# amin-wiki schema

這個目錄是依照 Karpathy 的「LLM Wiki」模式（https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f）為 Amin Pocket GBA 專案建立的知識庫。它是**文件/知識層**，跟 `amin-vault/` 這個正式運行的 Runtime 目錄完全分開，兩者不可混用：

- `amin-vault/` — 正式部署的產物（HTML/JS/CSS、簽章 APK、`native-release-manifest.json`、`runtime-manifest.json`）。修改它必須遵守根目錄 `AGENTS.md` 的發布閘門，任何 AI 助理都不得為了維護 wiki 而改動這裡的檔案。
- `amin-wiki/`（本目錄）— 純文件，沒有發布流程，不影響任何正式版本號、簽章或 manifest。

> **2026-08-06 對照原始 gist 後的修正**：早期版本把這個目錄拆成 `raw/` 與 `pages/` 兩層子資料夾，但原文只講「Raw sources — a curated collection of source documents」和「the wiki — a directory of LLM-generated markdown files」，沒有要求額外的子目錄分層。已扁平化：現在所有 wiki 頁面和 `sources.md` 都直接放在 `amin-wiki/` 底下，不再有 `raw/`、`pages/` 子目錄。

## 結構

1. **`sources.md`** — 原始來源清單。這裡不重複貼原始文件全文，而是指向 repo 裡既有的權威文件（`AGENTS.md`、`amin-vault/ARCHITECTURE.md`、`AMIN_POCKET_GBA_HANDOFF.md`、`README-NATIVE-SHELL-v0.9.1.md`、兩份 manifest、`android-native/` 下的簽章與驗收文件等）。這些來源本身仍是唯讀的（除非使用者或既有發布流程更新它們），wiki 不改寫它們。
2. **其餘 `*.md` 頁面**（例如 `architecture.md`、`controller-input.md`、`release-process.md`、`known-issues.md`）— LLM 維護的知識頁面，整合多個來源、標註矛盾與過時內容。
3. **`index.md`** — 全站目錄，列出每個來源與每個 wiki 頁面，附一行摘要。
4. **`log.md`** — 時間軸紀錄，格式為 `## [YYYY-MM-DD] ingest|query|lint | 標題`，方便用 `grep "^## \[" amin-wiki/log.md | tail -5` 查最近動態。

## 三種操作

**Ingest（收錄新來源）**：當 repo 新增或更新文件（例如新的 Bridge 版本說明、新的 handoff 檔），或使用者直接提供新資料時：
1. 讀取新來源，摘要重點。
2. **跟使用者討論這份來源的重點**——這是原始 gist 明講的步驟（"reads the source, discusses key takeaways with you"），不是自己讀完就悶頭寫。摘要完先跟使用者對一輪，確認理解對不對、有沒有要特別標註的地方，再落筆。
3. 在 `sources.md` 加一筆條目（連結 + 一行摘要 + 日期）。
4. 更新受影響的 `*.md` 頁面（例如版本號變了要同步 `release-process.md`）。原始 gist 提到「一份來源可能牽動 10-15 個頁面」——這是在描述一個成熟、頁面彼此高度交叉引用的 wiki；目前 amin-wiki 頁面數還不多，通常一次只會動到 1-3 頁，這是正常的，不用為了湊數硬修改不相關頁面。
5. 更新 `index.md`。
6. 在 `log.md` 追加一筆 `ingest` 紀錄。

**Query（查詢）**：先讀 `index.md` 找相關頁面，再深入其他 `*.md` 頁面或 `sources.md` 回答問題，附引用來源路徑。若答案本身有價值（例如一份比較表），可以寫成新的頁面存回 wiki，並在 `log.md` 記一筆 `query`。

**Lint（健檢）**：定期檢查：
- 頁面之間、或頁面與 `sources.md` 來源之間是否矛盾（例如某文件說版本停在 Bridge 16，另一份 manifest 卻顯示 Bridge 23）。
- 有沒有孤兒頁面（`index.md` 沒連到的頁面）。
- 有沒有明顯過時但未標註的內容（例如 `AMIN_POCKET_GBA_HANDOFF.md` 是 v0.9.0 時期的快照，現況已經到 Bridge 23）。
- 有沒有值得寫成頁面但還沒寫的重要主題。
- **缺失的交叉參照**：某個概念在 A 頁面被提到，但 A 沒有連到已經存在、講這個概念更完整的 B 頁面。
- **資料缺口**：某個問題目前所有來源都答不出來，應該明確記下「這裡缺資料」，而不是留白讓人以為沒人查過。

發現問題時，在對應頁面用「⚠️ 矛盾」或「⚠️ 已過時」標註，並在 `log.md` 記一筆 `lint`。**絕不**為了消除矛盾去竄改 raw 來源或正式 manifest —— 那是發布流程的事，不是 wiki 的事。

## 如何正確請 AI 執行維護任務

常見誤解：直接說「先讀 `amin-wiki/CLAUDE.md`，接著維護這個知識庫」，聽起來像是 `amin-wiki/` 已經在目前要修改的分支上——但這個目錄不一定跟著每個工作分支走（例如它曾經只存在於 `claude/llm-tokenizer-impl-6iod8c`，不在當時的工作分支或 `main` 上），會讓 AI 花時間搜尋、甚至誤判成「這個 repo 沒有知識庫」。

建議的指令寫法，明講位置與動作：

> `amin-wiki/` 目前存在於 `<分支名稱>`。請先把該分支的 `amin-wiki/` 整個資料夾帶進目前工作分支，讀 `amin-wiki/CLAUDE.md` 了解維護規則（ingest／query／lint），再依規則維護這個知識庫。

若不確定 `amin-wiki/` 在哪個分支，先用 `git branch -a` 或對 repo 做全文搜尋（`amin-wiki`）確認位置，再回報給使用者，不要直接假設「找不到＝不存在」或自行在錯的分支上新建一份。

## 邊界

- 不修改 `amin-vault/` 下任何檔案。
- 不修改兩份 manifest（`native-release-manifest.json`、`runtime-manifest.json`）—— 那些只能由 `AGENTS.md` 定義的發布流程更新。
- 不在 wiki 頁面裡宣稱某版本「已正式發布」，除非 raw 來源本身這樣寫；如果 raw 來源之間對「目前正式版本是什麼」有分歧，wiki 頁面要如實標註分歧，而不是自己選一個。
