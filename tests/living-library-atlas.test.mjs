import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
const require=createRequire(import.meta.url),C=require('../living-library/live/object-craft.js'),S=require('../living-library/live/atlas-state.js'),Voice=require('../living-library/live/atlas-voice.js');
function storage(){const m=new Map();return {getItem:k=>m.get(k)??null,setItem:(k,v)=>m.set(k,v)}}
let serial=0;const random={randomUUID:()=>String(++serial)};
test('card transfers remain atomic across atlas, collection, orbit and a fresh read',()=>{
 const st=storage(),a=C.createBlankCard(st,random),b=C.createBlankCard(st,random);
 C.editCard(st,a.id,{title:'資料卡',description:'保留原始資料'});
 C.placeCard(st,a.id,S.ATLAS,9);let card=C.listCards(st).find(c=>c.id===a.id);
 assert.equal(S.readerActive(card),true);assert.equal(C.floatingCards(st).length,1);assert.equal(C.getStats(st).inBooks,1);
 const before=st.getItem(C.KEY);assert.throws(()=>C.placeCard(st,b.id,S.ATLAS,9),/已有/);assert.equal(st.getItem(C.KEY),before);
 C.placeCard(st,a.id,S.COLLECTION,17);card=C.listCards(st).find(c=>c.id===a.id);assert.equal(S.readerActive(card),false);assert.equal(S.slots(C.listCards(st),S.COLLECTION)[17].id,a.id);
 assert.throws(()=>C.recycleCard(st,a.id),/先移出/);
 C.placeCard(st,a.id,null);card=C.listCards(st).find(c=>c.id===a.id);assert.equal(card.title,'資料卡');assert.equal(card.description,'保留原始資料');assert.equal(card.containerId,null);assert.equal(card.slotIndex,null);assert.equal(C.floatingCards(st).length,2);
 assert.equal(new Set(C.read(st).objects.map(c=>c.id)).size,2);
});
test('invalid slots, unknown containers, recycled cards and storage errors never overwrite data',()=>{
 const st=storage(),a=C.createBlankCard(st,random),before=st.getItem(C.KEY);
 for(const [book,slot] of [[S.ATLAS,10],[S.COLLECTION,18],[S.ATLAS,-1],['other',0],[S.ATLAS,1.5]])assert.throws(()=>C.placeCard(st,a.id,book,slot));
 assert.equal(st.getItem(C.KEY),before);C.recycleCard(st,a.id);assert.throws(()=>C.placeCard(st,a.id,S.ATLAS,0));
 const broken={getItem:()=>'{invalid',setItem:()=>assert.fail('must not overwrite')};assert.throws(()=>C.placeCard(broken,a.id,S.ATLAS,0));
 const blocked={getItem:()=>before,setItem:()=>{throw Error('quota')}};assert.throws(()=>C.placeCard(blocked,a.id,S.ATLAS,0),/quota/);
});
function webVoice(){let now=2000,called=0,instance;const timers=[];class Recognition{constructor(){instance=this}start(){this.onstart?.()}abort(){this.aborted=true}}
 const host={SpeechRecognition:Recognition,setTimeout:fn=>{timers.push(fn);return timers.length},clearTimeout:i=>{timers[i-1]=null}};
 const states=[],voice=Voice.create({host,commands:{book:()=>called++},onState:s=>states.push(s),now:()=>now});
 return {voice,states,timers,get recognizer(){return instance},advance:()=>now+=1500,get called(){return called}};
}
test('voice starts only explicitly, recognizes word boundaries, debounces and loops until stopped',()=>{
 const x=webVoice();assert.equal(x.voice.wanted,false);x.voice.dispatch('book');assert.equal(x.called,0);x.voice.start();
 x.voice.dispatch('notebook');assert.equal(x.called,0);x.voice.dispatch('Book!');assert.equal(x.called,1);x.voice.dispatch('Book');assert.equal(x.called,1);
 x.advance();x.voice.dispatch('book');assert.equal(x.called,2);x.recognizer.onend();assert.equal(x.timers.length,1);const late=x.timers[0];x.voice.stop();late();assert.equal(x.voice.wanted,false);x.voice.dispatch('book');assert.equal(x.called,2);
});
test('permission rejection does not auto-retry and stale web callbacks cannot execute commands',()=>{
 const x=webVoice();x.voice.start();const old=x.recognizer,late=old.onresult;x.voice.stop();x.voice.start();late({resultIndex:0,results:[Object.assign([{transcript:'book'}],{isFinal:true})]});assert.equal(x.called,0);
 x.recognizer.onerror({error:'not-allowed'});assert.equal(x.voice.wanted,false);x.recognizer.onend();assert.equal(x.timers.length,0);
});
test('native transport uses session-scoped text; late native replies are ignored',()=>{
 let now=2000,count=0;const sent=[],native={postMessage:x=>sent.push(JSON.parse(x))};const v=Voice.create({host:{LivingLibraryNativeVoice:native},commands:{book:()=>count++},now:()=>now});
 v.start();const first=sent.at(-1).session;native.onmessage({data:JSON.stringify({v:1,session:first,status:'listening'})});native.onmessage({data:JSON.stringify({v:1,session:first,status:'result',text:'book'})});assert.equal(count,1);
 v.stop();assert.equal(sent.at(-1).op,'stop');now+=1500;v.start();native.onmessage({data:JSON.stringify({v:1,session:first,status:'result',text:'book'})});assert.equal(count,1);
 const next=sent.at(-1).session;native.onmessage({data:JSON.stringify({v:1,session:next,status:'result',text:'book'})});assert.equal(count,2);
 native.onmessage({data:JSON.stringify({v:1,session:next,status:'off'})});assert.equal(v.wanted,false);
});
test('new scene modules compile and preserve shared public/native entries',async()=>{
 const Cat=require('../living-library/live/public-catalog.js'),c=Cat.buildCatalog();assert.equal(c.books.length,15);assert.equal(c.bookCards.gaming.length,6);
 for(const id of ['public.gba.runtime','public.vault.web','public.gaming.card.creation','public.gaming.book.creation'])assert.ok(c.bookCards.gaming.includes(id));
 assert.equal(c.cards['public.gba.runtime'].nativeRoute,'gba');assert.equal(c.bookCards.android.length,16);
 for(const name of ['atlas-runtime.js','atlas-model.js','atlas-voice.js'])assert.doesNotThrow(()=>new Function(require('node:fs').readFileSync(new URL('../living-library/live/'+name,import.meta.url),'utf8')));
 const page=await readFile(new URL('../living-library/live/atlas-controls.html',import.meta.url),'utf8');for(const s of page.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g))assert.doesNotThrow(()=>new Function(s[1]));
});
