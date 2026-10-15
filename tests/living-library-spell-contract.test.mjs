import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const {ActionRegistry}=require('../living-library/live/action-registry.js');
const {validateSpell,validateRune}=require('../living-library/live/spell-contract.js');
function fixture(){
 const registry=new ActionRegistry();
 for(const [action_id,kind] of [['book.drag_active','condition'],['voice.incantation_matched','event'],['book.release_grab','effect'],['book.return_to_assigned_shelf','effect']])registry.register({action_id,name:action_id,kind,version:'0.1.0',target_type:'library.book_instance',inputs:{type:'object',properties:{},required:[],additionalProperties:false},outputs:{type:'boolean'},platform:['web'],policy:{scope:'local'},guard:()=>true,execute:()=>{throw new Error('Validation must not execute handlers')}});
 const card={schema:'nw.spell.v1',spell_id:'spell.synthetic.return',version:'0.1.0',name:'Synthetic return',description:'Fixture only',incantations:['synthetic spell'],enabled:true,target_type:'library.book_instance',runes:['book.drag_active','voice.incantation_matched','book.release_grab','book.return_to_assigned_shelf'].map((action_id,i)=>({schema:'nw.rune.v1',rune_id:'r'+i,action_id,action_version:'0.1.0',label:action_id,parameters:{}})),conditions:{operator:'AND',refs:['r0','r1']},effects:{operator:'SEQUENCE',refs:['r2','r3']},connections:[{from:'r0',to:'r2',operator:'AND'},{from:'r1',to:'r2',operator:'AND'},{from:'r2',to:'r3',operator:'SEQUENCE'}]};
 return {registry,card};
}
const invalid=(mutate,code)=>{const {registry,card}=fixture();mutate(card);assert.throws(()=>validateSpell(card,registry),e=>e.code===code)};
test('AND plus ordered effects validates without running actions or claiming runtime success',()=>{const {registry,card}=fixture();const result=validateSpell(card,registry);assert.equal(result.valid,true);assert.equal(result.runtime_verified,false);assert.equal(result.rune_count,4)});
test('renaming a rune or spell preserves action identity',()=>{const {registry,card}=fixture();card.name='Renamed';card.runes[0].label='Changed label';assert.ok(validateSpell(card,registry).valid);assert.equal(registry.list().length,4);assert.equal(validateRune(card.runes[0],registry).action_id,'book.drag_active')});
test('empty or duplicate incantations, missing action and stale version fail',()=>{
 invalid(c=>c.incantations=[' '],'INVALID_INCANTATIONS');invalid(c=>c.incantations=['x',' x '],'INVALID_INCANTATIONS');invalid(c=>c.runes[0].action_id='book.absent','MISSING_ACTION');invalid(c=>c.runes[0].action_version='9.0.0','ACTION_VERSION_MISMATCH');
});
test('target types, condition/effect slots and unused or duplicate node identities are checked',()=>{
 invalid(c=>c.target_type='library.avatar','TARGET_TYPE_MISMATCH');invalid(c=>{c.conditions.refs=['r0','r2'];c.effects.refs=['r1','r3']},'WRONG_ACTION_KIND');invalid(c=>c.runes.push({...c.runes[0]}),'DUPLICATE_RUNE_ID');invalid(c=>c.effects.refs=['r2'],'DUPLICATE_OR_UNUSED_RUNE');
});
test('cycles, missing endpoints, duplicate edges and reversed sequence fail deterministically',()=>{
 invalid(c=>c.connections.push({from:'r3',to:'r2',operator:'SEQUENCE'}),'GRAPH_CYCLE');invalid(c=>c.connections[0].to='missing','MISSING_RUNE');invalid(c=>c.connections.push({...c.connections[0]}),'GRAPH_ORDER_MISMATCH');invalid(c=>c.connections[2]={from:'r3',to:'r2',operator:'SEQUENCE'},'GRAPH_ORDER_MISMATCH');
});
test('unknown keys and operators never silently enable extra execution paths',()=>{
 invalid(c=>c.javascript='synthetic text','INVALID_CARD_SCHEMA');invalid(c=>c.effects.operator='WHILE','UNSUPPORTED_OPERATOR');invalid(c=>c.connections[0].operator='OR','UNSUPPORTED_OPERATOR');invalid(c=>c.runes[0].parameters=[],'INVALID_PARAMETERS');
});
test('AND inputs require boolean event/condition outputs',()=>{
 const {registry,card}=fixture(),bad=new ActionRegistry();
 for(const action of registry.list())bad.register({...action,outputs:action.kind==='event'?{type:'string'}:action.outputs,guard:()=>true,execute:()=>true});
 assert.throws(()=>validateSpell(card,bad),e=>e.code==='CONDITION_TYPE_MISMATCH');
});
