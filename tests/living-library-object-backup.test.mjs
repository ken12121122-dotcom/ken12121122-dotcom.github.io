import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {webcrypto} from 'node:crypto';
const require=createRequire(import.meta.url),craft=require('../living-library/live/object-craft.js'),backup=require('../living-library/live/object-backup.js');
if(!globalThis.crypto)globalThis.crypto=webcrypto;
const memory=()=>{const values=new Map();return {getItem:k=>values.get(k)??null,setItem:(k,v)=>values.set(k,v)}};
function fixture(){const storage=memory();let n=0;const random={randomUUID:()=>`synthetic-${++n}`};const a=craft.createBlankCard(storage,random);craft.createBlankBook(storage,random,'Synthetic book');craft.recycleCard(storage,a.id);return storage}
test('exact byte backup includes recycled cards, book, IDs, revisions and unknown fields without writes',async()=>{
 const storage=fixture(),state=craft.read(storage);state.objects[0].future={nested:['preserve']};state.futureTop='keep';
 const raw=JSON.stringify(state,null,2)+'\n';storage.setItem(craft.KEY,raw);storage.setItem('synthetic-gba','keep');
 storage.setItem=()=>assert.fail('read-only backup wrote storage');
 const text=await backup.exportBackup(storage),result=await backup.inspectBackup(text);
 assert.equal(result.source_text,raw);assert.equal(result.summary.total,2);assert.equal(result.summary.recycled,1);assert.equal(result.summary.books,1);
 assert.deepEqual(result.summary.entries.map(x=>[x.id,x.revision]),state.objects.map(x=>[x.id,x.revision]));
 assert.equal(storage.getItem('synthetic-gba'),'keep');assert.ok(Object.isFrozen(result.summary.entries[0]));
});
test('absent storage differs from an explicitly saved empty collection',async()=>{
 const storage=memory();const absent=await backup.inspectBackup(await backup.exportBackup(storage));
 storage.setItem(craft.KEY,'{"version":1,"objects":[]}');const empty=await backup.inspectBackup(await backup.exportBackup(storage));
 assert.equal(absent.summary.present,false);assert.equal(absent.source_text,null);assert.equal(empty.summary.present,true);assert.notEqual(absent.sha256,empty.sha256);
});
test('corrupted backup checksum is rejected, never applied',async()=>{
 const storage=fixture(),raw=storage.getItem(craft.KEY),envelope=JSON.parse(await backup.exportBackup(storage));
 envelope.source_text=envelope.source_text.replace('Synthetic book','Tampered book');
 await assert.rejects(backup.inspectBackup(JSON.stringify(envelope)),{code:'BACKUP_CHECKSUM_MISMATCH'});assert.equal(storage.getItem(craft.KEY),raw);
});
test('unsupported versions, malformed JSON, duplicate IDs and invalid revisions fail closed',async()=>{
 const storage=fixture(),state=craft.read(storage);
 for(const [raw,code] of [['{','BACKUP_INVALID_JSON'],['{"version":2,"objects":[]}','BACKUP_SOURCE_VERSION'],
  [JSON.stringify({...state,objects:[state.objects[0],state.objects[0]]}),'BACKUP_DUPLICATE_ID'],
  [JSON.stringify({...state,objects:[{...state.objects[0],revision:0}]}),'BACKUP_INVALID_OBJECT']]){
  storage.setItem(craft.KEY,raw);await assert.rejects(backup.exportBackup(storage),{code});assert.equal(storage.getItem(craft.KEY),raw);
 }
});
test('storage errors are redacted and foreign envelopes are rejected',async()=>{
 await assert.rejects(backup.exportBackup({getItem(){throw Error('synthetic-secret')}}),{message:'BACKUP_STORAGE_UNAVAILABLE'});
 const text=await backup.exportBackup(memory()),envelope=JSON.parse(text);
 for(const patch of [{source_key:'other'},{schema:'nw.spell.v1'},{extra:true},{source_text:{}}])await assert.rejects(backup.inspectBackup(JSON.stringify({...envelope,...patch})),{code:'BACKUP_ENVELOPE_INVALID'});
});
test('bounded source rejects oversized bytes without deleting source',async()=>{
 const raw='x'.repeat(backup.MAX_BYTES+1);await assert.rejects(backup.exportBackup({getItem:()=>raw}),{code:'BACKUP_SIZE_OR_TYPE'});
});
