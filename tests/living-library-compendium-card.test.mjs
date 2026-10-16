import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFileSync} from 'node:fs';
const require=createRequire(import.meta.url),C=require('../living-library/live/object-craft.js'),Face=require('../living-library/live/compendium-card.js'),Backup=require('../living-library/live/object-backup.js');
const store=()=>{const m=new Map();return {getItem:k=>m.get(k)??null,setItem:(k,v)=>m.set(k,v)}};
const rng={randomUUID:()=> 'compendium-fixture'};
const picture={dataUrl:'data:image/jpeg;base64,/9j/2Q==',scale:1.5,x:25,y:75};
test('blank V2 has no image, type, numbering or ability; card edits retain location and ID',async()=>{
 const db=store(),a=C.createBlankCard(db,rng);
 assert.equal(a.image,null);assert.equal(a.cardType,'');assert.equal(a.linkedCardId,null);assert.equal(a.ability,null);assert.equal(a.collectionNumber,undefined);
 C.placeCard(db,a.id,'local.atlas.collection',3);
 const b=C.editCard(db,a.id,{title:'我的圖鑑卡',description:'手動輸入',cardType:'自訂',image:picture,linkedCardId:'public.gpt.skills'});
 const c=C.listCards(db)[0];assert.equal(c.id,a.id);assert.equal(c.containerId,'local.atlas.collection');assert.equal(c.slotIndex,3);assert.deepEqual(c.image,picture);assert.equal(c.cardType,'自訂');assert.deepEqual(c.permissions,[]);assert.equal(c.ability,null);assert.equal(c.revision,b.revision);
 const backup=await Backup.inspectBackup(await Backup.exportBackup(db));assert.deepEqual(JSON.parse(backup.source_text).objects[0].image,picture);
 C.editCard(db,a.id,{description:'只改字'});assert.deepEqual(C.listCards(db)[0].image,picture);
 C.editCard(db,a.id,{image:null});assert.equal(C.listCards(db)[0].image,null);
});
test('invalid image, crop and link never overwrite original data; quota errors retain old bytes',()=>{
 const db=store(),a=C.createBlankCard(db,rng),before=db.getItem(C.KEY);
 for(const image of [{...picture,dataUrl:'data:image/svg+xml;base64,PHN2Zz4='},{...picture,dataUrl:'https://example.test/photo.jpg'},{...picture,scale:4},{...picture,x:NaN},{...picture,dataUrl:'data:image/jpeg;base64,/9j/'+ 'A'.repeat(70000)}]){
  assert.throws(()=>C.editCard(db,a.id,{title:'不能寫入',image}));assert.equal(db.getItem(C.KEY),before);
 }
 assert.throws(()=>C.editCard(db,a.id,{linkedCardId:'javascript:alert(1)'}));assert.throws(()=>C.editCard(db,a.id,{ability:{exec:'anything'}}));
 const full={getItem:db.getItem,setItem(){throw Object.assign(Error('full'),{name:'QuotaExceededError'})}};
 assert.throws(()=>C.editCard(full,a.id,{image:picture}));assert.equal(db.getItem(C.KEY),before);
});
test('shared face draws only black text, no preset sigil, and asynchronous photos cancel cleanly',()=>{
 const texts=[],images=[];const x={fillStyle:'',fillRect(){},strokeRect(){},beginPath(){},moveTo(){},lineTo(){},stroke(){},fillText(text){texts.push([text,this.fillStyle])},save(){},rect(){},clip(){},restore(){},drawImage(){images.push(true)}};
 const canvas={getContext:()=>x};Face.draw(canvas,{});assert.ok(texts.some(([v])=>v==='尚未放入圖片'));assert.ok(texts.every(([,color])=>color==='#000000'));assert.ok(!texts.some(([v])=>v.includes('法陣')));
 const old=globalThis.Image,pending=[];globalThis.Image=class{constructor(){this.naturalWidth=480;this.naturalHeight=300;pending.push(this)}};
 try{let dirty=0;const cancel=Face.draw(canvas,{image:picture},()=>dirty++);cancel();pending[0].onload();assert.equal(dirty,0);Face.draw(canvas,{image:picture},()=>dirty++);pending[1].onload();assert.equal(dirty,1);assert.equal(images.length,1)}finally{globalThis.Image=old}
});
test('creator and live entry load the shared card face and their inline scripts parse',()=>{
 for(const name of ['index.html','card-creation.html']){
  const text=readFileSync(new URL('../living-library/live/'+name,import.meta.url),'utf8');assert.match(text,/compendium-card\.js/);
  for(const m of text.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g))assert.doesNotThrow(()=>new Function(m[1].includes('module')?'return async function(){'+m[2]+'}':m[2]));
 }
});
