import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
const require=createRequire(import.meta.url);
const Craft=require('../living-library/live/object-craft.js');
const Permanent=require('../living-library/live/permanent-cards.js');
const Build=require('../living-library/live/permanent-build.js');
const KEY=Craft.KEY,ATLAS='local.atlas.compendium',RUNE='local.rune.origin';
const make=(records)=>{
 let state=JSON.stringify({version:Craft.VERSION,objects:records});
 return {getItem:k=>k===KEY?state:null,setItem:(k,val)=>{assert.equal(k,KEY);state=val},get raw(){return state},
  objects:()=>JSON.parse(state).objects};
};
function card({id='local.card.original',title='初代法陣卡',revision=1,runeBinding=null,placement='hover',containerId=null,slotIndex=null,containment=null}={}){
 return {id,title,description:'原創作品',cardNumber:'001',objectType:'card',templateId:'blank-card-v1',
  status:'draft',revision,placement,containerId,ownerBookId:containerId,slotIndex,runeBinding,containment};
}
function staged(){
 const storage=make([card()]);
 const seal=Craft.sealRuneCard(storage,RUNE,'local.card.original',1);
 const shown=Craft.materializeRuneCard(storage,RUNE,seal.id,seal.revision);
 const reader=Craft.placeCard(storage,shown.id,ATLAS,9);
 return {storage,reader};
}
test('GBA recognizes a physically inserted, previously bound original card, not its editable number',()=>{
 const {storage,reader}=staged();const scan=Permanent.inspect(storage,reader.id);
 assert.equal(scan.eligible,true);assert.equal(scan.proposedNumber,1);
 assert.equal(scan.scope,'local-only');assert.equal(scan.verifiedByAI,false);
 assert.equal(reader.cardNumber,'001','manual display number does not assign a permanent identity');
});
test('permanent upgrade is atomic, retains ID and art, and is irreversible to ordinary editor/unbinding',()=>{
 const {storage,reader}=staged(),before=storage.raw;
 assert.throws(()=>Permanent.register(storage,{cardId:reader.id,expectedRevision:reader.revision,number:1,ownerConfirmed:false}),/最終確認/);
 assert.equal(storage.raw,before);
 const verified=Permanent.register(storage,{cardId:reader.id,expectedRevision:reader.revision,number:1,ownerConfirmed:true},()=> '2026-10-17T00:00:00Z');
 assert.equal(verified.id,reader.id);assert.equal(verified.title,reader.title);
 assert.equal(verified.permanentNumber,'001');assert.equal(verified.permanentRegistry.number,1);
 assert.equal(verified.permanentRegistry.slotIndex,0);
 assert.equal(verified.containerId,ATLAS);assert.equal(verified.slotIndex,0);
 assert.equal(verified.runeBinding.state,'cardified');
 assert.equal(Permanent.list(storage).length,1);
 assert.equal(Craft.getRuneBoundCard(storage).id,reader.id);
 assert.throws(()=>Craft.editCard(storage,reader.id,{title:'改名'}),/永久卡/);
 assert.throws(()=>Craft.recycleCard(storage,reader.id),/永久/);
 assert.throws(()=>Craft.placeCard(storage,reader.id,'local.atlas.collection',0),/永久卡/);
 assert.throws(()=>Craft.placeCard(storage,reader.id,ATLAS,1),/永久登錄|永久卡/);
 assert.throws(()=>Craft.restoreUncommittedRuneCard(storage),/封印後|永久/);
 assert.throws(()=>Permanent.register(storage,{cardId:reader.id,expectedRevision:verified.revision,number:2,ownerConfirmed:true}),/重新/);
});
test('GBA exclusively moves the SAME permanent original between its BOOK well and reader zero',()=>{
 const {storage,reader}=staged();
 const cert=Permanent.register(storage,{cardId:reader.id,expectedRevision:reader.revision,number:1,ownerConfirmed:true});
 const checked=Permanent.toReader(storage,cert.id,cert.revision);
 assert.equal(checked.id,cert.id);assert.equal(checked.slotIndex,9);
 assert.equal(Permanent.list(storage)[0].permanentRegistry.slotIndex,0);
 assert.throws(()=>Permanent.toReader(storage,checked.id,checked.revision),/BOOK/);
 const placed=Craft.placeCard(storage,checked.id,null);
 assert.equal(placed.placement,'hover');assert.equal(placed.runeBinding.state,'cardified');
 assert.throws(()=>Permanent.archive(storage,placed.id,placed.revision),/0 號/);
 const reinsert=Craft.placeCard(storage,placed.id,ATLAS,9);
 const archived=Permanent.archive(storage,reinsert.id,reinsert.revision);
 assert.equal(archived.slotIndex,0);
 assert.equal(storage.objects().length,1,'does not create a second physical original card');
});
test('permanent slot belongs to the original ID even while card is in GBA, hover, or world; archive fails closed on conflict',()=>{
 const {storage,reader}=staged();
 let original=Permanent.register(storage,{cardId:reader.id,expectedRevision:reader.revision,number:1,ownerConfirmed:true});
 const second={...card({id:'local.card.another',title:'一般卡'}),runeBinding:null};
 const state={version:Craft.VERSION,objects:[...storage.objects(),second]};
 storage.setItem(KEY,JSON.stringify(state));
 original=Permanent.toReader(storage,original.id,original.revision);
 const snap=storage.raw;
 assert.throws(()=>Craft.placeCard(storage,second.id,ATLAS,0),/永久保留/);
 assert.equal(storage.raw,snap);
 original=Permanent.archive(storage,original.id,original.revision);
 original=Permanent.toReader(storage,original.id,original.revision);
 const corrupted=storage.objects();
 const at=corrupted.findIndex(x=>x.id===second.id);
 corrupted[at]={...corrupted[at],placement:'book',containerId:ATLAS,ownerBookId:ATLAS,slotIndex:0};
 storage.setItem(KEY,JSON.stringify({version:Craft.VERSION,objects:corrupted}));
 const before=storage.raw;
 assert.throws(()=>Permanent.archive(storage,original.id,original.revision),/佔位衝突/);
 assert.equal(storage.raw,before);
});
test('world object anchor can only move after SAME permanent card transforms into sealed rune',()=>{
 const {storage,reader}=staged();
 let reg=Permanent.register(storage,{cardId:reader.id,expectedRevision:reader.revision,number:1,ownerConfirmed:true});
 assert.throws(()=>Permanent.movableAnchor(storage,reg.id,reg.revision,{x:2,y:0,z:2}),/已具現化/);
 reg=Permanent.toReader(storage,reg.id,reg.revision);
 reg=Craft.placeCard(storage,reg.id,null);
 reg=Craft.returnRuneCard(storage,RUNE,reg.id,reg.revision);
 const moved=Permanent.movableAnchor(storage,reg.id,reg.revision,{x:3.2,y:0,z:-1.5},Math.PI/3);
 assert.deepEqual(moved.permanentRegistry.anchor.position,{x:3.2,y:0,z:-1.5});
 assert.equal(moved.runeBinding.state,'sealed');
 assert.equal(moved.permanentRegistry.number,1);
 assert.throws(()=>Permanent.movableAnchor(storage,moved.id,moved.revision,{x:88,y:0,z:1}),/不合法/);
 assert.equal(Permanent.list(storage)[0].permanentRegistry.number,1);
});
test('projection permits free placement, grid snapping, nudging and rotation with invalid red positions',()=>{
 assert.equal(Build.valid({x:0,z:0}),true);
 assert.equal(Build.place({x:1.26,z:2.73},{grid:true}).position.x,1.5);
 assert.equal(Build.place({x:1.26,z:2.73},{grid:true}).position.z,2.5);
 assert.equal(Build.place({x:99,z:1}).valid,false);
 assert.equal(Build.nudge({x:1,z:2},'z',2,{grid:true}).position.z,3);
 assert.ok(Build.rotate(0)>0);
});
test('GBA must not directly summon or consume a world rune; scene loads canonical registry and controller',async()=>{
 const html=await readFile(new URL('../living-library/live/index.html',import.meta.url),'utf8');
 const runtime=await readFile(new URL('../living-library/live/atlas-runtime.js',import.meta.url),'utf8');
 assert.match(html,/permanent-cards\.js/);
 assert.match(html,/permanent-gba\.js/);
 assert.match(html,/id="atlasPermanentDialog"/);
 assert.match(html,/id="atlasPermanentHold"/);
 assert.match(runtime,/PermanentGba\?\.create/);
 assert.match(runtime,/permanentGba\?\.activate\(reader\)/);
 assert.match(runtime,/menuIndex=\(menuIndex\+\(key==='DOWN'\?1:4\)\)%5/);
 const gba=await readFile(new URL('../living-library/live/permanent-gba.js',import.meta.url),'utf8');
 assert.match(gba,/Registry\.toReader\(storage,card\.id,card\.revision\)/);
 assert.match(gba,/Registry\.archive\(storage,reader\.id,reader\.record\.revision\)/);
 assert.doesNotMatch(gba,/materializeRuneCard|returnRuneCard|runeGroup/);
});
