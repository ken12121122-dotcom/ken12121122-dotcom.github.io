# 圖鑑距離與第二本書調整

request：OWNER 2026-10-15 23:52 要求在系統之書既有參數卡加入圖鑑與卡片參數，回報第二本書未出現、圖鑑太遠與外圈相交；23:54 說明第二本是現成書，不需書本創造術。
base_sha / rollback_ref：f7029620df1bb3a2fb1e9f7582b957e36ca76467
book_id / card_id：system / public.system.controls；cardCollection / local.atlas.collection
surface_path：living-library/live/system-controls.html、index.html、atlas-state.js、atlas-runtime.js
scope_level：L2；shared_book_ids：system（新參數同時影響兩本 atlas 模型）
preserved_features：15 本公開書、原卡片 ID、其他書本軌道、GEN2、原生路由、語音、9+1 / 9+9 卡槽、卡片資料／存檔、雙書專注、煙霧、開合、讀卡與拖曳均保留。
changes：六個 bounded 本機參數與獨立 reset；預設距離 1.95→1.35；書本創造術內的現成卡片之書開啟入口；舊環繞偏好的新書引入遷移，不覆寫已歸架的新版偏好與 atlas save。
tests：Node、實際 Three.js 射線／座標／卡槽比例／平滑相機回歸；遠端瀏覽器需驗證實際 iframe 滑桿、確認、保存、重開與兩組 reset。
deployment_evidence：待本分支 CI／部署；原正式版 f702962 已由 OWNER 23:52 確認效果很好。
owner_device_verification：新增參數 NOT_TESTED；先前整合效果 OWNER PASS；辨識準確度無量測，不等同所有真機能力已驗收。

OWNER 23:58 指定第二本書從《遊戲之書》→「書本創造術」打開；沿用 `public.gaming.book.creation` 既有 reader iframe 的同源來源驗證，`summon-collection` 只接受這張卡。設定卡只放參數，不放新書入口。

## OWNER 2026-10-16 00:09 對齊後的最終範圍

先前「叫出固定卡片之書」不符合創造書本需求，撤回該入口設計；原先成果保留為空白魔法書選項。書本創造術現在選擇 `legacy` 空白書或 `atlas` 雙面九格卡冊，再施放生成獨立 `local.book.*`。共用既有 blank-book-v1 資料封套，`openingMode` 分辨模型；不重設舊存檔，不複製卡片。

每一本創造出的 atlas 書有獨立十八格、viewMode/viewOpen、active 與穩定 ID。runtime 依真實本機書記錄建立模型，支援外圈拿取／近身放開展開、拿起合書、卡槽／跨書拖曳、合上留內圈、歸架與重新展開、平滑專注及返回；舊固定卡冊及其中卡片保留相容。無 WebGL 時使用同一 atlas-controls 卡冊選單和 book query 選對獨立卡冊。

參數調整沿用上一階段六項與原四項；未更新 APK、其他書本或正式 GEN2。正式 main 仍未發布此 PR。

驗證新增：兩種創造型別／共同上限；跨兩本實體卡冊的十八個卡槽、原卡 ID/內容保存、獨立合上／歸架／reload；真實 iframe 型別選擇→創造→開啟對應卡冊→十八格。手機新增效果仍需 OWNER 驗收。
