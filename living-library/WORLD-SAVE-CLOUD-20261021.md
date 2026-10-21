# NW-CLOUD-SAVE-001｜念動圖書館 GitHub 雲端優先存檔（候選 v0.1）

review_status: generated
date: 2026-10-21
owner_choice: B（GitHub 私有雲端優先、手機保留即時存檔與離線副本）
change_class: C2
source: living-library/live/object-craft.js; living-library/live/object-backup.js; living-library/live/object-outbox.js; living-library/live/system-controls.html; living-library/live/index.html; living-library/live/card-github-inbox.js

## 使用者操作入口與身分
- **《系統之書》→既有第 12 號《連線與版本》功能卡**（`public.system.controls`、`./system-controls.html`），內新增「世界存檔｜私人 GitHub」。
- 不新增第 16 本書、不覆蓋《卡片之書》內容、不把全域存檔責任放到《萬物圖鑑》右頁 GBA 讀卡格。
- 新增「☁」連線、「↻」立即同步、「×」斷線、「ⓘ」唯讀盤點；圖示按鈕具 aria-label。
- GitHub fine-grained PAT 只存在執行記憶體，輸入欄位送出後立即清空，pagehide/斷線清除；**不要將權杖傳給 AI、寫入公開程式、localStorage、URL、日誌或 GitHub**。

## 嚴格目的地與資料邊界
- GitHub 私人儲存庫：`ken12121122-dotcom/gen2-knowledge`。
- 隔離分支：**既有** `card-inbox`（不碰私有庫 main/kb）。
- 目標檔：`world-saves/v1/player-world.json`；內容為 `nw.object-craft-backup.v1` 完整備份封套，包含 ObjectCraft 原始文字及 SHA-256，故玩家卡片圖片亦可能包含在內。
- 需使用者在手機頁面勾選同意，授權 PRIVATE repo 與 branch 驗證通過才允許首次傳送。
- GitHub 歷史永久保留卡片內容及圖片，請勿視為可直接刪除的暫存資料。
- 目前僅涵蓋 `living-library-player-objects-v1` 卡片／書本與其保存在同一資料中的契約內容。**不保證**涵蓋場景位置偏好、手機的其他 IndexedDB、GBA ROM/存檔、GEN2 私有 KB 及所有動畫即時記憶體。
- GitHub Contents API 單筆快照封套上限設為 600,000 UTF-8 bytes；超過時停止，不截斷、不假裝成功。

## 寫入與安全規則
1. 先以既有 `object-backup.js` 產生帶 SHA-256 的本機唯讀備份。若格式無效、來源變更或待清理刪卡交易存在，阻擋上傳。
2. 每次連線驗證 repository `private===true` 及指定 ref；讀取遠端檔案，禁止靜默寫入任何公開倉庫。
3. 遠端與本機資料相同時，先驗證雲端 SHA-256，再記錄不含金鑰的本機 baseline（Git blob SHA、來源 hash）。
4. 遠端不同且沒有本機已知 blob SHA，或他人已改變遠端 blob，回報 `CLOUD_REMOTE_CONFLICT`，絕不覆蓋。
5. 寫入使用 GitHub Contents SHA 條件更新；讀回 blob SHA 驗證成功才回報 synced。只更新私人 GitHub 資料檔，不會寫回/清空手機存檔。
6. 在本次遊戲網頁存活、金鑰授權有效時，每 60 秒比對一次本機資料；變動才會觸發遠端更新。WebView 關閉或 reload **需要重新授權**，目前不提供真正無人值守的長期認證。
7. 禁止遠端自動覆蓋本機，跨手機還原及雙向合併尚未開放；未通過此 Gate 前不宣稱 B 最終全面完成。
8. 原有單卡 `card-github-inbox.js`、舊金鑰對話框及卡片／契約執行邏輯不改。

## 驗收 Gate（區分證據）
- 語法與單元測試：`tests/living-library-world-save-sync.test.mjs`（私人儲存庫、首次上傳、等版本、修訂、衝突、權杖不落地、來源保留、待刪卡阻擋）。
- 候選 PR 必須完成適用 CI；不等於 Android WebView 實測。
- 未經 OWNER 手機操作及驗收，**不得稱正式網站已啟用同步，也不得發布 C2 核心程式**。
- 實機先確認原本手機存檔完整；試用 PAT 僅授權指定私人庫 Contents: Read/Write，不要求廣泛 repo 或 org scope。
- 安全回退：回退 PUBLIC repo 的 feature branch/PR（若未合併則直接保留候選），雲端 world-saves 歷史不自動刪除，手機原始資料保持不變。

## 尚未實作的 B 完整目標
- 完整手機資料鍵與 IndexedDB 分層盤點及大型資料拆包。
- 已登入狀態下可持續的後端短期授權（例如 GitHub App/OAuth），取代跨重載貼 PAT 的方式。
- 多裝置讀取、手動差異審查與安全還原，正式上雲後切換權威來源。
- 斷網 outbox 的端到端驗收、WebView 背景同步政策、更多世界模組與手機實測。

## 2026-10-21 擴充觀察同步 v0.2（候選）
- 沿用同一《系統之書》→「連線與版本」PAT/同意畫面與原分支；無額外憑證或新書。
- 在相同私人 `gen2-knowledge` 的 `card-inbox` 寫入第二份隔離檔：
  `world-saves/v1/world-observation.json`；原 `player-world.json` 不變。
- 世界觀察資料：3D 人物遊戲內座標（x/y/z）、視角角度與距離、書本 active/來源與 ID、既有場景物件穩定 ID/錨點，以及四個明確允許的遊戲本機偏好鍵。
- 無法啟動 WebGL 時 `session_scene.ready=false`，保存明確允許的偏好，不偽稱讀取 3D 實體。
- 不枚舉所有 localStorage，不傳送 GEN2 權杖、GBA 儲存、未知欄位、執行動畫或原始網頁 DOM。
- 快照沒有時間戳，同值不重複上傳；資料改變時才提交。遠端 SHA / 雙重驗證、錯誤停止與本地保護沿用第一階段。
- 新狀態 `scene_phase` 與安全同意文案提示「位置／場景／操作偏好」。
- 本階段只有**觀察與雲端備份**，沒有從 GitHub 還原人物位置、重新生成世界、伺服器長期 OAuth、多裝置同時寫入的自動合併。
- 私人倉庫的世界觀察檔僅在使用者於已更新手機網頁重新輸入 PAT、授權並成功寫入後才會出現；PR／CI 模擬不能代表真實手機已同步。
