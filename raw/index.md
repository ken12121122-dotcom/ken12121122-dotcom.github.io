# Raw sources

原始來源清單。這些檔案的正本都留在原本的位置，這裡只做索引，不重複內容。權威性以原檔為準；如果這份索引跟原檔對不上，以原檔為準並回報矛盾。

| 來源 | 路徑 | 最後標註更新日期 | 一行摘要 |
|---|---|---|---|
| AI 交接主檔 | [`/AGENTS.md`](https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io/blob/claude/llm-tokenizer-impl-6iod8c/AGENTS.md) | 2026-07-19 | 發布閘門、完成定義、手把輸入路徑、關鍵檔案清單；任何 AI 改程式前必讀。 |
| 架構文件（人讀） | [`/amin-vault/ARCHITECTURE.md`](https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io/blob/claude/llm-tokenizer-impl-6iod8c/amin-vault/ARCHITECTURE.md) | 2026-07-19 | Vault 分層、Runtime/原生拆分、手把輸入架構、全域控制盤、GBA 模擬中心、已知限制與下一步。 |
| 架構文件（機讀） | [`/amin-vault/architecture.json`](https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io/blob/claude/llm-tokenizer-impl-6iod8c/amin-vault/architecture.json) | 對應 ARCHITECTURE.md | JSON 版架構描述，供 CI/工具讀取。 |
| v0.9.0 交接檔 | [`/AMIN_POCKET_GBA_HANDOFF.md`](https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io/blob/claude/llm-tokenizer-impl-6iod8c/AMIN_POCKET_GBA_HANDOFF.md) | 2026-07-12 | 手把訊號診斷起點、PWA→原生橋接的轉折、v0.9.0 當時的已驗證/未驗證分界表。**已過時**：現況已到 Bridge 23，本檔案許多「未驗證」項目後續文件顯示已完成，見 [`pages/known-issues.md`](../pages/known-issues.md)。 |
| 原生外殼 v0.9.1 預覽說明 | [`/README-NATIVE-SHELL-v0.9.1.md`](https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io/blob/claude/llm-tokenizer-impl-6iod8c/README-NATIVE-SHELL-v0.9.1.md) | 無標註日期，內容對應 v0.9.1 preview | 網路狀態橋接、cartridge streaming、離線復原頁；強調 preview 簽章非正式管道。 |
| 專案入口 README | [`/README.md`](https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io/blob/claude/llm-tokenizer-impl-6iod8c/README.md) | 對應 Bridge 16 | 專案一句話介紹、目前驗證版本號、關鍵文件連結。 |
| APK 發布 manifest | [`/amin-vault/native-release-manifest.json`](https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io/blob/claude/llm-tokenizer-impl-6iod8c/amin-vault/native-release-manifest.json) | `publishedAt: 2026-07-22` | 機器可讀的 APK 正式版本來源：`latestVersionName`、簽章 SHA-256、release notes。 |
| Runtime 發布 manifest | [`/amin-vault/runtime-manifest.json`](https://github.com/ken12121122-dotcom/ken12121122-dotcom.github.io/blob/claude/llm-tokenizer-impl-6iod8c/amin-vault/runtime-manifest.json) | `publishedAt: 2026-07-19` | 機器可讀的 Runtime 版本、entry point、資產清單、capability 清單。 |
| Git 提交歷史 | `git log` | 持續更新 | Bridge 2 → Bridge 23 的實際發布提交序列，是版本演進最原始的紀錄。 |
| （外部）Dify repo | [`github.com/langgenius/dify`](https://github.com/langgenius/dify) | 讀取當下的 default branch | LLM 應用開發平台。收錄目的是研究它的節點-連結（node-link）workflow 架構，見 [`pages/reference-dify-node-architecture.md`](../pages/reference-dify-node-architecture.md)。跟 Amin 專案目前**沒有**已確認的關聯，純參考資料。 |

## 已知：來源之間的日期落差

`AGENTS.md` 與 `ARCHITECTURE.md` 都標註「最後人工驗證：2026-07-19」且明確寫「正式版本是 Bridge 16」，但 `native-release-manifest.json` 的 `publishedAt` 是 `2026-07-22`、`latestVersionName` 已經是 `bridge23`，git log 也顯示 Bridge 17 到 Bridge 23 都已經有提交。這代表 `AGENTS.md`／`ARCHITECTURE.md` 沒有跟上最新幾次發布 —— 細節與影響見 [`pages/release-process.md`](../pages/release-process.md) 的「⚠️ 矛盾」段落。
