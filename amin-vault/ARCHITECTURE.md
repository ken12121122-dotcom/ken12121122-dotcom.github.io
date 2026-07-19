# Amin Universal Vault and Pocket GBA Architecture

Last updated: 2026-07-19, Asia/Taipei

## Principle

資料屬於 Vault，不屬於任何單一 App。Android APK、GitHub Pages Runtime、GBA 模擬器、全域控制盤、Obsidian、ChatGPT、Codex、PWA 與 Google Drive 都是客戶端、轉接器或鏡像層。

## Current Verified State

- Android package: `com.amin.pocketgba`
- APK release: `0.9.2-bridge16`, versionCode `112`
- Runtime release: `0.9.2-rc19`
- Android release channel: enabled
- Permanent signer SHA-256: `3b9a3125b2cd19389c284e834c4ff9eb67caeecb647fe41897d923169f4152c7`
- Physical device: Samsung SM-A5560, Android 15
- Wired controller: native detection, binding, test feedback, and in-game control verified
- Universal control overlay: active with cursor and scroll modes

Release truth lives in:

- `native-release-manifest.json`
- `runtime-manifest.json`
- root `AGENTS.md`

## Product Surfaces

The project currently has three visual layers:

1. White Android native control center: canonical app entry and version management.
2. GBA Runtime: ROM library, controller settings, EmulatorJS, and mGBA.
3. Black legacy Pocket OS shell: archived or experimental surface, not the default return target.

Returning from GBA should lead to the white native control center.

## Runtime and Native Split

```text
Android APK
├─ WebView shell and native bridges
├─ file picker and native ROM staging
├─ APK update center
├─ KeyEvent / MotionEvent gamepad bridge
└─ AccessibilityService universal control overlay

GitHub Pages Runtime
├─ ROM library and save protection
├─ controller configuration
├─ EmulatorJS frontend
├─ mGBA WebAssembly core
└─ hot-update assets controlled by runtime-manifest.json
```

Java, Manifest, native Activity, AccessibilityService, or APK-bundled asset changes require a new Bridge APK. JavaScript, HTML, CSS, and controller mapping fixes should normally use the Runtime hot-update channel.

## Gamepad Input Architecture

Android WebView may expose an empty `navigator.getGamepads()` result even when Android receives the controller correctly. Therefore the native bridge is authoritative.

```text
USB / 2.4G receiver / system-paired Bluetooth controller
→ MainActivity.dispatchKeyEvent()
  or MainActivity.dispatchGenericMotionEvent()
→ JSON payload injected into WebView
→ gba-native-input.js / window.AMIN_NATIVE_INPUT
├─ gba-controller-native-addon.js
│  ├─ capture native bindings
│  ├─ show native device status
│  ├─ light the logical test buttons
│  └─ display native axis values
└─ gba-controller-runtime.js
   ├─ load controller profile
   ├─ evaluate native key and axis bindings
   └─ call EmulatorJS gameManager.simulateInput()
→ mGBA
```

Observed physical controller signals:

- Keys: `KEYCODE_BUTTON_1` through `KEYCODE_BUTTON_10`
- Axes: `AXIS_X`, `AXIS_Y`, `AXIS_Z`, `AXIS_RZ`, `AXIS_HAT_X`, `AXIS_HAT_Y`

Default native mappings include:

- A: BUTTON_2
- B: BUTTON_3
- Start: BUTTON_10
- Select: BUTTON_9
- L: BUTTON_5
- R: BUTTON_6
- directions: DPAD, AXIS_X/Y, or AXIS_HAT_X/Y

Controller profiles are stored under localStorage key `amin-gba-controller-profile-v1`.

## Universal Android Control Overlay

The accessibility overlay is independent of the emulator input path.

```text
Wake bubble
→ fixed GBA-style virtual buttons
├─ Cursor mode
│  ├─ D-pad moves a green cursor
│  ├─ A taps or long-presses
│  └─ B performs Back or Home
└─ Scroll mode
   ├─ D-pad sends swipe gestures
   └─ holding a direction repeats scrolling
```

Bridge 16 behavior:

- draggable edge-snapping wake bubble
- fades after two seconds of inactivity
- D-pad, A/B, L/R, Select/Start
- cursor step range 2 to 64 dp
- default step 16 dp
- presets 8, 16, and 32 dp
- continuous movement while holding directions
- long-press Select toggles cursor and scroll modes
- configurable automatic button collapse

Privacy boundary:

- `canPerformGestures=true`
- `canRetrieveWindowContent=false`
- overlay type: `TYPE_ACCESSIBILITY_OVERLAY`
- it does not inspect other applications' text or account data

## GBA Emulation Center

The GBA player lives at `/amin-vault/gba.html`.

```text
User-selected .gba / .bin / .zip
→ IndexedDB ROM library
→ stable ROM identity and save key
→ EmulatorJS 4.2.3
→ mGBA
→ local save protection and native vault adapters
```

Rules:

- ROM files are selected by the user.
- ROM and BIOS files are not distributed by this project.
- ROM binaries are not uploaded to GitHub, Supabase, Google Drive, or the public website.
- GBA BIOS is optional.
- Runtime updates and APK updates are separate channels.

## Vault Layers

### 1. Data Core

Supabase stores workspaces, objects, relationships, versions, change requests, audit logs, clients, sync runs, mirror-file registry, and curated public content.

### 2. Governance

```text
Capture
→ Change Request
→ Review
→ Publish
→ Version Snapshot
→ Audit Log
```

Protection mechanisms include RLS, owner binding, optimistic version checks, conflict blocking, and public/private separation.

### 3. Platform Adapters

- Android native shell
- GBA Runtime
- Accessibility universal control overlay
- Three.js PWA shell
- ChatGPT and Codex
- Google Drive mirror
- Obsidian Android and Windows
- future MCP

### 4. Mirror and Recovery

```text
Supabase
→ Google Drive Markdown Mirror
→ Obsidian
```

## Key Contracts

- AI handoff: `/AGENTS.md`
- Project entry: `/README.md`
- Web: `/amin-vault/`
- GBA: `/amin-vault/gba.html`
- Controller setup: `/amin-vault/gba-controller.html`
- Signal Lab: `/amin-vault/gba-signal-lab.html`
- Architecture JSON: `/amin-vault/architecture.json`
- Architecture Markdown: `/amin-vault/ARCHITECTURE.md`
- APK manifest: `/amin-vault/native-release-manifest.json`
- Runtime manifest: `/amin-vault/runtime-manifest.json`

## Known Limitations

- Web Gamepad API can be empty inside Android WebView.
- Signal Lab download and share controls have failed on the physical phone; copy works.
- Controller naming and per-device VID/PID/descriptor profiles are not implemented.
- Multiple-controller switching is not implemented.
- In-app Bluetooth scanning and pairing are not implemented; Android system pairing is used.
- Automatic IG/Facebook short-video mode is not implemented.
- The physical controller and universal overlay do not yet share one unified action core.

## Next Architecture Milestones

1. Controller Lab with device naming, descriptor identity, raw key display, and axis monitoring.
2. Per-controller profiles with automatic reconnect and mode selection.
3. Short-video mode with one stable swipe per press.
4. Repair Signal Lab native JSON download and share actions.
5. Extract one common action core for virtual buttons, physical controllers, and future remotes.

## Maintenance Rule

After every physical-device verification or formal APK/Runtime release, update `AGENTS.md`, both release manifests, this document, and `architecture.json`. Never claim a release is live without reading the corresponding manifest and verifying the build/signing result.
