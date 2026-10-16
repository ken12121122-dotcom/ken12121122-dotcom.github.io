import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
const require=createRequire(import.meta.url);
const Craft=require('../living-library/live/object-craft.js');
const State=require('../living-library/live/atlas-state.js');
function memory(objects){
 let json=JSON.stringify({version:Craft.VERSION,objects});
 const store={getItem:k=>k===Craft.KEY?json:null,
  setItem:(k,v)=>{assert.equal(k,Craft.KEY);json=v},
  state:()=>JSON.parse(json),raw:()=>json};
 return store;
}
function ordinary(id='local.card.orig'){
 return {id,objectType:'card',templateId:'blank-card-v1',
  title:'初始卡片',description:'未改說明',image:{dataUrl:'user-preserved-image'},
  cardNumber:'USER-01',linkedCardId:'public.gba.runtime',
  createdAt:'old',updatedAt:'old',status:'draft',placement:'hover',
  containerId:null,ownerBookId:null,slotIndex:null,revision:6};
}
function linked(id='local.card.orig'){
 return {...ordinary(id),runeBinding:{runeId:Craft.ORIGIN_RUNE,state:'bound',boundAt:'2026-10-16T13:00:00Z'},
  revision:7,updatedAt:'2026-10-16T13:00:00Z'};
}
const unbind=s=>Craft.restoreUncommittedRuneCard(s,Craft.ORIGIN_RUNE,()=> '2026-10-17T01:00:00Z');
test('owner can restore the SAME linked ordinary card: remove seal and retain identity/photo/content',()=>{
 const original=linked(),other=ordinary('local.card.other');
 const book={id:'local.book.existing',objectType:'book',templateId:'blank-book-v1',title:'原書',status:'draft'};
 const storage=memory([original,other,book]);
 const result=unbind(storage),objects=storage.state().objects;
 assert.equal(objects.length,3);
 assert.equal(result.id,original.id);
 assert.equal(result.title,original.title);
 assert.equal(result.description,original.description);
 assert.deepEqual(result.image,original.image);
 assert.equal(result.cardNumber,original.cardNumber);
 assert.equal(result.linkedCardId,original.linkedCardId);
 assert.equal(result.createdAt,original.createdAt);
 assert.equal(Object.hasOwn(result,'runeBinding'),false);
 assert.equal(result.status,'draft');
 assert.equal(result.placement,'hover');
 assert.equal(result.revision,original.revision+1);
 assert.equal(result.updatedAt,'2026-10-17T01:00:00Z');
 assert.equal(Craft.getRuneBoundCard(storage),null);
 assert.deepEqual(objects[1],other);
 assert.deepEqual(objects[2],book);
 assert.deepEqual(result,objects[0]);
 assert.equal(Craft.floatingCards(storage).length,2);
});
test('when linked card is in right GBA reader, undo returns original to floating and frees reader',()=>{
 const c={...linked(),containerId:State.ATLAS,ownerBookId:State.ATLAS,slotIndex:9,placement:'book'};
 const storage=memory([c]);
 assert.equal(State.readerActive(Craft.listCards(storage)[0]),true);
 const result=unbind(storage);
 assert.equal(result.containerId,null);
 assert.equal(result.ownerBookId,null);
 assert.equal(result.slotIndex,null);
 assert.equal(result.placement,'hover');
 assert.equal(State.readerActive(result),false);
 assert.equal(Craft.getRuneBoundCard(storage),null);
 assert.equal(Craft.read(storage).objects.length,1);
});
test('no bond, mismatched origin, duplicate bonds and sealed states fail closed',()=>{
 const cases=[
  [ordinary()],
  [{...linked(),runeBinding:{runeId:'local.rune.other',state:'bound'}}],
  [linked(),linked('local.card.second')],
  [{...linked(),runeBinding:{runeId:Craft.ORIGIN_RUNE,state:'sealed'}}],
  [{...linked(),runeBinding:{runeId:Craft.ORIGIN_RUNE,state:'cardified'}}],
  [{...linked(),status:'recycled'}],
  [{...linked(),placement:'book',containerId:State.COLLECTION,ownerBookId:State.COLLECTION,slotIndex:0}],
  [{...linked(),upgradeRequest:{state:'requested'}}]
 ];
 for(const cards of cases){
  const storage=memory(cards),before=storage.raw();
  assert.throws(()=>unbind(storage),Error);
  assert.equal(storage.raw(),before,'no partial mutation');
 }
 const storage=memory([linked()]),before=storage.raw();
 assert.throws(()=>Craft.restoreUncommittedRuneCard(storage,'wrong.rune'),/僅支援/);
 assert.equal(storage.raw(),before);
});
test('restoration is one-shot and does not erase cards bound at a later time by auto-trigger',()=>{
 const storage=memory([linked()]);
 unbind(storage);const once=storage.raw();
 assert.throws(()=>unbind(storage),/找不到唯一/);
 assert.equal(storage.raw(),once);
 // This module does NOT auto-unbind on page load; explicit action is required.
 assert.equal(typeof Craft.restoreUncommittedRuneCard,'function');
});
test('failed save preserves last stored record and does not silently wipe unrelated data',()=>{
 const original=linked(),state=memory([original]);
 const blocked={getItem:state.getItem,setItem:()=>{throw Error('quota')}};
 assert.throws(()=>unbind(blocked),/quota/);
 assert.deepEqual(state.state().objects[0],original);
});
test('after restore, original card is again eligible for nearest ordinary rune selection',()=>{
 const storage=memory([linked()]);const result=unbind(storage);
 const Contract=require('../living-library/live/rune-contract.js');
 assert.equal(Contract.isOrdinaryCard(result),true);
 const nearest=Contract.findNearestEligibleCard({x:0,z:0},[{
  id:result.id,record:result,position:{x:1,y:2,z:0},visible:true
 }]);
 assert.equal(nearest.cardId,result.id);
});
test('restore page is an explicit same-origin step, not a global automatic wipe',async()=>{
 const html=await readFile(new URL('../living-library/live/rune-restore.html',import.meta.url),'utf8');
 assert.match(html,/id="restore" disabled/);
 assert.match(html,/window\.confirm/);
 assert.match(html,/craft\.restoreUncommittedRuneCard\(localStorage,craft\.ORIGIN_RUNE\)/);
 assert.match(html,/craft\.getRuneBoundCard\(localStorage\)/);
 assert.match(html,/href="\.\/index\.html"/);
 assert.match(html,/id="state" role="alert"/);
 const inline=[...html.matchAll(/<script(?:[^>]*)>([\s\S]*?)<\/script>/g)].map(m=>m[1]).filter(Boolean);
 for(const js of inline)assert.doesNotThrow(()=>new Function(js));
 const scene=await readFile(new URL('../living-library/live/index.html',import.meta.url),'utf8');
 assert.doesNotMatch(scene,/restoreUncommittedRuneCard\(localStorage/,'must not reset all visitors on main scene load');
});
