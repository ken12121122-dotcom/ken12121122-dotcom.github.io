import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const Craft=require('../living-library/live/object-craft.js');
const read=path=>readFile(new URL('../'+path,import.meta.url),'utf8');

test('restoration retains original card identity and draft content',()=>{
 const memory=new Map(),db={
  getItem:k=>memory.get(k)??null,setItem:(k,v)=>memory.set(k,String(v))};
 const random={randomUUID:()=> '00000000-0000-4000-8000-000000000777'};
 const created=Craft.createBlankCard(db,random);
 const edited=Craft.editCard(db,created.id,{title:'我的元素卡',description:'未來加上能力'});
 Craft.recycleCard(db,created.id);
 const restored=Craft.restoreCard(db,created.id);
 assert.equal(restored.id,created.id);
 assert.equal(restored.title,edited.title);
 assert.equal(restored.description,edited.description);
 assert.equal(restored.placement,'hover');
 assert.equal(restored.ability,null);
 assert.deepEqual(restored.permissions,[]);
 assert.deepEqual(Craft.getStats(db),{
  totalCards:1,floatingCards:1,inBooks:0,recycledCards:0,createdBooks:0
 });
});

test('restored card uses identical in-world summon, not a DOM animation or duplicated card',async()=>{
 const live=await read('living-library/live/index.html');
 const creation=await read('living-library/live/card-creation.html');
 const start=live.indexOf("if(event.data.action==='card-restored'");
 const end=live.indexOf('// Only a trusted, currently opened same-origin card iframe',start);
 assert.ok(start>=0&&end>start);
 const handler=live.slice(start,end);
 assert.match(handler,/ObjectCraft\.floatingCards\(localStorage\)\.some\(card=>card\.id===id\)/);
 assert.match(handler,/readerCard\?\.id==='public\.gaming\.card\.creation'/);
 assert.match(handler,/readerCard\?\.id\?\.startsWith\('local\.card\.'\)/);
 assert.ok(handler.indexOf('scene.queueCardSummon(id)')>=0);
 assert.ok(handler.indexOf('scene.startCardSummon(id)')>handler.indexOf('scene.queueCardSummon(id)'));
 assert.ok(handler.indexOf('closeBook()')>handler.indexOf('scene.startCardSummon(id)'));
 assert.match(handler,/card-spell-leaving/);
 assert.match(handler,/cardSpellExitTimer=setTimeout/);
 assert.match(creation,/notify\('card-restored',card\.id\)/);
 assert.match(live,/function startCardSummon\(id\)/);
 assert.match(live,/puffs:createCardSmoke\(origin\)/);
 assert.match(live,/CARD_SUMMON_PHASES\.emerge/);
 assert.match(live,/CARD_SUMMON_PHASES\.flight/);
 assert.match(live,/CardPlacement\.orbitPosition\(i,currentOrbitTime,controlPrefs\)/);
 // No new permanent object or capability is created by replaying the animation.
 assert.doesNotMatch(handler,/createBlankCard\(|newFloatingCard\(/);
});
