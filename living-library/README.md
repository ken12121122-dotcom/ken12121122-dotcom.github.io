# Living Library · GEN2 main MD 工作台（候選）

review_status: generated。直接開發 B；未替換 Android 啟動首頁，未發布 APK。

## 使用

1. 開啟圖書館，按「連接 GEN2」。
2. 使用只授權 GEN2 repo 的 Contents: Read-only fine-grained token，或明確選擇沿用同網域 PackageCanvas 已保存的授權。預覽網域與 GitHub Pages 的本機授權不共用。
3. 頁面呼叫 PackageCanvas 的 GitHub collect，固定讀取 main/kb，並以 commit 固定同批文件。書本只來自 active Registry 登記及一致的非候選 Contract。
4. 書內 Skill／Workflow 取自實際受控文件；BOM／Workflow 跨書引用使用同一正式 ID。規則、Template、未登記、候選、generated review、退役和重複 ID 不建立正式卡。
5. 點卡即於可捲動書內頁閱讀來源文字、原始狀態、版本及 commit；可打開既有 GEN2 工作台。此入口不等於自動執行 Skill，OWNER Gate 保留在原系統。
6. 開頁授權後每 60 秒及重新切回頁面核對 main。OWNER 合併的新登記 KB／Skill 在下次讀取出現；未合併 PR 不出現。新增空 KB 允許空書。
7. 斷線清除本頁私有資料、書本及讀取快取。新貼權杖僅放記憶體；頁面不保存私有文件。只保存正式 ID 的書圈偏好。PackageCanvas 的既有保存設定由 PackageCanvas 管理。

## 正式狀態

GEN2 的 OWNER merge 是 release gate；既有 main 文件可能仍有 `status: draft`。本頁保留原值，區分「main 已收錄」與「執行已驗證」，不自行標記 approved。候選 Contract 即使在 main，也不因資料夾存在而成為正式 KB。

## 驗證與邊界

- 純投影重用 `packagecanvas/gen2-source.js` 的 frontmatter、分類、表格、Wiki links 及 gen2-run 解析。
- `node --test tests/*.test.mjs`：隔離 fixture 測試新增、空 KB、候選排除、Registry 不一致、ID 衝突、跨書引用；不提交正式私有資料。
- 原始文字以 textContent 顯示，避免 Markdown 的 HTML／script 注入。
- 不修改 main、GEN2 正式文件、Android Bridge、manifest、GBA 存檔；只允許獨立授權的候選分支／Draft PR 寫入，沒有 `/gen2 decide` 或 merge 能力。
- 尚需手機／WebView WebGL、授權 CORS、實際捲動及 iframe 相容性驗收。靜態預覽可能有不同 origin，請使用 session token。
- 回退：保留 PR 原提交；關閉候選頁即可回原控制中心。

## 2026-10-13 最新架構與 MD 工作台

- 正式 GEN2 知識、MD、Skill、Workflow 集中於私有 `gen2-knowledge` 的 `main/kb/`。每 KB 是一個資料夾；修改走暫時分支、PR、GEN2 Check、OWNER merge。舊分支不清理、不大量合併。
- GitHub 保存定義、版本與審核；ChatGPT／Work 讀取真實 MD，使用當前已授權工具執行。既有 Actions／Agent／Workflow 保留為可選執行途徑，不把每份 Skill 改成 Actions。
- Living Library 是統一搜尋、閱讀及候選編輯介面。書本、卡片及文件清單只在授權讀取時生成於記憶體，沒有另一份永久卡片資料庫、同步後端或新 AI 服務。
- 「搜尋 MD」跨全部正式書與規則做文字比對；「查閱本書 MD」包含 Contract、BOM、Resource、Skill、Workflow、Output。來源、SHA 與原始狀態可核對。沒有宣稱向量或語意搜尋。
- 已有受控 KB 文件可修改完整候選內容。先 stamp `draft / generated`、保留原 ID，再使用同一 PackageCanvas 架構檢查器比對新增 errors。規則／Registry／Resource 採用維持唯讀；此版本不建立新文件類型或新 KB。
- 「提交 Draft PR」使用另一個當次 Contents／Pull requests write token，不讀取唯讀 token。提交前核對 main commit 與原文；只建立 `candidate/living-library-*` 分支及 Draft PR，不 PATCH main、不合併、不刪分支。GEN2 Check 仍以私有 repo CI 為準。
- PR 提交失敗但候選分支已建立時，顯示補開 PR 的恢復網址，不自動重複建立分支。成功後本次編輯鎖定，後續 OWNER 退回修訂可交給 Work 更新同一 PR；本頁尚未實作既有 PR 編輯／恢復。
- 「複製給 AI 執行」只交接正式來源路徑、ID、版本與治理要求；接著在 Work 提供任務輸入。不在瀏覽器嵌入模型或自動啟動 Actions。

## 實際驗證記錄

- 私有 main SHA `eb63efd7b5d47d045368c72ef6d91bd98e16f3da`：讀取 49 個 MD，正式篩選後 32 個可查閱文件。三本正式書及七张真實能力卡維持。
- GitHub connector search：自然語句、工作紀錄、Google Docs、SK-TIME-001 均回傳空；故未知檔名以授權 recursive tree＋逐檔讀取補足。
- 本地跨正式 MD 文字檢索：「工作紀錄」7 文件／2 KB；「Google Docs」6 文件／2 KB＋規則；「OWNER Gate」12 文件／3 KB＋規則。不是語意搜尋測試。
- Work 直接完整讀取真實 `SK-TIME-001` 後，依其 Schema 整理真實公開開發 commit `48acf05c4739d5a2e7f5c8c037fb6d3f09b1d934` 為候選工作紀錄。日期 2026-10-13；起訖工時來源不足而留空；review_status generated；無 Calendar 寫入。未以 commit 時間冒充開始／結束時間。
- Library 同樣的描述式查詢會回傳候選，包括 2026-09-30 的 GEN2 投影 HTML，也有不相關結果。已讀回該快照。因語料与版本不同，此結果不支持「兩者相同」或一般搜尋效能排名；正式內容以目前 GitHub main 為準。
- 65 項 Node 測試通過，包含 UI 搜尋／候選編輯／提交模擬與隔離 GitHub API 流程。真實私有文件的候選檢查通過：baseline errors 0、warnings 7；沒有對私有 repo 建立測試 PR。
- 尚未驗證：瀏覽器／手機／WebView，實際讀寫 token 的 CORS／權限、私有 repo 端到端 PR 提交及該 PR 的 GEN2 Check、Library 與 GitHub 相同語料的語意召回率。無可用 control-browser 技能，因此不把 DOM 模擬列為真機通過。
