# Living Library · GEN2 main MD 工作台（候選）

review_status: generated。直接開發 B；未替換 Android 啟動首頁，未發布 APK。

## 使用

1. 開啟圖書館，按「連接 GEN2」。
2. 使用只授權 GEN2 repo 的 Contents: Read-only fine-grained token，或明確選擇沿用同網域 PackageCanvas 已保存的授權。預覽網域與 GitHub Pages 的本機授權不共用。
3. 頁面呼叫 PackageCanvas 的 GitHub collect，固定讀取 main/kb，並以 commit 固定同批文件。書本只來自 active Registry 登記及一致的非候選 Contract。
4. 書內 Skill／Workflow 取自實際受控文件；BOM／Workflow 跨書引用使用同一正式 ID。規則、Template、未登記、候選、generated review、退役和重複 ID 不建立正式卡。
5. 點卡即於可捲動書內頁閱讀來源文字、原始狀態、版本及 commit；可打開既有 GEN2 工作台。此入口不等於自動執行 Skill，OWNER Gate 保留在原系統。
6. 開頁授權後每 60 秒及重新切回頁面核對 main。OWNER 合併的新登記 KB／Skill 在下次讀取出現；未合併 PR 不出現。新增空 KB 允許空書。
7. 斷線清除本頁私有資料、GEN2 書本投影及讀取快取，公開功能書持續可用。新貼權杖僅放記憶體；頁面不保存私有文件。只保存書本 ID 的書圈偏好。PackageCanvas 的既有保存設定由 PackageCanvas 管理。

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
- Living Library 是統一搜尋、閱讀及候選編輯介面。GEN2 書本、私有卡片及文件清單只在授權讀取時生成於記憶體，沒有另一份永久卡片資料庫、同步後端或新 AI 服務。
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


## 2026-10-13 公開入口功能回歸修復

- 比對基準：原始功能版 commit `18969f275eb2fb6ecfa4c7fa2ab5b1400a7f34d3` 的 12 本書、7 張共享功能入口、每書引用及預設 7 本環繞。
- 新增 `public-catalog.js` 管理公開導航，不宣稱它們是 GEN2 正式 KB／Skill；初始無授權仍顯示全部 12 本公開書與可用入口。
- 恢復原始 research、canvas、startup、calendar、world、gba、vault 的 URL 及跨書引用；研究導航、PackageCanvas、公司設立任務、知識世界、GBA、Vault 留在書內 iframe。Calendar 提供書內說明與可點擊外開連結。
- 創業之書未授權有 startup、calendar、canvas 及創業技術研究卡。授權且正式 KB-STARTUP 通過投影後，在同一本書增加真實 Skill／Workflow，Book ID 使用正式 KB-STARTUP；公開分類別名只用來保留導航與偏好，不修改正式 kb_id。
- GEN2 私有卡只在授權後出現；斷線／授權失敗移除私有卡與文件，恢復公開书本。反覆讀取不重複加入公開卡。新增正式 KB 仍由共用投影器產生。
- 測試改為保護「原始入口不遺失」及「公開導航／正式能力清楚標示」，不再以移除原始書籍作為通過條件。
- 72 項 Node 回歸通過；含全部原始書與入口、離線創業研究卡、授權前後公司設立＋正式 Workflow 共存、斷線清理、UI 外開 Calendar、書內閱讀／返回／關閉。七個公開網站頁面在 main 來源均存在。公開卡使用 public-capabilities.json 的 canonical IDs，舊卡 ID 僅作 alias；斷線也清除私有 ID 的本機偏好，僅保存公開書 ID。
- 手機／瀏覽器／WebView 實際載入、捲動及權杖 CORS 仍未驗證；DOM 與 API 模擬不代表真機通過。


## GPT 之書｜ChatGPT Library ↔ GEN2 的公開導航卡（2026-10-13）

- 在原本 12 本公開功能書之外增加 **《GPT 之書》**，公開來源 ID 為 `gpt`，預設放在書架；保留所有既有書本、功能入口及 GEN2 的正式 ID。
- 書中 `public.gpt.bridge`（✧ 08｜知識庫連結導覽）打開同分支的 `living-library/gpt-library-bridge.html`；在 3D 書內可捲動，也能以原頁連結查看。候選預覽使用相對路徑以保持檔案版本一致。
- 此頁只保存**公開路由與使用說明**：ChatGPT Library `/30_KNOWLEDGE/00_GEN2_CHATGPT_LIVING_LIBRARY_BRIDGE_v1.0.md`、`/30_KNOWLEDGE/Rules_Skills/00_READ_FIRST_GEN2_CURRENT_SOURCE.md`，以及私有 GEN2 的入口連結。**不含私人 MD 正文、不持有 token、不提供私人檔案的公開下載網址**。
- 提供「複製 AI 交接指令」供 ChatGPT／Work 經授權讀取最新 GEN2 `main/kb/`。書中的公開導航卡不是正式 KB／Skill，也不能執行 Skill 或代替 OWNER 核准。
- **未實作** ChatGPT Library ↔ Living Library 的自動同步、私人文件網站讀取 API、跨系統持續狀態同步。未來以來源 ID／版本／權限為核心，先驗證最小唯讀同步，再決定是否新增後端。
- 回歸基線從 **12 本舊書＋7 張原有功能入口** 加到 **13 本公開書＋8 個公開功能入口**（另有原創業研究候選卡），GEN2 授權只會追加；斷線保留 GPT 之書與原本公開功能。
- 此變更仍在 Draft PR；尚需 Node 回歸測試、真實瀏覽器與手機 WebView 驗收，不以靜態檔案存在代表正式發布。


## 2026-10-13 行動裝置渲染節能候選（本 PR 未發布）
- 探索狀態：行動裝置（小螢幕或 coarse pointer）Three.js 以最高約 30 FPS 節流；桌面上限約 60 FPS。這是幀間隔控制，實際 FPS 受硬體、場景複雜度與瀏覽器影響。
- 書本展開、MD 搜尋或 GEN2 授權對話框開啟時，停止 WebGL 連續渲染；關閉且回到可見場景時才恢復。
- 點選「暫停漂浮」、系統偏好 reduced-motion 時，改為僅在需要時繪製單幀；鏡頭、選書、歸架仍可刷新畫面。瀏覽器背景／pagehide 同樣停繪。
- 新增 `tests/living-library-render-budget.test.mjs`，覆蓋讀書／對話框／頁面可見性／靜態按需重繪。另在隔離 JS 環境對實際程式渲染控制段落完成 30 項邏輯檢查，均通過。
- 本項目前未執行真機 WebGL、iframe 滑動、耗電與記憶體測量；整套 node --test 測試未確認由 CI 執行。不得標記 OWNER acceptance PASS。
- 獨立 `chatgpt.site` 預覽與 GitHub Draft PR 是不同的部署目標；本修改只在 GitHub 候選分支，沒有更新那個網址，也沒有合併 main。
- 不修改 GEN2 正本、Android Bridge、公開書本／卡片 Registry 或任何技能執行權限。


## 3D 書架與多圈召喚（候選）

- **修正前：** 僅 7 本環繞、全部同一圈；`active=false` 只令書在場景中消失。背景書架上的數百本裝飾書沒有對應真正的 KB／功能書 ID。
- **修正後：** `book-placement.js` 提供幾何無關的環繞圈與實體書架索引。書本超過 7 本後自動加入第二圈、第三圈；每圈依容量排列、不同高度／半徑／旋轉速度。沒有新增另一套資料庫。
- **3D 書架：** 7 組既有實體書櫃各預留 5 列 × 5 個有唯一位置的互動書位，合計 175 格。未召喚的書與環繞書共用同一個可點擊 `bookMesh` 和原 ID，放回時飛向自己的格位、縮放／旋轉為書脊，停止在書架而非隱藏；點擊架上書可飛回環繞圈。
- **動畫／效能：** 1.25 秒緩入緩出飛行加垂直弧線；變更前先關閉書本頁，避免 WebGL 於遮罩下暫停。使用者偏好 reduced motion 時不播放飛行而直接落位。既有 30fps 行動裝置上限及書內暫停背景渲染機制保留。
- **基線：** 13 本公開功能／GPT 書與 GEN2 授權動態書本相容；公開功能入口與 `KB-STARTUP` 正式身份不受影響。多圈與落架動態只改 UI 位置，沒有私有 MD 或 Token 持久化。
- **驗證：** 新增 `tests/living-library-book-placement.test.mjs`；尚須 GitHub CI 的完整 Node 測試、手機 WebGL 模型性能、真實點書及書架位置確認。超過 175 本架上書的擴充方式仍未實作，不能聲稱書架容量無限。


## 3D 書架實測回饋後的修正｜空格＋自由視角（候選）

- OWNER 手機測試確認多圈環繞、實際歸架飛行、從架上再次召喚運作；同時指出看不到宣稱的空格／其他書，拒絕填充裝飾假書或鎖定觀看角度。
- 最新公開書本數以 `public-catalog.js` 的實際清單為準：目前除了先前的 12 本及 GPT 之書，Work 又新增了 **安卓之書**，故現有基線是 **14 本公開書**，預設 9 本環繞、5 本實體歸架（使用者本機書本偏好可能讓數字不同）；175 是總空位容量，不是已藏書量。
- 原本每座書架 5 層、每層 23 個假書脊已移除；現在每座只有 5 層 × 5 個明確空格，沒有書的格位完全保持空白，只有實際未環繞的真書會放在指定格位，正面書封朝向使用者。共 7 座櫃子、175 個格位。
- **禁止鏡頭鎖定**：移除「檢視書架」的鎖定鏡頭與逐櫃切換；Three.js 的 theta 不再限制在 ±1.15 弧度，允許手機／電腦 360° 自由水平轉動，擴大俯仰和縮放。書櫃本來就只排列在圖書館的前側弧線，背面可以沒有書架。
- UI 直接顯示實際書本總數、環繞數、歸架數及 `已使用／175` 容量，不誤稱 175 本書。現有書本召喚、卡片、可捲動工作頁、GEN2 授權疊加仍保留。
- 新增回歸斷言禁止恢復 23 款假書脊與鎖定鏡頭。程式語法及純幾何檢查可驗證；真實手機上空格可視性／自由鏡頭操作仍待新預覽實測。

## 漂浮書名與實體書脊書名｜候選（2026-10-13）

- 依 OWNER 最新要求，環繞書上方加上真正跟著 3D 書本移動的可讀書名牌，歸架時書本旋轉 90 度，讓印有直排書名的側面（書脊）面向走道。
- **同源同步**：封面、漂浮標籤與書脊全部從同一書本物件 `b.name` 生成，不另建姓名對照資料庫。公開書本使用 public-catalog 名稱；授權後的 GEN2 書本使用正式投影名稱。
- `syncBookTitles` 在正式投影更新、同一書本 ID 換名時原地更新封面、漂浮標籤和書脊的 CanvasTexture。書名隨書移動，標籤可點擊，歸架後隱藏浮動標籤而留下印名書脊。
- 長書名自動縮字；書脊採直排漢字，混用如 GPT 的英文縮寫按詞組排。書架空格不放任何裝飾假書，鏡頭仍可水平 360 度自由旋轉。
- 新增 `tests/living-library-book-titles.test.mjs` 針對同源、書脊朝向、動態換名和點書進行回歸測試。尚需在手機 WebGL 實機驗證文字清晰度、遮擋和幀率。
- **限制**：自動重繪僅針對已讀取到的正式 GEN2／公開書本資料。這不代表網頁能直接同步 ChatGPT Library 私有檔案。仍為 Draft PR，沒有合併正式網站。

## 封面優先：移除漂浮文字框、加大高對比書名（2026-10-13 候選變更）

- 最新 OWNER 驗收後指定：**不使用懸浮在書本上方的額外文字框**，而是直接加大書本**正面封面書名**，提高字色與封面的對比，保留書架上的實體直排書脊文字。
- `index.html` 已移除 `orbitTitleTex`、`orbitLabel` 及 Three.Sprite 浮動文字告示，節省每本書一個 Sprite／貼圖；3D 環繞依然是原本的真書，點書或歸架照常。
- 書封 CanvasTexture 改為 **512×768** 高解析；封面以原本 `b.color` 作封邊與上方識別色，向深海軍藍漸層，印上**粗體米白大字、深黑描邊、金色邊框**。中文短書名分兩行，長書名最多五行並自動縮字，避免超出封面。
- 封面、書架印名書脊共用 `b.name`；正式 GEN2 在授權重新載入後如同一書 ID 名稱有變，`syncBookTitles` 同步更新兩者，不另儲存複製的書名。
- 維持真正空格、自由 360° 視角、多圈環繞、175 格容量、原有功能入口，不修改 Android APK 或私有 GEN2。
- 改寫 `tests/living-library-book-titles.test.mjs`，測試舊浮動標籤已被禁止、封面字體縮放與顏色對比、書脊及同源更新。實際手機視覺可讀性與 Three.js 完整渲染仍需新預覽驗收。
