# 念動圖書館候選驗收

OWNER 已於 2026-10-13 核准整合、改名、圖示與透過現有手機更新管道發布候選版。正式驗收不由此文件推定。

- Android baseline: `d6a9fdbad8a50b2bf6d850d4108e465a180dcabb`，Bridge 105/code 201；由成功發布 run `37568250785` 的 immutable source revision 證實。`release/android` 仍為 Bridge 103；本 PR 同時保留已安裝 Bridge 105 的功能，不能以舊分支覆蓋它。
- 公開 UI 基底：PR #195 `340271be8dacc272830ecddccefd6cdf4166c4e8`，保留 GPT 之書、多圈環繞、實體書架、GEN2 投影與公開入口。
- 新首頁為 APK 內封裝的公開 Living Library；不需先發布網頁 main。3D CDN、公開 iframe 和 GEN2 GitHub 讀取仍需要網路。網頁改動可在下一次候選 APK 或正式網頁工作中更新；沒有永久私有資料庫。
- 舊 `ControlCenterActivity` 成為返回圖書館的相容路由，原版 UI 原始碼保留作回退，不作日常首頁。
- 安卓之書：全域控制、API、鍵盤、關聯圖、外觀、語音球、狐狸、語音指令、Brain、權限、更新、FOX 知識庫、PackageCanvas、GEN2 待核准、狐狸聊天、財務。
- 遊戲之書的 GBA 和安卓之書的 PackageCanvas 沿用同一個公開卡 ID；APK 中從卡片開啟原 Activity，避免用普通 iframe 取代原生 GBA/SAF。
- 沒有新增 JS Bridge、Manifest permission、exported Activity 或任意 Intent 執行權限。只有本機精確 home URL、主框架、使用者點擊可開白名單原生畫面。私有 GEN2 MD/token 不封裝。
- 包名及永久 signer 維持不變。候選 metadata code 202/Bridge 106，必須由現有永久簽章 workflow 發到既有 `native-release-manifest.json`，再驗證 Pages 更新清單；Debug artifact 不能給手機更新。

## 執行證據

公開 UI Node 回歸 85 項 PASS（包含所有安卓卡的點選模擬、網頁不誤啟原生、返回與私有清除）。Manifest permission 與 105 相同、資源 XML parse、diff whitespace PASS。

Android unit/lint/build/emulator：由本 PR CI 驗證，未成功前不得稱 PASS。新增 route policy 單元測試與 LivingLibraryActivity instrumentation；原 PackageCanvas/GEN2/GBA 功能測試保留。

OWNER 手機 WebGL、捲動、多圈歸架、圖示形狀、原生卡片、返回、GEN2 授權、GBA 存檔/手把及原地更新：NOT TESTED。

候選發布授權已存在；真正手機驗收仍需實測結果，不能用預先批准冒充已通過。

## WebView 實測修正

最終新增的實際 WebView 開書／返回測試在 SwiftShader 模擬器出現 keyDispatchingTimedOut；不計為通過。加入軟體 GPU 的 2D 書庫 fallback（硬體 GPU 保留 3D）與 WebView onPause/onResume/關閉清理，並重新執行同一最新版 CI。此段記錄修正意圖，不代表新測試已成功。

已納入 PR195 的空格真空、實際書本數量、前方扇形書架與自由 360° 相機候選；不以舊版裝飾書籍取代現有成果。

## 2026-10-14｜Web-first 目錄與遊戲世界（Bridge 107 候選，未發布）

根因：已簽章的 code202/Bridge106 將 `public-catalog.js` 封裝在 APK；即使設計卡與 Pages 網頁已公開，App 內遊戲之書仍只顯示 GBA／Vault，無法純網頁新增卡片。

本次候選變更：
- 新的線上主入口固定為 `https://ken12121122-dotcom.github.io/living-library/live/index.html`；HTML/JS/書籍目錄以 GitHub Pages 為正本。遠端頁面由 web-only PR #203 上線，已有「遊戲之書 → 知識城堡」卡片。
- Android WebView 優先讀取該 HTTPS 頁；首頁載入錯誤或 HTTP 4xx/5xx 才自動改讀 APK 原本 `https://appassets.androidplatform.net/living-library/index.html`，保留離線 14 本公開書與既有原生入口。
- 不允許任意網站獲得原生導覽能力：僅精確 Pages 主頁或封裝首頁可在**主框架＋真實使用者手勢**下，使用既有 `amin-library://` 白名單跳轉；iframe 與其他頁面不能執行原生導覽。沒有增加 JavascriptInterface、Activity exported 或權限。
- 一代與 GEN2 正本皆不變；公開網頁不可冒充正式 Skill。授權資訊仍須使用者明確輸入；WebView origin 由 `appassets` 切到 Pages，**私有授權不自動搬移**。
- 準備提升至候選 `0.11.24-rc1-bridge107`／`versionCode=203`；**只有這一次切換載入來源需要 APK 更新，往後書籍、卡片與一般 3D 網頁場景變更應由 Pages 發布**。新增 Android 原生權限/控制/Bridge 仍需要審核及簽章更新。

驗收門檻：同一個 SHA 的 Android unit/lint/build/emulator/instrumentation acceptance、網路可用時 Web 入口與遊戲卡實際點擊、斷網自動退回、GBA 存檔手把與安卓之書真機原生返回、GEN2 明確授權與斷線清除、永久 signer／原地更新；未驗收不得發布或標示手機已更新。若遠端網頁部署回滾，既有 App 仍能用內建備援，不額外下載 APK。

已建立單元測試：遠端首頁只接受精確 HTTPS host/path/可選 v=數字，拒絕假主機、userInfo、port、encoded path、錯誤 query、iframe／無手勢。另有線上 web regression（PR #203），但不當成 Android 真機驗收。

## 2026-10-14｜OWNER 同意發布合併版本（Bridge 107 / code203）

OWNER 明確要求「發布新版，這次有其他的更動一起更新」，核准把此前 Bridge 106 已有的 3D 書庫／安卓之書／原生 GBA 功能保留，與此版本的 Web-first 頁面、離線備援、《遊戲之書》新卡一起納入**現有永久簽章管道**。未經驗證的舊草稿 PR #182/#137 不納入，避免擴大回歸範圍。

來源候選 PR #202（包含原 PR #198 的已簽 Bridge106 代碼）CI run 38480379217：validate = success，emulator-acceptance = success。線上圖書館 PR #203/#204 已部署並通過 web regression。Android 使用者當前更新清單仍為 Bridge106，故只有在**本次 GitHub Android 正式工作流驗證永久 signer、更新 manifest、Pages 完成部署**後，才算新 APK 已發布。

本版 metadata channel=release，versionCode=203 / 0.11.24-rc1-bridge107。發布不代表 OWNER 實際裝置全項驗收通過；遠端載入、斷線退回、GBA 遊戲存檔/手把、原生入口與同證書原地升級需要驗收，失敗時不得宣稱已成功。簽章證書指紋與既有通道一致。

**後續正常網頁新增與調整仍透過 Pages**，只有新 Android 權限、原生能力或修正原生 WebView 啟動機制才需重新產生 APK。

## 2026-10-15｜圖書館前景語音通道更新

OWNER 在本次對話明確要求「不用候選，直接幫我更新」，授權 APP 的語音連接更新。網頁整合仍保留獨立分支供驗收。

Bridge108 / code204 / 0.11.24-rc2：使用 WebViewCompat 的精確 origin + 主框架 + 前景 + 首頁 path 檢查，只提供 start/stop 與文字結果，不執行任何口令。命令映射在網頁，以後追加 Book 以外的單字不用逐次改 APK。permission、session、背景／導航停止均由原生處理；不新增權限，不搬移 GEN2 或存檔。

PR #236 第一輪 unit/lint/build 已成功；API35 emulator 圖書館導覽／返回已成功。增加橋接可見性及非法 session 測試與本次版本資訊後重新跑同 SHA 檢查。真實麥克風及辨識率仍 NOT_TESTED；先前需求已將語音列為待測，不得宣稱實機驗收通過。
