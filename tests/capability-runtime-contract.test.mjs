import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';

const root = new URL('../android-native/app/src/main/java/com/amin/pocketgba/', import.meta.url);
const contract = readFileSync(new URL('CapabilityGraphContract.java', root), 'utf8');
const projector = readFileSync(new URL('CapabilityInventoryProjector.java', root), 'utf8');
const provider = readFileSync(new URL('UnifiedGraphProvider.java', root), 'utf8');
const canvas = readFileSync(new URL('../android-native/app/src/main/assets/amin-wiki-graph/index.html', import.meta.url), 'utf8');

test('Step 11 reuses the shared kernel and existing Unified Graph projection', () => {
  assert.match(contract, /SharedGraphSyncKernel\.BATCH_FORMAT/);
  assert.match(contract, /put\("graph_scope", "work"\)/);
  assert.match(provider, /CapabilityInventoryStore/);
  assert.match(provider, /CapabilityInventoryProjector\.append/);
  assert.match(provider, /amin-dynamic-canvas-only/);
  assert.doesNotMatch(contract + projector, /createElement.*canvas|new Canvas|<canvas/);
});

test('Step 11 first slice cannot execute or raise autonomy', () => {
  assert.match(contract, /put\("autonomy_level", "none"\)/);
  assert.match(contract, /put\("execution_enabled", false\)/);
  assert.match(projector, /put\("enabled", false\)/);
  assert.doesNotMatch(contract + projector, /Runtime\.getRuntime|ProcessBuilder|workflow_dispatch|mergePullRequest|createRelease/);
});

test('Capability certification requires evidence and OWNER approval', () => {
  assert.match(contract, /CERTIFICATION_REQUIRES_APPROVAL_AND_EVIDENCE/);
  assert.match(contract, /certification_evidence/);
  assert.match(contract, /human_approval/);
  assert.match(contract, /review_status/);
  assert.match(contract, /certification_scope/);
  assert.match(contract, /trust_expires_at/);
  assert.match(contract, /CERTIFICATION_SCOPE_REQUIRED/);
  assert.match(contract, /certified_by/);
});

test('Capability governance stays a read-only manager inside the single Canvas', () => {
  assert.match(canvas, /id="capabilityManager"/);
  assert.match(canvas, /CAPABILITY 治理 · 唯讀/);
  assert.match(canvas, /不可執行、不可自動認證、不可提升 autonomy/);
  assert.match(canvas, /capabilityTypeFilter/);
  assert.match(canvas, /capabilityLifecycleFilter/);
  assert.match(canvas, /capabilityCertificationFilter/);
  assert.match(canvas, /<option>COMMAND<\/option>/);
  assert.match(canvas, /來源紀錄：/);
  assert.match(canvas, /effectiveCertification/);
  assert.match(canvas, /unsafeCapabilityCount/);
  assert.doesNotMatch(canvas, /id="executeCapability"|id="approveCapability"|id="certifyCapability"/);
});
