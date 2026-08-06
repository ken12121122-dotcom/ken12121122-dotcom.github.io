# amin-wiki index

Amin Pocket GBA 專案知識庫索引。結構與維護規則見 [`CLAUDE.md`](./CLAUDE.md)。

## Pages（LLM 維護的知識頁面）

| 頁面 | 摘要 |
|---|---|
| [`pages/architecture.md`](./pages/architecture.md) | 三個產品介面、Runtime/原生分工、Vault 四層資料哲學總覽。 |
| [`pages/controller-input.md`](./pages/controller-input.md) | 手把輸入架構現況、全域控制盤、以及 v0.9.0 交接檔已過時診斷的標註。 |
| [`pages/release-process.md`](./pages/release-process.md) | 發布閘門規則、簽章契約與實機驗收清單、Bridge 2→23 版本時間軸、**⚠️ 正式版本號矛盾**、**⚠️ 簽章指紋矛盾**。 |
| [`pages/known-issues.md`](./pages/known-issues.md) | 已知限制、建議下一步、**⚠️ Bridge 23 新功能與既有 milestone 對應關係待確認**。 |
| [`pages/reference-dify-node-architecture.md`](./pages/reference-dify-node-architecture.md) | 外部參考資料：Dify 的節點-連結 workflow 架構（graphon 圖引擎、NodeFactory、GraphEngine layers、React Flow 前端）。跟 Amin 專案目前無已確認關聯。 |
| [`pages/user-profile.md`](./pages/user-profile.md) | ⚠️ 跳脫專案範圍：使用者個人背景（軍旅、消防兵、氣象室、建築師助理、產線儲備幹部、倉管、現職職安主管）。跟 Amin Pocket GBA 專案無關，應使用者要求獨立收錄。 |

## Raw sources（原始來源索引，不重複內容）

見 [`raw/index.md`](./raw/index.md) —— 涵蓋 `AGENTS.md`、`amin-vault/ARCHITECTURE.md`、`amin-vault/architecture.json`、`AMIN_POCKET_GBA_HANDOFF.md`、`README-NATIVE-SHELL-v0.9.1.md`、`README.md`、兩份 release manifest、`android-native/` 下四份簽章與驗收文件、git log。

## Log

時間軸見 [`log.md`](./log.md)。

## 目前已知的三個待辦（跨頁面）

1. 確認 Bridge 17-23 是否已完成 `AGENTS.md` 規則 7-8 的測試與實機驗證，然後決定更新 `AGENTS.md`/`ARCHITECTURE.md`/`README.md` 到 Bridge 23，還是回滾 manifest。
2. 確認 Bridge 23 的 Amin Control API v1 跟「共用 Action Core」等既有 milestone 的關係。
3. 確認 `android-native/PERMANENT_SIGNING_IDENTITY.md` 記錄的簽章指紋（`aff1bab8...`）跟其餘所有來源（`AGENTS.md`、兩份 manifest、CI workflow，均為 `3b9a3125...`）不一致，是文件錯誤還是 Bridge 1→2 之間有未記錄的金鑰更替。見 [`pages/release-process.md`](./pages/release-process.md)。

這三個待辦都需要使用者提供更多來源（例如 Bridge 17-23 的 PR/驗收紀錄、簽章金鑰的實際歷史）才能解決，wiki 本身不會替使用者做決定。
