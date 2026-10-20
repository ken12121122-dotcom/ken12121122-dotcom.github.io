import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import gate from '../.github/scripts/check-future-branch-pr.cjs';
const read=p=>readFile(new URL('../'+p,import.meta.url),'utf8');
const legacy=JSON.parse(await read('docs/governance/branch-lifecycle-legacy-snapshot-20261020.json'));
const repo='ken12121122-dotcom/ken12121122-dotcom.github.io';
const sha='c03a7d29bcaa1f848f9c4f5669633e8d52915af8';
const body=[
 'Task-ID: NW-TEST-001',
 'Change-Class: C2',
 'Base-SHA: '+sha,
 'Reuse-Check: Checked GitHub open PRs; no existing task NW-TEST-001',
 'Scope: truth-eye scanner only',
 'Preserve: original player save and card identity',
 'Tests: NOT_TESTED; draft implementation awaiting CI',
 'Rollback: revert the new PR merge SHA; player data unchanged',
 'Closeout: DELETE_AFTER_MERGE_OR_REPORT_BLOCKER'
].join('\n');
const pr=(ref,bodyText=body,fullName=repo)=>({head:{ref,repo:{full_name:fullName}},base:{ref:'main'},body:bodyText});
test('frozen legacy snapshot is stable and excludes this new governance branch',()=>{
 assert.equal(legacy.captured_branch_count,261);
 assert.equal(legacy.branches.length,261);
 assert.equal(new Set(legacy.branches).size,261);
 assert.ok(legacy.branches.includes('candidate/truth-eye-longpress-selection-20261020'));
 assert.ok(legacy.branches.includes('release/android'));
 assert.ok(!legacy.branches.includes('policy/branch-lifecycle-future-only-20261020'));
});
test('old branches are exempt even with incomplete old PR bodies',()=>{
 for(const ref of ['release/android','candidate/truth-eye-longpress-selection-20261020']){
  const verdict=gate.evaluate(pr(ref,''),legacy);
  assert.equal(verdict.status,'SKIP');
  assert.equal(verdict.class,'LEGACY');
 }
});
test('new branch with complete, nonplaceholder PR lifecycle evidence passes',()=>{
 const verdict=gate.evaluate(pr('feat/truth-eye-contract-lifecycle-20261020'),legacy);
 assert.deepEqual(verdict.errors,[]);
 assert.equal(verdict.status,'PASS');
 assert.equal(verdict.class,'NEW_TEMP');
});
test('new branch with missing evidence is blocked, not grandfathered',()=>{
 const verdict=gate.evaluate(pr('feat/another-feature-20261020','Task-ID: NW-002'),legacy);
 assert.equal(verdict.status,'FAIL');
 assert.ok(verdict.errors.includes('missing PR metadata: Closeout'));
});
test('C0 cannot be used to bypass new-branch workflow; branch prefix and base must be valid',()=>{
 const wrong=body.replace('Change-Class: C2','Change-Class: C0').replace(sha,'bad-sha');
 const verdict=gate.evaluate(pr('unnamed',wrong),legacy);
 assert.equal(verdict.status,'FAIL');
 assert.ok(verdict.errors.some(x=>x.includes('C0 must not create a branch')));
 assert.ok(verdict.errors.some(x=>x.includes('40-character')));
 assert.ok(verdict.errors.some(x=>x.includes('prefix')));
});
test('fork branch named like a legacy source is not treated as repository legacy',()=>{
 assert.equal(gate.evaluate(pr('release/android','', 'outside/repo'),legacy).status,'FAIL');
});
test('root and Living Library governance both mandate future-only strict lifecycle',async()=>{
 const [root,scoped,policy]=await Promise.all([
  read('AGENTS.md'),read('living-library/AGENTS.md'),
  read('docs/governance/NW-BRANCH-LIFECYCLE-001_分支生命週期與分級創作發布_v1.0.md')
 ]);
 assert.match(root,/只對新分支加嚴/);
 assert.match(scoped,/261 條既有分支/);
 assert.match(policy,/Closeout: DELETE_AFTER_MERGE_OR_REPORT_BLOCKER/);
 assert.match(policy,/CLEANUP_BLOCKED/);
 assert.match(policy,/不追溯/);
});
