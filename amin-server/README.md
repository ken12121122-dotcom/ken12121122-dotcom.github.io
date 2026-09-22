# Amin Node Server

PC-side Node.js core that forwards structured commands to the Android `Amin Control API`.

## Requirements

- Node.js 18+
- Phone and PC reachable over the same LAN/hotspot network
- Android `Amin Control API` enabled with LAN mode and a valid token

## Run on Windows PowerShell

Do not commit your real token. Set it only in the local terminal session:

```powershell
$env:AMIN_PHONE_URL = "http://10.123.62.92:8765"
$env:AMIN_PHONE_TOKEN = "YOUR_CURRENT_TOKEN"
npm start
```

The PC server listens on `http://127.0.0.1:3000` by default.

## First endpoints

- `GET /health` — Node server health
- `GET /android/status` — forwarded Android API status
- `GET /android/actions` — list available Android actions
- `POST /android/actions/:action` — execute one Android action

Example:

```powershell
Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:3000/android/actions/SYSTEM_HOME" -ContentType "application/json" -Body '{"parameters":{}}'
```

## Boundary

Android retains device-native capabilities. Node.js becomes the PC-side orchestration layer. AI, n8n, databases, and other tools should be added above this bridge rather than duplicating Android device logic.
