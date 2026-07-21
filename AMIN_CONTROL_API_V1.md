# Amin Control API v1

Amin Control API 將 REST、WebSocket、Broadcast、Explicit Intent、Deep Link、Tasker、MacroDroid、Automate 與語音動作送進同一個 `AminInputGateway`，再由 Validator、`AminActionDispatcher`、Executor 與 EventStore 完成執行與稽核。

## 安全預設

- API 預設關閉。
- 開啟後預設只綁定 `127.0.0.1:8765`。
- LAN 模式必須由使用者主動開啟。
- LAN 模式強制 Bearer Token，並套用 IPv4 CIDR 允許清單。
- 預設允許清單：`192.168.0.0/16,10.0.0.0/8,172.16.0.0/12`。
- 預設速率限制：每個來源 IP 每分鐘 60 次。
- 外部 Android 自動化入口預設關閉，需在設定頁主動啟用。

## 可並存測試版

CI 會另外建立 `com.amin.pocketgba.api1test` 測試套件，顯示名稱為「Amin Control API v1 Test」。它可以和永久簽章的正式 `com.amin.pocketgba` 並存，不需要卸載正式 App，也不會覆蓋正式 App 的 GBA 存檔與設定。

下列 ADB 範例若用可並存測試版，將 package 改成 `com.amin.pocketgba.api1test`；元件 class 仍是 `com.amin.pocketgba.*`。

## 設定頁

從 Amin 控制中心點選「Amin Control API」，或使用：

```bash
adb shell am start -a android.intent.action.VIEW -d 'amin-api://settings' -p com.amin.pocketgba
```

## REST

手機的 localhost 不會直接等於電腦的 localhost。從電腦測試前先建立 ADB 轉送：

```bash
adb forward tcp:8765 tcp:8765
```

### 狀態

```bash
curl http://127.0.0.1:8765/v1/status
```

### 動作列表

```bash
curl http://127.0.0.1:8765/v1/actions
```

### 執行動作

```bash
curl -X POST \
  -H 'Content-Type: application/json' \
  -d '{}' \
  http://127.0.0.1:8765/v1/actions/SYSTEM_HOME
```

帶參數：

```bash
curl -X POST \
  -H 'Content-Type: application/json' \
  -d '{"parameters":{"mode":"scroll"},"requestId":"demo-001"}' \
  http://127.0.0.1:8765/v1/actions/CONTROL_MODE_SET
```

LAN 模式：

```bash
curl -H 'Authorization: Bearer YOUR_TOKEN' http://PHONE_IP:8765/v1/status
```

### 稽核事件

```bash
curl 'http://127.0.0.1:8765/v1/events?limit=50'
```

## 雙向 WebSocket

連線：

```text
ws://127.0.0.1:8765/v1/events
```

連線後會收到後續事件推送，也可傳送：

```json
{"action":"SYSTEM_HOME","parameters":{},"requestId":"ws-001"}
```

LAN 模式可用 `Authorization: Bearer YOUR_TOKEN` 或 `X-Amin-Token` 握手標頭。

## Broadcast Intent

```bash
adb shell am broadcast \
  -a com.amin.pocketgba.ACTION_EXECUTE \
  -n com.amin.pocketgba/.AminAutomationReceiver \
  --es amin_action SYSTEM_HOME \
  --es amin_request_id adb-broadcast-001
```

帶 JSON 參數：

```bash
adb shell am broadcast \
  -a com.amin.pocketgba.ACTION_EXECUTE \
  -n com.amin.pocketgba/.AminAutomationReceiver \
  --es amin_action CONTROL_MODE_SET \
  --es amin_parameters '{"mode":"scroll"}'
```

Ordered broadcast 的 `resultData` 與 `amin_result_json` extras 會回傳 `ExecutionResult`。也可傳入 `amin_reply_package`，App 會另外送出 `com.amin.pocketgba.ACTION_RESULT`。

可並存測試版的 explicit component 寫法：

```bash
adb shell am broadcast \
  -a com.amin.pocketgba.ACTION_EXECUTE \
  -n com.amin.pocketgba.api1test/com.amin.pocketgba.AminAutomationReceiver \
  --es amin_action SYSTEM_HOME
```

## Explicit Intent

```bash
adb shell am start \
  -n com.amin.pocketgba/.AminAutomationActivity \
  -a com.amin.pocketgba.ACTION_EXECUTE \
  --es amin_action SYSTEM_HOME
```

## Deep Link

```bash
adb shell am start \
  -a android.intent.action.VIEW \
  -d 'amin-control://execute?action=SYSTEM_HOME' \
  -p com.amin.pocketgba
```

帶參數時需 URL encode `parameters` JSON。

## Tasker

1. 建立 Task → Send Intent。
2. Action：`com.amin.pocketgba.ACTION_EXECUTE`。
3. Target：Broadcast Receiver。
4. Package：`com.amin.pocketgba`。
5. Class：`com.amin.pocketgba.AminAutomationReceiver`。
6. Extra：`amin_action:SYSTEM_HOME`。

## MacroDroid

1. 動作 → Send Intent。
2. Target 選 Broadcast。
3. Action 填 `com.amin.pocketgba.ACTION_EXECUTE`。
4. Package/Class 指向 AminAutomationReceiver。
5. String extra：`amin_action` = `SYSTEM_HOME`。

## Automate

1. 使用 Broadcast send block。
2. Action：`com.amin.pocketgba.ACTION_EXECUTE`。
3. Package：`com.amin.pocketgba`。
4. Receiver class：`com.amin.pocketgba.AminAutomationReceiver`。
5. Extras dictionary：`{"amin_action":"SYSTEM_HOME"}`。

## ExecutionResult

```json
{
  "requestId": "uuid",
  "success": true,
  "code": "OK",
  "message": "已回到首頁",
  "action": "SYSTEM_HOME",
  "source": "rest",
  "startedAt": "ISO-8601",
  "finishedAt": "ISO-8601",
  "data": {}
}
```
