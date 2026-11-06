import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const {OutboxPump}=require('../living-library/live/outbox-pump.js');
function fixture(){
 const row={spell_id:'spell.mock',local_revision:1,content_hash:'a'.repeat(64),idempotency_key:'mock-key',attempts:0,payload:Object.freeze({name:'Synthetic'})};
 const state={rows:[row],acks:0,failures:0};
 const store={due:async()=>state.rows,get:async()=>row,acknowledge:async()=>{state.acks++;state.rows=[];return {applied:true,reason:'ACKNOWLEDGED'}},recordFailure:async()=>{state.failures++;return {applied:true,reason:'RETRY_SCHEDULED'}}};
 return {store,state};
}
test('one bounded batch and immutable payload envelope; no background sends',async()=>{
 const {store,state}=fixture();let calls=0;
 const pump=new OutboxPump(store,async request=>{calls++;assert.ok(Object.isFrozen(request));return {}},{batch_limit:1});
 assert.equal(calls,0);assert.equal((await pump.run()).items[0].result,'ACKNOWLEDGED');assert.equal(state.acks,1);assert.equal((await pump.run()).status,'NO_WORK');assert.equal(calls,1);
});
test('same store single-flight spans separate pump objects',async()=>{
 const {store}=fixture();let send,entered;const started=new Promise(r=>entered=r);
 const first=new OutboxPump(store,()=>{entered();return new Promise(r=>send=r)});
 const running=first.run();await started;assert.equal((await new OutboxPump(store,async()=>({})).run()).status,'BUSY');send({});await running;
});
test('cancel before send never invokes transport or changes queue',async()=>{
 const {store,state}=fixture();let calls=0;const c=new AbortController();c.abort();
 assert.equal((await new OutboxPump(store,()=>{calls++}).run({signal:c.signal})).status,'CANCELLED');assert.equal(calls,0);assert.equal(state.failures,0);
});
test('cancel during transport ignores late receipt and retains lock until settled',async()=>{
 const {store,state}=fixture();let send,entered;const started=new Promise(r=>entered=r),c=new AbortController();
 const pump=new OutboxPump(store,()=>{entered();return new Promise(r=>send=r)});const run=pump.run({signal:c.signal});await started;c.abort();
 assert.equal((await run).status,'CANCELLED');assert.equal((await pump.run()).status,'BUSY');send({});await new Promise(r=>setTimeout(r,0));assert.equal(state.acks,0);assert.equal(state.failures,0);
});
test('timeout settles the run but holds lock for uncooperative transport and ignores late ACK',async()=>{
 const {store,state}=fixture();let send;
 const pump=new OutboxPump(store,()=>new Promise(r=>send=r),{timeout_ms:5});
 assert.equal((await pump.run()).status,'TIMEOUT');assert.equal(state.failures,1);assert.equal((await pump.run()).status,'BUSY');send({});await new Promise(r=>setTimeout(r,0));assert.equal(state.acks,0);
});
test('transport errors are redacted and schedule retry instead of false acknowledgement',async()=>{
 const {store,state}=fixture();const pump=new OutboxPump(store,async()=>{throw Error('synthetic-secret')});const result=await pump.run();
 assert.equal(result.items[0].result,'RETRY_SCHEDULED');assert.doesNotMatch(JSON.stringify(result),/synthetic-secret/);assert.equal(state.acks,0);
});
test('unmatched receipt retries and stale receipt leaves new revision alone',async()=>{
 const {store,state}=fixture();store.acknowledge=async()=>({applied:false,reason:'ACK_MISMATCH'});await new OutboxPump(store,async()=>({})).run();assert.equal(state.failures,1);
 store.acknowledge=async()=>({applied:false,reason:'STALE_OUTBOX'});assert.equal((await new OutboxPump(store,async()=>({})).run()).items[0].result,'STALE_OUTBOX');assert.equal(state.failures,1);
});
test('storage failure stops the batch, without treating a remote response as durable success',async()=>{
 const {store}=fixture();store.acknowledge=async()=>{throw Object.assign(Error('synthetic-storage'),{code:'OUTBOX_UPDATE_FAILED'})};
 const result=await new OutboxPump(store,async()=>({})).run();assert.equal(result.status,'STORAGE_ERROR');assert.equal(result.items.length,0);
});
