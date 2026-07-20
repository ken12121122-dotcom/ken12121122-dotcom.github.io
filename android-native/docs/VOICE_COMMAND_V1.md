# Voice Command v1

Status: implementation complete; automated validation required on every commit; physical-device verification pending
Issue: #11
Branch: `ai/voice-command-v1`

## Product decision

Voice Command v1 uses push-to-talk only. It does not keep the microphone active in the background and does not implement a wake word.

## Implemented entry points

1. A visible microphone card in `ControlCenterActivity`.
2. Long press on the accessibility wake bubble.
3. The `amin-voice://open` deep link for testing and future integrations.

## Pipeline

```text
User press-and-hold gesture
→ Android SpeechRecognizer
→ Traditional Chinese transcript
→ VoiceCommandParser
→ AminAction
→ AminActionDispatcher
→ UniversalControlAccessibilityService / Android Activity adapter
→ visible execution result
```

The microphone starts only after `ACTION_DOWN` from the user and stops when the user releases the button. Leaving the Activity cancels active recognition, and destroying the Activity destroys the recognizer instance.

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
- `requestId`: unique id for tracing and future deduplication.
- `createdAt`: event creation time.

## Implemented commands

| Spoken phrase | Action | Adapter |
|---|---|---|
| 開啟／打開／顯示控制盤 | `OVERLAY_OPEN` | Accessibility overlay |
| 關閉／收起／隱藏控制盤 | `OVERLAY_CLOSE` | Accessibility overlay |
| 游標模式 | `CONTROL_MODE_SET(cursor)` | Shared control mode |
| 捲動／滾動模式 | `CONTROL_MODE_SET(scroll)` | Shared control mode |
| 返回／上一頁 | `SYSTEM_BACK` | Accessibility global action |
| 回首頁／回桌面 | `SYSTEM_HOME` | Accessibility global action with Android home fallback |
| 點一下／點擊／按一下 | `CURSOR_TAP` | Shared cursor gesture |
| 長按／按住 | `CURSOR_LONG_PRESS` | Shared cursor gesture |
| 往上／下／左／右 | `DIRECTION_*` | Shared cursor or scroll action |
| 開啟遊戲／遊戲庫 | `OPEN_GBA` | Native Activity |
| 開啟控制器設定 | `OPEN_CONTROLLER_SETTINGS` | Controller page |
| 停止聆聽／停止語音 | `VOICE_STOP` | Voice session |

## Parser rules

- Normalize spaces, punctuation, full-width characters, and common variants.
- Prefer exact aliases before natural-speech containment matching.
- Aliases that map to the same action share one command specification and are not treated as ambiguous.
- Recognition confidence below `0.45` does not execute an action.
- Recognizers that do not provide a confidence score use the parser's bounded fallback confidence.
- Commands that genuinely map to multiple different actions return a clarification state.

## Security boundary

Voice v1 must not perform:

- APK install or release-channel switching.
- Stable Runtime manifest switching.
- ROM/save deletion or migration.
- Signing-key access.
- Accessibility permission changes.
- Background continuous listening or wake-word activation.
- New high-privilege expansion.

Potentially destructive actions require explicit on-screen confirmation and are out of scope for v1.

## Automated validation

The branch must pass all of the following for the same commit:

- `VoiceCommandParserTest`, including normalization, aliases, confidence gating, movement, tap, and home actions.
- `AminActionDispatcherTest`, including shared-action routing and AccessibilityService-disconnected failure behavior.
- Android unit tests, build, and lint.
- APK identity, permission, component, asset, and signature inspection.
- `ControlCenterActivityTest`, including the visible voice card and Activity routing.
- `VoiceCommandActivityTest`, including the push-to-talk surface and no background-listening claim.
- Android 35 emulator installation, launcher, GBA, permission, update, and voice deep links.
- Instrumentation acceptance tests.
- Existing JavaScript Runtime and save-contract tests.

## Required physical-device verification

Automated tests cannot prove microphone, OEM recognizer, or Accessibility gesture behavior on the target phone. Before release, verify on the Samsung SM-A5560 / Android 15 device:

1. Grant, deny, revoke, and re-grant `RECORD_AUDIO`.
2. Press and hold, speak, release, and confirm recognition starts and stops correctly.
3. Confirm no listening indicator remains after leaving the Activity.
4. Long press the floating wake bubble and confirm the voice screen opens without also toggling the control panel.
5. Execute overlay open/close, cursor/scroll mode, four directions, tap, long press, back, and home.
6. Repeat rapid commands and background/foreground transitions.
7. Test offline, weak network, recognizer unavailable, no-match, timeout, and busy states.
8. Confirm GBA, save protection, gamepad control, and native update behavior have not regressed.

## Release rule

This feature changes Java, Android Manifest, and AccessibilityService behavior and therefore requires a new Bridge APK. It must remain a Draft PR and produce CI-only artifacts until automated validation and the physical-device checklist pass and the repository owner explicitly approves a separate release change.

Do not update `native-release-manifest.json`, publish a stable or prerelease APK, use the production signing key, or let a feature workflow write to `main` from this branch.
