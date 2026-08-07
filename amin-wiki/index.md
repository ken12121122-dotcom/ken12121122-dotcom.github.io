# amin-wiki index

Amin Pocket GBA 專案知識庫索引。結構與維護規則見 [`CLAUDE.md`](./CLAUDE.md)。

## Pages（LLM 維護的知識頁面）

| 頁面 | 摘要 |
|---|---|
| [`pages/architecture.md`](./pages/architecture.md) | 三個產品介面、Runtime/原生分工、Vault 四層資料哲學總覽。 |
| [`pages/controller-input.md`](./pages/controller-input.md) | 手把輸入架構現況、全域控制盤、以及 v0.9.0 交接檔已過時診斷的標註。 |
| [`pages/release-process.md`](./pages/release-process.md) | 發布閘門規則、Bridge 2→23 版本時間軸、**⚠️ 正式版本號在文件與 manifest 間的矛盾**。 |
| [`pages/known-issues.md`](./pages/known-issues.md) | 已知限制、建議下一步、**⚠️ Bridge 23 新功能與既有 milestone 對應關係待確認**。 |
| [`pages/reference-dify-node-architecture.md`](./pages/reference-dify-node-architecture.md) | 外部參考資料：Dify 的節點-連結 workflow 架構（graphon 圖引擎、NodeFactory、GraphEngine layers、React Flow 前端）。跟 Amin 專案目前無已確認關聯。 |

## Cross-domain mobile graph nodes（使用者明確要求）

> 目前 Android Wiki 關聯圖固定只讀 `amin-wiki/pages`。以下 Life ERP 頁面是為了讓使用者在手機熱更新關聯圖直接看到架構，依 2026-08-07 明確要求暫存於此；它們不屬於 Amin Pocket GBA 技術域。

| 頁面 | 摘要 |
|---|---|
| [`pages/life-erp-architecture.md`](./pages/life-erp-architecture.md) | Life ERP 中心節點：Data Contract → WF-001 → Claude → n8n → Knowledge Base / ERP / 外部服務。 |
| [`pages/life-erp-data-contract.md`](./pages/life-erp-data-contract.md) | Universal Contract、Node / Relation / Domain Registry 與人工／系統治理邊界。 |
| [`pages/life-erp-wf001.md`](./pages/life-erp-wf001.md) | WF-001 Contract Approval Pipeline：驗證、Change Set、Approval Gate 與 Authority 分工。 |
| [`pages/life-erp-n8n.md`](./pages/life-erp-n8n.md) | n8n 的定位：Workflow / Execution Orchestrator，不是知識庫或最終決策者。 |
| [`pages/life-erp-knowledge-base.md`](./pages/life-erp-knowledge-base.md) | Knowledge Base 的任務路徑、Contract-first 原則與下一階段 Storage 決策。 |

## Raw sources（原始來源索引，不重複內容）

見 [`raw/index.md`](./raw/index.md) —— 涵蓋 `AGENTS.md`、`amin-vault/ARCHITECTURE.md`、`amin-vault/architecture.json`、`AMIN_POCKET_GBA_HANDOFF.md`、`README-NATIVE-SHELL-v0.9.1.md`、`README.md`、兩份 release manifest、git log。

## Log

時間軸見 [`log.md`](./log.md)。

## 目前已知的兩個待辦（跨頁面）

1. 確認 Bridge 17-23 是否已完成 `AGENTS.md` 規則 7-8 的測試與實機驗證，然後決定更新 `AGENTS.md`/`ARCHITECTURE.md`/`README.md` 到 Bridge 23，還是回滾 manifest。
2. 確認 Bridge 23 的 Amin Control API v1 跟「共用 Action Core」等既有 milestone 的關係。

這兩個待辦都需要使用者提供更多來源（例如 Bridge 17-23 的 PR/驗收紀錄）才能解決，wiki 本身不會替使用者做決定。
