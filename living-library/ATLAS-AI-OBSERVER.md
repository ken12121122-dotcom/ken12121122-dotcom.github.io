# 萬物圖鑑｜右側讀卡格／即時螢幕 AI 唯讀觀察接口 v0.1

review_status: generated
source_branch: feature/nw-ai-readonly-card-observer-20261016
target_branch: main
scope_level: L2
human_approval_required: true

## 使用者確認的實體介面

- 世界中的「萬物圖鑑」左頁九個卡槽、右頁**一個獨立讀卡格**（容器 `local.atlas.compendium`，`slotIndex=9`）。
- 右頁已經有一塊可更新的 3D 顯示幕及五個控制鈕（左／右／上／下／中間確認）。**重用原按鈕，不另外在卡片創造術加新按鍵。**
- 卡片放入右側讀卡格時顯示幕從現有 ObjectCraft 本機正本讀取卡面、照片、手動編號、卡名及類型。卡片編輯且保存後依 ID／revision 刷新顯示；移出時顯示待機畫面。
- 左／右按鈕切換「卡片畫面／AI 唯讀觀察」；上鍵回卡片畫面，下鍵進觀察模式。
- 在卡片畫面按中間確認＝**原本的開啟卡片編輯器**；在觀察模式按中間確認＝開啟唯讀 JSON 對話框。
- 對話框只有「⧉ 複製」及「× 關閉」。**不自動上傳、不向 GitHub 公開、不授予 AI 直接讀取手機資料權限**。使用者須自行複製貼到 ChatGPT，圖片像素則需由使用者另外傳送截圖。
- 畫面內文字與對話框採深色／黑字高對比；主場景原有按鈕、遊戲、語音、人物、書架與卡片搬移維持原樣。

## 固定介面 Schema

~~~yaml
schema: nw.ai-card-observation.v1
review_status: generated
observation_mode: manual_export_only
read_only: true
image_payload_included: false
exported_at: <ISO8601>
card:
  card_id: local.card.<ID>
  revision: <saved_revision>
  card_number: '001'
  title: <saved_title>
  description: <saved_description>
  card_type: <saved_type>
  linked_public_card_id: <optional_public_card_id>
  owner_book_id: local.atlas.compendium
  container_id: local.atlas.compendium
  slot_index: 9
  placement: book
  status: draft
  updated_at: <ISO8601>
  image:
    present: true
    mime_type: image/jpeg
    encoded_characters: <integer>
    scale: <number>
    x: <number>
    y: <number>
source_records:
  - source_type: device_local_storage
    source_key: living-library-player-objects-v1
    object_id: local.card.<ID>
    revision: <saved_revision>
unresolved_gaps:
  - photo_pixels_require_separate_user_screenshot
  - no_live_remote_device_access
~~~

資料僅採白名單，不包含 base64 照片、未知私有欄位、權杖或其他卡片。確認按鈕只是產生本機唯讀快照；使用者自己按複製才讀取剪貼簿權限，未授予 GitHub 寫入權限。

## 來源／依賴／驗證

- `living-library/live/atlas-state.js`：右側 slot 9 的唯一讀卡格。
- `living-library/live/atlas-model.js`：原 3D 螢幕，借用共用 `compendium-card.js` 的卡片 Canvas 即時預覽。
- `living-library/live/atlas-runtime.js`：原五個實體按鈕的 Raycast，重用觸碰／確認操作；不修改拖曳與書本流轉。
- `living-library/live/object-craft.js`：卡片唯一正本，**未修改**。
- `living-library/live/card-observer.js`：無寫入函數，只有唯讀的 `snapshot()`；錯誤時回固定代碼。
- `living-library/live/index.html`：螢幕控制路由、明確人工觸發的唯讀展示，手動複製。

驗證點：既有中間確認仍可開卡；未放卡不得觀察；移卡、重開、保存後螢幕跟隨新 revision；不改 source localStorage；不曝光圖片字串及私有欄位；基本圖鑑、製書、3D、無 WebGL 備援不退化。

## 狀態與發布閘門

- 原始卡片、圖鑑格子、卡片來源沒有任何搬遷、刪除或寫回。
- 介面只在既有實體 3D 螢幕／按鍵使用；無 WebGL 的 2D 備援仍可正常開卡，AI 觀察功能目前不在該備援顯示。
- 草稿測試使用合成資料，**不包含使用者手機真實的第一張卡內容**。
- 真機圖片顯示、頁面更新、Android WebView 點擊與剪貼簿權限均須 OWNER 驗收；未通過不可宣告正式可用。
- 回退：不合併候選 PR／回退 WebView 網頁 commit，即保留原照片與卡片內容。


## v0.2｜GBA 實體按鍵與右頁超近距離專注（OWNER 2026-10-16）

OWNER 提供實際手機截圖：右側小十字按鈕擠在一起，控制區仍有餘裕；要求不要用 A-E 自創代碼，改採熟悉的 GBA 模擬器按鍵命名。允許將按鈕分散至操作區左／右兩側，並增加**只看圖鑑右頁讀卡螢幕及控制區**的更近專注鏡頭。

**控制器**：使用標準 GBA 輸入代碼 `UP`, `DOWN`, `LEFT`, `RIGHT`, `A`, `B`, `START`, `SELECT`, `L`, `R`，目前是圖鑑內部操作協定，**不連接、不覆寫真正 GBA 遊戲模擬器輸入**。舊五顆小按鈕改為視覺較大、分散的 D-pad／A-B／START-SELECT／L-R，以物理 Three.js mesh + CanvasTexture 清晰標字；維持單一卡片格及上半螢幕原尺寸。D-pad 在底部左側、A/B 在右側、START/SELECT 在螢幕下緣，L/R 在右頁上緣。

| GBA key | 圖鑑使用意義 |
| --- | --- |
| A | 卡片畫面開啟原卡片內頁；唯讀畫面開啟單卡快照 |
| B | 觀察畫面退回卡片；已在近距離專注則回一般書本專注；一般書本專注再退回全景 |
| SELECT | 切換卡片／AI 唯讀畫面 |
| START | 切換圖鑑右頁專用超近距離專注；亦可點螢幕進入 |
| UP / DOWN | 指定卡片畫面／AI 唯讀畫面 |
| LEFT / RIGHT | 在兩種畫面間切換 |
| L / R | 在兩種畫面間切換（未來留作前後頁） |

**鏡頭**：3D 原模型 `local.atlas.compendium` 不複製；只把鏡頭瞄準其右頁局部 `(1.34,0,0.20)`，以右頁寬 `2.66*BASE` 與視窗長寬比計算距離（原一般書本用 `5.4*BASE`）。進入超近模式時，橫螢幕也只顯示本圖鑑右頁，不顯示其他並排書。原本右上 × 仍可一次退出至世界全景。視角與按鈕不會寫入任何卡片資料。

**相容性**：卡片建立、儲存、備份、拖曳、九格、單張右格、圖鑑螢幕、原生 APP 更新、GEN2、GBA 模擬器全部維持原狀。暫時不修改無 WebGL 2D 介面。新的按鍵、近距離模式待手機真機驗收，仍為 Draft PR，不等於正式發布。
