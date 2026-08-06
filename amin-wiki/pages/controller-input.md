# Controller input

來源：[`amin-vault/ARCHITECTURE.md`](../../amin-vault/ARCHITECTURE.md)、[`AGENTS.md`](../../AGENTS.md)、[`AMIN_POCKET_GBA_HANDOFF.md`](../../AMIN_POCKET_GBA_HANDOFF.md)（歷史，v0.9.0）

## 現況（Bridge 16 / rc19，2026-07-19 標註）

Android WebView 的 `navigator.getGamepads()` 可能回傳空結果，即使 Android 原生層已經正確收到手把訊號。因此**原生橋接是輸入的權威來源**，不是 Web Gamepad API。

```text
USB / 2.4G receiver / 系統已配對藍牙手把
→ MainActivity.dispatchKeyEvent() / dispatchGenericMotionEvent()
→ JSON payload 注入 WebView
→ gba-native-input.js / window.AMIN_NATIVE_INPUT
├─ gba-controller-native-addon.js（原生綁定、裝置狀態、測試按鈕、原生軸值顯示）
└─ gba-controller-runtime.js（載入控制器 profile → 判斷原生鍵/軸綁定 → 呼叫 EmulatorJS gameManager.simulateInput()）
→ mGBA
```

實測收到的訊號：`KEYCODE_BUTTON_1`～`BUTTON_10`；`AXIS_X`、`AXIS_Y`、`AXIS_Z`、`AXIS_RZ`、`AXIS_HAT_X`、`AXIS_HAT_Y`。

預設映射：A=BUTTON_2、B=BUTTON_3、Start=BUTTON_10、Select=BUTTON_9、L=BUTTON_5、R=BUTTON_6，方向可用 DPAD 或 AXIS_X/Y 或 AXIS_HAT_X/Y。Profile 存於 localStorage `amin-gba-controller-profile-v1`。

全域無障礙控制盤（跟上面的模擬器輸入路徑是**獨立**的另一條路）：浮動喚醒球 → 游標模式（D-pad 移動綠色游標、A 點擊/長按、B 返回/Home）或捲動模式（D-pad 送出滑動手勢）。Bridge 16 已含邊緣吸附、2 秒淡出、8/16/32 dp 步進預設、長按 Select 切換模式。隱私邊界：`canPerformGestures=true`、`canRetrieveWindowContent=false`，不讀取其他 App 內容。

## ⚠️ 已過時（不要照做）：v0.9.0 交接檔裡的診斷結論

`AMIN_POCKET_GBA_HANDOFF.md`（2026-07-12，v0.9.0）記錄的是**專案早期**、還在用純網頁 Gamepad API 時的診斷：

- 當時用的手把（Vendor 0810 / Product 0001）在 Chrome 只收到 `button 12-15`（十字鍵）與 `axis 0-3`（搖桿），**收不到實體按鈕 1-4／Start／Select／L／R**。
- 當時的結論是「純網頁 JavaScript 無法映射不存在於瀏覽器中的訊號」，因此才決定加上 Android 原生 `KeyEvent`/`MotionEvent` 橋接。
- 該檔案的「已驗證與未驗證分界表」把「按鈕 2→A」「按鈕 3→B」「Start/Select/L/R」都標成「未最終驗證」。

**這些「未驗證」在後續文件裡已經標記為已驗證**：`ARCHITECTURE.md`／`AGENTS.md`（2026-07-19，Bridge 16）明確寫「Wired controller: native detection, binding, test feedback, and in-game control verified」，且列出完整的按鈕/軸映射為既定行為，不再是待驗證項目。

**閱讀順序建議**：`AMIN_POCKET_GBA_HANDOFF.md` 只拿來理解「為什麼需要原生橋接」這個歷史脈絡，不要拿它的驗證狀態表當現況 —— 現況以 `ARCHITECTURE.md` / `AGENTS.md` 為準。

## 尚未解決（截至 Bridge 16 標註，2026-07-19）

- 控制器命名、每裝置 VID/PID/descriptor profile 未實作。
- 多控制器切換未實作。
- App 內藍牙掃描配對未實作（目前靠 Android 系統配對）。
- 實體控制器與全域控制盤尚未共用同一個 Action Core（`ARCHITECTURE.md` 下一步 milestone #5）。

Bridge 23 的 release notes（見 [`pages/release-process.md`](./release-process.md)）提到新增了 Amin Control API v1（語音、REST、WebSocket、Android 自動化統一入口），可能跟上面「共用 Action Core」的目標有關，但目前沒有 raw 來源明確說這個 milestone 已完成 —— 需要下次 ingest 新文件時確認，不要在這裡先下結論。
