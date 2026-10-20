import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
const require=createRequire(import.meta.url);
const Transfer=require('../living-library/live/directed-transfer.js');
const Craft=require('../living-library/live/object-craft.js');
const Rune=require('../living-library/live/rune-unbind-spell.js');
function card(id,title,more={}){
 return {id,objectType:'card',templateId:'blank-card-v1',title,
  image:{id:'keep-me'},cardNumber:'0003',description:'unchanged',createdAt:'old',
  status:'draft',placement:'hover',containerId:null,ownerBookId:null,
  slotIndex:null,revision:4,...more};
}
const linked=(id='local.card.target')=>card(id,'初代法陣卡',{runeBinding:{
 runeId:Craft.ORIGIN_RUNE,state:'bound',boundAt:'past'}});
const spell=(id='local.card.spell')=>card(id,'解除綁定');
function memory(objects){
 let data=JSON.stringify({version:Craft.VERSION,objects});
 return {getItem:k=>k===Craft.KEY?data:null,setItem:(k,v)=>{assert.equal(k,Craft.KEY);data=v},
 raw:()=>data,state:()=>JSON.parse(data)};
}
test('directed transfer traces 3D path into an arbitrary named target surface',()=>{
 const plan=Transfer.normalize({sourceId:'card.a',targetId:'card.b',
  from:{x:1,y:3,z:5},to:{x:-2,y:2,z:2},
  duration:1400,startedAt:500,initialScale:.9});
 assert.deepEqual(Transfer.sample(plan,500).position,{x:1,y:3,z:5});
 const mid=Transfer.sample(plan,1200);
 assert.ok(mid.position.y>2.5,'rise across middle of transfer');
 assert.ok(mid.scale<.9&&mid.scale>.001);
 assert.equal(mid.done,false);
 const end=Transfer.sample(plan,1900);
 assert.deepEqual(end.position,{x:-2,y:2,z:2});
 assert.equal(end.done,true);
 assert.ok(end.scale<.002);
 assert.deepEqual(Transfer.sample(plan,3000).position,end.position);
});
test('one generic transfer also supports ground rune as TARGET without changing engine',()=>{
 const plan=Transfer.normalize({sourceId:'card.a',targetId:Craft.ORIGIN_RUNE,
  from:{x:2,y:3,z:-1},to:{x:0,y:.45,z:0},startedAt:100,duration:1000});
 assert.ok(Math.abs(Transfer.sample(plan,1100).position.y-.45)<1e-10);
 assert.equal(Transfer.sample(plan,600).done,false);
 const moving=Transfer.sample(plan,600,{x:4,y:1,z:7});
 assert.ok(moving.position.x>2,'target can move in world space');
 assert.ok(moving.position.z>0);
});
test('transfer rejects invalid point/id and never mutates caller data',()=>{
 const raw={sourceId:'x',targetId:'y',from:{x:0,y:0,z:0},to:{x:0,y:1,z:0}};
 const before=structuredClone(raw),p=Transfer.normalize(raw);
 assert.deepEqual(raw,before);assert.equal(Object.isFrozen(p),true);
 assert.throws(()=>Transfer.normalize({...raw,targetId:'x'}),/不得相同/);
 assert.throws(()=>Transfer.normalize({...raw,to:{x:NaN,y:1,z:1}}),/座標/);
 assert.throws(()=>Transfer.sample(p,NaN),/進度/);
});
test('unbind atomically deletes the spell while preserving both original and unrelated card',()=>{
 const t=linked(),c=spell(),unrelated=card('local.card.third','普通卡');
 const s=memory([t,c,unrelated]);
 const result=Craft.unbindAndAbsorbSpell(s,{targetId:t.id,targetRevision:4,
  spellId:c.id,spellRevision:4},()=> '2026-10-17T02:00:00Z');
 assert.equal(s.state().objects.length,2);
 assert.equal(result.target.id,t.id);
 assert.equal(Object.hasOwn(result.target,'runeBinding'),false);
 assert.deepEqual(result.target.image,t.image);
 assert.equal(result.spellId,c.id);assert.equal(result.consumed,true);
 assert.equal(s.state().objects.some(x=>x.id===c.id),false,'spent spell is truly gone');
 assert.equal(Craft.listCards(s).length,2);
 assert.equal(Craft.getRuneBoundCard(s),null);
 assert.deepEqual(s.state().objects.find(x=>x.id===unrelated.id),unrelated);
});
test('unbinding cannot run twice and the spent spell cannot be placed, edited, or recycled',()=>{
 const s=memory([linked(),spell()]);
 const input={targetId:'local.card.target',targetRevision:4,spellId:'local.card.spell',spellRevision:4};
 Craft.unbindAndAbsorbSpell(s,input);
 const before=s.raw();
 assert.throws(()=>Craft.unbindAndAbsorbSpell(s,input),/不存在|契約|變動/);
 assert.equal(s.raw(),before);
 assert.throws(()=>Craft.placeCard(s,'local.card.spell','local.atlas.compendium',9),/找不到卡片/);
 assert.throws(()=>Craft.editCard(s,'local.card.spell',{title:'不應修改'}),/找不到可編輯/);
 assert.throws(()=>Craft.recycleCard(s,'local.card.spell'),/找不到|不存在/);
 assert.equal(s.raw(),before);
});
test('unknown target, stale revisions, altered spell, and blocked upgrades fail with no write',()=>{
 const scenarios=[
  [linked(),spell(),{targetRevision:99}],
  [linked(),spell(),{spellRevision:1}],
  [linked(),card('local.card.spell','一般說明'),{}],
  [linked(),card('local.card.spell','解除綁定',{placement:'book'}),{}],
  [card('local.card.target','初代法陣卡'),spell(),{}],
  [card('local.card.target','初代法陣卡',{runeBinding:{runeId:Craft.ORIGIN_RUNE,state:'sealed'},placement:'contained',containment:{kind:'rune_contract'}}),spell(),{}],
  [card('local.card.target','初代法陣卡',{runeBinding:{runeId:Craft.ORIGIN_RUNE,state:'bound'},upgradeRequest:{state:'requested'}}),spell(),{}]
 ];
 for(const [target,caster,override] of scenarios){
  const s=memory([target,caster]),before=s.raw();
  assert.throws(()=>Craft.unbindAndAbsorbSpell(s,{targetId:'local.card.target',
   spellId:'local.card.spell',targetRevision:4,spellRevision:4,...override}),Error);
  assert.equal(s.raw(),before);
 }
});
test('failed browser storage write leaves both linked target and casting card untouched',()=>{
 const s=memory([linked(),spell()]),before=s.raw();
 assert.throws(()=>Craft.unbindAndAbsorbSpell({getItem:s.getItem,
  setItem:()=>{throw Error('quota')}},{targetId:'local.card.target',targetRevision:4,
  spellId:'local.card.spell',spellRevision:4}),/quota/);
 assert.equal(s.raw(),before);
});
test('3D absorption and persistent hiding use SAME original world/card models',async()=>{
 const html=await readFile(new URL('../living-library/live/index.html',import.meta.url),'utf8');
 assert.match(html,/spellHexagram=ContractHexagram\.create/);
 assert.match(html,/spellHexagram\.update\(info\.progress,now\)/);
 assert.match(html,/spellHexagram\.group\.quaternion\.copy\(camera\.quaternion\)/);
 assert.match(html,/DirectedTransfer\.sample\(active\.plan,now/);
 assert.match(html,/caster\.group\.position\.copy\(a\)/);
 assert.match(html,/entry\.record\.placement==='contained'\|\|entry\.record\.containment/);
 assert.match(html,/unbindAndAbsorbSpell\(localStorage/);
 assert.match(html,/spellHold\.pointerDown\(e,card\.id\)/);
 assert.doesNotMatch(html,/window\.LivingObjectCraft\.recycleCard\(localStorage,active\.casterId/);
 assert.equal(Rune.isSpellCard(spell()),true);
});
