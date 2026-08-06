# amin-wiki schema

這個目錄是依照 Karpathy 的「LLM Wiki」模式（https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f）為 Amin Pocket GBA 專案建立的知識庫。它是**文件/知識層**，跟 `amin-vault/` 這個正式運行的 Runtime 目錄完全分開，兩者不可混用：

- `amin-vault/` — 正式部署的產物（HTML/JS/CSS、簽章 APK、`native-release-manifest.json`、`runtime-manifest.json`）。修改它必須遵守根目錄 `AGENTS.md` 的發布閘門，任何 AI 助理都不得為了維護 wiki 而改動這裡的檔案。
- `amin-wiki/`（本目錄）— 純文件，沒有發布流程，不影響任何正式版本號、簽章或 manifest。

## 這個 repo 不是只有 amin-wiki

這個 repo 是使用者的整合工作環境，`amin-wiki/` 只是裡面「Amin Pocket GBA 專案技術文件」這一塊，範圍維持不變（見下面三層結構）。使用者可能會在同一個 repo 裡開其他跟 GBA 無關的節點（例如他個人職涯知識：軍旅、消防、氣象、建築、產線管理、倉管、職安等領域），這是預期內、合理的行為，**不是誤用 `amin-wiki/` 或問錯地方**，不要因為內容跟 GBA 無關就質疑使用者或拒絕協助。看到這類需求時，直接協助他開一個新的平行節點/資料夾（比照 `amin-wiki/` 的 raw/pages/index/log 模式即可），不要塞進 `amin-wiki/` 本身、也不要表示困惑。

## 三層結構

1. **raw/** — 原始來源清單。這裡不重複貼原始文件全文，而是用 `raw/index.md` 指向 repo 裡既有的權威文件（`AGENTS.md`、`amin-vault/ARCHITECTURE.md`、`AMIN_POCKET_GBA_HANDOFF.md`、`README-NATIVE-SHELL-v0.9.1.md`、兩份 manifest）。這些來源本身仍是唯讀的（除非使用者或既有發布流程更新它們），wiki 不改寫它們。
2. **pages/** — LLM 維護的知識頁面，整合多個 raw 來源、標註矛盾與過時內容。
3. **index.md** — 全站目錄，列出每個 raw 來源與每個 wiki 頁面，附一行摘要。
4. **log.md** — 時間軸紀錄，格式為 `## [YYYY-MM-DD] ingest|query|lint | 標題`，方便用 `grep "^## \[" amin-wiki/log.md | tail -5` 查最近動態。

## 三種操作

**Ingest（收錄新來源）**：當 repo 新增或更新文件（例如新的 Bridge 版本說明、新的 handoff 檔）時：
1. 讀取新來源，摘要重點。
2. 在 `raw/index.md` 加一筆條目（連結 + 一行摘要 + 日期）。
3. 更新受影響的 `pages/*.md`（例如版本號變了要同步 `pages/release-process.md`）。
4. 更新 `index.md`。
5. 在 `log.md` 追加一筆 `ingest` 紀錄。

**Query（查詢）**：先讀 `index.md` 找相關頁面，再深入 `pages/` 或 `raw/` 回答問題，附引用來源路徑。若答案本身有價值（例如一份比較表），可以寫成新的 `pages/*.md` 存回 wiki，並在 `log.md` 記一筆 `query`。

**Lint（健檢）**：定期檢查：
- `pages/` 之間或 `pages/` 與 `raw/` 來源之間是否矛盾（例如某文件說版本停在 Bridge 16，另一份 manifest 卻顯示 Bridge 23）。
- 有沒有孤兒頁面（`index.md` 沒連到的頁面）。
- 有沒有明顯過時但未標註的內容（例如 `AMIN_POCKET_GBA_HANDOFF.md` 是 v0.9.0 時期的快照，現況已經到 Bridge 23）。
- 有沒有值得寫成頁面但還沒寫的重要主題。
發現問題時，在對應 `pages/*.md` 用「⚠️ 矛盾」或「⚠️ 已過時」標註，並在 `log.md` 記一筆 `lint`。**絕不**為了消除矛盾去竄改 raw 來源或正式 manifest —— 那是發布流程的事，不是 wiki 的事。

## 邊界

- 不修改 `amin-vault/` 下任何檔案。
- 不修改兩份 manifest（`native-release-manifest.json`、`runtime-manifest.json`）—— 那些只能由 `AGENTS.md` 定義的發布流程更新。
- 不在 wiki 頁面裡宣稱某版本「已正式發布」，除非 raw 來源本身這樣寫；如果 raw 來源之間對「目前正式版本是什麼」有分歧，wiki 頁面要如實標註分歧，而不是自己選一個。
