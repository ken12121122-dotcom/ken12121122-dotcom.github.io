import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import test from 'node:test';

const source = readFileSync(new URL(
  '../android-native/app/src/main/assets/amin-wiki-graph/index.html', import.meta.url
), 'utf8');

test('GitHub Work uses the existing single dynamic canvas', () => {
  assert.equal((source.match(/<canvas\s+id="graph"/g) || []).length, 1);
  assert.match(source, /id="workBtn"/);
  assert.match(source, /id="workManager"/);
  assert.match(source, /function renderWork\(/);
  assert.match(source, /function focusWorkNode\(/);
  assert.match(source, /layout:'single-force-canvas'/);
  assert.doesNotMatch(source, /createElement\(['"]canvas/);
});

test('mobile acceptance controls remain read-only and expose required status', () => {
  for (const marker of [
    '立即重新整理', 'Repository', 'Branch', '最近 Pull Request', 'CI / Workflow Run',
    'Failed Job / Step', '最近 Release', 'Artifact', 'API：', 'Partial：', 'Raw logs OFF'
  ]) assert.ok(source.includes(marker), `missing ${marker}`);
  assert.match(source, /AminWiki\.syncGitHubWorkNow\(\)/);
  assert.doesNotMatch(source, /workflow_dispatch|mergePullRequest|createRelease|\/logs/);
});
