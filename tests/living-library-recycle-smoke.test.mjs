import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const Craft=require('../living-library/live/object-craft.js');
const load=path=>readFile(new URL('../'+path,import.meta.url),'utf8');
function makeStore(){const map=new Map();return {getItem:k=>map.get(k)??null,setItem:(k,v)=>map.set(k,v)}}
let n=0;const rng={randomUUID:()=>('00000000-0000-4000-8000-'+String(++n).padStart(12,'0'))};
test('recycling a local 3D card is reversible and preserves the other cards and books',()=>{
 const store=makeStore(),a=Craft.createBlankCard(store,rng),b=Craft.createBlankCard(store,rng);
 const book=Craft.createBlankBook(store,rng,'測試書');
 Craft.recycleCard(store,a.id);
 assert.deepEqual(Craft.floatingCards(store).map(c=>c.id),[b.id]);
 assert.deepEqual(Craft.listRecycledCards(store).map(c=>c.id),[a.id]);
 assert.deepEqual(Craft.listBooks(store).map(c=>c.id),[book.id]);
 Craft.restoreCard(store,a.id);
 assert.deepEqual(Craft.floatingCards(store).map(c=>c.id),[a.id,b.id]);
 assert.equal(Craft.listRecycledCards(store).length,0);
});
test('3D recycle reuses real smoke sprites then removes its original mesh after animation, not before',async()=>{
 const s=await load('living-library/live/index.html');
 for(const token of ['function startCardRecycle(id)','CARD_RECYCLE_MS=1250','createCardSmoke(origin)',
  'updateCardSmoke(fx.puffs,fx.origin,age,CARD_RECYCLE_MS)',
  'entry.group.scale.setScalar(controlPrefs.cardSize*Math.max(.001,1-u*u))',
  'if(entry.recycle&&!next.has(id))continue','if(u>=1){','disposeFloatingCard(entry);floatingCardMeshes.delete(entry.id)',
  '!!x.summon||!!x.recycle','refreshFloatingCards,queueCardSummon,startCardSummon,startCardRecycle,exitCardFocus']){
  assert.ok(s.includes(token),'Missing 3D recycling effect contract: '+token);
 }
 assert.ok(s.indexOf('updateCardSmoke(fx.puffs,fx.origin,age,CARD_RECYCLE_MS)') <
  s.indexOf('disposeFloatingCard(entry);floatingCardMeshes.delete(entry.id)',s.indexOf('function updateFloatingCards(now)')));
});
test('both in-book recycle and focus-mode confirm trigger one shared effect; reduced motion remains immediate',async()=>{
 const s=await load('living-library/live/index.html');
 const cardPage=await load('living-library/live/card-creation.html');
 assert.match(s,/event\.data\.action==='card-recycled'/);
 assert.match(s,/ObjectCraft\.listRecycledCards\(localStorage\)\.some\(card=>card\.id===id\)/);
 assert.match(s,/scene\?\.startCardRecycle\?\.\(id\)/);
 assert.match(s,/window\.LivingObjectCraft\.recycleCard\(localStorage,id\)/);
 assert.match(s,/if\(!startCardRecycle\(id\)\)refreshFloatingCards\(\)/);
 assert.match(s,/if\(preferReduced\)\{refreshFloatingCards\(\);return true\}/);
 assert.match(s,/filter\(x=>!x\.summon&&!x\.recycle\)/);
 assert.match(cardPage,/notify\('card-recycled',id\)/);
 assert.match(cardPage,/notify\('card-restored',card\.id\)/);
});
