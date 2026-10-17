import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
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
  entry(bound('local.card.target',{runeBinding:{runeId:Craft.ORIGIN_RUNE,state:'sealed'}}),1),
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
test('world UI uses existing card pick, a circular 3s progress and confirmation dialog',async()=>{
 const html=await readFile(new URL('../living-library/live/index.html',import.meta.url),'utf8');
 assert.match(html,/rune-unbind-spell\.js/);
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
 assert.match(html,/spellArcGeometry\.setDrawRange\(0,Math\.floor\(spellProgressSegments\*info\.progress\)\*6\)/);
 assert.match(html,/spellTrack\.quaternion\.copy\(camera\.quaternion\)/);
 assert.match(html,/spellProgressSegments=RUNE_ARC_SEGMENTS/);
 assert.match(html,/spellPromptTimer=setTimeout/);
 assert.match(html,/unbindEffect=\{\.\.\.selection,start:performance\.now\(\)/);
 assert.match(html,/unbindAndAbsorbSpell\(/);
 assert.match(html,/DirectedTransfer\.sample\(active\.plan,now/);
 assert.match(html,/targetRevision:active\.expectedRevision,spellId:active\.casterId/);
 assert.match(html,/spellRevision:active\.spellRevision/);
 assert.match(html,/caster\.group\.scale\.setScalar\(scale\)/);
 assert.match(html,/runeBound=null;runeNearest=null/);
 assert.doesNotMatch(html,/>3秒</);
 const scripts=[...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)];
 for(const match of scripts)if(match[1]?.trim()&&match[0].includes('type="module"')) {
  // Strip static import checks only; dynamic import/await remain accepted in async wrapper.
  assert.doesNotThrow(()=>new Function('return (async()=>{'+match[1]+'})'));
 }
});
