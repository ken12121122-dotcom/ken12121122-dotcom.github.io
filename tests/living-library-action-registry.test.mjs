import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {ActionRegistry}=createRequire(import.meta.url)('../living-library/live/action-registry.js');
const inputs={type:'object',properties:{target_id:{type:'string',minLength:1}},required:['target_id'],additionalProperties:false};
const outputs={type:'boolean'};
const definition=(patch={})=>({action_id:'character.tap',name:'Character tap',kind:'event',version:'0.1.0',inputs,outputs,platform:['web'],policy:{scope:'local'},guard:()=>true,execute:()=>true,...patch});
const rejects=(promise,code)=>assert.rejects(promise,e=>e.code===code);
test('one stable action ID can be invoked from multiple independent references',async()=>{
 const r=new ActionRegistry();let calls=0;r.register(definition({execute:()=>{calls++;return true}}));
 for(const ref of [{action_id:'character.tap',label:'A'},{action_id:'character.tap',label:'B'}])assert.equal(await r.invoke(ref.action_id,{target_id:'synthetic-avatar'}),true);
 assert.equal(calls,2);assert.equal(r.list().length,1);
 assert.throws(()=>r.register(definition()),e=>e.code==='DUPLICATE_ACTION');
});
test('malformed definitions, absent handlers and unsupported schemas are rejected',()=>{
 for(const patch of [{action_id:'display label'},{guard:null},{execute:null},{version:'latest'},{policy:{scope:'admin'}},{inputs:{type:'object'}},{inputs:{type:'string',pattern:'a'}}])assert.throws(()=>new ActionRegistry().register(definition(patch)));
 assert.throws(()=>new ActionRegistry().register(definition({kind:'condition',outputs:{type:'string'}})),e=>e.code==='INVALID_CONDITION_OUTPUT');
});
test('input validation rejects missing IDs, blank text, extra fields and wrong target types before effects',async()=>{
 const r=new ActionRegistry();let calls=0;r.register(definition({execute:()=>{calls++;return true}}));
 for(const input of [{},{target_id:''},{target_id:'   '},{target_id:1},{target_id:'x',permission:'admin'},null])await rejects(r.invoke('character.tap',input),'INVALID_INPUT');
 assert.equal(calls,0);await rejects(r.invoke('not.registered',{}),'MISSING_ACTION');
});
test('guard, platform and owner gates cannot be bypassed by card payload or context',async()=>{
 const r=new ActionRegistry();r.register(definition({guard:()=>false}));await rejects(r.invoke('character.tap',{target_id:'x'}),'GUARD_REJECTED');
 for(const scope of ['private_sync','native']){const x=new ActionRegistry();x.register(definition({policy:{scope}}));await rejects(x.invoke('character.tap',{target_id:'x'},{approved:true,token:'synthetic'}),'OWNER_GATE_REQUIRED')}
 const android=new ActionRegistry({platform:'android'});android.register(definition());await rejects(android.invoke('character.tap',{target_id:'x'}),'UNSUPPORTED_PLATFORM');
});
test('nested reentry and overlapping calls are rejected, then the lock is released',async()=>{
 const r=new ActionRegistry();let finish;r.register(definition({execute:()=>new Promise(resolve=>{finish=resolve})}));
 const first=r.invoke('character.tap',{target_id:'x'});await Promise.resolve();
 await rejects(r.invoke('character.tap',{target_id:'y'}),'ACTION_REENTRY');finish(true);assert.equal(await first,true);
 const next=r.invoke('character.tap',{target_id:'z'});await Promise.resolve();finish(true);assert.equal(await next,true);
 const nested=new ActionRegistry();nested.register(definition({execute:p=>nested.invoke('character.tap',p)}));await rejects(nested.invoke('character.tap',{target_id:'x'}),'ACTION_REENTRY');
});
test('failed guard, thrown handler and invalid output release locks without false success',async()=>{
 const r=new ActionRegistry();let count=0;r.register(definition({execute:()=>{count++;if(count===1)throw new Error('synthetic failure');return count===2?'wrong':true}}));
 await assert.rejects(r.invoke('character.tap',{target_id:'x'}),/synthetic failure/);await rejects(r.invoke('character.tap',{target_id:'x'}),'INVALID_OUTPUT');assert.equal(await r.invoke('character.tap',{target_id:'x'}),true);
});
test('definitions and payloads cannot be changed through external references',async()=>{
 const r=new ActionRegistry();const d=definition({inputs:structuredClone(inputs),execute:p=>{assert.ok(Object.isFrozen(p));return p.target_id==='x'}});const registered=r.register(d);
 d.inputs.properties.target_id.type='number';assert.throws(()=>{registered.policy.scope='native'},TypeError);
 const payload={target_id:'x'},call=r.invoke('character.tap',payload);payload.target_id='y';assert.equal(await call,true);assert.equal(r.describe('character.tap').inputs.properties.target_id.type,'string');
});
