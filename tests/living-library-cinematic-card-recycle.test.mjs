import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const C=require('../living-library/live/object-craft.js');
const read=p=>readFile(new URL('../'+p,import.meta.url),'utf8');
function store(initial){const entries=new Map(initial||[]);return {getItem(k){return entries.get(k)??null},setItem(k,v){entries.set(k,String(v))},snapshot(){return entries.get(C.KEY)}}}
let seq=0;const random={randomUUID:()=>('00000000-0000-4000-8000-'+String(++seq).padStart(12,'0'))};
test('soft recycle keeps immutable card identity and restores without losing user content',()=>{
 const db=store(),id=C.createBlankCard(db,random).id;
 C.editCard(db,id,{title:'測試合書術',description:'使用手勢觸發'});
 const original=C.listCards(db)[0];
 const moved=C.recycleCard(db,id);
 assert.equal(moved.id,id);assert.equal(moved.status,'recycled');
 assert.equal(C.listCards(db).length,0);assert.equal(C.floatingCards(db).length,0);
 assert.equal(C.getStats(db).recycledCards,1);assert.equal(C.listRecycledCards(db)[0].id,id);
 assert.throws(()=>C.editCard(db,id,{title:'繞過回收'}),/可編輯/);
 const restored=C.restoreCard(db,id);
 assert.equal(restored.id,id);assert.equal(restored.title,original.title);
 assert.equal(restored.description,original.description);
 assert.equal(restored.placement,'hover');assert.equal(restored.status,'draft');
 assert.equal(C.floatingCards(db).length,1);assert.equal(C.getStats(db).recycledCards,0);
});
test('only owned blank-card IDs can be recycled; corrupted saves fail closed',()=>{
 const db=store(),card=C.createBlankCard(db,random),before=db.snapshot();
 assert.throws(()=>C.recycleCard(db,'public.system.controls'),/找不到/);
 assert.equal(db.snapshot(),before);
 C.recycleCard(db,card.id);
 const after=db.snapshot();
 assert.throws(()=>C.recycleCard(db,card.id),/找不到/);
 assert.equal(db.snapshot(),after);
 const broken=store([[C.KEY,'{broken data']]);
 assert.throws(()=>C.getStats(broken),/格式/);
 assert.equal(broken.snapshot(),'{broken data');
});
test('card and book counts remain separate, with active capacity independent of recycle bin',()=>{
 const db=store();const card=C.createBlankCard(db,random);C.createBlankBook(db,random,'法術實驗書');
 assert.deepEqual(C.getStats(db),{totalCards:1,floatingCards:1,inBooks:0,recycledCards:0,createdBooks:1});
 C.recycleCard(db,card.id);
 assert.deepEqual(C.getStats(db),{totalCards:0,floatingCards:0,inBooks:0,recycledCards:1,createdBooks:1});
 C.createBlankCard(db,random);
 assert.equal(C.getStats(db).totalCards,1);
});
test('cinematic sequence lives in actual 3D renderer and no iframe-only preview is used',async()=>{
 const html=await read('living-library/live/index.html'),creator=await read('living-library/live/card-creation.html');
 const controls=await read('living-library/live/system-controls.html');
 const placement=require('../living-library/live/card-placement.js');
 assert.equal(placement.DEFAULTS.cardSummonDuration,2850);
 assert.deepEqual(placement.LIMITS.cardSummonDuration,[1800,4500]);
 assert.match(controls,/id="cardSummonDuration" min="1800" max="4500"/);
 for(const token of ['CARD_SUMMON_PHASES','createCardSmoke','updateCardSmoke','startCardSummon',
 'scene.startCardSummon(id); // Stage the invisible card','card-spell-leaving',
 'const toViewer=camera.position.clone().sub(keeper.position)','entry.group.visible=t>=CARD_SUMMON_PHASES.gather',
 'CARD_SUMMON_PHASES.emerge','CARD_SUMMON_PHASES.flight','scene.startCardSummon(id)']){
  assert.ok(html.includes(token),'missing '+token);
 }
 assert.ok(html.includes('function refreshFloatingCards()'));
 assert.match(creator,/notify\('card-created',card\.id\)/);
 assert.match(creator,/id="recycleBin"/);
 assert.match(creator,/Craft\.restoreCard\(localStorage,card\.id\)/);
 assert.match(html,/cardRecycleConfirm/);
 assert.match(html,/window\.LivingObjectCraft\.recycleCard\(localStorage,id\)/);
 assert.match(html,/getLocalObjectStats\(\)/);
 assert.ok(html.includes("if(pickFloatingCard(ray))return 'card'"));
});
