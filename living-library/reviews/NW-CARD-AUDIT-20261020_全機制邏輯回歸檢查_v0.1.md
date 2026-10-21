# 《念動世界》卡片機制邏輯回歸檢查｜2026-10-20 v0.1

- **review_status:** generated
- **scope:** GitHub `main` 已提交程式之卡片資料與規則；未直接修改使用者真實存檔。
- **method:** 透過 GitHub connector 讀取原始碼，使用隔離 V8 JavaScript 測試執行環境與合成測試存檔重播既有 Node 回歸案例。
- **scope limitation:** V8 測試非原生 `node --test`、Playwright、真實手機 GPU/觸控、實際 GitHub Pages 部署驗證。不得宣稱已完成全站實機驗收。

## 測試結果
本次抽測 3 組，共 **124 個既有測試案例**：
- 組 A：創卡／空白卡／編輯／格式化／卡冊／GBA／永久銷毀，**50/52** 初次通過；1 項因隔離環境缺少 Node fs 模組未執行，1 項因測試硬編碼舊快取版號未通過；版號斷言改為功能性驗證後已另外通過。
- 組 B：掃描、群組收納、解除契約、初始法陣、回收、長按法術，**55/56** 通過；1 項因未載入 UI HTML 未執行。
- 組 C：永久 BOOK、卡片消散動畫、卡片創造術／選單，**14/16** 通過；2 項因隔離環境未載入額外 3D/UI 檔案未執行。
- 合計 **119 項既有案例在第一輪 V8 重播通過**；另有 1 項舊版快取字串斷言修正後再驗證通過；其餘 4 項因測試環境缺少檔案／Node 介面未執行，不等於產品缺陷。
- 另外以合成卡片存檔重現了三項規則缺口，並在修正後執行前置煙霧檢查；不得與真實使用者驗收混為一談。

## 已重現與修正
| 編號 | 等級 | 問題 | 處理 | 位置 |
| --- | --- | --- | --- | --- |
| QA-CARD-001 | P1 | `ObjectCraft.write` 重建頂層存檔時丟失未來版本／未知欄位。 | 保留原有未識別頂層 metadata；明確清除配方圖片時仍遵守刪除語意。 | `living-library/live/object-craft.js` |
| QA-CARD-002 | P1 | 有永久身分但 `permanentRegistry` 尚未完整遷移的卡片可進一般卡冊。 | `canStoreBoundCard` 增加對其他永久身分標記的保護。 | `object-craft.js` |
| QA-CARD-003 | P1 | 留有 `scanContract` 但缺少有效 `runeBinding` 的孤兒卡，可能被移入卡冊或回收。 | 位置轉移、回收均改為拒絕，須先合法解除或修復契約。 | `object-craft.js` |
| QA-CARD-004 | Test | 舊解除契約測試禁止確認視窗，但玩家目前要求先確認目標及群組再消耗術卡。 | 修正回歸測試，將確認視窗及確認閘門視為正式規則。 | `tests/living-library-rune-unbind-spell.test.mjs` |
| QA-CARD-005 | Test | 銷毀按鈕快取回歸測試綁定硬編碼版號，與其他同時進行的更新衝突。 | 改為驗證版本參數存在和功能頁連結正確，不依賴特定釋出版號。 | `tests/living-library-card-destruction.test.mjs` |

合成回歸案例已保存在 `tests/living-library-card-destruction.test.mjs`，未使用玩家實際卡片。

## 各機制查核
| 機制 | 本輪確認範圍 | 結論 |
| --- | --- | --- |
| 創造空白卡 | 獨立 ID、基本欄位、數量限制、無預設能力 | 單元測試通過 |
| 編輯／圖片／編號 | 內容修改、版本更新、權限限制、ID 保存 | 單元及靜態測試通過；圖片實際手機裁切未驗證 |
| 格式化 | 恢復為 11 項有值標準、保留 ID、有效契約保護 | 程式與相關案例通過；實機未驗證 |
| 卡片回收與復原 | 身分不變、回收保留、復原、非法契約狀態保護 | 單元測試通過；3D 煙霧尚需實機 |
| 永久銷毀 | 輸入確認、刪除本機紀錄、回收站、佇列、版本防競態 | 單元測試通過；外部備份不在刪除範圍 |
| GBA 下載 | 空白卡媒介、系統範本、無權限複製、版本防篡改 | 單元測試通過；3D 實際操作未驗證 |
| 法陣／永久卡 | 原卡保護、合法位置、唯一格位、封印與具現化 | 單元測試通過；3D 操作未驗證 |
| 真實之眼／群組掃描 | 允許名單、重複身分、物件釋放與重掃、孤兒保護 | 單元測試通過；真實 3D 場景未驗證 |
| 解除契約術 | 原卡／術卡原子更新、單次消耗、確認視窗、永久卡禁止解除 | 單元及靜態檢查通過；視覺特效未驗證 |
| 本機備份／待同步 | 核心隔離與版本檢查 | 跨 localStorage、IndexedDB 原子一致性仍待設計 |

## 尚待處理與風險
1. **P1：永久銷毀跨儲存區非原子。** 目前先移除 IndexedDB 中的離線待同步意圖，再刪除 localStorage 卡片；兩次操作間若斷電或來源版本改變，可能發生卡片仍存在但待同步快照已刪的部分完成狀態。需要補償交易／失敗復原規格與瀏覽器測試；不得宣稱端到端原子性。
2. **P2：已匯出的備份、其他裝置及獨立系統範本不受永久銷毀影響。** 目前「完全銷毀」實際範圍為當前網站來源的本機卡片與未送出佇列，不等於跨裝置資料抹除。
3. **P2：實際手機 3D 手勢、場景動畫、讀卡／長按確認／還原／銷毀的瀏覽器端到端測試尚未完成。** 必須使用真實 Playwright 或手機瀏覽器測試。
4. **P2：本機備份 SHA-256 與 IndexedDB 的真實瀏覽器端測試不能由本次隔離 V8 重播替代。** GitHub Actions 上完整 Node／Playwright CI 結果尚未核對。

## 原始碼／版本追蹤
- `living-library/live/object-craft.js`
- `living-library/live/card-creation.html`
- `living-library/live/atlas-runtime.js`
- `living-library/live/scan-bundle.js`
- `living-library/live/contract-policy.js`
- `living-library/live/object-outbox.js`
- `tests/living-library-card-destruction.test.mjs`
- `tests/living-library-rune-unbind-spell.test.mjs`
- `.github/workflows/living-library-ci.yml`

**本報告為候選稽核記錄，非正式全站驗收或玩家存檔保證。**


## 2026-10-21｜P1 銷毀跨儲存區修復（實作更新）

**新增元件**
- `living-library/live/card-destruction.js`：協調永久銷毀、待同步佇列刪除、啟動時補償清理。
- `ObjectCraft.destroyCard(..., { queueCleanup: true })`：在同一次 localStorage `setItem` 中原子化移除原卡、解除外觀複製來源關聯、寫入暫時性 `pendingCardOutboxPurges` 清理指令。
- `ObjectOutbox`：禁止在未完成清理時再次準備同步快照，避免重送已銷毀卡片的舊狀態。
- `card-creation.html`：載入新的協調器，銷毀失敗前後採用不同狀態訊息；已刪卡但離線佇列待補償時不會誤稱「原卡保留」。下次開啟會重播清理。
- 更新遊戲內嵌讀卡頁版本參數，避免舊版快取阻擋新機制。

**崩潰安全性質**
1. IndexedDB 一開始不可開啟：不動原卡。
2. 寫入 localStorage 失敗：原卡和待同步佇列維持原狀。
3. 本機卡片刪除成功後 IndexedDB 刪除失敗／中斷：原卡已刪；原子儲存的清理指令仍在；下一次開啟重試。
4. IndexedDB 清理成功但移除清理指令失敗：重試是冪等操作，允許再次刪除不存在的離線快照。
5. 清理紀錄格式異常或原卡意外重現：停止清理，不擅自刪除其他原卡。
6. 正常結束：刪除後的存檔不留卡片墓碑／清理欄位。

**驗證**
- `tests/living-library-card-destruction.test.mjs` 現增加 3 個合成事故測試；本次 V8 重播 **14/14 通過**。
- 隔離 mock outbox 協調器驗證 6 項（打開失敗、刪除後 IDB 失敗、保留記錄、重播恢復、冪等性、成功無墓碑）均通過。
- 新增 `tests/browser/card-lifecycle.cjs`，透過真實 Chromium 及 IndexedDB 進行建立→編輯→下載範本→原卡銷毀→注入離線清理失敗→重新整理恢復→格式化→群組掃描解除→回收站銷毀之合成端到端測試。
- 已將 browser 測試接入 `tests/browser/package.json` 和 `.github/workflows/living-library-ci.yml`，於 GitHub Actions 執行 **完整 Node + Chromium** 測試；本次尚未取得通過的 Actions 執行報告，不得宣稱真實瀏覽器測試已通過。
- 既有 `atlas-scene.cjs` 與 `truth-eye-manifest.cjs` 涵蓋 Three.js 幾何與視覺邏輯；**安卓實機 WebGL/觸控/煙霧特效仍未驗收**。

**剩餘風險**
- 真實資料不同裝置、離線站點存取權限、手動匯出備份以及未實作遠端同步不會因本機銷毀而被跨裝置刪除。
- 清理指令是處於異常恢復中的暫時 metadata，等待成功補償後才刪除。
- Chromium CI 成功與否以 GitHub Actions 實際結果為準；不得將隔離模擬 PASS 與實機 PASS 混用。
