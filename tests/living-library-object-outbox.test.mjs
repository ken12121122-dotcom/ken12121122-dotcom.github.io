import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
const require=createRequire(import.meta.url),{ObjectOutbox}=require('../living-library/live/object-outbox.js');
test('outbox refuses unavailable storage and unrelated database namespaces',async()=>{
 await assert.rejects(ObjectOutbox.open(),{code:'OBJECT_OUTBOX_UNAVAILABLE'});
});
test('explicit candidate creator capture never injects an outbox worker into the scene',async()=>{
 const text=await readFile(new URL('../living-library/live/index.html',import.meta.url),'utf8');
 assert.doesNotMatch(text,/object-outbox\.js|LivingObjectOutbox/);
});
