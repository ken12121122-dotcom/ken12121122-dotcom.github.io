import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const {ActionRegistry}=require('../living-library/live/action-registry.js');
const {TargetInteractionSession}=require('../living-library/live/interaction-session.js');
const {InteractionDispatcher}=require('../living-library/live/interaction-dispatch.js');
function fixture(){
 const state={time:0,guard:()=>true,effect:()=>true},calls=[];
 const session=new TargetInteractionSession({now:()=>state.time,idFactory:()=> 'synthetic',maxAgeMs:100});
 const registry=new ActionRegistry();
 const ids=['book.drag_active','book.synthetic_first','book.synthetic_second'];
 ids.forEach((action_id,i)=>registry.register({action_id,name:action_id,kind:i?'effect':'condition',version:'0.1.0',target_type:'library.book_instance',inputs:{type:'object',properties:{},required:[],additionalProperties:false},outputs:{type:'boolean'},platform:['web'],policy:{scope:'local'},guard:()=>i===1?state.guard():true,execute:(_,context)=>{calls.push({action_id,target:context.target_object_instance_id});return i===1?state.effect():true}}));
 const card={schema:'nw.spell.v1',spell_id:'spell.synthetic.dispatch',version:'0.1.0',name:'Synthetic',description:'No scene effects',incantations:['synthetic phrase'],enabled:true,target_type:'library.book_instance',runes:ids.map((action_id,i)=>({schema:'nw.rune.v1',rune_id:'r'+i,action_id,action_version:'0.1.0',label:action_id,parameters:{}})),conditions:{operator:'AND',refs:['r0']},effects:{operator:'SEQUENCE',refs:['r1','r2']},connections:[{from:'r0',to:'r1',operator:'AND'},{from:'r1',to:'r2',operator:'SEQUENCE'}]};
 const context=()=>{session.beginGrab({target_id:'book.synthetic.a',pointer_id:7});return session.resolveVoiceResult(session.captureVoiceContext(),{transcript:'synthetic phrase',incantations:card.incantations}).context};
 const dispatcher=new InteractionDispatcher({registry,session,allowedActions:ids});
 return {state,calls,session,registry,card,context,dispatcher,ids};
}
const options=context=>({context,incantation:'synthetic phrase'});
function holdGuard(f){let release,entered;const ready=new Promise(r=>entered=r);f.state.guard=()=>{entered();return new Promise(r=>release=r)};return {ready,release:()=>release(true)}}
test('controlled dispatcher preserves target, sequence and non-runtime receipt',async()=>{
 const f=fixture(),r=await f.dispatcher.run(f.card,options(f.context()));
 assert.equal(r.status,'success');assert.deepEqual(r.completed_effects,['r1','r2']);assert.equal(r.runtime_verified,false);
 assert.deepEqual(f.calls.map(x=>x.target),Array(3).fill('book.synthetic.a'));
});
test('forged, released and mismatched target contexts cannot enter a handler',async()=>{
 const f=fixture(),ctx=f.context();
 await assert.rejects(f.dispatcher.run(f.card,options({...ctx})),{code:'STALE_INTERACTION'});
 const wrong=f.context();f.card.target_type='library.other';
 // The graph itself rejects the mismatched target before dispatch.
 await assert.rejects(f.dispatcher.run(f.card,options(wrong)));
 f.card.target_type='library.book_instance';f.session.cancel();
 await assert.rejects(f.dispatcher.run(f.card,options(ctx)),{code:'STALE_INTERACTION'});assert.deepEqual(f.calls,[]);
});
for(const change of ['replace','cancel','expire','abort'])test('async guard cannot outlive '+change,async()=>{
 const f=fixture(),ctx=f.context(),held=holdGuard(f),abort=new AbortController();
 const pending=f.dispatcher.run(f.card,{...options(ctx),signal:abort.signal});await held.ready;
 if(change==='replace')f.session.beginGrab({target_id:'book.synthetic.b',pointer_id:8});
 if(change==='cancel')f.session.cancel();if(change==='expire')f.state.time=101;if(change==='abort')abort.abort();
 held.release();const receipt=await pending;
 assert.equal(receipt.status,'failed');assert.equal(receipt.error_code,change==='abort'?'SPELL_CANCELLED':'STALE_INTERACTION');
 assert.deepEqual(f.calls.map(x=>x.action_id),['book.drag_active']);
 assert.deepEqual(receipt.completed_effects,[]);
 // Existing executor conservatively marks attempted effects uncertain; not rollback.
 assert.equal(receipt.uncertain_effect,'r1');
});
test('same context is consumed before awaiting; fresh interaction permits a new run',async()=>{
 const f=fixture(),ctx=f.context(),held=holdGuard(f),pending=f.dispatcher.run(f.card,options(ctx));await held.ready;
 await assert.rejects(f.dispatcher.run(f.card,options(ctx)),{code:'CONTEXT_ALREADY_DISPATCHED'});
 held.release();assert.equal((await pending).status,'success');
 await assert.rejects(f.dispatcher.run(f.card,options(ctx)),{code:'CONTEXT_ALREADY_DISPATCHED'});
 f.state.guard=()=>true;assert.equal((await f.dispatcher.run(f.card,options(f.context()))).status,'success');
});
test('context invalidation by an effect stops remaining sequence with honest partial receipt',async()=>{
 const f=fixture();f.state.effect=()=>{f.session.cancel();return true};
 const r=await f.dispatcher.run(f.card,options(f.context()));assert.equal(r.status,'partial_failure');
 assert.deepEqual(r.completed_effects,['r1']);assert.equal(r.error_code,'STALE_INTERACTION');assert.equal(f.calls.length,2);
});
test('explicit allowlist is detached and cannot grant native or private authority',async()=>{
 const f=fixture(),allowed=[f.ids[0]],d=new InteractionDispatcher({registry:f.registry,session:f.session,allowedActions:allowed});allowed.push(...f.ids.slice(1));
 await assert.rejects(d.run(f.card,options(f.context())),{code:'ACTION_NOT_ALLOWED'});
 for(const scope of ['native','private_sync']){
  const registry=new ActionRegistry();registry.register({action_id:'synthetic.restricted',name:'Restricted',kind:'effect',version:'0.1.0',inputs:{type:'object',properties:{},required:[],additionalProperties:false},outputs:{type:'boolean'},platform:['web'],policy:{scope},guard:()=>true,execute:()=>true});
  assert.throws(()=>new InteractionDispatcher({registry,session:f.session,allowedActions:['synthetic.restricted']}),{code:'OWNER_GATE_REQUIRED'});
 }
 assert.deepEqual(f.calls,[]);
});
test('failure messages remain redacted and failed context cannot replay',async()=>{
 const f=fixture(),ctx=f.context();f.state.effect=()=>{throw Error('SYNTHETIC_PRIVATE_PAYLOAD')};
 const r=await f.dispatcher.run(f.card,options(ctx));assert.equal(r.error_code,'HANDLER_FAILED');assert.ok(!JSON.stringify(r).includes('PRIVATE_PAYLOAD'));
 await assert.rejects(f.dispatcher.run(f.card,options(ctx)),{code:'CONTEXT_ALREADY_DISPATCHED'});
});
test('checkpoint must be synchronous strict true and cannot override action guard',async()=>{
 const f=fixture(),ctx=f.context();
 await assert.rejects(f.registry.invoke(f.ids[1],{},ctx,()=>Promise.resolve(true)),{code:'STALE_INTERACTION'});
 f.state.guard=()=>false;let checkpoint=false;
 await assert.rejects(f.registry.invoke(f.ids[1],{},ctx,()=>{checkpoint=true;return true}),{code:'GUARD_REJECTED'});
 assert.equal(checkpoint,false);assert.deepEqual(f.calls,[]);
});
