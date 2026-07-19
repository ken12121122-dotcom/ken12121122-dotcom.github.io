# Amin Pocket GBA

Amin Pocket GBA combines an Android native shell, a GitHub Pages hot-update Runtime, EmulatorJS/mGBA, controller mapping, local ROM/save protection, and an Android accessibility-based universal control overlay.

## Current verified release

- Android APK: `0.9.2-bridge16` / code `112`
- Runtime: `0.9.2-rc19`
- Package: `com.amin.pocketgba`
- Physical test: Samsung SM-A5560, Android 15
- Wired gamepad: native detection, binding, test display, and in-game control verified

## Start here

- AI / developer handoff: [`AGENTS.md`](./AGENTS.md)
- Human-readable architecture: [`amin-vault/ARCHITECTURE.md`](./amin-vault/ARCHITECTURE.md)
- Machine-readable architecture: [`amin-vault/architecture.json`](./amin-vault/architecture.json)
- Runtime release truth: [`amin-vault/runtime-manifest.json`](./amin-vault/runtime-manifest.json)
- APK release truth: [`amin-vault/native-release-manifest.json`](./amin-vault/native-release-manifest.json)

## Important input rule

Android WebView may report no devices through the Web Gamepad API. The project therefore treats the Android native `KeyEvent` / `MotionEvent` bridge as the authoritative gamepad input path.

ROM and BIOS files are not distributed by this repository.
