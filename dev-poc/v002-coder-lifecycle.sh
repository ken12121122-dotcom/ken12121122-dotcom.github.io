#!/usr/bin/env bash
set -euo pipefail

CODER_VERSION="2.36.6"
CODER_URL="http://127.0.0.1:3000"
WORKSPACE="fox-v002-ws"
TEMPLATE="fox-v002"
POSTGRES_NAME="fox-v002-postgres"

cleanup() {
  set +e
  if [[ -n "${CODER_BIN:-}" && -n "${CODER_SESSION_TOKEN:-}" ]]; then
    "$CODER_BIN" delete "$WORKSPACE" --yes >/tmp/delete.log 2>&1 || true
  fi
  if [[ -f /tmp/coder-server.pid ]]; then
    kill "$(cat /tmp/coder-server.pid)" 2>/dev/null || true
  fi
  docker rm -f "$POSTGRES_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

mkdir -p /tmp/fox-v002-evidence /tmp/coder-bin

echo "[V002] start PostgreSQL"
docker run -d --name "$POSTGRES_NAME"   -e POSTGRES_USER=postgres   -e POSTGRES_PASSWORD=postgres   -e POSTGRES_DB=coder   -p 5432:5432   postgres:16-alpine >/tmp/fox-v002-evidence/postgres-container.txt

POSTGRES_READY=0
for i in $(seq 1 60); do
  if docker exec "$POSTGRES_NAME" pg_isready -q -U postgres -d coder >/dev/null 2>&1 \
    && [[ "$(docker exec "$POSTGRES_NAME" psql -U postgres -d coder -tAc 'SELECT 1' 2>/dev/null | tr -d '[:space:]')" == "1" ]]; then
    sleep 1
    if docker exec "$POSTGRES_NAME" pg_isready -q -U postgres -d coder >/dev/null 2>&1 \
      && [[ "$(docker exec "$POSTGRES_NAME" psql -U postgres -d coder -tAc 'SELECT 1' 2>/dev/null | tr -d '[:space:]')" == "1" ]]; then
      POSTGRES_READY=1
      break
    fi
  fi
  sleep 1
done

if [[ "$POSTGRES_READY" -ne 1 ]]; then
  docker logs "$POSTGRES_NAME" | tee /tmp/fox-v002-evidence/postgres.log
  exit 1
fi

docker exec "$POSTGRES_NAME" pg_isready -U postgres -d coder
docker exec "$POSTGRES_NAME" psql -U postgres -d coder -tAc 'SELECT 1'

echo "[V002] install Coder stable $CODER_VERSION"
curl -fsSL   -o /tmp/coder.tar.gz   "https://github.com/coder/coder/releases/download/v${CODER_VERSION}/coder_${CODER_VERSION}_linux_amd64.tar.gz"
tar -xzf /tmp/coder.tar.gz -C /tmp/coder-bin
CODER_BIN="$(find /tmp/coder-bin -type f -name coder | head -n 1)"
test -n "$CODER_BIN"
chmod +x "$CODER_BIN"
"$CODER_BIN" version | tee /tmp/fox-v002-evidence/coder-version.txt

echo "[V002] start isolated Coder server"
export CODER_PG_CONNECTION_URL="postgresql://postgres:postgres@127.0.0.1:5432/coder?sslmode=disable"
export CODER_ACCESS_URL="$CODER_URL"
export CODER_HTTP_ADDRESS="127.0.0.1:3000"
"$CODER_BIN" server >/tmp/fox-v002-evidence/coder-server.log 2>&1 &
echo $! >/tmp/coder-server.pid

READY=0
for i in $(seq 1 90); do
  if curl -fsS "$CODER_URL/api/v2/buildinfo" >/tmp/fox-v002-evidence/buildinfo.json; then
    READY=1
    break
  fi
  sleep 2
done
if [[ "$READY" -ne 1 ]]; then
  cat /tmp/fox-v002-evidence/coder-server.log
  exit 1
fi

echo "[V002] bootstrap ephemeral admin"
BOOTSTRAP_PASSWORD="$(openssl rand -hex 20)A1!"
export CODER_URL
export CODER_FIRST_USER_USERNAME="foxv002"
export CODER_FIRST_USER_EMAIL="fox-v002@example.test"
export CODER_FIRST_USER_FULL_NAME="FOX V002"
export CODER_FIRST_USER_PASSWORD="$BOOTSTRAP_PASSWORD"
export CODER_FIRST_USER_TRIAL="false"

"$CODER_BIN" login "$CODER_URL"   --first-user-username "$CODER_FIRST_USER_USERNAME"   --first-user-email "$CODER_FIRST_USER_EMAIL"   --first-user-full-name "$CODER_FIRST_USER_FULL_NAME"   --first-user-password "$CODER_FIRST_USER_PASSWORD"   --first-user-trial=false   >/tmp/fox-v002-evidence/login.txt

CODER_SESSION_TOKEN="$("$CODER_BIN" login token)"
export CODER_SESSION_TOKEN
test -n "$CODER_SESSION_TOKEN"

"$CODER_BIN" whoami | tee /tmp/fox-v002-evidence/whoami.txt

echo "[V002] reuse official Docker starter template"
rm -rf /tmp/fox-v002-template
"$CODER_BIN" templates init --id docker /tmp/fox-v002-template
grep -n 'module "code-server"' /tmp/fox-v002-template/main.tf   | tee /tmp/fox-v002-evidence/template-code-server.txt
"$CODER_BIN" templates push "$TEMPLATE"   --directory /tmp/fox-v002-template   --yes   | tee /tmp/fox-v002-evidence/template-push.txt

echo "[V002] verify rich parameter surface"
"$CODER_BIN" create --help | grep -E -- '--rich-parameter-file|--use-parameter-defaults' \
  | tee /tmp/fox-v002-evidence/create-help-rich-parameter.txt

"$CODER_BIN" templates list --output json \
  | tee /tmp/fox-v002-evidence/templates.json

TEMPLATE_VERSION_ID="$(jq -r --arg t "$TEMPLATE" '.[] | (.Template // .) | select(.name == $t) | .active_version_id' /tmp/fox-v002-evidence/templates.json | head -n 1)"
test -n "$TEMPLATE_VERSION_ID"
test "$TEMPLATE_VERSION_ID" != "null"

curl -fsS \
  "$CODER_URL/api/v2/templateversions/$TEMPLATE_VERSION_ID/rich-parameters" \
  -H "Coder-Session-Token: $CODER_SESSION_TOKEN" \
  | tee /tmp/fox-v002-evidence/rich-parameters.json >/dev/null

jq -e '.[] | select(.name == "jetbrains_ides" and .type == "list(string)")' \
  /tmp/fox-v002-evidence/rich-parameters.json >/dev/null

cat >/tmp/fox-v002-params.yaml <<'YAML'
jetbrains_ides:
  - IU
YAML
cat /tmp/fox-v002-params.yaml | tee /tmp/fox-v002-evidence/rich-parameter-input.yaml

echo "[V002] create workspace with explicit rich parameters"
set +e
timeout 180s "$CODER_BIN" create "$WORKSPACE" \
  --template "$TEMPLATE" \
  --rich-parameter-file /tmp/fox-v002-params.yaml \
  --use-parameter-defaults \
  --yes \
  </dev/null \
  > >(tee /tmp/fox-v002-evidence/create.txt) \
  2> >(tee /tmp/fox-v002-evidence/create-stderr.txt >&2)
CREATE_RC=$?
set -e

if [[ "$CREATE_RC" -eq 124 ]]; then
  echo "workspace create exceeded step-level timeout" >&2
  exit 124
fi
if [[ "$CREATE_RC" -ne 0 ]]; then
  echo "workspace create failed rc=$CREATE_RC" >&2
  exit "$CREATE_RC"
fi

"$CODER_BIN" list --output json   | tee /tmp/fox-v002-evidence/workspaces-running-cli.json

curl -fsS   "$CODER_URL/api/v2/users/me/workspace/$WORKSPACE"   -H "Coder-Session-Token: $CODER_SESSION_TOKEN"   | tee /tmp/fox-v002-evidence/workspace-running.json >/dev/null

WORKSPACE_ID="$(jq -r '.id' /tmp/fox-v002-evidence/workspace-running.json)"
test -n "$WORKSPACE_ID"
test "$WORKSPACE_ID" != "null"

jq '{
  id,
  name,
  status: .latest_build.status,
  transition: .latest_build.transition,
  health,
  apps: [
    .latest_build.resources[]?.agents[]?.apps[]?
    | {slug, display_name, url, sharing_level}
  ]
}' /tmp/fox-v002-evidence/workspace-running.json   | tee /tmp/fox-v002-evidence/workspace-summary-running.json

jq -e '.latest_build.status == "running"'   /tmp/fox-v002-evidence/workspace-running.json
jq -e '[.latest_build.resources[]?.agents[]?] | length > 0'   /tmp/fox-v002-evidence/workspace-running.json
jq -e '[.latest_build.resources[]?.agents[]?.apps[]? | select(.slug == "code-server")] | length > 0'   /tmp/fox-v002-evidence/workspace-running.json

echo "[V002] verify runtime command path"
"$CODER_BIN" ssh "$WORKSPACE" -- 'printf FOX_V002_SSH_OK'   | tee /tmp/fox-v002-evidence/ssh-running.txt
grep -q 'FOX_V002_SSH_OK' /tmp/fox-v002-evidence/ssh-running.txt

echo "[V002] verify watch API"
timeout 6s curl -sS -N   "$CODER_URL/api/v2/workspaces/$WORKSPACE_ID/watch"   -H "Accept: text/event-stream"   -H "Coder-Session-Token: $CODER_SESSION_TOKEN"   >/tmp/fox-v002-evidence/workspace-watch.txt || true
test -s /tmp/fox-v002-evidence/workspace-watch.txt

echo "[V002] stop"
"$CODER_BIN" stop "$WORKSPACE" --yes   | tee /tmp/fox-v002-evidence/stop.txt
curl -fsS   "$CODER_URL/api/v2/users/me/workspace/$WORKSPACE"   -H "Coder-Session-Token: $CODER_SESSION_TOKEN"   >/tmp/fox-v002-evidence/workspace-stopped.json
jq -e '.latest_build.status == "stopped"'   /tmp/fox-v002-evidence/workspace-stopped.json

echo "[V002] restart"
"$CODER_BIN" start "$WORKSPACE" --yes   | tee /tmp/fox-v002-evidence/start.txt
curl -fsS   "$CODER_URL/api/v2/users/me/workspace/$WORKSPACE"   -H "Coder-Session-Token: $CODER_SESSION_TOKEN"   >/tmp/fox-v002-evidence/workspace-restarted.json
jq -e '.latest_build.status == "running"'   /tmp/fox-v002-evidence/workspace-restarted.json

"$CODER_BIN" ssh "$WORKSPACE" -- 'printf FOX_V002_RESTART_OK'   | tee /tmp/fox-v002-evidence/ssh-restarted.txt
grep -q 'FOX_V002_RESTART_OK' /tmp/fox-v002-evidence/ssh-restarted.txt

APP_URL="$(jq -r '[.latest_build.resources[]?.agents[]?.apps[]? | select(.slug == "code-server")][0].url // ""'   /tmp/fox-v002-evidence/workspace-restarted.json)"
test -n "$APP_URL"

jq -nc   --arg workspace_id "$WORKSPACE_ID"   --arg app_url "$APP_URL"   '{
    contract:"WorkspaceProvider/v0",
    provider_id:"coder",
    workspace_id:$workspace_id,
    status:"ok",
    capabilities:{
      "workspace.status":"pass",
      "workspace.start":"pass",
      "workspace.stop":"pass",
      "workspace.open":"pass",
      "runtime.exec":"pass",
      "workspace.watch":"pass",
      "browser_ide_app":"pass"
    },
    app_url:$app_url,
    production_write:false
  }' | tee /tmp/fox-v002-evidence/fox-v002-result.json

echo "[V002] PASS"
