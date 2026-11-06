import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url),{ActionRegistry}=require('../living-library/live/action-registry.js');
const {CharacterTapAdapter}=require('../living-library/live/character-tap-adapter.js');
const inputs={type:'object',properties:{target_id:{type:'string',minLength:1}},required:['target_id'],additionalProperties:false};
function fixture(patch={},platform='web'){
 const registry=new ActionRegistry({platform}),calls=[],contexts=[],state={event:true,feedback:true,guard:()=>true,execute:null};
 for(const [action_id,kind] of [['character.tap','event'],['ui.toast','effect']])registry.register({action_id,name:action_id,kind,version:'0.1.0',inputs,outputs:{type:'boolean'},target_type:'library.synthetic_character',platform:['web'],policy:{scope:'local'},guard:()=>state.guard(action_id),execute:(payload,context)=>{calls.push(action_id);contexts.push(context);return state.execute?state.execute(action_id,payload,context):action_id==='character.tap'?state.event:state.feedback},...(patch[action_id]||{})});
 return {registry,calls,contexts,state,adapter:new CharacterTapAdapter(registry)};
}
function hold(f,id){let entered,release;const ready=new Promise(r=>entered=r);f.state.guard=action=>action===id?new Promise(r=>{release=r;entered()}):true;return {ready,release:()=>release(true)}}
test('synthetic tap dispatches registered event then local feedback with one immutable target context',async()=>{
 const f=fixture();assert.deepEqual(f.calls,[]);const receipt=await f.adapter.dispatch('synthetic.avatar');
 assert.equal(receipt.status,'success');assert.equal(receipt.runtime_verified,false);assert.deepEqual(f.calls,['character.tap','ui.toast']);
 assert.equal(f.contexts[0],f.contexts[1]);assert.equal(f.contexts[0].target_object_instance_id,'synthetic.avatar');assert.ok(Object.isFrozen(f.contexts[0]));
 assert.deepEqual(receipt.completed_actions,['character.tap','ui.toast']);assert.ok(Object.isFrozen(receipt.completed_actions));
 assert.equal((await f.adapter.dispatch('synthetic.avatar')).status,'success');
});
test('false event, rejected guard and invalid target do not produce feedback',async()=>{
 const f=fixture();f.state.event=false;assert.equal((await f.adapter.dispatch('synthetic.avatar')).status,'not_matched');assert.deepEqual(f.calls,['character.tap']);
 f.calls.length=0;f.state.guard=()=>false;assert.equal((await f.adapter.dispatch('synthetic.avatar')).error_code,'GUARD_REJECTED');assert.deepEqual(f.calls,[]);
 for(const target of ['',null,{},'with space','x'.repeat(129)])assert.equal((await f.adapter.dispatch(target)).error_code,'INVALID_INPUT');assert.deepEqual(f.calls,[]);
});
test('both input contracts are checked before either handler starts',async()=>{
 const f=fixture({'ui.toast':{inputs:{type:'object',properties:{message:{type:'string'}},required:['message'],additionalProperties:false}}});
 const receipt=await f.adapter.dispatch('synthetic.avatar');assert.equal(receipt.error_code,'INVALID_INPUT');assert.deepEqual(f.calls,[]);
});
test('abort during an awaited feedback guard prevents feedback and reports partial progress',async()=>{
 const f=fixture(),held=hold(f,'ui.toast'),controller=new AbortController(),pending=f.adapter.dispatch('synthetic.avatar',{signal:controller.signal});
 await held.ready;controller.abort();held.release();const r=await pending;
 assert.equal(r.status,'partial_failure');assert.equal(r.error_code,'DISPATCH_CANCELLED');assert.deepEqual(r.completed_actions,['character.tap']);assert.equal(r.uncertain_action,'ui.toast');assert.deepEqual(f.calls,['character.tap']);
});
test('disposing during the event guard prevents entry and permanently closes the adapter',async()=>{
 const f=fixture(),held=hold(f,'character.tap'),pending=f.adapter.dispatch('synthetic.avatar');await held.ready;f.adapter.dispose();f.adapter.dispose();held.release();
 assert.equal((await pending).error_code,'DISPATCH_DISPOSED');assert.deepEqual(f.calls,[]);assert.equal((await f.adapter.dispatch('synthetic.avatar')).status,'disposed');
});
test('overlapping or handler-recursive dispatch is busy and cannot duplicate feedback',async()=>{
 const f=fixture(),held=hold(f,'character.tap'),pending=f.adapter.dispatch('synthetic.avatar');await held.ready;
 assert.equal((await f.adapter.dispatch('synthetic.other')).status,'busy');held.release();assert.equal((await pending).status,'success');assert.equal(f.calls.length,2);
 f.state.guard=()=>true;f.state.execute=async id=>{if(id==='character.tap')assert.equal((await f.adapter.dispatch('synthetic.recursive')).status,'busy');return true};
 assert.equal((await f.adapter.dispatch('synthetic.avatar')).status,'success');assert.equal(f.calls.length,4);
});
test('feedback false, invalid output and handler errors never create a false success receipt',async()=>{
 const f=fixture();f.state.feedback=false;assert.equal((await f.adapter.dispatch('synthetic.avatar')).status,'feedback_not_confirmed');
 f.state.execute=()=> 'wrong';assert.equal((await f.adapter.dispatch('synthetic.avatar')).error_code,'INVALID_OUTPUT');
 f.state.execute=()=>{const e=Error('SYNTHETIC_PRIVATE_MESSAGE');e.code='SYNTHETIC_PRIVATE_CODE';throw e};const receipt=await f.adapter.dispatch('synthetic.avatar');assert.equal(receipt.error_code,'HANDLER_FAILED');assert.ok(!JSON.stringify(receipt).includes('SYNTHETIC_PRIVATE'));
 f.state.execute=null;f.state.feedback=true;assert.equal((await f.adapter.dispatch('synthetic.avatar')).status,'success');
});
test('missing handlers, wrong roles, private/native scope and incompatible targets fail construction',()=>{
 assert.throws(()=>new CharacterTapAdapter(new ActionRegistry()),{code:'MISSING_ACTION'});
 for(const [patch,code] of [[{'character.tap':{kind:'effect'}},'WRONG_ACTION_KIND'],[{'ui.toast':{policy:{scope:'private_sync'}}},'OWNER_GATE_REQUIRED'],[{'ui.toast':{policy:{scope:'native'}}},'OWNER_GATE_REQUIRED'],[{'ui.toast':{target_type:'library.other'}},'TARGET_TYPE_MISMATCH']])assert.throws(()=>fixture(patch),{code});
});
test('existing platform gate and pre-aborted cancellation remain effective without poisoning the adapter lock',async()=>{
 const android=fixture({},'android');assert.equal((await android.adapter.dispatch('synthetic.avatar')).error_code,'UNSUPPORTED_PLATFORM');assert.deepEqual(android.calls,[]);
 const f=fixture(),controller=new AbortController();controller.abort();assert.equal((await f.adapter.dispatch('synthetic.avatar',{signal:controller.signal})).error_code,'DISPATCH_CANCELLED');assert.deepEqual(f.calls,[]);assert.equal((await f.adapter.dispatch('synthetic.avatar')).status,'success');
});
