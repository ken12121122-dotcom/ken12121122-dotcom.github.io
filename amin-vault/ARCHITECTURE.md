# Amin Universal Vault Architecture

## Principle

資料屬於 Vault，不屬於任何單一 App。Obsidian、ChatGPT、Codex、PWA、GBA Center 與 Google Drive 都只是客戶端、轉接器或鏡像層。

## Interface Shell

The canonical PWA uses a Three.js desktop and navigation shell with a GBA-style interaction model.

```text
Touch / Keyboard / Gamepad
→ GBA Action Layer
→ Focus Manager or Pointer Mode
→ Three.js Portal
→ Existing Vault Feature or GBA Center
```

Controls:

- D-pad: move focus or pointer
- A: open or click
- B: return to the console
- L / R: cycle portals
- Start: system menu
- Select: switch between focus and pointer mode

Three.js controls presentation, spatial navigation, and input feedback. It does not replace the canonical database, RLS, versions, or audit flow.

## GBA Emulation Center

The PWA includes a dedicated Game Boy Advance player at `/amin-vault/gba.html`.

```text
User-selected .gba / .zip file
→ Browser IndexedDB ROM Library
→ EmulatorJS stable frontend
→ mGBA WebAssembly core
→ Touch, keyboard, USB or Bluetooth controls
```

Current rules:

- ROM files are selected by the user and stored only in the current browser.
- ROM files are not uploaded to Supabase, Google Drive, GitHub, or the public web layer.
- GBA BIOS is optional and is not distributed by this project.
- EmulatorJS manages local game saves and save states in browser storage.
- Cloud synchronization of GBA saves is not implemented yet.
- The project does not provide or distribute copyrighted ROM or BIOS files.

## Public and Private Separation

The public website reads only curated records from `vault_public_content`. Anonymous access to the private core tables is revoked. Authenticated users see private Workspace data only after ownership binding and RLS checks.

## Layers

### 1. Data Core

Supabase stores workspaces, objects, relationships, versions, change requests, audit logs, clients, sync runs, mirror-file registry, and the curated public-content layer.

### 2. Governance

```text
Capture
→ Change Request
→ Review
→ Publish
→ Version Snapshot
→ Audit Log
```

Protection mechanisms:

- Row Level Security
- Workspace owner binding
- Optimistic version checks
- Version-conflict blocking
- Public/private data separation
- Anonymous core-table privileges revoked

### 3. Platform Adapters

- Three.js GBA PWA
- Local GBA Game Center
- ChatGPT Connected App
- Google Drive Mirror
- Obsidian Android
- Obsidian Windows
- Codex
- Future MCP

### 4. Mirror and Recovery

```text
Supabase
→ Google Drive Markdown Mirror
→ Obsidian
```

The mirror runs hourly and uses stable Google Drive file IDs recorded in `vault_mirror_files`.

## Architecture Contracts

- Web: `/amin-vault/`
- GBA: `/amin-vault/gba.html`
- JSON: `/amin-vault/architecture.json`
- Markdown: `/amin-vault/ARCHITECTURE.md`
- Drive: `03_Knowledge/Universal Vault 核心架構.md`

## Current State

- Active objects: 5
- Draft objects: 4
- Total objects: 9
- Versions: 10
- Relationships: 6
- Mirror registry entries: 10
- Supabase security warnings: 0
- PWA version: 0.8.0
- Three.js shell: active
- GBA game center: active
- Emulator frontend: EmulatorJS stable
- GBA core: mGBA
- ROM library: local IndexedDB

## Pending

- GBA cloud save synchronization
- Android file-level automatic sync
- Vault MCP
- Cross-app Android system control
- Automatic three-way conflict merge
