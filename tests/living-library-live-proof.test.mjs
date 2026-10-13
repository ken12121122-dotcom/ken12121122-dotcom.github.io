import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
const html=()=>readFile(new URL('../living-library/index.html',import.meta.url),'utf8');

test('latest release contains distinct discoverable version and actual follower logic',async()=>{
 const s=await html();
 assert.ok(s.includes('name="living-library-release" content="2026-10-14.following-orbit.2"'));
 assert.ok(s.includes('p.x+bookOrbitAnchor.x'));
 assert.ok(s.includes('p.z+bookOrbitAnchor.z'));
 assert.ok(s.includes('torus(i*1.9,.013,0,-.24,0,stone2,scene'));
});

test('version badge is opt-in and keeps normal library design untouched',async()=>{
 const s=await html();
 assert.ok(s.includes("get('verify')==='1'"));
 assert.ok(s.includes("proof.id='livingReleaseProof'"));
 assert.ok(s.includes("const proof=document.getElementById('livingReleaseProof')"));
 assert.ok(s.includes("x(bookOrbitAnchor.x)"));
 assert.ok(s.includes("x(keeper.position.x)"));
 assert.ok(s.includes('地面法陣：0.0, 0.0（固定）'));
});
