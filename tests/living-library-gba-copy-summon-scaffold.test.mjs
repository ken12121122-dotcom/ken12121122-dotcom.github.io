import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
const require=createRequire(import.meta.url);
const Craft=require('../living-library/live/object-craft.js');
const Atlas=require('../living-library/live/atlas-state.js');
const Summon=require('../living-library/live/summon-plan.js');
const RuneSpell=require('../living-library/live/rune-unbind-spell.js');
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
test('copy a repeatable unbinding spell template into blank media and cast the copy later',()=>{
 const source=mk('local.card.spell-master','解除綁定術',1);
 const blank=mk('local.card.blank','空白卡片',9);
 const db=storage([source,blank]);
 const copied=copy(db,blank,source);
 assert.equal(copied.title,'解除綁定術');
 assert.equal(Craft.isBlankCopyMedium(copied),false);
 assert.equal(RuneSpell.isSpellCard(copied),false,'GBA card is not a floating castable spell');
 const released=Craft.placeCard(db,blank.id,null);
 assert.equal(released.id,blank.id);
 assert.equal(RuneSpell.isSpellCard(released),true,'approved name-based local ritual remains castable');
 assert.equal(Craft.listCards(db).length,2);
 assert.equal(Craft.copySources(db).length,1,'the original template remains in left display slot');
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
test('retro GBA menu selects downloadable formats without requiring left 9 wells',async()=>{
 const scene=await readFile(new URL('../living-library/live/index.html',import.meta.url),'utf8');
 const runtime=await readFile(new URL('../living-library/live/atlas-runtime.js',import.meta.url),'utf8');
 const model=await readFile(new URL('../living-library/live/atlas-model.js',import.meta.url),'utf8');
 assert.match(scene,/id="atlasDownloadDialog"/);
 assert.match(scene,/id="atlasDownloadConfirm"/);
 assert.match(scene,/id="atlasDownloadCancel"/);
 assert.match(runtime,/craft\.listDownloadableTemplates\?\.\(storage\)/);
 assert.match(runtime,/screenMode=screenMode==='menu'\?'card':'menu'/);
 assert.match(runtime,/menuIndex===2/);
 assert.match(runtime,/presentDownloadConfirmation\(reader\)/);
 assert.match(runtime,/craft\.forgeBlankFromTemplate\(storage,selection\)/);
 assert.match(runtime,/finishDownload\(now\)/);
 assert.match(model,/else if\(mode==='menu'\)/);
 assert.match(model,/else if\(mode==='download'\)/);
 assert.match(model,/else if\(mode==='downloading'\)/);
 assert.match(model,/商店（未開放）/);
 assert.doesNotMatch(runtime,/craft\.copyIntoBlankCard\(storage,selection\)/,
  'old left-page copy mechanic is dormant until player approves its rules');
});
test('the GBA template catalogue uses previously created copies wherever physically located',()=>{
 const blank=mk('local.card.blank','空白卡片',9);
 const shelf=mk('local.card.shelf','自然法術',1,{containerId:'local.book.other',
  ownerBookId:'local.book.other',placement:'book'});
 const floating=mk('local.card.floating','旅人記事',null,{containerId:null,ownerBookId:null,
  placement:'hover'});
 const contained=mk('local.card.old-spell','解除綁定術',null,{
  containerId:null,ownerBookId:null,placement:'contained',
  containment:{kind:'consumed_spell',holderId:'local.card.origin',holderType:'card',state:'stored'}});
 const unsafe=mk('local.card.unique','特殊唯一卡',2,{unique:true});
 const db=storage([blank,shelf,floating,contained,unsafe]);
 const names=Craft.listDownloadableTemplates(db).map(x=>x.id);
 assert.equal(names.length,3);
 assert.deepEqual(new Set(names),new Set([shelf.id,floating.id,contained.id]));
 assert.equal(Craft.canDownloadTemplate(unsafe),false);
 assert.equal(Craft.canDownloadTemplate(blank),false);
});
test('template download transfers format into existing right-slot blank; stored source is unchanged',()=>{
 const source=mk('local.card.stored','解除綁定術',null,{
  containerId:null,ownerBookId:null,placement:'contained',
  containment:{kind:'consumed_spell',holderId:'local.card.origin',holderType:'card',state:'stored'},
  image:{dataUrl:'data:image/jpeg;base64,/9j/AA==',scale:1,x:50,y:50},
  description:'原卡仍被封存',cardNumber:'SPELL-0001'});
 const blank=mk('local.card.blank','空白卡片',9);
 const db=storage([blank,source]),before=db.objects()[1];
 const made=Craft.forgeBlankFromTemplate(db,{
  blankId:blank.id,blankRevision:1,sourceId:source.id,sourceRevision:1},()=> 't2');
 assert.equal(made.id,blank.id);
 assert.equal(made.title,source.title);
 assert.deepEqual(made.image,source.image);
 assert.equal(made.cardNumber,'');
 assert.equal(made.placement,'book');
 assert.equal(made.slotIndex,9);
 assert.equal(made.containment,undefined);
 assert.deepEqual(made.copyInfo,{kind:'gba-template-download',sourceId:source.id,
  sourceRevision:1,copiedAt:'t2'});
 assert.deepEqual(db.objects()[1],before);
 assert.equal(db.objects().length,2);
 assert.equal(Craft.isBlankCopyMedium(made),false);
});
test('template download rejects forbidden special cards, stale edits and quota failures atomically',()=>{
 const forbidden=mk('local.card.special','獨特符文',null,{
  placement:'hover',containerId:null,ownerBookId:null,runeBinding:{runeId:Craft.ORIGIN_RUNE,state:'bound'}});
 const blank=mk('local.card.blank','空白卡片',9);
 const db=storage([blank,forbidden]),before=db.raw();
 const params={blankId:blank.id,blankRevision:1,sourceId:forbidden.id,sourceRevision:1};
 assert.throws(()=>Craft.forgeBlankFromTemplate(db,params),/不允許/);
 assert.equal(db.raw(),before);
 const allowed=mk('local.card.free','花園',null,{placement:'hover',ownerBookId:null,containerId:null});
 const ok=storage([blank,allowed]),prev=ok.raw();
 for(const patch of [{sourceRevision:2},{blankRevision:2}])
  assert.throws(()=>Craft.forgeBlankFromTemplate(ok,{...params,sourceId:allowed.id,...patch}),/變動/);
 assert.equal(ok.raw(),prev);
 const blocked={getItem:ok.getItem,setItem:()=>{throw Error('quota')}};
 assert.throws(()=>Craft.forgeBlankFromTemplate(blocked,{...params,sourceId:allowed.id}),/quota/);
 assert.equal(ok.raw(),prev);
});

