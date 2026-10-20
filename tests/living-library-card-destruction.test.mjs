import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const C=require('../living-library/live/object-craft.js');
const memory=()=>{const map=new Map();return {getItem:k=>map.get(k)??null,setItem:(k,v)=>map.set(k,String(v))}};
const rand=(()=>{let n=0;return {randomUUID:()=>String(++n)}})();
const snap=s=>s.getItem(C.KEY);
function erase(s,c,typed='銷毀',expected=snap(s)){
 return C.destroyCard(s,{id:c.id,expectedRevision:c.revision,confirmation:typed,expectedSnapshot:expected});
}
test('irreversible delete leaves no live card, recycle history or tombstone',()=>{
 const s=memory(),a=C.createBlankCard(s,rand),b=C.createBlankCard(s,rand);
 const before=snap(s);
 assert.throws(()=>erase(s,a,'NO'),/確認/);
 assert.equal(snap(s),before);
 assert.equal(erase(s,a).deleted,true);
 assert.deepEqual(C.read(s).objects.map(c=>c.id),[b.id]);
 assert.equal(C.listRecycledCards(s).length,0);
 assert.throws(()=>erase(s,a),/不存在/);
});
test('recycled card disappears without touching another card or unrelated game key',()=>{
 const s=memory(),a=C.createBlankCard(s,rand),b=C.createBlankCard(s,rand);
 C.recycleCard(s,a.id);
 const recycled=C.listRecycledCards(s)[0];
 s.setItem('unrelated-game-key','keep');
 erase(s,recycled);
 assert.equal(C.listRecycledCards(s).length,0);
 assert.equal(C.listCards(s)[0].id,b.id);
 assert.equal(s.getItem('unrelated-game-key'),'keep');
});
test('active contract, permanent identity and duplicate card ID block without writes',()=>{
 const s=memory(),a=C.createBlankCard(s,rand);let state=C.read(s);
 state.objects[0].scanContract={members:[],form:'card'};
 s.setItem(C.KEY,JSON.stringify(state));let before=snap(s);
 assert.throws(()=>erase(s,C.listCards(s)[0]),/契約/);
 assert.equal(snap(s),before);
 state.objects[0].scanContract=null;state.objects[0].permanentRegistry={state:'registered'};
 s.setItem(C.KEY,JSON.stringify(state));before=snap(s);
 assert.throws(()=>erase(s,C.listCards(s)[0]),/永久/);
 assert.equal(snap(s),before);
 state.objects[0].permanentRegistry=null;state.objects.push({...state.objects[0]});
 s.setItem(C.KEY,JSON.stringify(state));before=snap(s);
 assert.throws(()=>erase(s,C.listCards(s)[0]),/身分重複/);
 assert.equal(snap(s),before);
});
test('card inside user book can be destroyed but active book references block first',()=>{
 const s=memory(),a=C.createBlankCard(s,rand),book=C.createCardBook(s,rand);
 C.placeCard(s,a.id,book.id,0);
 const placed=C.listCards(s)[0],state=C.read(s);
 state.objects.find(x=>x.id===book.id).contentIds=[a.id];
 s.setItem(C.KEY,JSON.stringify(state));
 const before=snap(s);
 assert.throws(()=>erase(s,placed),/引用/);
 assert.equal(snap(s),before);
 state.objects.find(x=>x.id===book.id).contentIds=[];
 s.setItem(C.KEY,JSON.stringify(state));erase(s,placed);
 assert.equal(C.listCards(s).length,0);
 assert.equal(C.listBooks(s).length,1);
});
test('GBA visual copy keeps independent identity and image; source ID is erased',()=>{
 const s=memory(),a=C.createBlankCard(s,rand),b=C.createBlankCard(s,rand);
 const state=C.read(s),copy=state.objects.find(x=>x.id===b.id);
 copy.copyInfo={kind:'gba-template-download',sourceId:a.id,copiedAt:'T'};
 copy.image={dataUrl:'synthetic'};
 s.setItem(C.KEY,JSON.stringify(state));
 const plan=C.planDestroyCard(s,a.id,a.revision);
 assert.deepEqual(plan.affectedCopyIds,[b.id]);
 assert.equal(erase(s,a).detachedCopies,1);
 const survivor=C.listCards(s)[0];
 assert.equal(survivor.id,b.id);assert.equal(survivor.image.dataUrl,'synthetic');
 assert.equal(survivor.copyInfo,undefined);
 assert.equal(survivor.revision,b.revision+1);
});
test('stale revision, changed snapshot, malformed store all fail closed',()=>{
 const s=memory(),a=C.createBlankCard(s,rand),v1=snap(s);
 C.editCard(s,a.id,{title:'已變更'});
 let before=snap(s);
 assert.throws(()=>erase(s,a),/已變更/);assert.equal(snap(s),before);
 const updated=C.listCards(s)[0];
 assert.throws(()=>erase(s,updated,'銷毀',v1),/已變動/);
 assert.equal(snap(s),before);
 s.setItem(C.KEY,'{corrupt');before=snap(s);
 assert.throws(()=>C.planDestroyCard(s,a.id,a.revision),/格式/);
 assert.equal(snap(s),before);
});
test('Card Creation Spell exposes confirmed destroy, recycle-bin destroy, offline purge',async()=>{
 const html=await readFile(new URL('../living-library/live/card-creation.html',import.meta.url),'utf8');
 const outbox=await readFile(new URL('../living-library/live/object-outbox.js',import.meta.url),'utf8');
 assert.match(html,/id="destroyCard"/);
 assert.match(html,/id="destroyInput"/);
 assert.match(html,/outbox\.forget\(\[plan\.id,\.\.\.plan\.affectedCopyIds\]\)/);
 assert.match(html,/Craft[.]destroyCard[(]localStorage/);
 assert.match(html,/erase[.]addEventListener[(]'click'/);
 assert.match(outbox,/forget[(]ids[)]/);
 assert.match(outbox,/objectStore[(]'pending'[)][.]delete/);
});

test('permanent destroy control is prominent above editor form and iframe uses fresh version',async()=>{
 const html=await readFile(new URL('../living-library/live/card-creation.html',import.meta.url),'utf8');
 const index=await readFile(new URL('../living-library/live/index.html',import.meta.url),'utf8');
 const header=html.indexOf('<strong>空白圖鑑卡</strong>');
 const button=html.indexOf('id="destroyCard"');
 const form=html.indexOf('<form id="editForm">');
 assert.ok(header>=0&&button>header&&form>button,'permanent destroy button must appear BEFORE the long editor fields');
 assert.match(html,/id="destroyHint"/);
 assert.match(html,/🗑 永久銷毀卡片/);
 assert.match(html,/erase[.]textContent='🗑 銷毀'/);
 assert.match(index,/fresh[.]searchParams[.]set[(]'v','[^']+'[)]/);
 assert.match(index,/readerExternal'[)][.]href=fresh[.]toString[(][)]/);
});

test('unknown top-level save metadata survives normal card edits and recycle',()=>{
 const s=memory(),card=C.createBlankCard(s,rand);
 const state=C.read(s);state.futureTop={schema:'nw.future',marker:['keep']};
 s.setItem(C.KEY,JSON.stringify(state));
 C.editCard(s,card.id,{description:'修改說明'});
 assert.deepEqual(C.read(s).futureTop,state.futureTop);
 C.recycleCard(s,card.id);
 assert.deepEqual(C.read(s).futureTop,state.futureTop);
});
test('incompletely registered permanent identity cannot move to an ordinary book',()=>{
 const s=memory(),card=C.createBlankCard(s,rand),book=C.createCardBook(s,rand);
 const state=C.read(s);state.objects.find(x=>x.id===card.id).permanentCard=true;
 s.setItem(C.KEY,JSON.stringify(state));const before=snap(s);
 assert.throws(()=>C.placeCard(s,card.id,book.id,0),/永久|不允許/);
 assert.equal(snap(s),before);
});
test('orphan scan contract must not be moved to a book or silently recycled',()=>{
 const s=memory(),card=C.createBlankCard(s,rand),book=C.createCardBook(s,rand);
 const state=C.read(s);state.objects.find(x=>x.id===card.id).scanContract={
  version:1,artifactId:'scan.bundle.'+card.id,form:'world',
  members:[{kind:'book',id:'gaming'}]
 };
 s.setItem(C.KEY,JSON.stringify(state));const before=snap(s);
 assert.throws(()=>C.placeCard(s,card.id,book.id,0),/契約|不允許/);
 assert.throws(()=>C.recycleCard(s,card.id),/契約/);
 assert.equal(snap(s),before);
});
