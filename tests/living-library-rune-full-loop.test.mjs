import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
const require=createRequire(import.meta.url);
const Craft=require('../living-library/live/object-craft.js');
const State=require('../living-library/live/atlas-state.js');
const Spell=require('../living-library/live/rune-unbind-spell.js');
function card(id,title='初代法陣卡',revision=1,patch={}){
 return {id,objectType:'card',templateId:'blank-card-v1',title,
 description:'original',image:null,cardNumber:'editable-num',
 status:'draft',placement:'hover',ownerBookId:null,containerId:null,slotIndex:null,
 revision,createdAt:'2026-10-17',updatedAt:'2026-10-17',...patch};
}
function memory(records){
 let json=JSON.stringify({version:Craft.VERSION,objects:records});
 return {getItem:k=>k===Craft.KEY?json:null,setItem:(k,value)=>{assert.equal(k,Craft.KEY);json=value},
 raw:()=>json,all:()=>JSON.parse(json).objects};
}
const originId='local.card.origin',spellId='local.card.unbind',blankId='local.card.blank';
function make(){
 const target=card(originId);
 const spell=card(spellId,'解除契約卡',1,{downloadTemplate:true});
 const blank=card(blankId,'空白卡片',1,{description:'',image:null,cardNumber:'',cardType:'',
  linkedCardId:null,ability:null,permissions:[],placement:'book',
  containerId:State.ATLAS,ownerBookId:State.ATLAS,slotIndex:9});
 return memory([target,spell,blank]);
}
function unbind(db,target,spell){
 return Craft.unbindAndAbsorbSpell(db,{
  runeId:Craft.ORIGIN_RUNE,targetId:target.id,targetRevision:target.revision,
  spellId:spell.id,spellRevision:spell.revision},()=> '2026-10-17T15:00:00Z');
}
test('full cycle: original card is sealed, manifested, returned, and unbound while the spent spell disappears',()=>{
 const db=make(),snapshot=db.all();
 let target=Craft.sealRuneCard(db,Craft.ORIGIN_RUNE,originId,1);
 assert.equal(target.runeBinding.state,'sealed');
 assert.equal(target.placement,'contained');
 assert.equal(target.containment.holderId,Craft.ORIGIN_RUNE);
 assert.equal(Craft.floatingCards(db).some(x=>x.id===originId),false);
 target=Craft.materializeRuneCard(db,Craft.ORIGIN_RUNE,originId,target.revision);
 assert.equal(target.runeBinding.state,'cardified');
 assert.equal(target.placement,'hover');
 assert.equal(target.id,originId);
 target=Craft.returnRuneCard(db,Craft.ORIGIN_RUNE,originId,target.revision);
 assert.equal(target.runeBinding.state,'sealed');
 assert.equal(target.containment.holderId,Craft.ORIGIN_RUNE);
 assert.equal(Craft.floatingCards(db).some(x=>x.id===originId),false);
 target=Craft.materializeRuneCard(db,Craft.ORIGIN_RUNE,originId,target.revision);
 assert.equal(target.runeBinding.state,'cardified');
 const used=unbind(db,target,snapshot[1]);
 assert.equal(used.target.runeBinding,undefined);
 assert.equal(used.target.placement,'hover');
 assert.equal(used.target.cardNumber,'editable-num');
 assert.equal(used.spellId,spellId);assert.equal(used.consumed,true);
 assert.equal(db.all().length,2,'spent spell has no stored record');
 assert.equal(Craft.getRuneBoundCard(db),null);
 assert.equal(Craft.floatingCards(db).some(x=>x.id===spellId),false);
});
test('copyable world spell must be downloaded BEFORE its one-shot consumption; no historic source remains',()=>{
 const db=make();
 const originalSpell=db.all().find(c=>c.id===spellId);
 assert.equal(Craft.canDownloadTemplate(originalSpell),true);
 const generated=Craft.forgeBlankFromTemplate(db,{blankId,blankRevision:1,
  sourceId:originalSpell.id,sourceRevision:originalSpell.revision});
 assert.equal(generated.id,blankId);assert.equal(generated.title,'解除契約卡');
 assert.equal(Spell.isSpellCard(generated),false,'downloaded card is still in the GBA reader');
 let target=Craft.sealRuneCard(db,Craft.ORIGIN_RUNE,originId,1);
 target=Craft.materializeRuneCard(db,Craft.ORIGIN_RUNE,originId,target.revision);
 const first=unbind(db,target,originalSpell);
 assert.equal(first.spellId,spellId);
 assert.equal(db.all().some(c=>c.id===spellId),false);
 assert.equal(Craft.listDownloadableTemplates(db).some(c=>c.id===spellId),false);
 const newSpell=Craft.placeCard(db,blankId,null);
 assert.equal(Spell.isSpellCard(newSpell),true);
 const reSealed=Craft.sealRuneCard(db,Craft.ORIGIN_RUNE,originId,db.all().find(x=>x.id===originId).revision);
 const rematerialized=Craft.materializeRuneCard(db,Craft.ORIGIN_RUNE,originId,reSealed.revision);
 const second=unbind(db,rematerialized,newSpell);
 assert.equal(second.spellId,blankId);
 assert.equal(db.all().length,1,'both spent spells permanently deleted');
 assert.equal(Craft.getRuneBoundCard(db),null);
});
test('future permanent upgrade locks unbinding WITHOUT treating manually editable cardNumber as permanent ID',()=>{
 for(const patch of [
  {l2Certified:true},
  {permanentCard:true},
  {permanentRegistration:{state:'registered',number:'P-1'}},
  {permanentRegistry:{state:'registered'}},
  {permanentNumber:'PERM-100'},
  {certification:{level:'L2'}}
 ]){
  const db=make();
  let target=Craft.sealRuneCard(db,Craft.ORIGIN_RUNE,originId,1);
  target=Craft.materializeRuneCard(db,Craft.ORIGIN_RUNE,originId,target.revision);
  const objects=db.all(),at=objects.findIndex(x=>x.id===originId);
  objects[at]={...objects[at],...patch,revision:objects[at].revision+1};
  db.setItem(Craft.KEY,JSON.stringify({version:Craft.VERSION,objects}));
  const locked=Craft.getRuneBoundCard(db),spell=objects.find(x=>x.id===spellId),before=db.raw();
  assert.equal(Craft.isPermanentRuneCard(locked),true,JSON.stringify(patch));
  assert.equal(Craft.canDownloadTemplate(locked),false);
  assert.throws(()=>unbind(db,locked,spell),/永久編號卡/);
  assert.equal(db.raw(),before,'permanent unbind rejection cannot consume spell');
  const resealed=Craft.returnRuneCard(db,Craft.ORIGIN_RUNE,locked.id,locked.revision);
  assert.equal(resealed.runeBinding.state,'sealed','form-change remains distinct from unbinding');
  assert.equal(Craft.isPermanentRuneCard(resealed),true);
 }
 const db=make(),manual=db.all()[0];
 assert.equal(manual.cardNumber,'editable-num');
 assert.equal(Craft.isPermanentRuneCard(manual),false);
});
test('stale return revisions, wrong phase and failed writes do not alter any original ID',()=>{
 const db=make(),target=Craft.sealRuneCard(db,Craft.ORIGIN_RUNE,originId,1);
 const initial=db.raw();
 assert.throws(()=>Craft.returnRuneCard(db,Craft.ORIGIN_RUNE,originId,target.revision),/具現化/);
 assert.equal(db.raw(),initial);
 const original=Craft.materializeRuneCard(db,Craft.ORIGIN_RUNE,originId,target.revision),before=db.raw();
 assert.throws(()=>Craft.returnRuneCard(db,Craft.ORIGIN_RUNE,originId,original.revision-1),/已變動/);
 const blocked={getItem:db.getItem,setItem:()=>{throw Error('quota')}};
 assert.throws(()=>Craft.returnRuneCard(blocked,Craft.ORIGIN_RUNE,originId,original.revision),/quota/);
 assert.equal(db.raw(),before);
});
test('world scene has both 3-second transitions and irreversible-upgrade checks',async()=>{
 const html=await readFile(new URL('../living-library/live/index.html',import.meta.url),'utf8');
 for(const pattern of [
  /startRuneTransfer\('seal',record\)/,/startRuneTransfer\('cardify',latest\)/,
  /startRuneTransfer\('return',card\)/,/craft\.returnRuneCard\(localStorage/,
  /id="runeReturnDialog"/,/id="runeReturnConfirm"/,
  /returnCardHold\.pointerDown\(e,manifestation\.id\)/,
  /returnCardHold\.tick\(\);updateRuneReturnVisual\(now\)/,
  /isPermanentRuneCard\(current\)/,
  /ContractPolicy\.canUnbind\(bound\)/,
  /unbindAndAbsorbSpell\(localStorage/
 ])assert.match(html,pattern);
 assert.match(html,/runeGroup\.visible=!cardify/);
 assert.match(html,/window\.LivingObjectCraft\.isPermanentRuneCard/);
});
