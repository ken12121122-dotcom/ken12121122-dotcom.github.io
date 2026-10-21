import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
const require=createRequire(import.meta.url);
const Craft=require('../living-library/live/object-craft.js');
const BOOK='local.atlas.compendium',NORMAL='local.atlas.collection';
const card=(id,title='空白卡片',patch={})=>({id,objectType:'card',templateId:'blank-card-v1',
 title,description:'',image:null,cardNumber:'',cardType:'',status:'draft',placement:'hover',
 containerId:null,ownerBookId:null,slotIndex:null,revision:1,...patch});
function storage(cards){
 let raw=JSON.stringify({version:Craft.VERSION,objects:cards});
 return {getItem:k=>k===Craft.KEY?raw:null,setItem:(k,v)=>{assert.equal(k,Craft.KEY);raw=v},
  raw:()=>raw,all:()=>JSON.parse(raw).objects};
}
const registered=(id,n)=>card(id,'永久編號卡',{permanentCard:true,permanentNumber:String(n).padStart(3,'0'),
 permanentRegistry:{state:'registered',number:n,bookId:BOOK,cardId:id,slotIndex:n-1}});
test('nine unique-book showcase wells accept ONLY a registered original in its exact own slot',()=>{
 const normal=card('local.card.blank');
 const manual=card('local.card.display','偽編號卡',{cardNumber:'001'});
 const inUpgrade=card('local.card.upgrade','申請升級',{upgradeRequest:{state:'requested'}});
 const origin=card('local.card.origin','初代法陣卡',{runeBinding:{runeId:'local.rune.origin',state:'cardified'}});
 const eye=card('local.card.eye','真實之眼',{runeBinding:{runeId:'artifact.truth-eye',state:'bound'},
  truthEyeContract:{artifactId:'artifact.truth-eye',form:'card'}});
 const fakePermanent=card('local.card.fake','手動永久',{l2Certified:true,permanentCard:true,permanentNumber:'001'});
 for(const c of [normal,manual,inUpgrade,origin,eye,fakePermanent])
  for(let i=0;i<9;i++)assert.equal(Craft.canStoreBoundCard(c,BOOK,i),false,
   'nonregistered '+c.id+' must be rejected in showcase well '+i);
 for(let i=0;i<9;i++){
  const original=registered('local.card.real.'+i,i+1);
  for(let j=0;j<9;j++)assert.equal(Craft.canStoreBoundCard(original,BOOK,j),i===j,
   'registered original belongs only to its canonical permanent-number address');
  assert.equal(Craft.canStoreBoundCard(original,BOOK,9),true);
  assert.equal(Craft.canStoreBoundCard({...original,permanentRegistry:{...original.permanentRegistry,cardId:'local.card.someone-else'}},BOOK,i),false);
 }
});
test('right GBA reader accepts all physically available ordinary, bound and permanent cards',()=>{
 const all=[
  card('local.card.empty'),
  card('local.card.edited','玩家編輯卡',{image:{dataUrl:'synthetic'},cardNumber:'001'}),
  card('local.card.origin','初始法陣',{runeBinding:{runeId:'local.rune.origin',state:'cardified'}}),
  card('local.card.eye','真實之眼',{runeBinding:{runeId:'artifact.truth-eye',state:'bound'},
   truthEyeContract:{artifactId:'artifact.truth-eye',form:'card'}}),
  registered('local.card.perm',1)
 ];
 for(const one of all){
  assert.equal(Craft.canStoreBoundCard(one,BOOK,9),true,one.id);
  const db=storage([one]),placed=Craft.placeCard(db,one.id,BOOK,9);
  assert.equal(placed.id,one.id);
  assert.equal(placed.slotIndex,9);
  assert.equal(db.all().length,1,'never mint extra original cards');
 }
});
test('ordinary books remain available while unique-book display stays locked',()=>{
 const regular=card('local.card.regular');
 const eye=card('local.card.eye','真實之眼',{runeBinding:{runeId:'artifact.truth-eye',state:'bound'},
  truthEyeContract:{artifactId:'artifact.truth-eye',form:'card'}});
 const permanent=registered('local.card.permanent',1);
 assert.equal(Craft.canStoreBoundCard(regular,NORMAL,0),true);
 assert.equal(Craft.canStoreBoundCard(eye,NORMAL,0),true);
 assert.equal(Craft.canStoreBoundCard(eye,'local.book.user-built',0),true);
 assert.equal(Craft.canStoreBoundCard(permanent,NORMAL,0),false);
 assert.equal(Craft.canStoreBoundCard(regular,BOOK,0),false);
 assert.equal(Craft.canStoreBoundCard(eye,BOOK,0),false);
});
test('old illegal empty and unupgraded bound cards return to orbit with same ID, contents and contracts',()=>{
 const art={dataUrl:'kept-picture'};
 const blank=card('local.card.blank');
 const bound=card('local.card.bound','真實之眼',{runeBinding:{runeId:'artifact.truth-eye',state:'bound'},
  truthEyeContract:{artifactId:'artifact.truth-eye',form:'card'},image:art,description:'自製藝術品'});
 const real=registered('local.card.permanent',1);
 const reader=card('local.card.reader','右側讀取');
 const ordinary=card('local.card.normal','其他卡片');
 const inLeft=(x,slot)=>({...x,placement:'book',containerId:BOOK,ownerBookId:BOOK,slotIndex:slot});
 const db=storage([inLeft(blank,1),inLeft(bound,2),inLeft(real,0),inLeft(reader,9),ordinary]);
 const before=db.all();
 const corrected=Craft.restoreUnregisteredUniqueWells(db,()=> '2026-10-18T10:00:00Z');
 assert.deepEqual(corrected,[blank.id,bound.id]);
 const after=db.all();
 for(const id of corrected){
  const from=before.find(x=>x.id===id),to=after.find(x=>x.id===id);
  assert.deepEqual(to,{...from,placement:'hover',containerId:null,ownerBookId:null,slotIndex:null,
   revision:from.revision+1,updatedAt:'2026-10-18T10:00:00Z'});
 }
 for(const id of [real.id,reader.id,ordinary.id])
  assert.deepEqual(after.find(x=>x.id===id),before.find(x=>x.id===id));
 assert.deepEqual(Craft.restoreUnregisteredUniqueWells(db),[]);
 assert.equal(db.all().length,before.length);
});
test('invalid card movements and failed repair writes never overwrite existing saved records',()=>{
 const one=card('local.card.invalid');
 const db=storage([one]);
 const raw=db.raw();
 for(let slot=0;slot<9;slot++)assert.throws(()=>Craft.placeCard(db,one.id,BOOK,slot),/唯一圖鑑左側/);
 assert.equal(db.raw(),raw);
 const old=card('local.card.previous','未升級卡',{placement:'book',containerId:BOOK,ownerBookId:BOOK,slotIndex:0});
 const legacy=storage([old]),snap=legacy.raw();
 assert.throws(()=>Craft.restoreUnregisteredUniqueWells({
  getItem:legacy.getItem,setItem:()=>{throw Error('quota exceeded')}},()=> 'now'),/quota/);
 assert.equal(legacy.raw(),snap);
});
test('visual drag highlight uses same canonical eligibility as persistent placement',async()=>{
 const code=await readFile(new URL('../living-library/live/atlas-runtime.js',import.meta.url),'utf8');
 const html=await readFile(new URL('../living-library/live/index.html',import.meta.url),'utf8');
 assert.match(code,/craft\.canStoreBoundCard\(pointer\.entry\.record,target\.id,target\.slot\)/);
 assert.match(html,/ObjectCraft\.restoreUnregisteredUniqueWells\(localStorage\)/);
});
