# Living Library UI Manager Registry | KW-UI-LIBRARIAN-001

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
