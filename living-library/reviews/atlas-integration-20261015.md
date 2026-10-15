# 萬物圖鑑整合開發中檢查點

status: draft
review_status: generated
base_sha: 28c5418908aeda6dce9514a9c15ad9158a229d35
branch: feat/living-atlas-integration-20261015
production_changed: false

## 已對齊的需求

- 人物點選開搖桿與辨識，搖桿存續期間聆聽 Book；Book 反覆切換圖鑑。
- 萬物圖鑑左九格右讀卡區，隨身不可歸架。
- 卡片之書左右九格，外圈、內圈、書架；僅此書提供合書與歸架操作。
- 卡片創造術的原 ID、資料、編輯與回收沿用；拖入空槽轉移，滿格不交換；取消回來源。
- 單書直式／雙書橫式專注，同場景平滑運鏡，保留搖桿。
- 原世界卡片煙霧共用；合書後才消失，召喚先出現合起的書再展開。
- 右側讀卡按鈕開現有內頁閱讀器，關閉返回。
- 未經 OWNER 驗收不得合併 main／發布正式版。

## 目前進度（非可驗收成品）

已新增候選的狀態／模型／語音生命週期／場景控制模組，並初接主場景。
尚未完成：2D 備援頁 atlas-controls.html、互動修正、測試期望更新、完整回歸、圖形／手機驗收、私有預覽。
首輪原有 Living Library Node 測試 125 個中 115 通過、10 失敗。失敗涉及原先固定 14 本書與 5 張遊戲卡的契約；新候選新增卡片之書及第六張入口，仍需逐項審查並更新測試，不得視為全數通過。
新模組 JavaScript syntax check 通過，尚不足以證明實際觸控與渲染可用。

## 原生語音阻塞

查得 origin/release/android 有 LivingLibraryActivity.java、VoiceCommandActivity.java 及 FloatingVoiceController.java。
LivingLibraryActivity 目前明確無 JS bridge，沒有嵌入式 SpeechRecognizer，VoiceCommandActivity 是獨立原生頁面。
現有語音入口不等於圖書館頁面已可在搖桿顯示期間持續接收語音。
網頁模組只在 SpeechRecognition/WebkitSpeechRecognition 存在時啟動，否則清楚回報 unsupported；沒有偽造原生接口。
需要 OWNER 決定是否本輪一併新增原生連接並提供 CI-only 候選 APK。原生工作應從 release/android 另建對應 feature 分支，不把其龐大歷史合回 main。

## 回退與安全

目前修改只在 feature 分支；main、release/android、APK manifest、正式網頁版本號均未變更。
卡片保存沿用 ObjectCraft key，新增 placeCard 原子轉移，不重建卡片身份；卡冊顯示偏好另有版本 key。
未完成的原生、WebView、真實語音與圖形驗證皆 NOT_TESTED。
