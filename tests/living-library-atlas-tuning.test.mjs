import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
const require=createRequire(import.meta.url),S=require('../living-library/live/atlas-state.js');
test('atlas preferences reject malformed values, clamp finite limits and preserve valid zero-free defaults',()=>{
 assert.deepEqual(S.normalizeSettings({atlasDistance:Infinity,atlasSize:'2',atlasHeight:null}),S.DEFAULTS);
 for(const [key,[lo,hi]] of Object.entries(S.LIMITS)){
  assert.equal(S.normalizeSettings({[key]:-1e5})[key],lo);
  assert.equal(S.normalizeSettings({[key]:1e5})[key],hi);
 }
});
test('old orbit selection introduces collection once; subsequent shelving and atlas saves remain respected',()=>{
 const live=readFileSync(new URL('../living-library/live/index.html',import.meta.url),'utf8');
 const source=live.slice(live.indexOf('function storeBooks(){'),live.indexOf('restoreBooks();storeBooks();'));
 const db=new Map([['books',JSON.stringify({active:['research']})]]),books=[{id:'research',publicId:'research',active:true},{id:'system',publicId:'system',active:false},{id:'cardCollection',publicId:'cardCollection',active:true}];
 const ctx={BOOKS:books,BOOK_PREFS_KEY:'books',selectedId:'research',book:()=>books[0],window:{LivingAtlasState:S},localStorage:{getItem:k=>db.get(k)||null,setItem:(k,v)=>db.set(k,v)}};
 vm.createContext(ctx);vm.runInContext(source+'restoreBooks();storeBooks();',ctx);
 assert.equal(books[2].active,true);assert.equal(books[1].active,false);
 books[2].active=false;vm.runInContext('storeBooks();restoreBooks();',ctx);assert.equal(books[2].active,false);
 db.set('books',JSON.stringify({active:['research']}));db.set('living-library-atlas-view-v1','{"collection":"shelf"}');vm.runInContext('restoreBooks();',ctx);assert.equal(books[2].active,false);
});
