import test from 'node:test';
import assert from 'node:assert/strict';
import {convertSurfaceLabWorld} from '../living-library/experiments/nw-surface-bridge/surface-world-adapter.mjs';
const pt=(x,y,z)=>({x,y,z});
const uv=(u,v)=>({u,v});
const rect=[uv(0,0),uv(100,0),uv(100,100),uv(0,100)];
const base=()=>({id:'FACE001',kind:'base',a:pt(0,0,0),b:pt(100,0,0),c:pt(0,0,100),points:rect,thickness:5});
const wall=()=>({id:'FACE002',kind:'wall',a:pt(0,0,0),b:pt(100,0,0),c:pt(0,100,0),points:rect,thickness:2});
const scene=(surfaces)=>({version:4,unit:'cm',grid:.5,next:100,surfaces,anchors:[]});

test('Surface Lab cm -> Three.js preview meters, Y-up, preserves source world',()=>{
 const original=scene([base()]);const copy=JSON.stringify(original);
 const result=convertSurfaceLabWorld(original);
 assert.equal(JSON.stringify(original),copy);
 assert.equal(result.objects.length,1);
 assert.equal(result.objects[0].polygon[1].x,1);
 assert.equal(result.objects[0].depth,.05);
 assert.equal(result.objects[0].basis.n.y,-1);
 assert.equal(result.objects[0].nodeId,'surface:FACE001');
 assert.equal(result.objects[0].cardContractStatus,'NOT_APPLICABLE');
});
test('wall extrudes along +Z',()=>{
 const obj=convertSurfaceLabWorld(scene([wall()])).objects[0];
 assert.equal(obj.basis.n.z,1);
 assert.equal(obj.polygon[2].y,1);
});
test('supports child extrusion attached to parent front face',()=>{
 const child={id:'SOLID003',kind:'extrude',a:pt(0,0,0),b:pt(100,0,0),c:pt(0,100,0),points:rect,thickness:30,attachment:{parentId:'FACE002',panelKey:'front',vertexCount:4}};
 const result=convertSurfaceLabWorld(scene([wall(),child]));
 assert.equal(result.objects.length,2);
 const obj=result.objects.find(x=>x.sourceId==='SOLID003');
 assert.equal(obj.basis.n.z,1);
 assert.equal(obj.depth,.3);
});
test('does not claim folded surface support before validated converter exists',()=>{
 const folded={...wall(),fold:{active:true,path:[{u:0,d:0},{u:100,d:0}]}};
 const result=convertSurfaceLabWorld(scene([folded]));
 assert.equal(result.objects.length,0);
 assert.equal(result.gaps[0].reason,'FOLD_GEOMETRY_PENDING');
});
test('missing parent or changed topology are gaps, not guessed positions',()=>{
 const child={...wall(),id:'SOLID003',attachment:{parentId:'FACE404',panelKey:'front'}};
 const result=convertSurfaceLabWorld(scene([child]));
 assert.equal(result.objects.length,0);
 assert.equal(result.gaps[0].reason,'UNRESOLVED_ATTACHMENT');
});
test('malformed world version is rejected without side effects',()=>{
 assert.throws(()=>convertSurfaceLabWorld(scene([]).surfaces),/Surface Lab 005/);
 assert.throws(()=>convertSurfaceLabWorld({...scene([]),version:5}),/v4 JSON/);
});
test('duplicates do not overwrite original geometry identity',()=>{
 const result=convertSurfaceLabWorld(scene([base(),base()]));
 assert.equal(result.objects.length,1);
 assert.equal(result.gaps[0].reason,'INVALID_OR_DUPLICATE_ID');
});
