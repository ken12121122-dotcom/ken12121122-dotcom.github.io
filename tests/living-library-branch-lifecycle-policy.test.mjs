import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';

const read=path=>readFile(new URL('../'+path,import.meta.url),'utf8');
const policyPath='docs/governance/NW-BRANCH-LIFECYCLE-001_分支生命週期與分級創作發布_v1.0.md';

test('branch lifecycle procedure is linked in root and Living Library guardrails',async()=>{
 const [root,scoped,registry,policy]=await Promise.all([
  read('AGENTS.md'),read('living-library/AGENTS.md'),
  read('living-library/UI-MANAGER-REGISTRY.md'),read(policyPath)
 ]);
 const name='NW-BRANCH-LIFECYCLE-001_分支生命週期與分級創作發布_v1.0.md';
 assert.ok(root.includes(name));
 assert.ok(scoped.includes(name));
 assert.ok(registry.includes(name));
 assert.match(policy,/# NW-BRANCH-LIFECYCLE-001/);
 for(const label of ['C0','C1','C2','C3','ACTIVE','DRAFT PR','BLOCKED','ABANDONED','20','3']){
  assert.ok(policy.includes(label),'policy missing: '+label);
 }
});

test('risk tiers never bypass existing code, Android, GEN2 or owner approval gates',async()=>{
 const [root,policy,scoped]=await Promise.all([read('AGENTS.md'),read(policyPath),read('living-library/AGENTS.md')]);
 assert.match(root,/功能分支不得直接或間接推送 `main`/);
 assert.match(root,/不得使用永久簽章/);
 assert.match(root,/使用者明確批准/);
 assert.match(policy,/目前需要 PR/);
 assert.match(policy,/C0.*?不需要/s);
 assert.match(policy,/刪除分支、關閉尚未通過的 PR/);
 assert.match(policy,/不建立背景分支清理機器人/);
 assert.match(scoped,/public-catalog\.js/);
 assert.match(scoped,/GEN2/);
});
