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
 assert.equal(c.books.length,14);
 assert.deepEqual(c.bookCards.gaming,[
  'public.gba.runtime','public.vault.web','KW-GAME-MEMORY-PALACE-20261014',
  'public.gaming.card.creation','public.gaming.book.creation'
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
