import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const file=p=>fs.readFileSync(path.join(root,p),'utf8');

test('NW-SCAN-IMPLICIT-SELECT-001 is discoverable before every Living Library change',()=>{
 const rootAgents=file('AGENTS.md'),localAgents=file('living-library/AGENTS.md'),rule=file('living-library/rules/NW-SCAN-IMPLICIT-SELECT-001.md');
 for(const source of [rootAgents,localAgents,rule])assert.ok(source.includes('NW-SCAN-IMPLICIT-SELECT-001'));
 assert.ok(rootAgents.includes('每次修改'));
 assert.ok(localAgents.includes('先讀再改'));
 assert.ok(rule.includes('CORE_P0_RELEASE_BLOCKER'));
});

test('scan-is-selection remains true after adding any new scene object',()=>{
 const eye=file('living-library/live/truth-eye-world.js');
 assert.ok(eye.includes("const scanTargets=type==='scan'?targets():null"));
 assert.ok(eye.includes("selected:scannedTargets().map(o=>o.id)"));
 assert.ok(eye.includes("if(id&&start('scan',e.pointerId,e.clientX,e.clientY,null,id))"));
 assert.ok(!eye.includes('selectedScanIds'));
 assert.ok(!eye.includes('function toggleSelected('));
 assert.ok(eye.includes("countLabel.textContent='掃描 '+objects.length+' 件物件'"));
});
