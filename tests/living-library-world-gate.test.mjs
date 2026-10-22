import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import {fileURLToPath} from 'node:url';
const here=path.dirname(fileURLToPath(import.meta.url));
const live=path.join(here,'..','living-library/live');
const source=fs.readFileSync(path.join(live,'world-gate.js'),'utf8');
const ctx={TextDecoder};ctx.self=ctx;vm.runInNewContext(source,ctx);
const Gate=ctx.LivingWorldGate;
test('fixed portal is an ordinary 3D asset with stable identity and no permanent-card dependency',()=>{
 assert.equal(Gate.ID,'scene.gate.obsidian.001');
 assert.match(source,/scanObjectId=ID/);
 assert.match(source,/inertUntilScanned=true/);
 assert.doesNotMatch(source,/createBlankCard|permanentRegistry|beginPermanentBuild|localStorage\.setItem/);
});
test('shipped GLB matches six modular gate components, not a preview HTML or image',()=>{
 const data=fs.readFileSync(path.join(live,'assets/gates/NW_OBSIDIAN_GATE_Contour3D_DEMO.glb'));
 const model=Gate.parse(data.buffer.slice(data.byteOffset,data.byteOffset+data.byteLength));
 assert.equal(model.gltf.meshes.length,32);
 assert.equal(model.gltf.nodes.length,33);
 for(const prefix of ['G01','G02','G03','G04','G05','G06'])
  assert.ok(model.gltf.meshes.some(m=>m.name.startsWith(prefix+'__')),'missing '+prefix);
 assert.equal(model.gltf.materials.length,10);
});
test('library attaches gate as scene geometry only, without minting cards',()=>{
 const html=fs.readFileSync(path.join(live,'index.html'),'utf8');
 assert.match(html,/world-gate\.js/);
 assert.match(html,/LivingWorldGate\.attach\(\{THREE,scene\}\)/);
 assert.match(html,/registerScene\(gate\.id,\[gate\.root\]\)/);
 assert.match(html,/truthEye\.applyPacked\(\)/);
 assert.match(html,/inertHit\.distance<bookHit\.distance/);
});
test('gate stays in the playable walking circle instead of only at the unreachable world rim',()=>{
 const movement=requireMovement();
 assert.equal(movement.WALK_RADIUS,10);
 assert.match(source,/position=\{x:0,y:0\.006,z:8\.4\}/);
 const gateCenter=8.4;
 const gateNearFace=gateCenter-0.9*1.5008;
 assert.ok(gateNearFace<movement.WALK_RADIUS,'keeper can reach front face within walking limit');
 assert.ok(gateCenter<movement.WALK_RADIUS,'keeper can reach portal center');
});
function requireMovement(){const movementCode=fs.readFileSync(path.join(live,'avatar-movement.js'),'utf8');const context={};context.self=context;vm.runInNewContext(movementCode,context);return context.LivingAvatarMovement;}

test('the Eye automatically selects every live-scanned target; no second tap is required',()=>{
 const eyeSource=fs.readFileSync(path.join(live,'truth-eye-world.js'),'utf8');
 assert.match(eyeSource,/const scanTargets=type==='scan'\?targets\(\):null/,
  'a hold contracts the scanned snapshot directly');
 assert.match(eyeSource,/selected:scannedTargets\(\)\.map\(o=>o\.id\)/,
  'inspection exposes scanned objects as the implicit selection');
 assert.match(eyeSource,/countLabel\.textContent='掃描 '\+objects\.length\+' 件物件'/,
  'scanner displays discovered count without an extra manual-selection tally');
 assert.doesNotMatch(eyeSource,/selectedScanIds|toggleSelected\(/,
  'do not reintroduce an independent tap selection state');
 assert.match(eyeSource,/if\(id&&start\('scan',e\.pointerId,e\.clientX,e\.clientY,null,id\)\)/,
  'holding any scanned object starts a single group ritual');
});
