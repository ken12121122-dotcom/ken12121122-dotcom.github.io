import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url),{ActionRegistry}=require('../living-library/live/action-registry.js'),{SpellExecutor}=require('../living-library/live/spell-executor.js');
function fixture(input={type:'object',properties:{},required:[],additionalProperties:false},parameters={}){
 const registry=new ActionRegistry(),calls=[],guards=[];
 const ids=['book.drag_active','book.synthetic_effect'];
 ids.forEach((action_id,i)=>registry.register({action_id,name:action_id,kind:i?'effect':'condition',version:'0.1.0',target_type:'library.book_instance',inputs:input,outputs:{type:'boolean'},platform:['web'],policy:{scope:'local'},guard:()=>{guards.push(i);return true},execute:payload=>{calls.push({i,payload});return true}}));
 const card={schema:'nw.spell.v1',spell_id:'spell.synthetic.snapshot',version:'0.1.0',name:'Synthetic',description:'No scene',incantations:['synthetic phrase'],enabled:true,target_type:'library.book_instance',runes:ids.map((action_id,i)=>({schema:'nw.rune.v1',rune_id:'r'+i,action_id,action_version:'0.1.0',label:action_id,parameters})),conditions:{operator:'AND',refs:['r0']},effects:{operator:'SEQUENCE',refs:['r1']},connections:[{from:'r0',to:'r1',operator:'AND'}]};
 return {card,calls,guards,executor:new SpellExecutor(registry)};
}
const run=f=>f.executor.run(f.card,{incantation:'synthetic phrase'});
async function rejected(f){await assert.rejects(run(f),e=>e.code==='INVALID_CARD_SCHEMA'&&e.message==='INVALID_CARD_SCHEMA');assert.deepEqual(f.calls,[]);assert.deepEqual(f.guards,[])}
test('card getters are rejected before contract reads, without running getter code',async()=>{
 const f=fixture();let reads=0;Object.defineProperty(f.card,'enabled',{enumerable:true,get(){reads++;return true}});await rejected(f);assert.equal(reads,0);
});
test('nested parameter getters are rejected without executing accessors or handlers',async()=>{
 const f=fixture();let reads=0;Object.defineProperty(f.card.runes[1].parameters,'secret',{enumerable:true,get(){reads++;return 'synthetic'}});await rejected(f);assert.equal(reads,0);
});
test('serialization hooks cannot replace the configuration after contract validation',async()=>{
 const f=fixture();let hooks=0;Object.defineProperty(f.card,'toJSON',{value(){hooks++;return {...f.card,enabled:false}}});await rejected(f);assert.equal(hooks,0);
});
test('non-JSON payloads are not silently dropped, normalized or leaked in errors',async()=>{
 for(const value of [undefined,NaN,Infinity,1n,()=>true,new Date(0),new Map(),Object.assign(Object.create({}),{v:1})]){
  const f=fixture();f.card.runes[1].parameters.extra=value;await rejected(f);
 }
 const f=fixture();f.card.runes[1].parameters.self=f.card;await rejected(f);
});
test('hidden fields, symbols, extended and sparse arrays reject before any guard',async()=>{
 for(const mutate of [f=>Object.defineProperty(f.card,'hidden',{value:'synthetic'}),f=>f.card[Symbol('synthetic')]=1,f=>f.card.incantations.extra=1,f=>{f.card.incantations.length=2}]){const f=fixture();mutate(f);await rejected(f)}
});
test('shared plain JSON data and null-prototype objects remain valid and detached',async()=>{
 const parameters=Object.create(null),f=fixture(undefined,parameters),before=JSON.stringify(f.card);const receipt=await run(f);assert.equal(receipt.status,'success');assert.equal(JSON.stringify(f.card),before);assert.equal(Object.isFrozen(parameters),false);assert.equal(f.calls.length,2);assert.notEqual(f.calls[1].payload,parameters);assert.equal(Object.isFrozen(f.calls[1].payload),true);
});
test('maximum registry payload depth remains valid within the card envelope',async()=>{
 let schema={type:'integer'},value=1;
 for(let i=0;i<12;i++){schema={type:'object',properties:{v:schema},required:['v'],additionalProperties:false};value={v:value}}
 const f=fixture(schema,value);assert.equal((await run(f)).status,'success');assert.equal(f.calls.length,2);
});
test('over-deep data fails safely and a corrected card can subsequently run',async()=>{
 const f=fixture();let value={};for(let i=0;i<30;i++)value={v:value};f.card.runes[1].parameters=value;await rejected(f);f.card.runes[1].parameters={};assert.equal((await run(f)).status,'success');
});
