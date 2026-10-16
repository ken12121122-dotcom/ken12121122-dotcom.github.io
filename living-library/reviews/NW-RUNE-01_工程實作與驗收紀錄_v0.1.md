# NW-RUNE-001 / RUNE-01｜候選實作紀錄｜2026-10-16

> `status: implementation_candidate` · `review_status: generated` · `owner_approval_required: true` · `deployment: NOT_PUBLISHED`

## 預期完成內容

- **重用**現有地面原點法陣：同樣的 4 層圓形基座、3 個光環及 22 枚符文改掛在同一個零位移 `THREE.Group` 下。統一標註候選內部 ID `local.rune.origin`（非永久收藏號），不移動原點、不改造渲染器或人物。
- `LivingRuneContract`：純函數 `isEligibleEntry` 與 `findNearestEligibleCard`，使用法陣與漂浮卡的 **X/Z 世界座標**，不是畫面像素或玩家位置；忽略非普通卡、回收/書內/忙碌/未命名/已綁定/L2 卡。預設最多 3.0 世界單位、距離差 < 0.15 單位判為並列待選；均待 OWNER 實機校準。
- `LivingScene.rune` 提供唯讀 `id/displayName/anchor()/hitTest(clientX,clientY)/findNearestCard()`，`hitTest` 對已註冊的可見法陣網格執行 Three.js Raycaster，候選選卡時從當前 3D card Group 取真實世界座標。
- **完全不加入 pointer 監聽、不觸發 3 秒長按、不進行綁定、不修改 localStorage、不建立印記、沒有動畫或永久發號**。這些屬於 RUNE-02/03/04/05，不能將此技術基座誤認為已完成儀式。
- **不改 `living-library/live/atlas-l2-threejs-preview.html`**，先前美術原型仍在原獨立候選分支，不發布。

## 檔案與安全邊界

| 檔案 | 本階段變更 |
| --- | --- |
| `living-library/live/rune-contract.js` | 新增純函數與法陣候選身分 |
| `living-library/live/index.html` | 加載純函數，重組**相同地面幾何**為零偏移 runeGroup，暴露唯讀場景檢查 API |
| `tests/living-library-rune-contract.test.mjs` | 測最近卡、範圍、等距、身份、狀態與回歸靜態連線 |
| `tests/browser/living-library-p0.cjs` | 在 GPU 失敗降級模式驗證候選函數及不寫入存檔；修復舊測試 progressNotice 的非同步等待 |

**未變更**：任何正式 `main` 檔、`object-craft.js` 玩家存檔格式、普通卡 ID、`cardNumber`、動畫、GEN2/Android/GBA/manifest、圖鑑升級 PR #250。

## 測試與驗收分界

- Node 契約單元測試和 Playwright 2D 降級檢查由 PR CI 執行（結果以 run 為準）。
- 當 CI 在軟體 GPU 環境因現有 renderer 政策拒絕 WebGL 時，不能把 `rune.hitTest` 的真實 WebGL 選取宣稱為 PASS；必須保留 `NOT_TESTED`。
- 本階段未操作真實玩家卡，僅合成 `local.card.*` 測試值；不導入用戶圖片或任何私人資料。
- 下一步 RUNE-02 在法陣實際命中後才能消耗 pointer 事件並啟動 3000ms 計時與選中動畫，必須保留人物、書本、卡片拖動原交互優先權。

## 重要已知限制

1. `local.rune.origin` 僅是內部邏輯 ID，非收藏編號；名稱 `初始法陣` 尚待 OWNER 視覺確認；現有舊卡可能叫 `初代法陣`，不得偷偷改名。
2. 當遊戲主場景沒有 GPU 而退回 2D 清單時，本階段 `LivingRuneContract` 可使用，但 3D 命中/近距離掃描尚無替代互動入口。
3. 3.0 世界單位與 0.15 並列門檻是候選，當法陣與卡片環繞遠離角色時應拒絕，不應穿越世界自動綁卡。
4. `LivingScene.rune.hitTest` 是「法陣模型是否被滑鼠光線射中」的**唯讀結果**，還未做所有遮擋物的第一交點優先裁決；RUNE-02 必須處理角色擋住法陣的點擊競爭。

## 回退

本候選來自 `main`；沒有原存檔資料遷移，只需不合併 Draft PR 即保留正式網站原狀。若需回退，本 PR 不需覆寫玩家檔案，保留候選分支供核對。
