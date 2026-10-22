import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url),{ActionRegistry}=require('../living-library/live/action-registry.js'),{SpellExecutor}=require('../living-library/live/spell-executor.js'),{validateSpell}=require('../living-library/live/spell-contract.js');
const empty={type:'object',properties:{},required:[],additionalProperties:false};
function fixture(){
 const registry=new ActionRegistry(),calls=[],guards=[],state={firstGuard:true,lateFail:false,hold:null,slot:null};
 const ids=['book.drag_active','book.synthetic_first','book.synthetic_later'];
 ids.forEach((action_id,i)=>registry.register({action_id,name:action_id,kind:i?'effect':'condition',version:'0.1.0',target_type:'library.book_instance',inputs:i===2?{type:'object',properties:{slot:{type:'integer',minimum:0,maximum:2}},required:['slot'],additionalProperties:false}:empty,outputs:{type:'boolean'},platform:['web'],policy:{scope:'local'},guard:()=>{guards.push(action_id);return i===1?state.firstGuard:true},execute:payload=>{calls.push(action_id);if(i===0&&state.hold)return state.hold;if(i===2){state.slot=payload.slot;if(state.lateFail)throw Error('SYNTHETIC_PRIVATE_DETAIL')}return true}}));
 const card={schema:'nw.spell.v1',spell_id:'spell.synthetic.preflight',version:'0.1.0',name:'Synthetic',description:'No live scene',incantations:['synthetic phrase'],enabled:true,target_type:'library.book_instance',runes:ids.map((action_id,i)=>({schema:'nw.rune.v1',rune_id:'r'+i,action_id,action_version:'0.1.0',label:action_id,parameters:i===2?{slot:1}:{}})),conditions:{operator:'AND',refs:['r0']},effects:{operator:'SEQUENCE',refs:['r1','r2']},connections:[{from:'r0',to:'r1',operator:'AND'},{from:'r1',to:'r2',operator:'SEQUENCE'}]};
 return {registry,calls,guards,state,card,executor:new SpellExecutor(registry)};
}
const run=f=>f.executor.run(f.card,{incantation:'synthetic phrase'});
const noEffects=r=>{assert.deepEqual(r.completed_effects,[]);assert.deepEqual(r.attempted_effects,[]);assert.equal(r.uncertain_effect,null);assert.equal(r.runtime_verified,false)};
test('missing later-effect parameter is rejected before any event, guard or effect',async()=>{
 const f=fixture();f.card.runes[2].parameters={};const r=await run(f);assert.equal(r.status,'failed');assert.equal(r.error_code,'INVALID_INPUT');noEffects(r);assert.deepEqual(f.calls,[]);assert.deepEqual(f.guards,[]);
});
test('wrong types, extra keys and out-of-range later parameters never cause avoidable partial effects',async()=>{
 for(const parameters of [{slot:'1'},{slot:3},{slot:1,extra:true}]){const f=fixture();f.card.runes[2].parameters=parameters;const r=await run(f);assert.equal(r.status,'failed');assert.equal(r.error_code,'INVALID_INPUT');noEffects(r);assert.deepEqual(f.calls,[]);assert.deepEqual(f.guards,[])}
});
test('malformed first-effect and condition parameters also fail before handlers',async()=>{
 for(const index of [0,1]){const f=fixture();f.card.runes[index].parameters={extra:true};const r=await run(f);assert.equal(r.error_code,'INVALID_INPUT');noEffects(r);assert.deepEqual(f.calls,[]);assert.deepEqual(f.guards,[])}
});
test('draft validation, disabled and phrase-mismatch behavior remain separate from executable input validation',async()=>{
 const f=fixture();f.card.runes[2].parameters={};assert.equal(validateSpell(f.card,f.registry).valid,true);
 f.card.enabled=false;assert.equal((await run(f)).status,'disabled');f.card.enabled=true;assert.equal((await f.executor.run(f.card,{incantation:'other'})).status,'not_matched');assert.deepEqual(f.calls,[]);
 assert.equal((await run(f)).error_code,'INVALID_INPUT');
});
test('valid parameter preflight does not execute or bypass dynamic guards',async()=>{
 const f=fixture();f.state.firstGuard=false;const r=await run(f);assert.equal(r.error_code,'GUARD_REJECTED');assert.deepEqual(f.calls,['book.drag_active']);assert.deepEqual(f.guards,['book.drag_active','book.synthetic_first']);
});
test('preflight uses detached execution configuration; later edits affect only the next run and locks release',async()=>{
 const f=fixture();let finish;f.state.hold=new Promise(r=>finish=r);const pending=run(f);await Promise.resolve();await Promise.resolve();f.card.runes[2].parameters.slot='invalid';finish(true);
 assert.equal((await pending).status,'success');assert.equal(f.state.slot,1);f.calls.length=0;f.guards.length=0;
 assert.equal((await run(f)).error_code,'INVALID_INPUT');assert.deepEqual(f.calls,[]);assert.deepEqual(f.guards,[]);f.card.runes[2].parameters.slot=2;assert.equal((await run(f)).status,'success');assert.equal(f.state.slot,2);
});
test('pre-aborted runs keep cancellation priority and perform no preflight handlers',async()=>{
 const f=fixture(),controller=new AbortController();f.card.runes[2].parameters={};controller.abort();const r=await f.executor.run(f.card,{incantation:'synthetic phrase',signal:controller.signal});assert.equal(r.error_code,'SPELL_CANCELLED');noEffects(r);assert.deepEqual(f.calls,[]);
});
test('runtime handler failure after valid preflight still reports partial effects honestly',async()=>{
 const f=fixture();f.state.lateFail=true;const r=await run(f);assert.equal(r.status,'partial_failure');assert.deepEqual(r.completed_effects,['r1']);assert.deepEqual(r.attempted_effects,['r1','r2']);assert.equal(r.uncertain_effect,'r2');assert.equal(r.error_code,'HANDLER_FAILED');assert.ok(!JSON.stringify(r).includes('PRIVATE_DETAIL'));
});
