# Living Library UI Manager Registry | KW-UI-LIBRARIAN-001

## UI 標準：操作按鈕只用圖示

來源：OWNER 2026-10-15 指定為 UI 設計標準，並進一步明確要求「我認為直接不要按鈕文字」。此要求取代同日稍早允許極簡文字的版本。

- 適用念動圖書館現有與後續畫面的操作按鈕；只顯示可辨識圖示，不在按鈕內顯示文字，同一動作沿用同一圖示。
- 授權、送審、刪除或正式發布的完整意義，放在按鈕以外的說明、提示及無障礙名稱，保留原有確認與權限 Gate。
- 完整動作名稱放在 `aria-label`；可加 `title` 作桌面提示，但手機不得只依賴滑鼠懸停。
- 圖示縮減不能縮小可觸控範圍：主要操作至少 44 × 44 CSS px，保留鍵盤焦點與 disabled 狀態。
- 書名、卡名、搜尋結果及正文是內容身分，不因其容器可點擊而移除名稱；權限、風險及執行結果說明也保留。
- 這是顯示規則，不授權更改動作、入口、權限、卡片身分或發布 Gate。候選版與正式站的套用範圍須分別記錄。


> OWNER 2026-10-14 核准將 UI 名詞、書籍分工與自然語言定位制度納入專案管理。此檔是 GitHub 公開前端**管理規則**，不是 GEN2 正式 Skill；不儲存私有資料，不代表未實測功能已完成。

## 1. 診斷與修改時的最小快照

所有 UI 改動先記錄：

1. **runtime**：用戶確認的 APK Bridge 版本及依據、正式 WebView URL、目前可能是遠端或離線；遠端部署實際檢驗結果另列，不得由 APK 版本推出它已載新頁。
2. **source**：正本 repo、`main` commit、實際讀取的 HTML／JS 路徑及 blob SHA、讀取日期；先查 `living-library/live/`，再比對 `living-library/` 開發版有無差異。
3. **identity**：`book_id`、`card_id`、`slot_index`（1-based）、`number`（視覺展示編號）、`display_label`、`selector`，不要混為一談。
4. **surface**：是書／卡清單、首頁共用 HTML、內嵌目標 `card.url`、Android 原生路由、還是 GEN2 私有投影。
5. **dependents**：枚舉所有引用相同 Card ID 的書籍以及登入前後可見性。若影響共享元件應擴大核對。
6. **evidence**：原功能保留、新功能測試、GitHub Actions、Pages HTTP、WebView、OWNER 實機各別標 `PASS`／`FAIL`／`NOT_TESTED`。

## 2. 定位詞及畫面座標

- 「圖書館首頁／主場景」：`living-library/live/index.html#stage`。頂部功能列 `.top-actions`；書單 `#bookList`；搖桿 `#walkPad`；視角控制 `#resetBtn`；人物探索 `#exploreToggle`。
- 「書頁」：`#drawerWrap`；「魔導卡冊」：`#drawerFunctions`；「第 N 張卡片」：先查 `BOOK_CARDS[book_id][N-1]` 再以 `.codex-slot[data-card-id="ID"]` 找到按鈕。
- 「卡片內容分頁」：`#readerShell`；「返回魔導卡冊」：`#readerBack`；「內嵌網頁」：`#readerFrame`；點進去的真正功能頁以 `card.url` 判斷。
- 「APP 原生首頁／權限／版本更新」：Android 原生功能，並非 `/living-library/live/index.html`；需到原生路由及 Android 程式確認。
- 完整人類可查的最新公開書卡對照：`/living-library/ui-map.html`（動態讀取 live 公開目錄）。

## 3. 確認深度

| 深度 | 內容 | 使用情境 |
| --- | --- | --- |
| L0 | 確認版本、目標 Book ID、Card ID、slot／展示 number、實際檔案路徑 | 問名稱／找位置 |
| L1（預設） | L0 ＋相關 UI selector、共享引用、需要改動的最少檔案／保留項 | 新增按鈕、更名、調整單張卡 |
| L2 | L1 ＋卡片閱讀器／導覽回返、場景／操作流程、瀏覽器及 WebView 驗證 | 互動或視覺布局變更 |
| L3 | L2 ＋Android 原生權限／簽章／路由、私有 GEN2 審核及安全 | 原生／受控功能 |

## 4. 責任分工，避免重複

- 一書一主要責任，可多入口；一功能一 canonical Card ID，可被多書引用。書本分類不是權限或後端副本。
- 研究→文獻／假設；創業→商業決策；知識世界→知識圖譜／空間；任務→工作進度；學習→個人學習；資產→財務；職安→職業安全領域；寫作→文字／文件；設計→排版／UI；教學→教案；遊戲→遊戲內容；系統→UI／維護索引；GPT→ChatGPT／GEN2 導航；安卓→原生路由／更新。
- 分工是 OWNER 核准的**管理分類**，不宣稱職安、寫作、設計等分類頁已有獨立領域 Skill，且不修改任何既存業務功能。
- 先查現成能力是否已引用：`public.canvas.workbench`、`public.research.navigation`、`public.startup.tasks`、`public.calendar`、`public.knowledge.world`、`public.vault.web` 均為跨書共用。
- 有新功能時優先掛入既有書，只有主責不同且需求明確才評估建立新書；未經 OWNER 核准不得自動刪減、合併、重排現有書、卡或首頁按鈕。

## 5. 自然語言例子與變更憑證

「系統之書第二張卡的按鈕」 → `book_id=system` → 第二個 slot `card_id=public.canvas.workbench`（目前視覺編號是 02）→ 比較共享引用 → 判定要改卡片外觀還是 `/packagecanvas/` 內頁；先提供 L1 差異清單。

「首頁上方視角重設」 → `living-library/live/index.html` → `#resetBtn` → 同步檢查開發版 `living-library/index.html` 的差異 → L1 修改與回歸。

每次變更需留下：`request`、`base_sha`、`book_id`、`card_id`、`surface_path`、`scope_level`、`shared_book_ids`、`preserved_features`、`changes`、`tests`、`deployment_evidence`、`owner_device_verification`、`rollback_ref`。

## 6. 發布責任

- 正式 Web-first 主路徑是 `https://ken12121122-dotcom.github.io/living-library/live/index.html`；不得把開發版 `/living-library/index.html` 的測試當作正式 WebView 驗收。
- 文件可以標明 OWNER 已核准管理規則；新 UI 顯示生效仍需 CI、OWNER 合併、Pages 實際服務及手機實測。不得將不同層次驗收混成 `PASS`。
- 限制：原生 Android、私有 GEN2 正本、權限、APK 版本及簽章保持不變。

## 7. Branch / PR 與卡片的單一索引

- 管理首頁：系統之書 `public.system.ui.map` → `/living-library/ui-map.html`。本頁整合 UI 名詞、書籍職責、Live 卡片身分、開發版差異與 GitHub 公開狀態，但**不是獨立權威資料庫**。
- 卡片統計：只計一次 canonical `card_id`；配置數另外計 `book_id → card_id` 的關係。同張卡多書引用不另造卡 ID；分支數與卡片數不能相加。
- 分支統計：必須從 GitHub `/branches` 分頁讀完，或標註 `未驗證`，不可硬編前次 189；卡片相關分支名稱篩選為 `card|book|library|memory-palace`（不代表這些分支要合併或刪除）。
- PR 狀態：GitHub `/pulls?state=open` 分頁獲得；Draft / Open 不代表已併入 `main`。卡片索引的歷史 PR 僅是已知線索，不能作為完整追溯證據。
- 主版本：`main` SHA、GitHub Pages 部署驗證、OWNER Bridge 107 手機驗證三者**分別記錄**，不能因 GitHub REST 成功就標 `Pages PASS` 或 `PHONE PASS`。
- Live / Dev 差異：使用既有 `living-library/live/public-catalog.js`（正式 WebView 來源）與 `living-library/public-catalog.js`（開發版）；列出缺失卡 ID、變更的卡屬性及書本卡槽差異，必要時回溯 PR，**只比對不自動補卡／刪卡**。
- GitHub 查詢設計：僅由操作員點擊按鈕，以公開 GET 讀 GitHub API；無 token、無網頁自動 poll、不得將私有 GEN2 或手機原生憑證寫入公共網頁。API 失敗顯示 `FAIL／未驗證`。
- 真正刪除歷史 GitHub 分支／合併尚未通過的 PR 是另一個需經 OWNER 明確批准的受控行為；本頁不提供此類寫入功能。
- 更新方法：改動目錄／UI 名稱先更新 canonical `book_id`／`card_id`，測試 Live 與 Dev 是否一致；版本發布後 UI 只會顯示其當下已部署的公開資料，不能以 PR 的候選頁聲稱主站已更新。

## 8. 3D 遊戲參數的既有卡片歸屬（2026-10-15 候選版）

**避免誤認兩張 UI 卡**：`public.system.ui.map`（《系統之書》→「UI 名詞與書籍分工」）是**UI／卡片路由與管理索引**，不是即時編輯器；玩家真正能調整操作偏好的既有卡是 `public.system.controls`（《系統之書》→「連線與版本」，`living-library/live/system-controls.html`）。

2026-10-15 新增於 PR #224（**候選，不表示已在正式手機上生效**）：「3D 世界參數（實驗）」區塊，使用原本 `living-library-controls-v1` 本機偏好及既有同源 iframe 訊息白名單；**不新增第 15 本書或獨立設定卡**。

| 參數 | 內部欄位 | 預設 | 可調區間 | 影響範圍 |
| --- | --- | ---: | ---: | --- |
| 3D 卡片尺寸 | `cardSize` | 1.0× | 0.65～1.20× | 世界內與專注模式的卡片模型外觀 |
| 卡片內圈距離 | `cardOrbitDistance` | 1.0× | 0.88～1.06× | 人物與最內圈卡片間的軌道距離倍率，仍在書本軌道內側 |
| 卡片環繞速度 | `cardOrbitSpeed` | 1.0× | 0～1.8× | 卡片公轉，不影響書本公轉 |
| 卡片召喚時間 | `cardSummonDuration` | 1700 ms | 900～2600 ms | 3D 煙霧創卡後移入軌道的動畫長度 |

**程式位置**：`living-library/live/card-placement.js` 定義卡片圈層、預設與範圍；`living-library/live/index.html` 負責安全偏好正規化、同源指令驗證、套用 3D 變化；`living-library/live/system-controls.html` 顯示滑桿與「↺」恢復預設。與 `living-library/live/book-placement.js` 的**原有環繞書**數學分開；不得將數值當成原始卡片資料或改寫物件唯一 ID。開發參數變更需更新本表、測試與 GitHub Diff；WebView/手機視效須另驗證。

## 萬物圖鑑整合（2026-10-15 開發分支）

- 新增 `book_id=cardCollection`（卡片之書）與 `card_id=public.gaming.atlas`；原 14 本書及遊戲之書原 5 張卡均保留。Live 合計 15 本書、遊戲之書 6 張入口；Dev 舊版維持原樣。
- 萬物圖鑑不是額外公開 KB：場景模型 `local.atlas.compendium`，左右 9+1 格，無歸架按鈕；卡片之書的卡片容器為 `local.atlas.collection`，左右 9+9 格。
- `ObjectCraft` 是本機卡片正本：卡片 ID 不變，`containerId`、`ownerBookId`、`slotIndex` 記錄移動；所有格子是有限整數且滿格拒絕轉移。卡片之書的位置偏好另存版本 key，不存私有 GEN2。
- `atlas-state.js` 定義格位；`atlas-model.js` 承接提供的 v0.2 3D 原型；`atlas-runtime.js` 重用原 renderer、煙霧與閱讀器；`atlas-voice.js` 使用原生訊息通道或瀏覽器辨識，網頁負責命令路由。
- 主場景點人物開啟搖桿及語音；鬆開／移動搖桿不停止辨識。進入專注保留搖桿，× 只退出視角；卡片之書另有合上、歸架。語音在頁面隱藏／離開 APP 時停止。
- 無 WebGL 時以 `atlas-controls.html` 操作同一份卡片資料；同源 allowlist 明確加入此頁。原始 GBA、GEN2、存檔、Android 路由不變。
- 網頁正式發布仍需 OWNER 驗收。原生語音連接由使用者於本次對話另行授權直接更新 APP；Android 自動檢查及語音手機實測分別記錄，不將 parser／幾何測試冒充實機證據。
