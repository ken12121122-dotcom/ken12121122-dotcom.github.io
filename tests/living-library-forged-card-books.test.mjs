import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url),C=require('../living-library/live/object-craft.js');
function fixture(){const data=new Map();let n=0;return {storage:{getItem:k=>data.get(k)??null,setItem:(k,v)=>data.set(k,v)},random:{randomUUID:()=>String(++n)},data}}
test('book forge preserves blank books and creates independent eighteen-slot card books',()=>{
 const {storage,random}=fixture();const legacy=C.createBlankBook(storage,random,'舊式空白書'),a=C.createCardBook(storage,random,'甲冊'),b=C.createCardBook(storage,random,'乙冊');
 assert.equal(legacy.openingMode,'legacy');assert.equal(a.openingMode,'atlas');assert.notEqual(a.id,b.id);assert.equal(C.listBooks(storage).length,3);
 const first=C.createBlankCard(storage,random),second=C.createBlankCard(storage,random);C.editCard(storage,first.id,{title:'原始卡',description:'資料保留'});
 C.placeCard(storage,first.id,a.id,17);C.placeCard(storage,second.id,b.id,17);
 let before=storage.getItem(C.KEY);assert.throws(()=>C.placeCard(storage,first.id,b.id,17),/已有/);assert.equal(storage.getItem(C.KEY),before);
 assert.throws(()=>C.placeCard(storage,first.id,legacy.id,0),/無效/);assert.throws(()=>C.placeCard(storage,first.id,'local.book.missing',0),/無效/);assert.throws(()=>C.placeCard(storage,first.id,a.id,18),/無效/);assert.equal(storage.getItem(C.KEY),before);
 C.placeCard(storage,first.id,b.id,0);const card=C.listCards(storage).find(c=>c.id===first.id);assert.equal(card.description,'資料保留');assert.equal(card.title,'原始卡');assert.equal(card.ownerBookId,b.id);
 C.setBookView(storage,a.id,'inner',false);C.setBookView(storage,b.id,'shelf',false);
 const books=C.listBooks(storage);assert.equal(books.find(x=>x.id===a.id).viewMode,'inner');assert.equal(books.find(x=>x.id===a.id).viewOpen,false);assert.equal(books.find(x=>x.id===b.id).active,false);
 before=storage.getItem(C.KEY);assert.throws(()=>C.setBookView(storage,legacy.id,'inner',true),/找不到/);assert.throws(()=>C.setBookView(storage,a.id,'bad',true),/不正確/);assert.equal(storage.getItem(C.KEY),before);
 const reopened={getItem:k=>storage.getItem(k)};assert.equal(C.listCards(reopened).find(x=>x.id===first.id).containerId,b.id);
});
test('both book templates share the existing capacity without overwriting previous creations',()=>{
 const {storage,random}=fixture();for(let i=0;i<C.MAX_BOOKS;i++)(i%2?C.createBlankBook:C.createCardBook)(storage,random,'第'+i+'冊');
 const before=storage.getItem(C.KEY);assert.throws(()=>C.createCardBook(storage,random,'超額'),/上限/);assert.throws(()=>C.createBlankBook(storage,random,'超額'),/上限/);assert.equal(storage.getItem(C.KEY),before);
});
