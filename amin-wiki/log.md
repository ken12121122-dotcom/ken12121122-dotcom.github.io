# Log

Append-only。格式：`## [YYYY-MM-DD] ingest|query|lint | 標題`。查最近幾筆：`grep "^## \[" amin-wiki/log.md | tail -5`。

## [2026-08-06] ingest | 初始建立 amin-wiki，套用 Karpathy LLM Wiki 模式

依 https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f 的模式，在不動 `amin-vault/`（正式 Runtime 目錄）的前提下，建立獨立的 `amin-wiki/` 知識庫。

收錄的 raw 來源：`AGENTS.md`、`amin-vault/ARCHITECTURE.md`、`amin-vault/architecture.json`、`AMIN_POCKET_GBA_HANDOFF.md`、`README-NATIVE-SHELL-v0.9.1.md`、`README.md`、`amin-vault/native-release-manifest.json`、`amin-vault/runtime-manifest.json`、git log（Bridge 2-23）。

寫出的頁面：`pages/architecture.md`、`pages/controller-input.md`、`pages/release-process.md`、`pages/known-issues.md`。

## [2026-08-06] lint | 發現正式版本號矛盾

`AGENTS.md`／`ARCHITECTURE.md`／`README.md`（均標註 2026-07-19）都說正式版本停在 Bridge 16，且 `AGENTS.md` 明確要求「未經使用者批准前必須維持 Bridge 16」；但 `native-release-manifest.json`（`publishedAt: 2026-07-22`）已經是 Bridge 23，git log 也有 Bridge 17-23 的發布提交。詳見 [`pages/release-process.md`](./pages/release-process.md) 的 ⚠️ 矛盾段落。未修改任何 raw 來源或 manifest —— 這屬於發布流程決策，留給使用者處理。

## [2026-08-06] ingest | Dify workflow 節點-連結架構（外部參考）

使用者要求研究 `github.com/langgenius/dify` 的節點連結架構並收錄進知識庫。實際 clone repo 讀取原始碼（`node_factory.py`、`workflow_entry.py`、trigger constants、前端 `workflow/` 元件），寫成 [`pages/reference-dify-node-architecture.md`](./pages/reference-dify-node-architecture.md)：重點包括視覺執行圖（React Flow）與資料流圖（VariablePool + selector）分離、圖引擎已抽成外部套件 `graphon`、NodeFactory 依賴注入模式、GraphEngine + 可插拔 Layer 執行模型。已在 `raw/index.md` 與 `index.md` 加註：這是獨立參考資料，跟 Amin 專案**沒有**已確認關聯，不預設會用在 Amin Control API 上。

## [2026-08-06] lint | 過時內容標註

`AMIN_POCKET_GBA_HANDOFF.md`（v0.9.0，2026-07-12）的「已驗證/未驗證分界表」把按鈕映射、Start/Select/L/R 標成未驗證，但這些項目在後續 Bridge 16 文件中已標記為已驗證。已在 [`pages/controller-input.md`](./pages/controller-input.md) 加上 ⚠️ 已過時標註，避免未來誤讀成現況。

## [2026-08-06] ingest | 收錄 `android-native/` 簽章與實機驗收文件

例行維護巡查時發現 `android-native/` 下四份先前未收錄的文件：`RELEASE_SIGNING.md`（正式簽章契約）、`PERMANENT_SIGNING_IDENTITY.md`（簽章身分紀錄）、`UPDATE_BRIDGE_SIGNING.md`（Bridge 1 簽章操作說明）、`RC092_DEVICE_ACCEPTANCE.md`（v0.9.2 RC 實機驗收清單），以及一份已過時的 `android-native/README.md`（v0.9.0 時期）。五份都在初始提交 `1a9c84d`（2026-07-19）之後未再更新，內容對應 Bridge 1/rc2-rc6 時期。已在 [`raw/index.md`](./raw/index.md) 加入索引，並在 [`pages/release-process.md`](./pages/release-process.md) 新增「簽章與正式發布驗收」段落，說明 `RC092_DEVICE_ACCEPTANCE.md` 的驗收粒度與「全部通過前 `enabled` 必須是 `false`」的原始要求。

## [2026-08-06] lint | 新發現：簽章指紋矛盾

比對上述新收錄文件時發現 `android-native/PERMANENT_SIGNING_IDENTITY.md` 記錄的簽章 SHA-256（`aff1bab8...`）跟其餘所有來源（`AGENTS.md`、`ARCHITECTURE.md`、`architecture.json`、`native-release-manifest.json`、四個 CI workflow 檔）記的 `3b9a3125...` 不一致——後者是唯一被 CI 實際拿去 `grep` 驗證簽章的值。已在 [`pages/release-process.md`](./pages/release-process.md) 新增 ⚠️ 矛盾段落並列出完整來源對照表，同時更新 [`index.md`](./index.md) 的待辦清單。未修改任何 raw 來源、manifest 或簽章相關檔案——這是需要使用者確認「文件錯誤」還是「未記錄的金鑰更替」的問題，不是 wiki 能自行判定的事。

## [2026-08-06] ingest | 使用者個人背景（明確跳脫專案範圍）

使用者在對話中口述自我介紹，並在確認過這跟 Amin Pocket GBA 專案無關後，仍要求收錄進 `amin-wiki`。內容：嘉義人、志願役空軍 5 年（3 年消防兵、2 年氣象室）、退伍後歷任建築師助理／生產線儲備幹部／倉庫管理員、現職職業安全衛生主管。寫成獨立頁面 [`pages/user-profile.md`](./pages/user-profile.md)，開頭即標註範圍警告，不連結進任何 Amin 專案頁面（`architecture.md`／`controller-input.md`／`release-process.md`／`known-issues.md`），避免污染專案知識查詢。已在 [`raw/index.md`](./raw/index.md) 與 [`index.md`](./index.md) 加註來源與範圍警告。

## [2026-08-06] ingest | CLAUDE.md 補上「如何正確請 AI 執行維護任務」

本次維護一開始就因為指令沒有指名 `amin-wiki/` 所在分支而繞了一圈（`amin-wiki/` 起初只存在於 `claude/llm-tokenizer-impl-6iod8c`，不在當時的工作分支）。使用者請求把這個教訓寫回 `CLAUDE.md`，加了「如何正確請 AI 執行維護任務」段落：說明常見誤解、建議的指令寫法（明講分支＋要求先把資料夾帶進工作分支）、以及找不到時該怎麼查而不是誤判成不存在。屬於 schema 文件本身的 meta 維護，不影響任何 raw 來源或 manifest。
