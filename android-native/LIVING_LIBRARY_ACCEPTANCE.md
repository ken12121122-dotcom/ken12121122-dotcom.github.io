# 念動圖書館候選驗收

OWNER 已於 2026-10-13 核准整合、改名、圖示與透過現有手機更新管道發布候選版。正式驗收不由此文件推定。

- Android baseline: `d6a9fdbad8a50b2bf6d850d4108e465a180dcabb`，Bridge 105/code 201；由成功發布 run `37568250785` 的 immutable source revision 證實。`release/android` 仍為 Bridge 103；本 PR 同時保留已安裝 Bridge 105 的功能，不能以舊分支覆蓋它。
- 公開 UI 基底：PR #195 `359910cbd7ab80d2ab70f60b52829b0b92d27cf1`，保留 GPT 之書、多圈環繞、實體書架、GEN2 投影與公開入口。
- 新首頁為 APK 內封裝的公開 Living Library；不需先發布網頁 main。3D CDN、公開 iframe 和 GEN2 GitHub 讀取仍需要網路。網頁改動可在下一次候選 APK 或正式網頁工作中更新；沒有永久私有資料庫。
- 舊 `ControlCenterActivity` 成為返回圖書館的相容路由，原版 UI 原始碼保留作回退，不作日常首頁。
- 安卓之書：全域控制、API、鍵盤、關聯圖、外觀、語音球、狐狸、語音指令、Brain、權限、更新、FOX 知識庫、PackageCanvas、GEN2 待核准、狐狸聊天、財務。
- 遊戲之書的 GBA 和安卓之書的 PackageCanvas 沿用同一個公開卡 ID；APK 中從卡片開啟原 Activity，避免用普通 iframe 取代原生 GBA/SAF。
- 沒有新增 JS Bridge、Manifest permission、exported Activity 或任意 Intent 執行權限。只有本機精確 home URL、主框架、使用者點擊可開白名單原生畫面。私有 GEN2 MD/token 不封裝。
- 包名及永久 signer 維持不變。候選 metadata code 202/Bridge 106，必須由現有永久簽章 workflow 發到既有 `native-release-manifest.json`，再驗證 Pages 更新清單；Debug artifact 不能給手機更新。

## 執行證據

公開 UI Node 回歸 84 項 PASS（包含所有安卓卡的點選模擬、網頁不誤啟原生、返回與私有清除）。Manifest permission 與 105 相同、資源 XML parse、diff whitespace PASS。

Android unit/lint/build/emulator：由本 PR CI 驗證，未成功前不得稱 PASS。新增 route policy 單元測試與 LivingLibraryActivity instrumentation；原 PackageCanvas/GEN2/GBA 功能測試保留。

OWNER 手機 WebGL、捲動、多圈歸架、圖示形狀、原生卡片、返回、GEN2 授權、GBA 存檔/手把及原地更新：NOT TESTED。

候選發布授權已存在；真正手機驗收仍需實測結果，不能用預先批准冒充已通過。

## WebView 實測修正

最終新增的實際 WebView 開書／返回測試在 SwiftShader 模擬器出現 keyDispatchingTimedOut；不計為通過。加入軟體 GPU 的 2D 書庫 fallback（硬體 GPU 保留 3D）與 WebView onPause/onResume/關閉清理，並重新執行同一最新版 CI。此段記錄修正意圖，不代表新測試已成功。
