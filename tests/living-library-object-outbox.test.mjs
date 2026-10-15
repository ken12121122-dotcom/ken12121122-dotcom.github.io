import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
const require=createRequire(import.meta.url),{ObjectOutbox}=require('../living-library/live/object-outbox.js');
test('outbox refuses unavailable storage and unrelated database namespaces',async()=>{
 await assert.rejects(ObjectOutbox.open(),{code:'OBJECT_OUTBOX_UNAVAILABLE'});
});
test('candidate object outbox is not enabled by the production scene or creator',async()=>{
 for(const name of ['index.html','card-creation.html']){
  const text=await readFile(new URL('../living-library/live/'+name,import.meta.url),'utf8');
  assert.doesNotMatch(text,/object-outbox\.js|LivingObjectOutbox/);
 }
});
