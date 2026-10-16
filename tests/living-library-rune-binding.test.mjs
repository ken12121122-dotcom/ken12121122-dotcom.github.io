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
test('bonded card cannot be moved to a book or recycled without an unbinding ritual',()=>{
 const db=storage(card());bind(db);const before=db.raw();
 assert.throws(()=>Craft.placeCard(db,'local.card.sample','local.atlas.collection',0),/已連結法陣/);
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
