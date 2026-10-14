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

## 6. 自動重新連結與更新視窗（2026-10-14）

- L4 正式 live 頁每次啟動時讀取無快取版本指紋（HTML、公開卡片目錄、3D 行走／書架 JS、GPT 卡片 HTML 等），在保持 3D 書庫及私有授權隔離的前提下，比對本機保留的**前次公開版摘要**。
- `↻ 重新連結` 是使用者要求刷新書本／卡片／按鈕／UI 的操作入口；背景每 180 秒及頁面恢復可見時檢查有無網頁更新。發現變更後，如果書頁／權限對話沒有開啟可自動重新連線；編輯與閱讀中不強制斷線，改顯示更新提示供使用者操作。
- 重新連線只使用 `/living-library/live/index.html?v=<13位數時間>`，符合 Bridge107 原生 Android WebView 對遠端首頁的精確路由白名單；重新載入的公開 Javascript 加入即時 `?v=<時間>`，避免舊檔快取。**不能在 App 使用額外 `?book`、`?card` 參數來重新導頁**。
- 重新連結完成後，彈出符合書內羊皮紙、金邊與卡槽外觀的 `知識書庫更新完成` 公告，列出根據前後快照識別的「新增／修改／移除卡片、書籍卡槽／新增／變更按鈕、UI／3D 場景變更、卡片網頁內容」。
- 沒有舊版快照時，公告必須明確標成「首次建立更新紀錄」，不得把原本已存在的項目說成剛更新。只要一次確認即可保存新基準，避免重複跳視窗。
- **唯一會重設的是** `living-library-workbench-v1`（書本選取、漂浮環繞與書架顯示偏好）；保留 Android/GBA ROM 與存檔、GEN2/Drive/ChatGPT Library 正本、Token／權限、私有工作紀錄及所有其他 localStorage Key。原本的使用者保存紀錄不得因新增卡片遭全域清除。
- 更新檢查失敗／離線時保留當前可用頁面與偏好，不冒稱完成。Web 已部署與 OWNER 實機實際看到的狀態分開。
- 這是純網頁功能，不升版 APK；長期仍需進一步加入正式手機／WebView 操作驗收與來源一致性檢查。

## 7. 卡片圖鑑主畫面精簡與人物操控（OWNER 核准 2026-10-14）

- **禁止在 3D 圖書館首頁重建八個快捷控制鈕。** `重新連結`由背景自動檢查，手動備援在「系統之書｜連線與版本」卡；`GPT 技能卡`與`遊戲之書・新卡`快捷入口直接淘汰，保留原有 GPT 卡與遊戲之書；`搜尋 MD`移到「知識世界｜正式 MD 搜尋」網頁卡；`連接 GEN2`改由「系統之書｜連線與版本」卡觸發既有安全授權視窗；`暫停漂浮／視角重設／工作台說明`的**首頁按鈕直接淘汰**，不把廢止快捷鍵複製進系統之書。首頁隱藏的「書庫首頁」舊鈕也一併撤下。
- 書籍／卡片依既有 `public-catalog.js` canonical ID 登記；新增 `public.knowledge.search`、`public.system.controls`，都各有同目錄公開、安全、響應式 HTML；使用者點卡後仍在書內 `readerFrame` 鑲嵌，不另開控制台。
- 嵌入控制頁面使用同源 `postMessage` 送固定三種**公共 UI 操作請求**，主頁檢查 `event.origin === location.origin`、`event.source === readerFrame.contentWindow`、`readerCard.id` 與 action 白名單；授權資料仍由現有 GEN2 對話視窗內處理，公開頁不保存私鑰。
- 主場景移除左側說明宣傳區（不再與人物／書本重疊）；不在主畫面顯示 WEB LIVE 偵錯文字、GEN2 連線／書架格數等系統狀態，也不顯示底部 ACTIVE/ORBIT 附屬狀態；對話及卡片需要的 DOM 狀態可保留但以 CSS 脫離視覺畫面，避免破壞既有 JS。
- **移動互動改成**：單指輕點 3D 人物模型 → 在該觸點浮現搖桿；按住搖桿拖曳 → 人物依鏡頭方向移動，書本漂浮錨點跟隨；手指放開 → 人物停止、搖桿保留；輕點場景空白位置 → 搖桿消失；輕點書本仍可展開圖鑑。搖桿沒有獨立的「人物探索」切換按鈕。
- **鏡頭不突變**：點人物、顯示／隱藏搖桿、移動人物、開書頁都不可重設 camera distance、FOV、pitch；僅真正的雙指捏放與桌面滑鼠滾輪能修改 camera distance。視窗尺寸變更只更新投影縱橫比／既定手機 FOV，並保留現有使用者縮放距離，**不得再強制切換 distance=6.8**。單指在非搖桿區域拖曳只轉視角。
- 這次優先改 `/living-library/live/`（Bridge107 實際使用的遠端頁），保持 Android 原生功能和簽章不變。網站 CI／Pages 部署不等於手機真實 WebGL/觸控驗收；後續以 OWNER 截圖和操作證據獨立完成。
