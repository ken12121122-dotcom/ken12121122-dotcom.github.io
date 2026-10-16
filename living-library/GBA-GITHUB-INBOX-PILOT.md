# 萬物圖鑑｜GBA A 鍵傳送私人 GitHub 試行 v0.1

review_status: generated
owner_request: 2026-10-16 SELECT 進 AI 模式，A 發送 GitHub，GBA 螢幕顯示成功或失敗
scope: existing right-hand physical GBA only

## 入口與回饋

1. 把已儲存圖鑑卡放入「萬物圖鑑」右側單一卡片讀取格 (local.atlas.compendium slot 9)。
2. 先進入整本圖鑑第一層專注，再點右頁進第二層 GBA 專注；第一層按鍵不會作用。
3. 按 SELECT 進 AI 模式。GBA 螢幕顯示「等待發送卡片／A 發送私人 GitHub」。
4. 按 A **準備發送**。第一次／每次測試跳出 GitHub 私人傳送授權視窗。確認目的地後輸入 user-owned fine-grained PAT 並按 ↑ 才真正發送（單次使用，不存 localStorage、URL、session 或 GitHub）。
5. 3D GBA 螢幕回報待授權、傳送中、上傳成功加 Git commit、或上傳失敗加固定錯誤碼。B、方向、START、L、R 等原有功能均不變。

## 安全限制與唯一目的地

- 目標為 **private** GitHub 儲存庫 `ken12121122-dotcom/gen2-knowledge`，**隔離**分支 `card-inbox`，專用資料夾 `card-inbox/v1/`。
- 已先建立分支；不操作 `main`、`main/kb`、正式 GEN2 檔案、公開 `*.github.io` 或 GitHub Pages。僅在 A 鍵和人手權杖同意後寫入專用 JSON 檔。
- 資料路徑 `card-inbox/v1/local.card.<CARD_ID>.json`（一張卡一檔，再傳送則更新同路徑）。
- 上傳資料包含這張卡的 ID／編號／名稱／說明／類別／所屬位置與 revision、來源識別，以及**既有縮小的 JPEG dataURL 圖片**。不會傳送其他卡片、其他 localStorage 內容、密碼、私人 GEN2 檔案或 GBA 遊戲存檔。
- 對 GET repository 先確認確實 `private: true` 且符合預期儲存庫；對 GET Git ref 確認是 `refs/heads/card-inbox`；GET 原檔得 SHA 後 PUT，再 GET 比對 blob SHA，**驗證通過才顯示成功**。
- 不自動重試、不可悄悄切換公開儲存庫、不可假裝 GitHub 已收到。不同手機 WebView 跨網路與 PAT 權限需實際測試。
- GitHub PAT 必須單獨在 GitHub 帳號建立（fine-grained，Repository access: Only selected repositories → gen2-knowledge；Permissions → Contents: Read and write），**不要傳給 ChatGPT**。ChatGPT GitHub 連接器的登入權限不能直接供手機網頁使用。
- GitHub private repo 的 branch / commit history 會保留上傳圖片，這不是一個有自動刪除的暫存區。不授予其他協作者私人卡片權限。
- 惟有正確使用者明確授權並從手機實際按 A 送出後，ChatGPT 才可能透過連接器在同一私人儲存庫中的指定 inbox 位置讀取。即使 GitHub 上傳成功，也不表示 ChatGPT 已收到聊天內的訊息；需使用者通知檢索。

## 測試與發布閘門

- 本版不自動傳送真實資料；模擬 GitHub REST 的失敗／成功／私有 repo 驗證，在 CI 跑。
- GitHub CI PASS 不等於 Android WebView + 真實 PAT + 真實寫入已通過。
- 提供 GBA 成功或失敗可見回饋，但 Android 實機須依使用者回報驗收。
- 未正式發布前僅為候選程式，不改 Android APK／版本清單。發布仍須 owner 授權。

