import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
const require=createRequire(import.meta.url);
const Craft=require('../living-library/live/object-craft.js');
const Rune=require('../living-library/live/rune-contract.js');
const storage=(card)=>{
 let value=JSON.stringify({version:Craft.VERSION,objects:[card]});
 return {getItem:k=>k===Craft.KEY?value:null,setItem:(k,v)=>{assert.equal(k,Craft.KEY);value=v},
  raw:()=>value};
};
const card=(id='local.card.sample')=>({
 id,objectType:'card',templateId:'blank-card-v1',title:'初始法陣',description:'未更動',
 image:{dataUrl:'image-preview-placeholder'},cardNumber:'001',status:'draft',
 placement:'hover',ownerBookId:null,containerId:null,revision:7,createdAt:'old',updatedAt:'old'
});
const bind=(db,id='local.card.sample',rev=7)=>Craft.bindRuneCard(db,Rune.RUNE_ID,id,rev,()=> '2026-10-16T15:30:00Z');
test('explicit confirmation links the SAME card and retains its original data',()=>{
 const original=card(),db=storage(original),before=JSON.parse(db.raw());
 const bonded=bind(db);const objects=Craft.read(db).objects;
 assert.equal(objects.length,1);
 assert.equal(objects[0].id,original.id);
 assert.equal(objects[0].cardNumber,original.cardNumber);
 assert.deepEqual(objects[0].image,original.image);
 assert.equal(objects[0].description,original.description);
 assert.equal(objects[0].revision,8);
 assert.equal(objects[0].runeBinding.runeId,Rune.RUNE_ID);
 assert.equal(objects[0].runeBinding.state,'bound');
 assert.equal(Craft.getRuneBoundCard(db).id,original.id);
 assert.equal(before.objects[0].runeBinding,undefined);
 assert.equal(Rune.isOrdinaryCard(bonded),false,'bound card must leave future candidates');
});
test('single existing bond remains idempotent and rejects a second unrelated card',()=>{
 const db=storage(card()),first=bind(db);const once=db.raw();
 assert.deepEqual(bind(db,first.id,1),first);
 assert.equal(db.raw(),once);
 const s=JSON.parse(db.raw());
 s.objects.push({...card('local.card.second'),title:'另一張'});
 db.setItem(Craft.KEY,JSON.stringify(s));
 assert.throws(()=>Craft.bindRuneCard(db,Rune.RUNE_ID,'local.card.second',7),/已經連結/);
 assert.equal(db.raw(),JSON.stringify(s));
});
test('stale revision, blank cards and invalid bindings preserve the existing snapshot',()=>{
 for(const patch of [
  {title:'空白卡片'}, {containerId:'local.atlas.collection'},
  {placement:'book'}, {status:'recycled'}, {runeBinding:{runeId:'local.rune.other'}}
 ]){
  const db=storage({...card(),...patch}),before=db.raw();
  assert.throws(()=>bind(db),Error);
  assert.equal(db.raw(),before);
 }
 const db=storage(card()),before=db.raw();
 assert.throws(()=>bind(db,'local.card.sample',6),/已變動/);
 assert.throws(()=>Craft.bindRuneCard(db,'other.rune','local.card.sample',7),/只支援/);
 assert.equal(db.raw(),before);
});
test('linked card can use right GBA reader slot 9 without losing its rune bond, identity or image',()=>{
 const db=storage(card()),original=bind(db),bond=original.runeBinding;
 const placed=Craft.placeCard(db,original.id,'local.atlas.compendium',9,()=> '2026-10-17T00:00:00Z');
 assert.equal(placed.containerId,'local.atlas.compendium');
 assert.equal(placed.ownerBookId,'local.atlas.compendium');
 assert.equal(placed.slotIndex,9);
 assert.equal(placed.placement,'book');
 assert.deepEqual(placed.runeBinding,bond);
 assert.deepEqual(placed.image,original.image);
 assert.equal(placed.cardNumber,original.cardNumber);
 assert.equal(placed.id,original.id);
 assert.equal(Craft.read(db).objects.length,1);
 assert.equal(Craft.getRuneBoundCard(db).id,original.id);
 assert.equal(require('../living-library/live/atlas-state.js').readerActive(placed),true);
 // Undoing the reader placement returns the SAME linked card to the floating orbit.
 const floated=Craft.placeCard(db,original.id,null,null,()=> '2026-10-17T00:01:00Z');
 assert.equal(floated.placement,'hover');
 assert.equal(floated.containerId,null);
 assert.equal(floated.slotIndex,null);
 assert.deepEqual(floated.runeBinding,bond);
 assert.equal(floated.id,original.id);
 assert.equal(Craft.read(db).objects.length,1);
});
test('linked card is locked out of nine left atlas cells and any other book, but remains readable in GBA',()=>{
 const db=storage(card());bind(db);
 for(const [container,slot] of [
  ...Array.from({length:9},(_,i)=>['local.atlas.compendium',i]),
  ['local.atlas.collection',0],['local.atlas.collection',17]
 ]){
  const before=db.raw();
  assert.equal(Craft.canStoreBoundCard(Craft.listCards(db)[0],container,slot),false);
  assert.throws(()=>Craft.placeCard(db,'local.card.sample',container,slot),/右側 GBA 讀卡格/);
  assert.equal(db.raw(),before,'failed drop must never change user card data');
 }
 const bound=Craft.listCards(db)[0];
 assert.equal(Craft.canStoreBoundCard(bound,'local.atlas.compendium',9),true);
 assert.equal(Craft.canStoreBoundCard(bound,null,null),true);
 assert.equal(Craft.canStoreBoundCard(bound,'local.atlas.compendium',8),false);
 assert.equal(Craft.canStoreBoundCard(bound,'local.atlas.collection',0),false);
 assert.throws(()=>Craft.recycleCard(db,'local.card.sample'),/已連結法陣/);
});
test('right GBA slot remains exclusive and respects quota failure atomically',()=>{
 const db=storage(card());bind(db);
 const objects=Craft.read(db).objects;
 objects.push({...card('local.card.other'),title:'正常讀卡測試',containerId:'local.atlas.compendium',
   ownerBookId:'local.atlas.compendium',placement:'book',slotIndex:9});
 db.setItem(Craft.KEY,JSON.stringify({version:Craft.VERSION,objects}));
 const before=db.raw();
 assert.throws(()=>Craft.placeCard(db,'local.card.sample','local.atlas.compendium',9),/已有卡片/);
 assert.equal(db.raw(),before);
 const blocked={getItem:db.getItem,setItem:()=>{throw Error('quota')}};
 assert.throws(()=>Craft.placeCard(blocked,'local.card.sample',null),/quota/);
 assert.equal(db.raw(),before);
});
test('ordinary unbound cards retain all normal slot and book destinations',()=>{
 const db=storage(card());
 assert.equal(Craft.canStoreBoundCard(Craft.listCards(db)[0],'local.atlas.compendium',0),true);
 let placed=Craft.placeCard(db,'local.card.sample','local.atlas.compendium',0);
 assert.equal(placed.slotIndex,0);
 placed=Craft.placeCard(db,'local.card.sample','local.atlas.collection',17);
 assert.equal(placed.slotIndex,17);
 assert.equal(Craft.read(db).objects.length,1);
});
test('rune binding cannot be recycled directly and still requires unbinding',()=>{
 const db=storage(card());bind(db);const before=db.raw();
 assert.throws(()=>Craft.recycleCard(db,'local.card.sample'),/已連結法陣/);
 assert.equal(db.raw(),before);
});
test('UI renders a visible selection glow, beam, consent question and real card seal',async()=>{
 const html=await readFile(new URL('../living-library/live/index.html',import.meta.url),'utf8');
 assert.match(html,/id="runeBindDialog"/);
 assert.match(html,/是否將「<strong id="runeBindCardName"/);
 assert.match(html,/id="runeBindConfirm"/);
 assert.match(html,/id="runeBindCancel"/);
 assert.match(html,/runeGlow\.visible=illuminated\|\|!!runeBound/);
 assert.match(html,/runeCandidateBeam\.visible=true/);
 assert.match(html,/runeBindingPulseUntil=performance\.now\(\)\+2800/);
 assert.match(html,/new THREE\.TorusGeometry\(\.058,\.009,5,36\)/);
 assert.match(html,/LivingObjectCraft\.bindRuneCard\(localStorage/);
 assert.match(html,/refreshFloatingCards\(\); \/\/ Recreate the same card/);
 assert.match(html,/RUNE_ARC_SEGMENTS=192/);
 assert.doesNotMatch(html,/id="runeBind.*>3秒/);
});

test('3D atlas drag affordance follows the same GBA right-slot eligibility gate',async()=>{
 const code=await readFile(new URL('../living-library/live/atlas-runtime.js',import.meta.url),'utf8');
 assert.match(code,/craft\.canStoreBoundCard\(pointer\.entry\.record,target\.id,target\.slot\)/);
 assert.match(code,/if\(!occupied&&allowed\)/);
 const state=require('../living-library/live/atlas-state.js');
 assert.equal(state.readerActive({containerId:state.ATLAS,slotIndex:9}),true);
 assert.equal(state.readerActive({containerId:state.ATLAS,slotIndex:8}),false);
});
