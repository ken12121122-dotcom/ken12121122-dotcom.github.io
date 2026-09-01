import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';

const html = readFileSync(new URL(
  '../android-native/app/src/main/assets/amin-wiki-graph/index.html', import.meta.url
), 'utf8');
const projector = readFileSync(new URL(
  '../android-native/app/src/main/java/com/amin/pocketgba/AgentStatusProjector.java', import.meta.url
), 'utf8');
const contract = readFileSync(new URL(
  '../android-native/app/src/main/java/com/amin/pocketgba/AgentExecutionStatusContract.java', import.meta.url
), 'utf8');

test('Step 12 dashboard stays inside the existing single Dynamic Canvas', () => {
  assert.equal((html.match(/<canvas\s+id="graph"/g) || []).length, 1);
  for (const marker of [
    'id="agentBtn"', 'id="agentManager"', 'id="agentSummary"', 'id="ownerAttentionList"',
    'id="agentList"', 'function renderAgents()', 'AGENT CONTROL · 唯讀'
  ]) assert.ok(html.includes(marker), `missing ${marker}`);
  assert.doesNotMatch(html, /createElement\(['"]canvas/);
});

test('Agent status fails closed and keeps CI outside the self-declared marker', () => {
  assert.match(contract, /AGENT_ID_PROJECTION_MISMATCH|INVALID_AGENT_ID/);
  assert.match(contract, /OWNER_ATTENTION_REASON_REQUIRED/);
  assert.doesNotMatch(contract, /put\("ci_status"/);
  assert.match(projector, /latestRelatedRun/);
  assert.match(projector, /"idle"/);
  assert.match(projector, /"github-work-evidence"/);
});

test('Dashboard exposes evidence navigation without write or autonomy actions', () => {
  for (const marker of ['OWNER ATTENTION', '同步 GitHub Evidence', 'data-agent-node', 'data-work-node']) {
    assert.ok(html.includes(marker), `missing ${marker}`);
  }
  assert.doesNotMatch(html, /assignAgent|dispatchAgent|approveAgent|cancelAgent|mergePullRequest/);
  assert.match(html, /agentUnsafeCount/);
});

test('Verified Agent work exposes a deterministic read-only context receipt', () => {
  for (const marker of [
    'agent-execution-receipt-v1', 'execution_receipt', 'context_node_ids',
    'trusted-github-pr-marker', '印記：', 'Context：'
  ]) assert.ok(projector.includes(marker) || html.includes(marker), `missing ${marker}`);
  assert.match(projector, /The PR marker cannot inject arbitrary context node IDs/);
  assert.doesNotMatch(contract, /context_node_ids/);
});
