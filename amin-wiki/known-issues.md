# Known issues & next milestones

來源：[`amin-vault/ARCHITECTURE.md`](../amin-vault/ARCHITECTURE.md)、[`AGENTS.md`](../AGENTS.md)

## 已知限制（Bridge 16 標註，2026-07-19）

- Android WebView 內 Web Gamepad API 可能是空的（原生橋接才是輸入真相來源，見 [`controller-input.md`](./controller-input.md)）。
- Signal Lab 的下載與分享按鈕在實機上曾無反應，複製功能可用。
- 尚未做控制器命名、每裝置 VID/PID/descriptor profile。
- 尚未做多控制器切換。
- 尚未做 IG/FB 短影音自動模式。
- 尚未做 App 內藍牙掃描與配對（目前靠 Android 系統配對）。
- 實體控制器與全域控制盤尚未共用同一個 Action Core。

## 建議下一步（`ARCHITECTURE.md` + `AGENTS.md` 合併）

1. Controller Lab：裝置命名、descriptor 識別、原始按鍵與軸監看。
2. 每控制器獨立 profile，含自動重連與模式選擇。
3. 短影音模式（每次按壓對應一個穩定滑動）。
4. 修復 Signal Lab 原生 JSON 下載與分享。
5. 抽出一個共用 Action Core，讓虛擬按鍵、實體控制器、未來遙控器都走同一條路。

## ⚠️ 待確認：Bridge 23 是否已經動到上面任何一項

`native-release-manifest.json`（見 [`release-process.md`](./release-process.md) 的版本矛盾段落）顯示 Bridge 23 新增了「Amin Control API v1」，包含語音、REST、WebSocket、Broadcast/Explicit Intent、Deep Link、Tasker/MacroDroid/Automate、ADB 控制等入口，統一進 `AminInputGateway`。這聽起來跟上面「共用 Action Core」「Controller Lab」的方向有關，但：

- `ARCHITECTURE.md`／`AGENTS.md` 都還沒更新到 Bridge 23，沒有明確把 Amin Control API v1 跟這些 milestone 對應起來。
- 沒有 raw 來源說明 Bridge 17-23（voice command / floating voice controls / Amin Control API）跟這份「建議下一步」清單的關係，也沒說清單裡的項目哪些已完成。

這是下一次 ingest 新文件時該優先釐清的缺口，不要在沒有 raw 來源佐證的情況下,把 Bridge 23 的功能直接對應成「已完成」某個 milestone。
