import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url),{create}=require('../living-library/live/object-reconciler.js');
test('creator coalesces triggers in one flight with at most two fresh captures',async()=>{
 let release,calls=0,closed=0,revision=1;const snapshots=[],statuses=[];
 const gate=new Promise(r=>release=r);
 const r=create({storage:{getItem:()=>revision},onStatus:s=>statuses.push(s),open:async()=>({
  async capture(storage){calls++;snapshots.push(storage.getItem());if(calls===1)await gate;else r.request();return {queued:1}},
  pending:async()=>[{}],close(){closed++}
 })});
 const flight=r.request();await new Promise(r=>setImmediate(r));revision=2;
 assert.equal(r.request(),flight);release();const result=await flight;
 assert.equal(calls,2);assert.equal(closed,2);assert.deepEqual(snapshots,[1,2]);assert.equal(result.state,'RECONCILE_REQUIRED');
 assert.equal(statuses.at(-1).state,'RECONCILE_REQUIRED');r.dispose();
});
test('quota failure does not loop, leak errors or write source; a fresh trigger recovers',async()=>{
 let fail=true,calls=0;const states=[],storage={getItem:()=> 'original'};
 const r=create({storage,onStatus:s=>states.push(s),open:async()=>({capture:async()=>{calls++;if(fail)throw Error('synthetic-secret');return {queued:1}},pending:async()=>[{}],close(){}})});
 assert.equal((await r.request()).state,'RECONCILE_REQUIRED');assert.equal(calls,1);
 assert.doesNotMatch(JSON.stringify(states),/synthetic-secret/);assert.equal(storage.getItem(),'original');
 fail=false;assert.equal((await r.request()).state,'LOCAL_PENDING');assert.equal(calls,2);
});
test('source-change gets only one retry per trigger burst',async()=>{
 let calls=0;
 const r=create({storage:{},open:async()=>({capture:async()=>{calls++;throw {code:'OBJECT_OUTBOX_SOURCE_CHANGED'}},close(){}})});
 assert.equal((await r.request()).state,'RECONCILE_REQUIRED');assert.equal(calls,2);
});
test('dispose during open closes handle without capturing or updating detached UI',async()=>{
 let release,captures=0,closed=0;const states=[];
 const r=create({storage:{},onStatus:s=>states.push(s),open:()=>new Promise(x=>release=x)});
 const f=r.request();await new Promise(r=>setImmediate(r));r.dispose();
 release({capture:async()=>{captures++},close(){closed++}});
 assert.equal((await f).state,'STOPPED');assert.equal(captures,0);assert.equal(closed,1);assert.equal(states.length,1);
 assert.equal((await r.request()).state,'STOPPED');
});
