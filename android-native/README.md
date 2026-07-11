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

## Build

The GitHub Actions workflow builds a debug APK using:

- Android Gradle Plugin 9.2.0
- Gradle 9.4.1
- JDK 17
- compileSdk 37
- targetSdk 35
- minSdk 26

Local command when Gradle is installed:

```bash
gradle -p android-native :app:assembleDebug
```

Output:

```text
android-native/app/build/outputs/apk/debug/app-debug.apk
```

## Boundaries

- The first launch needs network access to load the web shell and EmulatorJS core.
- Game ROMs and saves remain in this app's WebView storage.
- Uninstalling the app or clearing its storage can erase ROMs and saves.
- The current CI artifact is debug-signed. A production release needs a private signing key configured as GitHub Actions secrets.
