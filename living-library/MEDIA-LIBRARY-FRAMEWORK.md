# 念動世界｜共用圖片資源庫框架 v0.1（候選）

狀態：generated。尚未部署、尚未連接卡片、尚未經手機實測。程式碼與設計均為候選草稿。

## 目的與範圍
先建立獨立、僅限本機的 IndexedDB 圖片資源庫，未來供圖鑑卡、書本、場景共用。本次不改動原有卡片存檔、圖片自動壓縮、回收站、舊備份、UI、APK、更新管道或 GEN2 權限。無雲端傳輸、無批次遷移、無自動刪除。

## 已有來源與現況
- living-library/live/card-creation.html：輸入限制 10 MB，壓縮到最長邊 480 像素起、JPEG 品質 0.78，超過 70,000 字元則逐步縮小，原樣保留。
- living-library/live/object-craft.js：既有本機正本 living-library-player-objects-v1，卡片圖片依然是 image.dataUrl，原 schema 不變。
- living-library/live/object-backup.js：現有備份只包含 ObjectCraft 原始文字，**不涵蓋新圖片庫**；在完成新備份方案前不可移走舊照片。
- living-library/live/media-library.js：新增獨立底層模組，不掛入現有頁面；只有明確開啟才建立本機資料庫。

## API／可驗證接口
~~~js
const store = await NWMediaLibrary.open();
const result = await store.putProcessed({blob, width, height});
const metadata = await store.getMetadata(result.asset_id);
const image = await store.getBlob(result.asset_id);
const list = await store.listMetadata();
const usage = await store.stats(); // {count, total_bytes}
store.close();
~~~

Schema nw.media.asset.v1；資料庫 nw-media-library-v1 (DB_VERSION=1)。
資料表 assets 僅存 metadata，binary 存 Blob；同一 IndexedDB transaction 原子寫入。
圖片 ID：local.media.sha256.<64hex>。使用 SHA-256 進行內容去重，並非驗證圖片來源。
只接受 caller 已成功解碼、壓縮的 JPEG／PNG／WebP Blob；上限 128 KiB，寬高均 1～2048 px。拒絕 SVG、HTML 與 JSON。僅限 MIME 與元資料檢查，不假稱真正圖片解碼驗真。
本模組不具備任意刪圖、遠端同步、權限變更、讀取既有卡片等功能。

未來卡片候選引用規格（本次**不寫入**既有卡片）：
~~~yaml
image_ref:
  asset_id: local.media.sha256.<64hex>
  scale: 1.0
  x: 50
  y: 50
  review_status: generated
~~~

## 漸進擴建
1. v0.1（本 PR）：獨立底層圖片庫、雜湊去重、回讀、容量統計、獨立測試。
2. v0.2（待批准）：單張引用轉接與預覽；老卡 dataUrl 仍優先。
3. v0.3（待批准）：完整 Blob 匯出／還原、備份一致性驗證、明確確認後遷移與回退。
4. v0.4（待批准）：統計所有卡片、書本、回收站引用，建立孤兒圖片候選清單；禁止自動刪除。
5. v0.5（待批准）：書本與場景共用、容量儀表板；遠端同步另案審查。

## 測試、風險及人工作業閘門
Node 單元測試檢查參數、拒絕非圖片、無 IndexedDB／Crypto 時封閉、原頁不載入模組。
Playwright browser 測試採真正 IndexedDB 驗證單檔去重、資料重開保存、blob/metadata、容量、舊 localStorage 不受影響。
手機 WebView 實測＝NOT_TESTED；完整圖片備份還原＝NOT_IMPLEMENTED。
IndexedDB 不可用、容量不足、交易失敗時回傳固定錯誤碼；不得清除或覆寫既有圖片。清除瀏覽器網站／APP 資料可能導致本機素材遺失。
凡修改卡片 schema、資料搬遷、永久刪除、雲端同步或正式發布均須 OWNER 再核准。
回退方式：不引入模組至主頁即可零行為變更；不得清除資料庫原資料。
