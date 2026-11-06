import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const Core=require('../living-library/live/object-contract-core.js');
const A='local.card.alpha',B='local.card.beta',C='scan.bundle.'+A;
const refs=[{kind:'book',id:'research'},{kind:'scene',id:'scene.named-object'}];
const obj=(id,contract,members=refs,form='card')=>({id,objectType:'card',status:'draft',placement:'hover',runeBinding:{runeId:contract,state:'bound'},scanContract:{version:1,artifactId:contract,form,members}});
const src=(objects)=>({version:1,objects});
test('P0: contract-first owner/member lookups never use scene, position or visibility',()=>{
 const data=src([obj(A,C),{id:B}]),before=JSON.stringify(data),i=Core.buildIndex(data);
 assert.equal(i.healthy,true);assert.equal(i.ownerOf(refs[0]),A);assert.equal(i.ownerOf(refs[1]),A);
 assert.deepEqual(i.membersOf(A),refs);assert.equal(i.cardContract(A).contractId,C);
 assert.equal(i.cardContract(A).recovery,'UNVERIFIED');assert.equal(i.assertHealthy(),true);
 assert.equal(JSON.stringify(data),before);assert.throws(()=>i.membersOf(A).push(refs[0]),TypeError);
});
test('P0: form switches preserve owner and members through reload',()=>{
 for(const form of ['card','world','card','world','card']){
  const i=Core.buildIndex(JSON.parse(JSON.stringify(src([obj(A,C,refs,form)]))));
  assert.equal(i.healthy,true);assert.equal(i.ownerOf(refs[1]),A);assert.equal(i.cardContract(A).form,form);assert.deepEqual(i.membersOf(A),refs);
 }
});
test('P0: inactive former owner not included in current index',()=>{
 const old=obj(A,C);old.runeBinding.state='released';
 const i=Core.buildIndex(src([old,obj(B,'scan.bundle.'+B)]));
 assert.equal(i.healthy,true);assert.equal(i.ownerOf(refs[0]),B);assert.equal(i.cardContract(A),null);
});
test('P0: competing owner for same member fails closed with diagnosis',()=>{
 const i=Core.buildIndex(src([obj(A,C),obj(B,'scan.bundle.'+B,[refs[0]])]));
 assert.equal(i.healthy,false);assert.equal(i.ownerOf(refs[0]),null);
 assert.ok(i.diagnostics.some(d=>d.code==='DUPLICATE_MEMBER_OWNER'));
 assert.throws(()=>i.assertHealthy(),/CONTRACT_INDEX_INCONSISTENT/);
});
test('P0: duplicate contract ID on different cards never guesses owner',()=>{
 const i=Core.buildIndex(src([obj(A,C),obj(B,C,[{kind:'scene',id:'scene.other'}])]));
 assert.equal(i.healthy,false);assert.equal(i.ownerOf(refs[0]),null);
 assert.ok(i.diagnostics.some(d=>d.code==='DUPLICATE_CONTRACT_OWNER'));
});
test('P0: invalid or duplicate member and contradictory contract ID fail closed',()=>{
 const bad=obj(A,C);bad.scanContract.artifactId='scan.bundle.other';
 const i=Core.buildIndex(src([bad]));assert.equal(i.healthy,false);assert.equal(i.ownerOf(refs[0]),null);
 assert.ok(i.diagnostics.some(d=>d.code==='CONTRACT_ID_MISMATCH'));
 assert.equal(Core.buildIndex(src([obj(A,C,[refs[0],refs[0]])])).healthy,false);
 assert.throws(()=>Core.buildIndex(src([{id:'duplicate'},{id:'duplicate'}])),/INVALID_OR_DUPLICATE_SAVED_ID/);
});
test('P0: geometry-derived scene ID cannot be asserted persistent',()=>{
 const i=Core.buildIndex(src([obj(A,C,[{kind:'scene',id:'scene.auto.generated.1'}])]));
 assert.equal(i.healthy,false);assert.equal(i.ownerOf({kind:'scene',id:'scene.auto.generated.1'}),null);
 assert.ok(i.diagnostics.some(d=>d.code==='UNVERIFIED_SCENE_ID'));
});
test('P0: identical member ID under different kinds stays independent',()=>{
 const i=Core.buildIndex(src([obj(A,C,[{kind:'scene',id:'shared.id'}]),obj(B,'scan.bundle.'+B,[{kind:'book',id:'shared.id'}])]));
 assert.equal(i.healthy,true);assert.equal(i.ownerOf({kind:'scene',id:'shared.id'}),A);
 assert.equal(i.ownerOf({kind:'book',id:'shared.id'}),B);
});
test('P0: origin rune and unique artifact use common lookup machinery',()=>{
 const rune={id:A,runeBinding:{runeId:'local.rune.origin',state:'sealed'}};
 const artifact={id:B,runeBinding:{runeId:'artifact.truth-eye',state:'bound'},truthEyeContract:{artifactId:'artifact.truth-eye',form:'card'}};
 const i=Core.buildIndex(src([rune,artifact]));assert.equal(i.healthy,true);
 assert.equal(i.ownerOf({kind:'rune',id:'local.rune.origin'}),A);
 assert.equal(i.ownerOf({kind:'artifact',id:'artifact.truth-eye'}),B);
});
test('P0: unrecognized ID and missing owner cannot invent a reconstructible object',()=>{
 const i=Core.buildIndex(src([obj(A,C)]));
 assert.equal(i.ownerOf({kind:'scene',id:'scene.not-found'}),null);
 assert.equal(i.cardContract(A).recovery,'UNVERIFIED');assert.equal(i.cardContract('no-such-card'),null);
 assert.deepEqual(i.membersOf('no-such-card'),[]);
});
