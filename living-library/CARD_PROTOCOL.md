# CARD-PROTOCOL-001｜念動系統「某某卡」與內嵌網頁協議 v1.0

> OWNER 指令來源：2026-10-14。適用於念動圖書館、GPT 之書與四層念動系統的「卡片」介面。此檔規範 **公開網站的 UI／路由**，不是 GEN2 受控 Skill、執行授權或私有資料同步機制。

## 1. 對話意圖判斷

- 在「念動系統／念動圖書館／某某之書／新增卡片／技能卡／工作卡」上下文，**「某某卡」預設指一張能在書內直接開啟「獨立響應式網頁」的功能或資訊卡**，不是只有標題的佔位卡；展示以既有書內 `readerFrame` iframe 為預設。
- 「建立 X 卡」＝辨識目標書本與來源 → 建立可獨立開啟的 HTML → 在 `living-library/live/public-catalog.js` 登記 canonical ID、標題、摘要、guild、來源狀態、URL → 將卡片 ID 放入 `bookCards.<book>` → 確認該 URL 在書中 iframe 可讀 → 測試 → 部署 Pages。
- 「修改 X 卡」＝先反查 canonical card ID、所屬書、HTML 真正路徑與使用者實機載入來源，優先更新同一張卡的頁面；不另建重複 ID／假卡。
- 「打開 X 卡」＝開啟所屬書與內嵌正文；不假設卡片本身有讀寫任何私人資料或能執行外部工具。
- 若上下文明確是「課堂學習抽認卡／信用卡／名片／遊戲道具卡」則按真實意思處理，**不要強制建立網頁卡**。有重要歧義或權限衝突時先確認目標。

## 2. 四層協作與權威

| 層 | 角色 | 卡片流程 |
|---|---|---|
| L1 ChatGPT 檔案庫 | 討論／規則／交接 | 保存規格、來源、連結與驗收紀錄，不充當正式 GEN2 Skill。 |
| L2 Google Drive | 原始雲端文件 | 只引用已授權可公開部分；不把私人文件直接發布到 Pages。 |
| L3 GEN2 正式技能核心 | 正式 KB／Skill／Workflow／權限 | 只以已授權正式 GitHub main 與 OWNER Gate 確認。網頁卡存在不代表已授權執行。 |
| L4 念動圖書館 | 顯示／互動／讀取入口 | 網頁正文、書本卡片、iframes 及 GitHub Pages 部署。 |

## 3. 安全、發布與驗收

1. 網頁卡 **唯讀展示預設**；需要操作 Gmail、Calendar、GitHub、Android 或 GEN2 時，由有授權的 ChatGPT／原生路由獨立處理，禁止把「卡片能看」說成「能力可執行」。
2. `readerFrame` 只開既有白名單可信網頁或明確驗證的本地主頁同源頁。新增卡頁優先放 `living-library/live/` 並確保相對 URL 可在正式入口解析；不任意擴大 URL 白名單、不插入 JS Bridge。
3. 不能在公開 `gpt-skills-catalog.js`／卡片 HTML 泄露私人 GEN2、ChatGPT Library 內容、憑證、權杖與其他敏感資料。若來源是私人資料，應改展示安全的路由說明與授權入口。
4. 先比對 `living-library/index.html` 與正式 `living-library/live/index.html`；**實際部署正本是 live**，包含其 JS/CSS／卡片目錄；改純網頁不發 APK，修改原生權限／Activity／Bridge 才需要原生發版。
5. 驗收按順序標示：來源已改 → web 測試通過 → PR 合併 → GitHub Pages 部署成功 → 確認新頁 URL → 手機實機仍待 OWNER 驗收。每一階段分別回報，不得用 GitHub merge 冒稱已在手機看到。

## 4. 目前實例：GPT 技能總覽卡

- 目標書：`GPT 之書`（ID `gpt`）。
- Canonical card ID：`public.gpt.skills`。
- 網頁正文：`/living-library/live/gpt-skills.html`；技能唯讀快照資料 `/living-library/live/gpt-skills-catalog.js`。
- UI：點卡在書內 `readerFrame` 開啟；另有書庫頁 `✦ GPT 技能卡` 快捷入口；支援 `?card=public.gpt.skills`（網頁瀏覽器）。
- 可見能力：此回合可列舉的 190 項技能規程、21 個類別、36 種工具命名空間，以及四層意圖工作流、命名卡片規則。**只是 2026-10-14 的可用技能索引快照**，不代表每項外部 App 已連結，不含無法被列舉的通用模型能力，也不會在網頁背景自動刷新。
- 主要測試：`tests/gpt-skills-embedded-card.test.mjs`；既有 `tests/living-library-web-runtime-contract.test.mjs` 與 3D 行走／書名測試不得退化。

## 5. 與 ChatGPT Library 協議的關係

遵循 `/30_KNOWLEDGE/Knowledge_World/INTENT-ROUTER-001_念動知識系統四層命名與單一指令規則_v1.0.md` 和 `KW-WEB-UPDATE-001`。這份公開專案指引只處理 **L4 卡片界面與 UI 交付**，不能取代 L3 私有 GEN2 正式規則。
