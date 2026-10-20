import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const craft=require('../living-library/live/object-craft.js');
const catalog=require('../living-library/live/public-catalog.js');
const root=new URL('../',import.meta.url);
const read=path=>readFile(new URL(path,root),'utf8');
function storage(initial={}){
 const map=new Map(Object.entries(initial));
 return {getItem:key=>map.get(key)??null,setItem:(key,value)=>map.set(key,String(value)),get raw(){return map}};
}
const rand={randomUUID:(()=>{let n=0;return ()=>('00000000-0000-4000-8000-'+String(++n).padStart(12,'0'))})()};
test('card creation leaves a single empty hovering draft with stable identity and no permissions',()=>{
 const store=storage(),card=craft.createBlankCard(store,rand,()=> '2026-10-15T00:00:00Z');
 assert.match(card.id,/^local\.card\./);
 assert.equal(card.ownerBookId,null);
 assert.equal(card.containerId,null);
 assert.equal(card.placement,'hover');
 assert.equal(card.ability,null);
 assert.deepEqual(card.permissions,[]);
 assert.equal(card.status,'draft');
 assert.equal(card.reusePolicy,'not-configured');
 assert.equal(craft.floatingCards(store).length,1);
 assert.equal(craft.listBooks(store).length,0);
 const updated=craft.editCard(store,card.id,{title:'合書術草稿',description:'未實作的設計意圖'});
 assert.equal(updated.id,card.id);
 assert.equal(updated.revision,2);
 assert.equal(updated.ability,null);
 assert.equal(craft.listCards(store)[0].title,'合書術草稿');
 assert.equal(craft.floatingCards(store)[0].id,card.id);
});
test('book creation reuses object identity contract, starts empty and independently orbits',()=>{
 const store=storage();craft.createBlankCard(store,rand);
 const b=craft.createBlankBook(store,rand,'我的魔法實驗書');
 assert.match(b.id,/^local\.book\./);
 assert.equal(b.objectType,'book');
 assert.equal(b.active,true);
 assert.equal(b.openingMode,'legacy');
 assert.deepEqual(b.contentIds,[]);
 assert.equal(craft.listCards(store).length,1);
 assert.equal(craft.listBooks(store)[0].id,b.id);
 craft.setBookActive(store,b.id,false);
 assert.equal(craft.listBooks(store)[0].active,false);
 assert.equal(craft.floatingCards(store).length,1);
});
test('malformed local save and restricted patches never erase an existing record',()=>{
 const bad=storage({[craft.KEY]:'{malformed'});
 assert.throws(()=>craft.createBlankCard(bad,rand),/格式/);
 assert.equal(bad.getItem(craft.KEY),'{malformed');
 const store=storage(),card=craft.createBlankCard(store,rand);
 const before=store.getItem(craft.KEY);
 assert.throws(()=>craft.editCard(store,card.id,{ability:{kind:'arbitrary-js'}}),/只能/);
 assert.throws(()=>craft.createBlankBook(store,rand,' '.repeat(31)),/書名/);
 assert.equal(store.getItem(craft.KEY),before);
});
test('original Game Book destinations remain and both independent creation spells are embedded',async()=>{
 const c=catalog.buildCatalog();
 assert.equal(c.books.length,15);
 assert.deepEqual(c.bookCards.gaming,[
  'public.gba.runtime','public.vault.web','KW-GAME-MEMORY-PALACE-20261014',
  'public.gaming.card.creation','public.gaming.book.creation','public.gaming.atlas'
 ]);
 for(const [id,url] of [['public.gaming.card.creation','./card-creation.html'],['public.gaming.book.creation','./book-creation.html']]){
  assert.equal(c.cards[id].kind,'public_function');
  assert.equal(c.cards[id].url,url);
  await read('living-library/live/'+url.replace('./',''));
 }
 const live=await read('living-library/live/index.html');
 assert.match(live,/function syncPlayerObjects\(/);
 assert.match(live,/function refreshFloatingCards\(/);
 assert.match(live,/newFloatingCard\(record\)/);
 assert.match(live,/function pickFloatingCard\(/);
 assert.match(live,/function focusCard\(/);
 assert.match(live,/function exitCardFocus\(/);
 assert.match(live,/updateFloatingCards\(now\);/);
 assert.match(live,/new URL\('\.\/card-creation\.html'/);
 assert.match(live,/new URL\('\.\/book-creation\.html'/);
 assert.match(live,/ObjectCraft\.setBookActive/);
 assert.match(live,/scene\.add\(group\)/);
 assert.match(live,/readerFrame/);
 assert.match(live,/event\.origin!==location\.origin/);
});

test('format X returns edited/downloaded card to exactly the new blank schema (11 populated fields)',()=>{
 const store=storage();
 const original=craft.createBlankCard(store,rand,()=> '2026-10-20T01:00:00Z');
 assert.equal(craft.inspectCard(store,original.id).stats.filled,11,'factory baseline');
 const image={dataUrl:'data:image/jpeg;base64,/9j/AA==',scale:1,x:50,y:50};
 const changed=craft.editCard(store,original.id,{title:'舊卡',description:'曾經有內容',
  image,cardNumber:'E-01',cardType:'普通',downloadTemplate:true});
 const before=craft.read(store);
 const formatted=craft.formatCardToBlank(store,changed.id,changed.revision,()=> '2026-10-20T02:00:00Z');
 assert.equal(formatted.id,original.id);
 assert.equal(formatted.createdAt,original.createdAt);
 assert.equal(formatted.revision,changed.revision+1);
 assert.equal(craft.inspectCard(store,formatted.id).stats.filled,11);
 assert.deepEqual(Object.keys(formatted).sort(),Object.keys(original).sort(),'exact default shape');
 assert.equal(formatted.image,null);
 assert.equal(formatted.description,'');
 assert.equal(formatted.title,'空白卡片');
 assert.equal(Object.hasOwn(formatted,'downloadTemplate'),false);
 assert.deepEqual(before.objects.map(o=>o.id),craft.read(store).objects.map(o=>o.id),'format reuses ID without adding or consuming card');
 const placed=craft.placeCard(store,formatted.id,'local.atlas.compendium',9);
 assert.equal(craft.isBlankCopyMedium(placed),true,'GBA recognizes actual blank');
});

test('format removes stale released metadata but refuses active, orphan, permanent and unknown contracts',()=>{
 const factory=()=>{
  const store=storage(),base=craft.createBlankCard(store,rand);
  const set=(fields)=>{
   const state=craft.read(store);state.objects[0]={...base,...fields};
   store.setItem(craft.KEY,JSON.stringify(state));return {store,card:state.objects[0]};
  };
  return {set};
 };
 const old=factory().set({title:'舊版已解除',copyInfo:{sourceId:'old'},runeBinding:{runeId:'local.rune.origin',state:'released'}});
 const blank=craft.formatCardToBlank(old.store,old.card.id,old.card.revision);
 assert.equal(craft.inspectCard(old.store,blank.id).stats.filled,11);
 for(const fields of [
  {runeBinding:{runeId:'local.rune.origin',state:'bound'}},
  {runeBinding:{runeId:'local.rune.origin',state:'sealed'},placement:'contained',containment:{kind:'rune_contract'}},
  {scanContract:{artifactId:'scan.bundle.local.card.suspicious',members:[]}},
  {truthEyeContract:{artifactId:'artifact.truth-eye',form:'world'}},
  {boundRuneId:'local.rune.origin'},
  {permanentCard:true},
  {permanentRegistry:{state:'registered'}},
  {upgradeRequest:{state:'requested'}},
  {runeBinding:{runeId:'local.rune.origin',state:'unknown'}}
 ]){
  const {set}=factory(),{store,card}=set({...fields,title:'被保護的卡'});
  const saved=store.getItem(craft.KEY);
  assert.throws(()=>craft.formatCardToBlank(store,card.id,card.revision));
  assert.equal(store.getItem(craft.KEY),saved,'failed formatting never touches the save');
 }
 const {set}=factory(),{store,card}=set({title:'已更名的卡',revision:5});
 const saved=store.getItem(craft.KEY);
 assert.throws(()=>craft.formatCardToBlank(store,card.id,4),/版本/);
 assert.equal(store.getItem(craft.KEY),saved);
});
test('format X in card creator opens a confirmation instead of only erasing the photo',async()=>{
 const editor=await read('living-library/live/card-creation.html');
 assert.match(editor,/id="removePhoto"[^>]+aria-label="將整張卡片格式化為真正空白卡"/);
 assert.match(editor,/id="removePhotoOnly"[^>]+aria-label="只移除卡面圖片"/);
 assert.match(editor,/id="formatCardDialog"/);
 assert.match(editor,/Craft\.formatCardBlockReason\(card\)/);
 assert.match(editor,/Craft\.formatCardToBlank\(localStorage,selectedFormat\.id,selectedFormat\.revision\)/);
 assert.match(editor,/notify\('card-formatted',card\.id\)/);
 assert.match(editor,/Craft\.inspectCard\(localStorage,card\.id\)\.stats\.filled/);
});

test('regression: visibly blank former card may have 14 filled values until X formats back to 11',()=>{
 const store=storage(),blank=craft.createBlankCard(store,rand,()=> '2026-10-20T00:00:00Z');
 const raw=craft.read(store),old={...blank,title:'空白卡片',
  cardNumber:'舊編號',copyInfo:{sourceId:'舊格式'},runeBinding:{runeId:'local.rune.origin',state:'released'}};
 raw.objects[0]=old;store.setItem(craft.KEY,JSON.stringify(raw));
 assert.equal(craft.inspectCard(store,blank.id).stats.filled,14);
 const cleaned=craft.formatCardToBlank(store,blank.id,old.revision,()=> '2026-10-20T02:00:00Z');
 assert.equal(craft.inspectCard(store,blank.id).stats.filled,11);
 assert.equal(cleaned.id,blank.id);
 assert.equal(craft.listCards(store).length,1);
});
test('formatting a formerly downloaded card in GBA returns the same 11-value blank to orbit',()=>{
 const store=storage(),blank=craft.createBlankCard(store,rand);
 const reader=craft.placeCard(store,blank.id,'local.atlas.compendium',9);
 const fromGba=craft.formatCardToBlank(store,reader.id,reader.revision);
 assert.equal(fromGba.placement,'hover');
 assert.equal(fromGba.containerId,null);
 assert.equal(fromGba.ownerBookId,null);
 assert.equal(craft.inspectCard(store,blank.id).stats.filled,11);
 assert.equal(craft.listCards(store).length,1);
 const reinserted=craft.placeCard(store,blank.id,'local.atlas.compendium',9);
 assert.equal(craft.isBlankCopyMedium(reinserted),true);
});

test('lost scanner-labelled cards can be located without assuming names are contracts',()=>{
 const db=storage(),card=craft.createBlankCard(db,rand,()=> '2026-10-20T12:00:00Z');
 const stored=craft.placeCard(db,card.id,'local.atlas.collection',10);
 const crafted=craft.editCard(db,stored.id,{title:'掃描收納卡'});
 const before=db.getItem(craft.KEY);
 assert.equal(craft.getStats(db).floatingCards,0);
 assert.equal(craft.getStats(db).inBooks,1);
 assert.equal(craft.listCards(db)[0].containerId,'local.atlas.collection');
 assert.equal(craft.listCards(db)[0].slotIndex,10);
 assert.equal(craft.listCards(db)[0].scanContract,undefined,'name alone is NOT proof of a live contract');
 assert.equal(db.getItem(craft.KEY),before,'listing and diagnostics never change save');
 const returned=craft.placeCard(db,crafted.id,null);
 assert.equal(returned.id,card.id,'retrieval reuses original card ID');
 assert.equal(returned.containerId,null);
 assert.equal(returned.title,'掃描收納卡','retrieval does not reformat or rename a card');
 assert.equal(craft.getStats(db).floatingCards,1);
 assert.equal(craft.getStats(db).inBooks,0);
});

test('safe location recovery preserves an actual scanner contract and does not unbind it',()=>{
 const db=storage(),created=craft.createBlankCard(db,rand);
 const original=craft.read(db);
 original.objects[0]={...created,title:'掃描收納卡',
  runeBinding:{runeId:'scan.bundle.'+created.id,state:'bound'},
  scanContract:{artifactId:'scan.bundle.'+created.id,version:1,form:'card',
   members:[{kind:'book',id:'book.1'}]}};
 db.setItem(craft.KEY,JSON.stringify(original));
 const kept=craft.placeCard(db,created.id,'local.atlas.collection',2);
 const recovered=craft.placeCard(db,kept.id,null);
 assert.deepEqual(recovered.scanContract,original.objects[0].scanContract);
 assert.deepEqual(recovered.runeBinding,original.objects[0].runeBinding);
 assert.equal(recovered.id,created.id);
 const world=craft.read(db);world.objects[0].scanContract.form='world';
 db.setItem(craft.KEY,JSON.stringify(world));
 const unchanged=db.getItem(craft.KEY);
 assert.throws(()=>craft.placeCard(db,created.id,null),/不允許|狀態/);
 assert.equal(db.getItem(craft.KEY),unchanged,'world-form object cannot be forcibly retrieved');
});

test('card creator shows precise stored locations and requires consent to recover a card',async()=>{
 const editor=await read('living-library/live/card-creation.html');
 assert.match(editor,/id="cardRecoverySummary"/);
 assert.match(editor,/id="retrieveCardDialog"/);
 assert.match(editor,/storedLocation\(card\)/);
 assert.match(editor,/scanBindingStatus\(card\)/);
 assert.match(editor,/retrieveDialog\.showModal\(\)/);
 assert.match(editor,/Craft\.placeCard\(localStorage,item\.id,null\)/);
 assert.match(editor,/localStorage\.getItem\(Craft\.KEY\)!==item\.snapshot/);
 assert.match(editor,/notify\('card-edited',returned\.id\)/);
 assert.match(editor,/此表|存檔共有|讀取本機存檔/);
});
