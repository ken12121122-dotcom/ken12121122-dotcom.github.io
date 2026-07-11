# Amin Pocket GBA Android

Native Android shell for the Amin Pocket Vault GBA player.

## Why this app exists

The web Gamepad API detected the generic USB controller as `Vendor 0810 / Product 0001`, but Android Chrome exposed only D-pad buttons 12–15 and four axes. Face buttons, shoulders, Start, and Select never reached JavaScript.

This Android app captures controller input before WebView:

```text
USB / Bluetooth controller
→ Activity.dispatchKeyEvent(KeyEvent)
→ Activity.dispatchGenericMotionEvent(MotionEvent)
→ WebView.evaluateJavascript
→ AMIN_NATIVE_INPUT
→ custom controller profile
→ EmulatorJS gameManager.simulateInput
→ mGBA
```

## Implemented

- Native `KeyEvent` capture for standard and generic controller buttons
- Native `MotionEvent` capture for left stick, right stick, hats, and triggers
- Default mapping for this controller requirement:
  - physical button 2 → GBA A
  - physical button 3 → GBA B
  - physical button 1 and 4 → no function
  - D-pad and left stick → directions
  - right stick → no function
  - Start, Select, L1, R1 → GBA Start, Select, L, R
- Web mapping page can capture native key names and native axes
- Fullscreen landscape mode with hidden system bars
- WebView file picker for `.gba`, `.bin`, and `.zip`
- Persistent WebView DOM storage / IndexedDB
- Save flush when the Android app pauses
- Hardware Back routes through the safe emulator-exit flow
- Remote navigation restricted to the project's HTTPS host
- No ROM or BIOS is bundled

## Current build toolchain

The validated GitHub Actions workflow uses:

- Android Gradle Plugin 8.7.3
- Gradle 8.9
- JDK 17
- compileSdk 35
- targetSdk 35
- minSdk 26
- app version 0.9.0

Local command when Gradle is installed:

```bash
gradle -p android-native :app:assembleDebug
```

Output:

```text
android-native/app/build/outputs/apk/debug/app-debug.apk
```

The CI artifact name is:

```text
Amin-Pocket-GBA-v0.9.0-debug
```

## Validation state

- GitHub PR #1 was used only as a CI validation branch.
- The PR records that GitHub Actions successfully built and uploaded the debug APK.
- The stable Android 15 toolchain changes were also applied directly to `main`.
- The APK shell has been opened on the user's Android device.
- Final physical-controller verification is still pending: the next test must confirm that native `KeyEvent` values for buttons 1–4, Start, Select, L, and R reach the in-app signal lab and then control mGBA.

## Boundaries

- The first launch needs network access to load the web shell and EmulatorJS core.
- Game ROMs and saves remain in this app's WebView storage.
- Uninstalling the app or clearing its storage can erase ROMs and saves.
- The current CI artifact is debug-signed. A production release needs a private signing key configured as GitHub Actions secrets.
- There is no GitHub Releases auto-updater yet.

## Handoff

See [`AMIN_POCKET_GBA_HANDOFF.md`](../AMIN_POCKET_GBA_HANDOFF.md) for the exact continuation point, links, completed work, pending verification, and new-chat startup prompt.
