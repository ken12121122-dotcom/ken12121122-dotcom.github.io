import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
const require=createRequire(import.meta.url);
const Spell=require('../living-library/live/rune-unbind-spell.js');
const Craft=require('../living-library/live/object-craft.js');
const Hold=require('../living-library/live/rune-hold.js');
const card=(id,title,patch={})=>({id,objectType:'card',templateId:'blank-card-v1',
 title,status:'draft',placement:'hover',containerId:null,ownerBookId:null,revision:2,...patch});
const source=()=>card('local.card.spell','解除綁定術');
const bound=(id='local.card.target',patch={})=>card(id,'法陣契約卡',
 {runeBinding:{runeId:Craft.ORIGIN_RUNE,state:'bound',boundAt:'past'},...patch});
const entry=(record,x=0,z=0,patch={})=>({id:record.id,record,position:{x,y:2,z},
 visible:true,isDragging:false,isSummoning:false,isRecycling:false,isFocused:false,...patch});
const choose=(spell,rest,opts)=>Spell.findNearestBoundCard(spell.id,spell,[spell,...rest],opts);
function mem(objects){
 let v=JSON.stringify({version:Craft.VERSION,objects});
 return {getItem:k=>k===Craft.KEY?v:null,
 setItem:(k,content)=>{assert.equal(k,Craft.KEY);v=content},
 snapshot:()=>JSON.parse(v),raw:()=>v};
}
test('only the player-authored named unbinding card acts as the spell',()=>{
 for(const title of ['解除綁定','解除綁定術','解除綁定卡片','解除契約','契約解除','解除綁定魔法'])
  assert.equal(Spell.isSpellCard(card('local.card.spell',title)),true,title);
 for(const title of ['連結術','空白卡片','解綁','解除綁定說明','締結契約'])
  assert.equal(Spell.isSpellCard(card('local.card.spell',title)),false,title);
 assert.equal(Spell.isSpellCard(card('local.card.spell','解除綁定',{containerId:'local.atlas.collection'})),false);
 assert.equal(Spell.isSpellCard(card('local.card.spell','解除綁定',{runeBinding:{runeId:Craft.ORIGIN_RUNE}})),false);
 assert.equal(Spell.isSpellCard(card('public.gaming.unbind','解除綁定')),false);
});
test('search targets only nearby already bound ORIGINAL ordinary card using X/Z',()=>{
 const spell=entry(source(),1,0);
 const t=entry(bound(),2,0);
 const before=structuredClone([spell,t]);
 const result=choose(spell,[t]);
 assert.equal(result.status,'selected');assert.equal(result.cardId,t.id);assert.equal(result.distance,1);
 assert.deepEqual([spell,t],before,'search must not write records');
 const other=entry(card('local.card.other','普通卡'),1.1,0);
 assert.equal(choose(spell,[other,t]).cardId,t.id);
});
test('single existing unbind spell detects Truth Eye card OR real 3D world artifact',()=>{
 const spell=entry(source(),.8,0);
 const truth=id=>card(id,'真實之眼',{
  runeBinding:{runeId:'artifact.truth-eye',state:'bound'},
  truthEyeContract:{artifactId:'artifact.truth-eye',form:'card',version:2}});
 const target=truth('local.card.eye');
 const cardForm=entry(target,2,0);
 assert.equal(choose(spell,[cardForm]).cardId,target.id);
 assert.equal(choose(spell,[cardForm]).runeId,'artifact.truth-eye');
 const sealed={...target,truthEyeContract:{...target.truthEyeContract,form:'sealed'}};
 const hidden=entry(sealed,2,0,{visible:false});
 assert.equal(choose(spell,[hidden]).reason,'no_linked_card');
 const worldEntry=entry(sealed,1.0,0,{visible:true,artifactWorldAnchor:true});
 assert.equal(choose(spell,[worldEntry]).cardId,sealed.id);
 assert.equal(choose(spell,[worldEntry]).runeId,'artifact.truth-eye');
 assert.equal(choose(spell,[entry({...sealed,truthEyeContract:{...sealed.truthEyeContract,form:'world'}},1,0,
  {visible:true,artifactWorldAnchor:true})]).cardId,sealed.id);
 assert.equal(choose(spell,[entry({...sealed,truthEyeContract:{...sealed.truthEyeContract,form:'card'}},1,0,
  {visible:true,artifactWorldAnchor:true})]).cardId,sealed.id);
 assert.equal(choose(spell,[entry({...sealed,runeBinding:{runeId:'artifact.truth-eye',state:'bound'},
  truthEyeContract:{artifactId:'unrelated.artifact',form:'world'}},1,0,
  {visible:true,artifactWorldAnchor:true})]).reason,'no_linked_card');
 assert.equal(choose(spell,[entry(sealed,4.5,0,{visible:true,artifactWorldAnchor:true})]).reason,'no_linked_card');
});
test('unbinding Truth Eye is atomic, deletes the spell, and does not unbind origin',()=>{
 const eye=card('local.card.eye','真實之眼',{
  runeBinding:{runeId:'artifact.truth-eye',state:'bound',boundAt:'past'},
  truthEyeContract:{artifactId:'artifact.truth-eye',form:'world',version:2},
  cardNumber:'EYE-1',description:'使用者畫的眼睛',revision:3});
 const origin=bound('local.card.origin');
 const cast=source(),db=mem([eye,origin,cast]),before=db.raw();
 assert.throws(()=>Craft.unbindAndAbsorbSpell(db,{
  runeId:'artifact.truth-eye',targetId:eye.id,targetRevision:2,spellId:cast.id,spellRevision:2}),/變動/);
 assert.equal(db.raw(),before,'failing revision cannot consume spell or clear card');
 const result=Craft.unbindAndAbsorbSpell(db,{
  runeId:'artifact.truth-eye',targetId:eye.id,targetRevision:3,spellId:cast.id,spellRevision:2});
 assert.equal(result.target.id,eye.id);
 assert.equal(result.target.revision,4);
 assert.equal(result.target.runeBinding,undefined);
 assert.equal(result.target.truthEyeContract,undefined);
 assert.equal(result.target.cardNumber,'EYE-1');
 assert.equal(result.target.description,'使用者畫的眼睛');
 assert.equal(result.spellId,cast.id);assert.equal(result.consumed,true);
 assert.equal(db.snapshot().objects.length,2,'spent spell is erased');
 assert.deepEqual(db.snapshot().objects.find(x=>x.id===origin.id),origin,
  'the separate origin rune contract is untouched');
});
test('recycled, mutated, upgraded and unrelated Eye targets fail closed without consuming spell',()=>{
 const base=card('local.card.eye','真實之眼',{
  runeBinding:{runeId:'artifact.truth-eye',state:'bound'},
  truthEyeContract:{artifactId:'artifact.truth-eye',form:'sealed'}});
 for(const patch of [{truthEyeContract:{artifactId:'wrong',form:'world'}},
  {runeBinding:{runeId:'artifact.truth-eye',state:'broken'}},
  {runeBinding:{runeId:'artifact.truth-eye',state:'pending'}},
  {permanentRegistry:{state:'registered'}},
  {containerId:'local.atlas.collection',placement:'book'}]){
   const target={...base,...patch},cast=source(),db=mem([target,cast]),before=db.raw();
   assert.throws(()=>Craft.unbindAndAbsorbSpell(db,{
    runeId:'artifact.truth-eye',targetId:target.id,targetRevision:target.revision,
    spellId:cast.id,spellRevision:cast.revision}));
   assert.equal(db.raw(),before);
 }
});
test('standalone card/book editors still boot without the world-only contract policy',async()=>{
 const source=await readFile(new URL('../living-library/live/object-craft.js',import.meta.url),'utf8');
 const global={};const scope=vm.createContext({globalThis:global});
 assert.doesNotThrow(()=>vm.runInContext(source,scope,{timeout:1500}));
 const standalone=global.LivingObjectCraft;
 assert.equal(typeof standalone.createBlankBook,'function');
 const s=mem([]);
 const book=standalone.createBlankBook(s,{randomUUID:()=> 'standalone1'},'測試書本');
 assert.equal(book.title,'測試書本');
 assert.equal(standalone.listBooks(s).length,1);
});
test('future brand-new object IDs are detected automatically without unbind source changes',()=>{
 const spell=entry(source(),0,0);
 for(const identity of ['gadget.compass.v42','world.portal.ABC','local.spell.weather-2032']){
  const target=card('local.card.future','自製神器',{
   runeBinding:{runeId:identity,state:'manifested',boundAt:'past'},
   customContract:{artifactId:identity,form:'in_world'},
   description:'保留我的原圖和說明',
   image:{url:'library.asset'},cardNumber:'X-55'});
  const found=choose(spell,[entry(target,.9)]);
  assert.equal(found.status,'selected');
  assert.equal(found.runeId,identity);
  const state=mem([target,source()]);
  const out=Craft.unbindAndAbsorbSpell(state,{runeId:identity,targetId:target.id,
   targetRevision:target.revision,spellId:'local.card.spell',spellRevision:2});
  assert.equal(out.target.runeBinding,undefined);
  assert.equal(out.target.customContract,undefined);
  assert.equal(out.target.description,target.description);
  assert.deepEqual(out.target.image,target.image);
  assert.equal(out.target.cardNumber,target.cardNumber);
  assert.equal(out.target.id,target.id);
  assert.equal(out.spellId,'local.card.spell');
  assert.equal(state.snapshot().objects.length,1);
 }
});
test('common target rules reject pending upgrades, permanent and malformed contracts',()=>{
 const spell=entry(source(),0,0);
 for(const patch of [{l2Certified:true},{upgradeRequest:{state:'requested'}},
   {certification:{level:'L2'}},{permanentRegistry:{state:'registered'}},
   {permanentNumber:7},{runeBinding:{runeId:'artifact.random',state:'pending'}},
   {runeBinding:{runeId:'artifact.random',state:'broken'}},
   {customContract:{artifactId:'a-different-artifact'}}]){
  const card0=card('local.card.test','任意已契約',{
   runeBinding:{runeId:'artifact.random',state:'bound'},
   customContract:{artifactId:'artifact.random'},...patch});
  const db=mem([card0,source()]),before=db.raw();
  assert.equal(choose(spell,[entry(card0,1)]).status,'unavailable');
  assert.throws(()=>Craft.unbindAndAbsorbSpell(db,{
   runeId:'artifact.random',targetId:card0.id,targetRevision:card0.revision,
   spellId:'local.card.spell',spellRevision:2}));
  assert.equal(db.raw(),before,'rejected contracts must never consume spell');
 }
});
test('no matching bound target or target too far must fail closed',()=>{
 const spell=entry(source());
 assert.equal(choose(spell,[]).reason,'no_linked_card');
 assert.equal(choose(spell,[entry(bound(),3.61)]).reason,'no_linked_card');
 assert.equal(choose(spell,[entry(bound(),3.6)]).status,'selected');
 assert.equal(choose(spell,[entry(bound(),3)],{radius:1}).reason,'no_linked_card');
 assert.equal(choose(spell,[entry(bound(),3)],{radius:999}).status,'selected','invalid radius uses safe default');
});
test('seal/hidden/dragged/book-held/transition targets are rejected',()=>{
 const spell=entry(source());
 for(const target of [
  entry(bound('local.card.target',{runeBinding:{runeId:Craft.ORIGIN_RUNE,state:'sealed'},placement:'contained',containment:{kind:'rune_contract'}}),1),
  entry(bound('local.card.target',{placement:'book',containerId:'local.atlas.compendium'}),1),
  entry(bound(),1,0,{visible:false}),
  entry(bound(),1,0,{isDragging:true}),
  entry(bound(),1,0,{isRecycling:true}),
  entry(bound(),1,0,{isSummoning:true}),
  entry(bound(),1,0,{isFocused:true}),
  entry(bound(),1,0,{position:{x:NaN,z:0}})
 ])assert.equal(choose(spell,[target]).status,'unavailable',JSON.stringify(target.record));
});
test('the source card is never considered its own unbinding target',()=>{
 const caster=entry(source());
 assert.equal(choose(caster,[caster]).reason,'no_linked_card');
 assert.equal(Spell.findNearestBoundCard(caster.id,{...caster,visible:false},[]).reason,'invalid_spell');
});
test('revision guard protects existing contract from changes during animation',()=>{
 const s=mem([bound()]),before=s.raw();
 assert.throws(()=>Craft.restoreUncommittedRuneCard(s,Craft.ORIGIN_RUNE,()=> 'now',
  'local.card.wrong',2),/變動/);
 assert.throws(()=>Craft.restoreUncommittedRuneCard(s,Craft.ORIGIN_RUNE,()=> 'now',
  'local.card.target',999),/變動/);
 assert.equal(s.raw(),before);
 const restored=Craft.restoreUncommittedRuneCard(s,Craft.ORIGIN_RUNE,()=> 'now',
  'local.card.target',2);
 assert.equal(restored.id,'local.card.target');
 assert.equal(restored.runeBinding,undefined);
 assert.equal(restored.placement,'hover');
 assert.equal(s.snapshot().objects.length,1);
});
test('spell cancellation leaves linked card completely untouched until confirmation',()=>{
 let now=0,serial=0;const timers=new Map(),selected=[];
 const spell=Hold.create({now:()=>now,setTimer:(cb,ms)=>{const id=++serial;timers.set(id,{cb,due:now+ms});return id},
  clearTimer:id=>timers.delete(id),onSelected:id=>selected.push(id)});
 const record=bound(),s=mem([record]),before=s.raw();
 const e={pointerId:12,isPrimary:true,button:0,clientX:35,clientY:70};
 spell.pointerDown(e,source().id);now=2200;spell.pointerUp(e);
 for(const t of timers.values())if(t.due<=now)t.cb();
 assert.equal(selected.length,0);assert.equal(s.raw(),before);
});
test('world UI uses long hold followed by an explicit irreversible-unbind confirmation and visual effect',async()=>{
 const html=await readFile(new URL('../living-library/live/index.html',import.meta.url),'utf8');
 assert.match(html,/rune-unbind-spell\.js/);
 assert.match(html,/function boundObjectInScene\(cardId\)/);
 assert.match(html,/record\.worldContractTarget=true/);
 assert.match(html,/eligibleUnbindContracts\(spellId\)/);\n assert.match(html,/id="runeUnbindTargetPicker"/);\n assert.match(html,/chooseUnbindContract\(\)/);
 assert.match(html,/getRuneBoundCard\(localStorage,selection\.runeId\)/);
 assert.match(html,/runeId:selection\.runeId,targetId:selection\.targetId/);
 assert.match(html,/scene\.dispatchEvent\(\{type:'living-contract-unbound'/);
 assert.match(html,/id="runeUnbindProgress"/);
 assert.match(html,/id="runeUnbindDialog"/);
 assert.match(html,/id="runeUnbindConfirm"/);
 assert.match(html,/id="runeUnbindCancel"/);
 assert.match(html,/window\.LivingRuneUnbindSpell/);
 assert.match(html,/spellCardAt\(e\)/);
 assert.match(html,/spellHold\.pointerDown\(e,card\.id\)/);
 assert.match(html,/spellHold\.pointerMove\(e\)/);
 assert.match(html,/spellHold\.pointerUp\(e\)/);
 assert.match(html,/spellHold\.tick\(\);updateSpellEffect\(now\)/);
 assert.match(html,/spellHexagram\.update\(info\.progress,now\)/);
 assert.match(html,/spellHexagram\.group\.quaternion\.copy\(camera\.quaternion\)/);
 assert.match(html,/spellHexagram=ContractHexagram\.create/);
 assert.match(html,/confirmSpellUnbind\(\)/);
 assert.match(html,/unbindDialog\.showModal\(\)/);
 assert.match(html,/if\(!unbindDialog\.open\|\|!spellSelection\|\|unbindEffect\)return/);
 assert.match(html,/unbindEffect=\{\.\.\.selection,fxCard:visual/);
 assert.match(html,/unbindAndAbsorbSpell\(/);
 assert.match(html,/DirectedTransfer\.sample\(active\.plan,now/);
 assert.match(html,/targetRevision:selection\.expectedRevision,spellId:selection\.casterId/);
 assert.match(html,/spellRevision:selection\.spellRevision/);
 assert.match(html,/caster\.group\.scale\.setScalar\(scale\)/);
 assert.match(html,/runeBound=null;runeNearest=null/);
 assert.doesNotMatch(html,/>3秒</);
 const scripts=[...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)];
 for(const match of scripts)if(match[1]?.trim()&&match[0].includes('type="module"')) {
  // Strip static import checks only; dynamic import/await remain accepted in async wrapper.
  assert.doesNotThrow(()=>new Function('return (async()=>{'+match[1]+'})'));
 }
});
