# Amin MCP v0.1

Read-only MCP server for the Amin Android and Web Runtime project.

## Tools

- `get_project_status`
- `get_latest_version`
- `get_recent_changes`
- `create_test_plan`

No tool can modify GitHub, publish APKs, switch release manifests, access a phone, or delete data.

## Local verification

```bash
cd amin-mcp
npm install
npm run check
npm start
```

Health check:

```bash
curl http://127.0.0.1:3001/health
```

Expected response:

```json
{"ok":true,"service":"amin-mcp","version":"0.1.0","mode":"read-only"}
```

MCP endpoint:

```text
http://127.0.0.1:3001/mcp
```

Use MCP Inspector or another Streamable HTTP MCP client to initialize the endpoint, list tools, and call each tool.

## Environment variables

```text
PORT=3001
AMIN_GITHUB_OWNER=ken12121122-dotcom
AMIN_GITHUB_REPO=ken12121122-dotcom.github.io
AMIN_GITHUB_BRANCH=main
GITHUB_TOKEN=
ALLOWED_ORIGINS=https://chatgpt.com,https://chat.openai.com
```

`GITHUB_TOKEN` is optional while the repository is public. Add a read-only token later to avoid anonymous GitHub API rate limits. Never place the token in source control.

## Acceptance checklist

1. `npm run check` passes.
2. `/health` returns HTTP 200 and `mode: read-only`.
3. MCP Inspector lists exactly four tools.
4. `get_latest_version` matches both release manifest files in GitHub.
5. Invalid inputs are rejected by the tool schema.
6. A request with an unapproved `Origin` receives HTTP 403.
7. No tool name or handler performs a write operation.

## Deployment

Deploy this folder as a Node.js service. The public MCP URL must use HTTPS and end in `/mcp`. Configure environment variables in the hosting platform, not in the repository.

This version deliberately has no widget UI, OAuth, device relay, APK updater, Logcat access, or real-device control. Those are later stages after the read-only bridge is verified.
