# Voice Command v1

Status: implementation scaffold
Issue: #11
Branch: `ai/voice-command-v1`

## Product decision

Voice Command v1 uses push-to-talk only. It does not keep the microphone active in the background and does not implement a wake word.

## Entry points

1. Microphone button in `ControlCenterActivity`.
2. Long press on the accessibility wake bubble after the shared action dispatcher is available.

## Pipeline

```text
User gesture
→ Android SpeechRecognizer
→ Traditional Chinese transcript
→ VoiceCommandParser
→ AminAction
→ AminActionDispatcher
→ Android / Accessibility / Runtime adapter
→ visual + haptic result
```

## Action contract

```json
{
  "action": "CONTROL_MODE_SET",
  "parameters": {
    "mode": "cursor"
  },
  "source": "voice",
  "confidence": 0.96,
  "requestId": "uuid",
  "createdAt": "ISO-8601"
}
```

Required fields:

- `action`: stable action identifier.
- `source`: `voice`, `gamepad`, `overlay`, `remote`, or `system`.
- `confidence`: 0 to 1 for interpreted input.
- `requestId`: unique id for deduplication.
- `createdAt`: event creation time.

## Initial commands

| Spoken phrase | Action |
|---|---|
| 開啟控制盤 | `OVERLAY_OPEN` |
| 關閉控制盤 | `OVERLAY_CLOSE` |
| 游標模式 | `CONTROL_MODE_SET(cursor)` |
| 捲動模式 | `CONTROL_MODE_SET(scroll)` |
| 返回 | `SYSTEM_BACK` |
| 回首頁 | `SYSTEM_HOME` |
| 點一下 | `CURSOR_TAP` |
| 長按 | `CURSOR_LONG_PRESS` |
| 往上／下／左／右 | `DIRECTION_*` |
| 開啟遊戲 | `OPEN_GBA` |
| 開啟控制器設定 | `OPEN_CONTROLLER_SETTINGS` |
| 停止聆聽 | `VOICE_STOP` |

Parser rules:

- Normalize spaces, punctuation, full-width characters, and common variants.
- Prefer exact command aliases before fuzzy matching.
- Do not execute below the configured confidence threshold.
- Ambiguous commands return a clarification state rather than an action.

## Security boundary

Voice v1 must not perform:

- APK install or release-channel switching.
- Stable Runtime manifest switching.
- ROM/save deletion or migration.
- Signing-key access.
- Accessibility permission changes.
- New high-privilege expansion.

Potentially destructive actions require explicit on-screen confirmation and are out of scope for v1.

## Android lifecycle requirements

- Request `RECORD_AUDIO` only from a user gesture.
- Create one recognizer instance at a time.
- Stop/cancel recognition when the Activity leaves the foreground.
- Call `destroy()` when the owning component is destroyed.
- Handle permission denial, permanent denial, recognizer unavailable, no match, timeout, network error, and busy states.
- Do not start a microphone foreground service in v1.

## Test matrix

- Permission grant, denial, revoke, and re-grant.
- Rapid repeated microphone taps.
- Background/foreground switching while listening.
- Screen rotation and different screen sizes.
- Offline and weak network conditions.
- Missing recognition service.
- Empty, partial, ambiguous, and repeated transcripts.
- Duplicate action request ids.
- Overlay disabled or AccessibilityService disconnected.
- Long-duration repeated command usage.

## Release rule

This feature changes Java and Android Manifest files and therefore requires a new Bridge APK. The implementation must remain on an AI branch until build, lint, unit tests, emulator tests, and physical-device verification pass. Do not update `native-release-manifest.json`, publish a stable APK, or use the production signing key from this branch.
