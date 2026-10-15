import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const Craft=require('../living-library/live/object-craft.js');
const read=p=>readFile(new URL('../'+p,import.meta.url),'utf8');
function memoryStore(){
 const data=new Map();
 return {getItem:k=>data.get(k)??null,setItem:(k,v)=>data.set(k,String(v))};
}
let count=0;
const random={randomUUID:()=>('00000000-0000-4000-8000-'+String(++count).padStart(12,'0'))};
test('recycled original card returns with same ID, title, body and no duplicate',()=>{
 const store=memoryStore();
 const first=Craft.createBlankCard(store,random);
 const second=Craft.createBlankCard(store,random);
 Craft.editCard(store,first.id,{title:'空間收藏卡',description:'原有手寫內容'});
 Craft.recycleCard(store,first.id);
 const restored=Craft.restoreCard(store,first.id);
 assert.equal(restored.id,first.id);
 assert.equal(restored.title,'空間收藏卡');
 assert.equal(restored.description,'原有手寫內容');
 assert.deepEqual(Craft.listCards(store).map(x=>x.id),[first.id,second.id]);
 assert.deepEqual(Craft.floatingCards(store).map(x=>x.id),[first.id,second.id]);
 assert.deepEqual(Craft.listRecycledCards(store),[]);
 assert.equal(Craft.read(store).objects.filter(x=>x.id===first.id).length,1);
});
test('restoration event is same-origin restricted and launches the exact same cinematic as create',async()=>{
 const html=await read('living-library/live/index.html');
 const page=await read('living-library/live/card-creation.html');
 assert.match(page,/Craft\.restoreCard\(localStorage,card\.id\);refresh\(\);notify\('card-restored',card\.id\)/);
 const start=html.indexOf("window.addEventListener('message',event=>{");
 const end=html.indexOf('// Only a trusted, currently opened same-origin card iframe',start);
 const receiver=html.slice(start,end);
 assert.match(receiver,/event\.origin!==location\.origin\|\|event\.source!==\$\('readerFrame'\)\.contentWindow/);
 assert.match(receiver,/event\.data\.action==='card-created'/);
 assert.match(receiver,/event\.data\.action==='card-restored'/);
 assert.match(receiver,/ObjectCraft\.floatingCards\(localStorage\)\.some\(card=>card\.id===id\)/);
 assert.match(receiver,/if\(restored\)launchCardArrival\(id\)/);
 assert.match(receiver,/if\(exists\)launchCardArrival\(id\)/);
 assert.match(receiver,/function launchCardArrival\(id\)/);
 assert.match(receiver,/scene\.queueCardSummon\(id\)/);
 assert.match(receiver,/cardSpellExitTimer=setTimeout\(\(\)=>/);
 assert.match(receiver,/scene\.startCardSummon\(id\)/);
 assert.match(receiver,/closeBook\(\);drawer\.classList\.remove\('card-spell-leaving'\)/);
 assert.ok(receiver.indexOf("window.LivingScene?.refreshFloatingCards?.()")<receiver.indexOf("if(event.data.action==='card-restored'"));
 assert.match(page,/正在退出書頁，準備於 3D 世界播放煙霧召喚動畫/);
});
test('restoration replays existing large birth smoke, not the smaller recycle-only effect',async()=>{
 const s=await read('living-library/live/index.html');
 const region=s.slice(s.indexOf('function startCardSummon(id)'),s.indexOf('function startCardRecycle(id)'));
 assert.match(region,/puffs:createCardSmoke\(origin\)/);
 assert.doesNotMatch(region,/puffs:createCardSmoke\(origin,true\)/);
 assert.match(s,/const CARD_SUMMON_PHASES=Object\.freeze\(\{gather:\.24,emerge:\.48,flight:\.87\}\)/);
 assert.match(s,/if\(preferReduced\)\{refreshFloatingCards\(\);return true\}/);
 const opts=require('../living-library/live/card-placement.js');
 assert.equal(opts.DEFAULTS.cardSummonDuration,2850);
});
