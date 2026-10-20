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
test('spent one-shot spells never appear in summon history; sealed rune card still can be inspected',()=>{
 const owner=mk('local.card.origin','初代法陣卡',null,{placement:'hover',containerId:null,ownerBookId:null});
 const consumed=mk('local.card.spell','解除綁定術',null,{placement:'contained',containerId:null,ownerBookId:null,
  consumedBy:owner.id,containment:{kind:'consumed_spell',holderId:owner.id,holderType:'card',state:'stored'}});
 const sealed=mk('local.card.sealed','初始法陣卡',null,{placement:'contained',containerId:null,ownerBookId:null,
  containment:{kind:'rune_contract',holderId:'local.rune.origin',holderType:'rune',state:'stored'}});
 assert.deepEqual(Summon.storedCards([owner,consumed,sealed],owner.id),[]);
 assert.equal(Summon.preview([owner,consumed,sealed],{holderId:owner.id,cardId:consumed.id}).allowed,false);
 assert.equal(Summon.preview([owner,consumed,sealed],{holderId:owner.id,cardId:consumed.id,action:'summon-spell'}).reason,'unsupported_action');
 assert.deepEqual(Summon.storedCards([owner,consumed,sealed],'local.rune.origin').map(c=>c.cardId),[sealed.id]);
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
  ownerBookId:'local.book.other',placement:'book',downloadTemplate:true});
 const floating=mk('local.card.floating','旅人記事',null,{containerId:null,ownerBookId:null,
  placement:'hover'});
 const contained=mk('local.card.old-spell','解除綁定術',null,{
  containerId:null,ownerBookId:null,placement:'contained',
  containment:{kind:'consumed_spell',holderId:'local.card.origin',holderType:'card',state:'stored'},downloadTemplate:true});
 const unsafe=mk('local.card.unique','特殊唯一卡',2,{unique:true,downloadTemplate:true});
 const db=storage([blank,shelf,floating,contained,unsafe]);
 const names=Craft.listDownloadableTemplates(db).map(x=>x.id);
 assert.equal(names.length,2);
 assert.deepEqual(new Set(names),new Set([Craft.UNBIND_BLUEPRINT_ID,shelf.id]));
 assert.equal(Craft.canDownloadTemplate(contained),false,'spent spell cannot be a historical template');
 assert.equal(Craft.canDownloadTemplate(floating),false,'regular unselected floating cards never appear');
 assert.equal(Craft.canDownloadTemplate(unsafe),false);
 assert.equal(Craft.canDownloadTemplate(blank),false);
});
test('template download transfers format into existing right-slot blank; existing world source is unchanged',()=>{
 const source=mk('local.card.stored','解除綁定術',null,{
  containerId:null,ownerBookId:null,placement:'hover',
  image:{dataUrl:'data:image/jpeg;base64,/9j/AA==',scale:1,x:50,y:50},
  description:'原卡仍在世界',cardNumber:'SPELL-0001',downloadTemplate:true});
 const blank=mk('local.card.blank','空白卡片',9);
 const db=storage([blank,source]),before=db.objects()[1];
 const made=Craft.forgeBlankFromTemplate(db,{
  blankId:blank.id,blankRevision:1,sourceId:source.id,sourceRevision:1},()=> 't2');
 assert.equal(made.id,blank.id);
 assert.equal(made.title,source.title);
 assert.deepEqual(made.image,source.image);
 assert.equal(made.downloadTemplate,false,'new copies do not inherit download permissions');
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
 const allowed=mk('local.card.free','花園',null,{placement:'hover',ownerBookId:null,containerId:null,downloadTemplate:true});
 const ok=storage([blank,allowed]),prev=ok.raw();
 for(const patch of [{sourceRevision:2},{blankRevision:2}])
  assert.throws(()=>Craft.forgeBlankFromTemplate(ok,{...params,sourceId:allowed.id,...patch}),/變動/);
 assert.equal(ok.raw(),prev);
 const blocked={getItem:ok.getItem,setItem:()=>{throw Error('quota')}};
 assert.throws(()=>Craft.forgeBlankFromTemplate(blocked,{...params,sourceId:allowed.id}),/quota/);
 assert.equal(ok.raw(),prev);
});


test('GBA downloads only cards that the player explicitly classified as 可複製卡',()=>{
 const normal=mk('local.card.normal','初代之書',null,{placement:'hover',containerId:null,ownerBookId:null});
 const spell=mk('local.card.spell','解除契約術',null,{placement:'hover',containerId:null,ownerBookId:null});
 const data=storage([normal,spell]);
 assert.deepEqual(Craft.listDownloadableTemplates(data).map(c=>c.id),[Craft.UNBIND_BLUEPRINT_ID],
  'built-in spell recipe is always present but arbitrary old cards are not');
 assert.equal(Craft.canDownloadTemplate({...normal,copyPolicy:'allowed'}),false,'legacy copy policy alone is not user opt-in');
 const marked=Craft.editCard(data,spell.id,{downloadTemplate:true});
 assert.equal(marked.downloadTemplate,true);
 assert.deepEqual(Craft.listDownloadableTemplates(data).map(c=>c.id),[Craft.UNBIND_BLUEPRINT_ID,spell.id]);
 const rebooted=storage(data.objects());
 assert.deepEqual(Craft.listDownloadableTemplates(rebooted).map(c=>c.id),[Craft.UNBIND_BLUEPRINT_ID,spell.id],'classification survives reload');
 const hidden=Craft.editCard(data,spell.id,{downloadTemplate:false});
 assert.equal(hidden.downloadTemplate,false);
 assert.deepEqual(Craft.listDownloadableTemplates(data).map(c=>c.id),[Craft.UNBIND_BLUEPRINT_ID],
  'unchecking removes only the player template; system spell recipe remains');
});
test('cannot opt protected, empty or invalid cards into GBA downloads',()=>{
 for(const patch of [
  {runeBinding:{runeId:'local.rune.origin',state:'bound'}},
  {scanContract:{artifactId:'scan.bundle.local.card.protected'} ,runeBinding:{runeId:'scan.bundle.local.card.protected',state:'bound'}},
  {unique:true},{copyPolicy:'forbidden'},{permanentCard:true},
  {ability:{type:'unsafe'}},{permissions:['remote']},
  {containment:{kind:'untrusted',holderId:'x'}},
  {upgradeRequest:{state:'requested'}}
 ]){
  const card=mk('local.card.protected','保護卡',null,{placement:'hover',containerId:null,ownerBookId:null,...patch});
  const db=storage([card]),before=db.raw();
  assert.throws(()=>Craft.editCard(db,card.id,{downloadTemplate:true}),Error,JSON.stringify(patch));
  assert.equal(db.raw(),before);
 }
 const blank=mk('local.card.blank','空白卡片',null,{placement:'hover',containerId:null,ownerBookId:null});
 const db=storage([blank]),before=db.raw();
 assert.throws(()=>Craft.editCard(db,blank.id,{downloadTemplate:true}),/不可列為可複製卡/);
 assert.throws(()=>Craft.editCard(db,blank.id,{downloadTemplate:'yes'}),/只能啟用或停用/);
 assert.equal(db.raw(),before);
});
test('changing a copyable source revision or removing opt-in blocks GBA writing atomically',()=>{
 const source=mk('local.card.source','允許下載',0,{downloadTemplate:true});
 const blank=mk('local.card.blank','空白卡片',9),db=storage([source,blank]);
 const unmarked=Craft.editCard(db,source.id,{downloadTemplate:false});
 const raw=db.raw();
 assert.throws(()=>Craft.forgeBlankFromTemplate(db,{blankId:blank.id,blankRevision:1,
  sourceId:unmarked.id,sourceRevision:unmarked.revision}),/不允許/);
 assert.equal(db.raw(),raw);
});
test('card creation UI exposes an independent copyable category, not overloaded card type',async()=>{
 const ui=await readFile(new URL('../living-library/live/card-creation.html',import.meta.url),'utf8');
 assert.match(ui,/id="editDownloadable" type="checkbox"/);
 assert.match(ui,/downloadTemplate:\$\('editDownloadable'\)\.checked/);
 assert.match(ui,/\$\('editDownloadable'\)\.checked=card\?\.downloadTemplate===true/);
 assert.match(ui,/可複製卡（顯示在 GBA 卡片下載）/);
});

test('GBA always shows a non-card unbinding RECIPE even after every physical spell is consumed',()=>{
 const db=storage([]);
 const templates=Craft.listDownloadableTemplates(db);
 assert.equal(templates.length,1);
 const recipe=templates[0];
 assert.equal(recipe.id,Craft.UNBIND_BLUEPRINT_ID);
 assert.equal(recipe.title,'解除契約卡');
 assert.equal(recipe.sourceKind,'system-copyable-recipe');
 assert.equal(Craft.canDownloadTemplate(recipe),false,'recipe itself is NOT a player card');
 assert.deepEqual(db.objects(),[],'recipe is not inserted in card book, world or localStorage');
});

test('built-in unbinding recipe downloads repeatedly into genuine blank media without resurrecting old spell',()=>{
 const a=mk('local.card.blank1','空白卡片',9);
 const db=storage([a]);
 const recipe=Craft.listDownloadableTemplates(db)[0];
 const made=Craft.forgeBlankFromTemplate(db,{blankId:a.id,blankRevision:a.revision,
  sourceId:recipe.id,sourceRevision:recipe.revision},()=> '2026-10-20T14:00:00Z');
 assert.equal(made.id,a.id);
 assert.equal(made.title,'解除契約卡');
 assert.equal(made.downloadTemplate,false,'individual generated copies cannot silently become templates');
 assert.equal(made.copyInfo.sourceId,recipe.id);
 assert.equal(db.objects().length,1,'system recipe is not stored as a phantom card');
 assert.equal(RuneSpell.isSpellCard(made),false,'GBA reader-held card cannot cast');
 const floating=Craft.placeCard(db,a.id,null);
 assert.equal(RuneSpell.isSpellCard(floating),true,'downloaded card can cast after leaving GBA');
 const b=mk('local.card.blank2','空白卡片',9);
 const again=storage([floating,b]);
 const regenerated=Craft.forgeBlankFromTemplate(again,{blankId:b.id,blankRevision:b.revision,
  sourceId:recipe.id,sourceRevision:recipe.revision});
 assert.equal(regenerated.id,b.id);
 assert.equal(again.objects().length,2,'new physical copy uses existing blank ID');
});

test('spell recipe rejects stale or fake versions without consuming blank or writing history',()=>{
 const blank=mk('local.card.blank','空白卡片',9),db=storage([blank]),before=db.raw();
 for(const choice of [
  {blankId:blank.id,blankRevision:blank.revision+1,sourceId:Craft.UNBIND_BLUEPRINT_ID,sourceRevision:1},
  {blankId:blank.id,blankRevision:blank.revision,sourceId:Craft.UNBIND_BLUEPRINT_ID,sourceRevision:999},
  {blankId:blank.id,blankRevision:blank.revision,sourceId:'system.template.nonexistent',sourceRevision:1}
 ]){assert.throws(()=>Craft.forgeBlankFromTemplate(db,choice),Error);assert.equal(db.raw(),before)}
 const blocked={getItem:db.getItem,setItem:()=>{throw Error('quota')}};
 assert.throws(()=>Craft.forgeBlankFromTemplate(blocked,{blankId:blank.id,blankRevision:blank.revision,
  sourceId:Craft.UNBIND_BLUEPRINT_ID,sourceRevision:1}),/quota/);
 assert.equal(db.raw(),before);
});

test('GBA exposes blueprint browsing before card insertion but requires blank before writing',async()=>{
 const runtime=await readFile(new URL('../living-library/live/atlas-runtime.js',import.meta.url),'utf8');
 const model=await readFile(new URL('../living-library/live/atlas-model.js',import.meta.url),'utf8');
 assert.match(runtime,/screenMode='download';templateIndex=0/,'download selection opens recipe browser');
 assert.doesNotMatch(runtime,/if\(screenMode==='download'&&!craft\.isBlankCopyMedium/,
  'GBA must not kick users out of browser when right reader well is empty');
 assert.match(runtime,/if\(!reader\|\|!craft\.isBlankCopyMedium\?\.\(reader\.record\)\)/,
  'writing still requires a valid blank card');
 assert.match(model,/else if\(mode==='download'\)\{[\s\S]*?請建立可複製卡/,
  'empty reader shows downloadable template titles');
});
