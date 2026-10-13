# Living Library · GEN2 唯讀書庫（候選）

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
- 不修改 main、GEN2 正式文件、Android Bridge、manifest、GBA 存檔；無寫入或 `/gen2 decide` 能力。
- 尚需手機／WebView WebGL、授權 CORS、實際捲動及 iframe 相容性驗收。靜態預覽可能有不同 origin，請使用 session token。
- 回退：保留 PR 原提交；關閉候選頁即可回原控制中心。
