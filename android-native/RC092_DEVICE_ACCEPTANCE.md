# Amin Pocket GBA v0.9.2 RC 實機驗收

狀態：自動化驗收完成，實體裝置與正式簽章尚未執行

本清單全部通過前，PR 保持 Draft，`native-release-manifest.json` 保持 `enabled: false`。

符號：

- `[x]` 已由 CI／Android 35 Emulator 驗證
- `[ ]` 必須在實體裝置或正式簽章環境驗證

## A. 安裝與並存

- [ ] v0.9.2 Preview 可在使用者的 Android 15 實體手機安裝。
- [x] v0.9.1 Preview／舊 App 未被覆蓋或刪除。
- [x] 兩個 App 的套件名稱與版本可辨識。
- [x] 兩版 App 均可在 Android 35 Emulator 啟動。

## B. Runtime 更新

- [ ] 實體手機連網開啟後取得 `0.9.2-rc2`。
- [ ] 更新過程顯示進度。
- [ ] 更新後重新啟動仍使用新 Runtime。
- [ ] 更新途中斷網時保留上一個完整 Runtime。
- [ ] 完成一次更新後，離線重新開啟成功。

## C. 手把回歸

- [ ] USB／藍牙實體搖桿可辨識。
- [ ] 十字鍵與左類比方向正常。
- [ ] A、B、Start、Select、L、R 正常。
- [ ] 右搖桿與未映射按鈕不誤觸遊戲。
- [ ] 進入與退出遊戲後焦點仍正常。

## D. 卡匣入口

- [ ] 從實體手機檔案管理器點 `.gba` 可開啟 Preview。
- [ ] 分享 `.zip` 到 Preview 可進入匯入流程。
- [ ] 不支援的檔案被拒絕。
- [ ] 匯入後 ROM 可啟動。
- [ ] App 重新啟動後 ROM 仍存在。

## E. 快速備份與還原

- [ ] 建立至少一個遊戲內存檔。
- [ ] 建立至少一個即時存檔。
- [ ] 修改手把設定。
- [ ] 匯出不含 ROM 的 `.agbbackup`。
- [ ] 備份檔可由 ZIP 工具檢查，包含 `manifest.json`。
- [ ] 在乾淨的 Preview 資料環境匯入備份。
- [ ] 原遊戲重新匯入後，遊戲內存檔可讀。
- [ ] 即時存檔可讀。
- [ ] 手把設定已還原。

## F. 完整備份與還原

- [ ] 匯出包含 ROM 的完整備份。
- [ ] 顯示的備份大小合理。
- [ ] 清空 Preview 資料後匯入完整備份。
- [ ] ROM、遊戲內存檔、即時存檔與設定全部恢復。
- [ ] 備份中不存在的資料庫未被錯誤刪除。

## G. 診斷報告

- [x] 診斷模組、Runtime 資產與控制項通過 CI 靜態驗證。
- [ ] 實體裝置診斷頁顯示 Native／Runtime 版本。
- [ ] 顯示正確網路類型。
- [ ] 顯示 Service Worker 狀態。
- [ ] 顯示 ROM 數量與儲存空間。
- [ ] 匯出 JSON 成功。
- [ ] JSON 不包含 ROM 二進位內容。
- [ ] JSON 不包含密碼、Secret 或完整私人 Token。

## H. 離線救援

- [x] APK 內建救援頁已由 APK 結構驗證確認存在。
- [ ] 清除 Preview 後，在實體裝置完全離線狀態開啟。
- [ ] 重新連線按鈕正常。
- [ ] 離線選擇卡匣後，恢復連線可完成匯入。

## I. Native Update Center

目前正式通道應保持停用：

- [x] `amin-update://check` 可在 Android 35 Emulator 開啟 Native Update Center。
- [x] Native Update Center Activity 通過 Android instrumentation UI 測試。
- [ ] 實體手機顯示正式通道尚未啟用。
- [ ] 實體手機未下載任何 APK。

正式簽章完成後再執行：

- [ ] 只接受 HTTPS 更新清單。
- [ ] SHA-256 不符時拒絕安裝。
- [ ] package ID 不符時拒絕安裝。
- [ ] versionCode 不符時拒絕安裝。
- [ ] signer certificate 不符時拒絕安裝。
- [ ] 全部相符時才開啟 Android 安裝畫面。
- [ ] 覆蓋更新後 ROM 與存檔保留。

## J. 固定簽章與復原

- [ ] 建立永久 keystore。
- [ ] 記錄憑證 SHA-256。
- [ ] 建立兩份不同實體位置的加密離線備份。
- [ ] 設定四個 GitHub Secrets。
- [ ] Signed Release workflow 通過。
- [ ] 在乾淨電腦從備份還原 keystore。
- [ ] 還原後簽出的憑證指紋與原始值相同。

## K. 權限控制中心

- [x] 權限中心 Activity 與 UI 控制項通過 Android instrumentation 測試。
- [x] `amin-permissions://open` 可在 Android 35 Emulator 開啟 Permission Center。
- [x] APK Manifest 只包含核准權限。
- [x] APK Manifest 不包含整機檔案、相機、麥克風、精確定位、聯絡人、電話、簡訊或懸浮窗權限。
- [ ] Android 13 以上實體手機可由通知開關叫出系統授權視窗。
- [ ] 通知拒絕後狀態顯示為未開啟。
- [ ] 通知允許後狀態顯示為已開啟。
- [ ] APK 安裝開關可開啟「允許來自此來源」系統頁。
- [ ] 關閉安裝來源後，權限中心可正確偵測為未開啟。
- [ ] 「啟用建議權限」依序處理通知與 APK 安裝來源。
- [ ] App 完整權限頁可正常開啟。
- [ ] 通知詳細設定可正常開啟。
- [ ] 電池最佳化設定可正常開啟。
- [ ] 返回 App 後所有開關狀態自動刷新。
- [ ] ROM 選檔仍可使用，且系統未要求整機檔案管理權。
- [ ] USB 與藍牙手把仍可正常使用，未多要求位置或附近裝置權限。

## 自動化驗收紀錄

- Build／Lint／APK 結構：GitHub Actions run `29179833120`，全部通過。
- Android 35 Emulator：GitHub Actions run `29179833129`，全部通過。
- 自動化涵蓋：兩版安裝並存、兩版啟動、兩個原生深連結、MainActivity、Permission Center、Native Update Center instrumentation tests。

## 最終放行

- [x] 最終 head 的靜態 CI 全綠。
- [x] Android 35 Emulator 自動驗收全綠。
- [ ] 實體 Android 裝置適用項目全部通過。
- [ ] `.agbbackup` 真實資料搬家至少成功一次。
- [ ] 正式簽章覆蓋更新至少成功一次。
- [ ] 才能將 release manifest 設為 `enabled: true`。
