import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFileSync} from 'node:fs';
const require=createRequire(import.meta.url);
const Craft=require('../living-library/live/object-craft.js');
const Observer=require('../living-library/live/card-observer.js');
const memory=()=>{const d=new Map();return {
 getItem:k=>d.has(k)?d.get(k):null,
 setItem:(k,v)=>d.set(k,v)
}};
const rng={randomUUID:()=> 'observer-fixture'};
const pic={dataUrl:'data:image/jpeg;base64,/9j/2Q==',scale:1.5,x:20,y:70};

test('one saved card gives allowlisted readonly snapshot, without changing card or exporting photo bytes',()=>{
 const storage=memory(),card=Craft.createBlankCard(storage,rng);
 Craft.editCard(storage,card.id,{cardNumber:'001',title:'第一張圖鑑',description:'測試說明',cardType:'圖鑑',image:pic});
 Craft.placeCard(storage,card.id,'local.atlas.compendium',9);
 const raw=storage.getItem(Craft.KEY);
 const observation=Observer.snapshot({storage,craft:Craft,cardId:card.id,clock:()=> '2026-10-16T00:00:00Z'});
 assert.equal(observation.schema,'nw.ai-card-observation.v1');
 assert.equal(observation.read_only,true);
 assert.equal(observation.image_payload_included,false);
 assert.equal(observation.card.card_id,card.id);
 assert.equal(observation.card.card_number,'001');
 assert.equal(observation.card.title,'第一張圖鑑');
 assert.equal(observation.card.container_id,'local.atlas.compendium');
 assert.equal(observation.card.slot_index,9);
 assert.equal(observation.card.image.present,true);
 assert.equal(observation.card.image.scale,1.5);
 assert.equal(observation.card.image.encoded_characters,pic.dataUrl.length);
 assert.equal(observation.source_records[0].revision,observation.card.revision);
 assert.equal(storage.getItem(Craft.KEY),raw);
 assert.ok(!JSON.stringify(observation).includes(pic.dataUrl));
 assert.ok(!JSON.stringify(observation).includes('/9j/2Q=='));
});

test('future/private fields are excluded and source without card is untouched',()=>{
 const storage=memory(),card=Craft.createBlankCard(storage,rng);
 const state=Craft.read(storage);
 state.objects[0].private_token='synthetic-private-secret';
 state.objects[0].future={hidden:'synthetic-private-secret'};
 storage.setItem(Craft.KEY,JSON.stringify(state));
 const raw=storage.getItem(Craft.KEY);
 const result=Observer.snapshot({storage,craft:Craft,cardId:card.id});
 assert.ok(!JSON.stringify(result).includes('synthetic-private-secret'));
 assert.equal(storage.getItem(Craft.KEY),raw);
 assert.throws(()=>Observer.snapshot({storage,craft:Craft,cardId:'local.card.missing'}),{code:'OBSERVATION_CARD_NOT_FOUND'});
 assert.equal(storage.getItem(Craft.KEY),raw);
});

test('no write, network, background observation or arbitrary id',()=>{
 assert.throws(()=>Observer.snapshot({storage:memory(),craft:Craft,cardId:'../../etc/passwd'}),{code:'OBSERVATION_CARD_ID_INVALID'});
 const storage=memory(),c=Craft.createBlankCard(storage,rng);
 Craft.recycleCard(storage,c.id);
 const before=storage.getItem(Craft.KEY);
 storage.setItem=()=>assert.fail('snapshot must not write');
 assert.throws(()=>Observer.snapshot({storage,craft:Craft,cardId:c.id}),{code:'OBSERVATION_CARD_NOT_FOUND'});
 assert.equal(storage.getItem(Craft.KEY),before);
 const html=readFileSync(new URL('../living-library/live/card-creation.html',import.meta.url),'utf8');
 assert.doesNotMatch(html,/card-observer\.js|atlasObservationDialog/);
 const main=readFileSync(new URL('../living-library/live/index.html',import.meta.url),'utf8');
 assert.match(main,/card-observer\.js/);
 assert.match(main,/id="atlasObservationDialog"/);
 assert.match(main,/containerId==='local\.atlas\.compendium'&&x\.slotIndex===9/);
 assert.match(main,/observeCard:id=>window\.LivingLibrary\.openAtlasObservation\(id\)/);
 for(const m of main.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g))
  assert.doesNotThrow(()=>new Function(m[1].includes('module')?'return async function(){'+m[2]+'}':m[2]));
});
