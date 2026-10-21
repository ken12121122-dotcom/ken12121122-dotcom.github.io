import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
const require=createRequire(import.meta.url);
const Craft=require('../living-library/live/object-craft.js');
const Registry=require('../living-library/live/permanent-cards.js');
function store(records){
 let raw=JSON.stringify({version:Craft.VERSION,objects:records});
 return {getItem:k=>k===Craft.KEY?raw:null,setItem:(k,v)=>{if(k!==Craft.KEY)throw Error('wrong key');raw=v},get raw(){return raw},
  objects:()=>JSON.parse(raw).objects};
}
function created(){
 const card={id:'local.card.rune01',objectType:'card',templateId:'blank-card-v1',
  title:'初代法陣卡',status:'draft',revision:1,placement:'hover',
  containerId:null,ownerBookId:null,slotIndex:null,cardNumber:'001'};
 const db=store([card]);
 let item=Craft.sealRuneCard(db,Craft.ORIGIN_RUNE,card.id,1);
 item=Craft.materializeRuneCard(db,Craft.ORIGIN_RUNE,card.id,item.revision);
 item=Craft.placeCard(db,item.id,'local.atlas.compendium',9);
 item=Registry.register(db,{cardId:item.id,expectedRevision:item.revision,number:1,ownerConfirmed:true});
 item=Registry.toReader(db,item.id,item.revision);
 item=Craft.placeCard(db,item.id,null);
 return {db,item};
}
test('long-press projection is one atomic return-rune transaction including transform, not two writes',()=>{
 const {db,item}=created(),before=db.raw;
 assert.throws(()=>Craft.returnRuneCard(db,Craft.ORIGIN_RUNE,item.id,item.revision,undefined,
  {position:{x:90,y:0,z:0},rotationY:0}),/不合法/);
 assert.equal(db.raw,before);
 const saved=Craft.returnRuneCard(db,Craft.ORIGIN_RUNE,item.id,item.revision,undefined,
  {position:{x:4,y:0,z:-3},rotationY:Math.PI/2});
 assert.equal(saved.id,item.id);
 assert.equal(saved.placement,'contained');
 assert.equal(saved.runeBinding.state,'sealed');
 assert.equal(saved.permanentRegistry.anchor.position.x,4);
 assert.equal(saved.permanentRegistry.anchor.position.z,-3);
 assert.equal(saved.permanentRegistry.anchor.rotationY,Math.PI/2);
 assert.equal(db.objects().length,1);
 const out=Craft.materializeRuneCard(db,Craft.ORIGIN_RUNE,item.id,saved.revision);
 assert.equal(out.id,saved.id);
 assert.deepEqual(out.permanentRegistry.anchor.position,{x:4,y:0,z:-3});
});
test('ordinary rune contract cannot move anchor through newly added permanent-only option',()=>{
 const db=store([{id:'local.card.rune01',objectType:'card',templateId:'blank-card-v1',
  title:'普通法陣卡',status:'draft',revision:1,placement:'hover',containerId:null,ownerBookId:null}]);
 let item=Craft.sealRuneCard(db,Craft.ORIGIN_RUNE,'local.card.rune01',1);
 item=Craft.materializeRuneCard(db,Craft.ORIGIN_RUNE,item.id,item.revision);
 const before=db.raw;
 assert.throws(()=>Craft.returnRuneCard(db,Craft.ORIGIN_RUNE,item.id,item.revision,undefined,
  {position:{x:4,y:0,z:0},rotationY:0}),/不合法/);
 assert.equal(db.raw,before);
});
test('world move remains reversible visually but permanent contract and BOOK home ID stay locked',()=>{
 const {db,item}=created(),sealed=Craft.returnRuneCard(db,Craft.ORIGIN_RUNE,item.id,item.revision);
 const first=Registry.movableAnchor(db,sealed.id,sealed.revision,{x:1,y:0,z:2},Math.PI/6);
 const before=db.raw;
 assert.throws(()=>Registry.movableAnchor(db,first.id,first.revision-1,{x:3,y:0,z:0},0),/版本/);
 assert.equal(db.raw,before);
 const second=Registry.movableAnchor(db,first.id,first.revision,{x:3,y:0,z:0},0);
 assert.equal(second.permanentRegistry.number,1);
 assert.equal(second.permanentRegistry.bookId,'local.atlas.compendium');
 assert.equal(second.runeBinding.state,'sealed');
 assert.deepEqual(second.permanentRegistry.anchor.position,{x:3,y:0,z:0});
 assert.equal(Craft.getRuneBoundCard(db).id,item.id);
});
test('construction UI runs one projection, touch+joystick, rotated grid preview and icon-only controls',async()=>{
 const html=await readFile(new URL('../living-library/live/index.html',import.meta.url),'utf8');
 for(const re of [
  /id="permanentBuildToggle"/,/id="permanentBuildTools"/,
  /id="permanentBuildSnap"/,/id="permanentBuildRotate"/,
  /id="permanentBuildApply"/,/id="permanentBuildCancel"/,
  /function beginPermanentBuild\(mode,card\)/,
  /beginPermanentBuild\('card',card\)/,
  /beginPermanentBuild\('world',card\)/,
  /const holoGroup=new THREE\.Group\(\)/,
  /function pickBuildGround\(e\)/,
  /Registry\.movableAnchor\(localStorage/,
  /startRuneTransfer\('return',original,/,
  /craft\.returnRuneCard\(localStorage,RuneContract\.RUNE_ID,active\.cardId,active\.expectedRevision,undefined,active\.placement\)/,
  /updateHologram\(now\)/,
  /runeGroup\.position\.set\(pos\.x,pos\.y,pos\.z\)/,
  /if\(buildState\)\{/,
  /!!buildState/
 ])assert.match(html,re);
 const ui=await readFile(new URL('../living-library/live/card-creation.html',import.meta.url),'utf8');
 assert.match(ui,/const immutable=card\?\.permanentRegistry\?\.state==='registered'/);
 assert.match(ui,/\$\('saveCard'\)\.disabled=!card\|\|immutable/);
});
