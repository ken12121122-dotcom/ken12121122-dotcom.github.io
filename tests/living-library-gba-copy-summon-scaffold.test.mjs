import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
const require=createRequire(import.meta.url);
const Craft=require('../living-library/live/object-craft.js');
const Atlas=require('../living-library/live/atlas-state.js');
const Summon=require('../living-library/live/summon-plan.js');
const mk=(id,title,slot,over={})=>({
 id,objectType:'card',templateId:'blank-card-v1',title,description:'',
 image:null,cardNumber:'',cardType:'',linkedCardId:null,ability:null,
 permissions:[],reusePolicy:'not-configured',status:'draft',placement:'book',
 ownerBookId:Atlas.ATLAS,containerId:Atlas.ATLAS,slotIndex:slot,
 revision:1,createdAt:'t0',updatedAt:'t0',...over
});
function storage(objects){
 let data=JSON.stringify({version:Craft.VERSION,objects});
 return {getItem:k=>k===Craft.KEY?data:null,setItem:(k,v)=>{assert.equal(k,Craft.KEY);data=v},
 objects:()=>JSON.parse(data).objects,raw:()=>data};
}
const copy=(db,b,s)=>Craft.copyIntoBlankCard(db,{blankId:b.id,blankRevision:b.revision,
 sourceId:s.id,sourceRevision:s.revision},()=> 't1');
test('GBA uses only unmodified blank card in right well as the copy medium',()=>{
 const blank=mk('local.card.blank','空白卡片',9);
 assert.equal(Craft.isBlankCopyMedium(blank),true);
 for(const over of [{title:'二代卡'},{slotIndex:0},{image:{dataUrl:'x'}},
  {cardNumber:'001'},{description:'a'},{copyInfo:{sourceId:'a'}},
  {runeBinding:{runeId:Craft.ORIGIN_RUNE,state:'bound'}},
  {containerId:null},{placement:'hover'}])
  assert.equal(Craft.isBlankCopyMedium({...blank,...over}),false,JSON.stringify(over));
});
test('GBA SOURCE list is left 9 display slots, not public cards or remote assets',()=>{
 const blank=mk('local.card.blank','空白卡片',9);
 const a=mk('local.card.a','一般設計卡',6),b=mk('local.card.b','卡片B',0);
 const outside=mk('local.card.outside','世界卡',3,{containerId:null,ownerBookId:null,placement:'hover'});
 const linked=mk('local.card.linked','已綁定卡',2,{runeBinding:{runeId:Craft.ORIGIN_RUNE,state:'bound'}});
 const unique=mk('local.card.unique','唯一卡',1,{copyPolicy:'forbidden'});
 const db=storage([blank,a,b,outside,linked,unique]);
 assert.deepEqual(Craft.copySources(db).map(x=>x.id),[b.id,a.id]);
});
test('GBA copies appearance to the existing blank-ID, no ability or duplicate identity',()=>{
 const target=mk('local.card.blank','空白卡片',9,{cardNumber:''});
 const source=mk('local.card.source','雲霧符文',4,{
  description:'使用者撰寫的卡片說明',cardType:'圖鑑設計',
  image:{dataUrl:'data:image/jpeg;base64,/9j/AA==',scale:1,x:50,y:50},
  cardNumber:'UNIQUE-004',linkedCardId:'public.calendar',
  copyPolicy:'allowed',createdAt:'s0',updatedAt:'s1'
 });
 const db=storage([source,target]);
 const before=db.objects()[0];
 const result=copy(db,target,source);
 assert.equal(result.id,target.id);
 assert.equal(result.title,source.title);
 assert.deepEqual(result.image,source.image);
 assert.equal(result.description,source.description);
 assert.equal(result.cardType,source.cardType);
 assert.equal(result.cardNumber,'','unique edition/number never duplicated');
 assert.equal(result.linkedCardId,null,'no public action linkage/authority copied');
 assert.equal(result.ability,null);
 assert.deepEqual(result.permissions,[]);
 assert.equal(result.containerId,Atlas.ATLAS);
 assert.equal(result.slotIndex,9);
 assert.equal(result.revision,2);
 assert.deepEqual(result.copyInfo,{kind:'appearance',sourceId:source.id,sourceRevision:1,copiedAt:'t1'});
 assert.deepEqual(db.objects()[0],before);
 assert.equal(db.objects().length,2,'no duplicate objects');
 assert.equal(Craft.listCards(db).length,2);
 assert.equal(Craft.isBlankCopyMedium(result),false,'one blank medium is spent');
});
test('GBA copy cannot duplicate a contract, consumed spell, L2, exclusive or used destination',()=>{
 for(const patch of [
  {runeBinding:{runeId:Craft.ORIGIN_RUNE,state:'cardified'}},
  {l2Certified:true},{unique:true},{cardType:'唯一'}, {certification:{level:'L2'}},
  {ability:{kind:'spell'}},{permissions:['admin']},{reusePolicy:'unique'},
  {upgradeRequest:{state:'requested'}},{containment:{kind:'consumed_spell',holderId:'a'}}
 ]){
  const source=mk('local.card.source','保護卡',0,patch);
  assert.equal(Craft.canCopyAppearance(source),false,JSON.stringify(patch));
  const target=mk('local.card.blank','空白卡片',9);
  const db=storage([source,target]),before=db.raw();
  assert.throws(()=>copy(db,target,source),/不允許/);
  assert.equal(db.raw(),before);
 }
});
test('stale source/blank revisions and same object never partially modify the card store',()=>{
 const blank=mk('local.card.blank','空白卡片',9),source=mk('local.card.source','起源卡',0);
 const db=storage([blank,source]),before=db.raw();
 const args=[{blankId:blank.id,blankRevision:9,sourceId:source.id,sourceRevision:1},
  {blankId:blank.id,blankRevision:1,sourceId:source.id,sourceRevision:9},
  {blankId:blank.id,blankRevision:1,sourceId:blank.id,sourceRevision:1}];
 for(const params of args)assert.throws(()=>Craft.copyIntoBlankCard(db,params),Error);
 assert.equal(db.raw(),before);
 const fail={getItem:db.getItem,setItem:()=>{throw Error('quota')}};
 assert.throws(()=>Craft.copyIntoBlankCard(fail,args[0]),Error);
 assert.equal(db.raw(),before);
});
test('consumed spell can be DISCOVERED for future summon but not respawned yet',()=>{
 const owner=mk('local.card.origin','初代法陣卡',null,{placement:'hover',containerId:null,ownerBookId:null});
 const consumed=mk('local.card.spell','解除綁定術',null,{placement:'contained',containerId:null,ownerBookId:null,
  containment:{kind:'consumed_spell',holderId:owner.id,holderType:'card',state:'stored',absorbedAt:'t1'}});
 const other=mk('local.card.other','別的物件內卡',null,{placement:'contained',containerId:null,ownerBookId:null,
  containment:{kind:'consumed_spell',holderId:'local.card.different',holderType:'card',state:'stored'}});
 const before=structuredClone([owner,consumed,other]);
 assert.deepEqual(Summon.storedCards([owner,consumed,other],owner.id).map(x=>x.cardId),[consumed.id]);
 const preview=Summon.preview([owner,consumed,other],{holderId:owner.id,cardId:consumed.id});
 assert.equal(preview.allowed,true);
 assert.equal(preview.card.requiredAction,'summon-spell');
 const blocked=Summon.preview([owner,consumed,other],{holderId:owner.id,cardId:consumed.id,action:'summon-spell'});
 assert.equal(blocked.allowed,false);
 assert.equal(blocked.reason,'release_not_implemented');
 assert.deepEqual([owner,consumed,other],before,'summon architecture is entirely read-only');
});
test('3D GBA exposes copy by SELECT, navigates left page with arrows and requires confirmation',async()=>{
 const scene=await readFile(new URL('../living-library/live/index.html',import.meta.url),'utf8');
 const runtime=await readFile(new URL('../living-library/live/atlas-runtime.js',import.meta.url),'utf8');
 const model=await readFile(new URL('../living-library/live/atlas-model.js',import.meta.url),'utf8');
 assert.match(scene,/id="atlasCopyDialog"/);
 assert.match(scene,/id="atlasCopyConfirm"/);
 assert.match(scene,/id="atlasCopyCancel"/);
 assert.match(runtime,/craft\.isBlankCopyMedium\?\.\(reader\.record\)/);
 assert.match(runtime,/\['card','observe','copy'\]/);
 assert.match(runtime,/key==='DOWN'\?1:length-1/);
 assert.match(runtime,/key==='A'\)\{[\s\S]*presentCopyConfirmation\(reader\)/);
 assert.match(runtime,/craft\.copyIntoBlankCard\(storage,selection\)/);
 assert.match(model,/else if\(mode==='copy'\)/);
 assert.match(model,/SELECT 切換模式/);
 assert.match(model,/↑↓ 選卡 A 複製/);
});
