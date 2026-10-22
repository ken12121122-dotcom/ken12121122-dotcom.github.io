# 念動世界｜現行版本基準與大型分支整合準備（2026-10-21）

`STATUS=READ_ONLY_ANALYZED`　`INTEGRATION_MERGE=NOT_DONE`　`PUBLISH=NOT_DONE`

## OWNER 指令

- **僅處理《念動世界／念動圖書館》**：其他 Android/GBA/GEN2 專案不納入。
- 優先沿用原有 `work/nw-gamedev-p0-20261014`，**不另開 Git 分支**。
- 脫節成果列入 `docs/nw-museum/README.md` 歷史博物館。**當前分析、比較、修正依據不得讀博物館；只有 OWNER 明確要求歷史考古才例外。**
- **任何程式修改、合併、刪除、發布及超出原核准的影響範圍都要先由 OWNER 確認。** 此階段只有同步準備、衝突檢查、歷史館及分析文件，沒有核准實際 `merge/rebase/cherry-pick`。

## 唯一現行基準

**每次任務重新取得當下最新的 GitHub `main` HEAD 與真實檔案**，加上 `living-library/AGENTS.md` 中 P0 `NW-CORE-CONTRACT-001`、ChatGPT Library `00_CORE` 正式設計，以及現場實測證據。此文件只保存盤點時點快照，不取代現行程式。

盤點時：
- `main=e0364126ceb20b405d15c02a79159a91e11c2d49`。
- 工作分支原始歷史快照 `5a448b4f99100481bc676767df36a02b2a2a20e8`。
- `compare main...work`：`diverged`，`ahead_by=87`，`behind_by=182`，87 個差異檔案（25 evidence、2 docs、14 新 runtime、5 已修改 runtime、28 新測試、11 改測試、1 workflow、1 其他）。
- 原分支舊 `index.html` 1747 行 vs `main` 3207 行；舊 `object-craft.js` 119 行 vs `main` 829 行。舊分支缺主線 `scan-bundle.js`、`rune-unbind-spell.js`，`living-library/AGENTS.md` 亦不含最高層 P0。
- 原分支 `docs/work-nw-gamedev/progress.json` 狀態 `PAUSED_BY_OWNER` 是舊進度紀錄，不是繼續自動開發授權；PR #219 仍為未合併 Draft，範圍原本是法術草稿，不可逕自改成大型整合／自動合併。

## 同步風險與不允許覆寫的位置

| 現行模組 | 主要檔案 | 歷史同步風險 |
|---|---|---|
| 遊戲與治理最高規則 | `AGENTS.md`、`living-library/AGENTS.md` | 舊分支漏掉最新 P0、治理規範 |
| 卡片唯一身分、存檔、格式化 | `living-library/live/object-craft.js`、`card-creation.html` | 舊資料欄位／介面直接取代新卡片生命週期 |
| 場景與契約形態 | `index.html`、`scan-bundle.js`、`truth-eye-world.js` | 重新引入再次掃描、距離唯一定位、舊動畫控制 |
| 解除契約 | `rune-unbind-spell.js`、`contract-policy.js` | 解除條件與確認時機衝突；可能損及契約／卡片 |
| 卡片目錄與閱讀器 | `public-catalog.js`、`index.html` | 舊版讀卡頁取代目前入口 |
| 候選動作／法術模組 | `action-registry.js`、`spell-contract.js`、`spell-store.js` 等 | 尚未在現行遊戲驗證；不可因舊測試聲稱已上線 |
| CI、出站佇列 | `.github/workflows/living-library-ci.yml`、`object-outbox.js` | 舊 Gate 與本機權限／資料流差異 |

## 下一步審核 Gate

1. **先繼續只讀同步比較**：以最新 main 為真相，確認舊分支 87 差異中哪些純新增候選、哪些與目前 runtime 同名衝突。歷史檔本身不作現行程式依據。
2. 只保留「候選獨立模組／測試」的需求清單，**不將舊檔直接整批併回**。要採用某一模組，需事前確認檔案、函數、主線相容性、資料風險、測試與回退。
3. 只有 OWNER 核准指定檔案，才在這條既有工作分支修改。過程若新增受影響檔案或跨保護區，一律再問。
4. **不改 main、不啟動 PR auto-merge、不發布、不修改玩家存檔、不清理 Git branches。**
5. GitHub CODEOWNERS、Branch Rulesets 仍未設置，不可以把文件當成平台強制保護。

此文件新增於已核准沿用的大型工作分支，**僅作開發分流及同步準備紀錄**。
