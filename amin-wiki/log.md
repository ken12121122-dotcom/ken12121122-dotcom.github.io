# Log

Append-only。格式：`## [YYYY-MM-DD] ingest|query|lint | 標題`。查最近幾筆：`grep "^## \[" amin-wiki/log.md | tail -5`。

## [2026-08-06] ingest | 初始建立 amin-wiki，套用 Karpathy LLM Wiki 模式

依 https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f 的模式，在不動 `amin-vault/`（正式 Runtime 目錄）的前提下，建立獨立的 `amin-wiki/` 知識庫。

收錄的 raw 來源：`AGENTS.md`、`amin-vault/ARCHITECTURE.md`、`amin-vault/architecture.json`、`AMIN_POCKET_GBA_HANDOFF.md`、`README-NATIVE-SHELL-v0.9.1.md`、`README.md`、`amin-vault/native-release-manifest.json`、`amin-vault/runtime-manifest.json`、git log（Bridge 2-23）。

寫出的頁面：`pages/architecture.md`、`pages/controller-input.md`、`pages/release-process.md`、`pages/known-issues.md`。

## [2026-08-06] lint | 發現正式版本號矛盾

`AGENTS.md`／`ARCHITECTURE.md`／`README.md`（均標註 2026-07-19）都說正式版本停在 Bridge 16，且 `AGENTS.md` 明確要求「未經使用者批准前必須維持 Bridge 16」；但 `native-release-manifest.json`（`publishedAt: 2026-07-22`）已經是 Bridge 23，git log 也有 Bridge 17-23 的發布提交。詳見 [`pages/release-process.md`](./pages/release-process.md) 的 ⚠️ 矛盾段落。未修改任何 raw 來源或 manifest —— 這屬於發布流程決策，留給使用者處理。

## [2026-08-06] lint | 過時內容標註

`AMIN_POCKET_GBA_HANDOFF.md`（v0.9.0，2026-07-12）的「已驗證/未驗證分界表」把按鈕映射、Start/Select/L/R 標成未驗證，但這些項目在後續 Bridge 16 文件中已標記為已驗證。已在 [`pages/controller-input.md`](./pages/controller-input.md) 加上 ⚠️ 已過時標註，避免未來誤讀成現況。
