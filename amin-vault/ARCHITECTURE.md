# Amin Universal Vault Architecture

## Principle

資料屬於 Vault，不屬於任何單一 App。Obsidian、ChatGPT、Codex、PWA 與 Google Drive 都只是客戶端、轉接器或鏡像層。

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

### 3. Platform Adapters

- PWA
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

## Current State

- Active objects: 5
- Draft objects: 4
- Total objects: 9
- Versions: 10
- Relationships: 6
- Mirror registry entries: 9
- Supabase security warnings: 0
- PWA version: 0.6.2

## Pending

- Android file-level automatic sync
- Vault MCP
- Automatic three-way conflict merge
