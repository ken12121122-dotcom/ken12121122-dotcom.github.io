import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {ActionRegistry}=createRequire(import.meta.url)('../living-library/live/action-registry.js');
function fixture(){const registry=new ActionRegistry(),calls=[];const definition={action_id:'synthetic.definition',name:'Synthetic',kind:'effect',version:'0.1.0',inputs:{type:'integer',minimum:2},outputs:{type:'boolean'},platform:['web'],policy:{scope:'local'},guard:()=>{calls.push('guard');return true},execute:()=>{calls.push('execute');return true}};return {registry,definition,calls}}
function rejected(f,code){assert.throws(()=>f.registry.register(f.definition),{code});assert.equal(f.registry.describe('synthetic.definition'),null);assert.deepEqual(f.registry.list(),[]);assert.deepEqual(f.calls,[])}
test('input serialization hooks cannot relax an already validated schema',()=>{
 const f=fixture();let hooks=0;Object.defineProperty(f.definition.inputs,'toJSON',{value(){hooks++;return {type:'integer',minimum:0}}});rejected(f,'INVALID_SCHEMA');assert.equal(hooks,0);
});
test('output serialization hooks cannot replace an already validated output type',()=>{
 const f=fixture();let hooks=0;Object.defineProperty(f.definition.outputs,'toJSON',{value(){hooks++;return {type:'string'}}});rejected(f,'INVALID_SCHEMA');assert.equal(hooks,0);
});
test('policy accessors cannot switch private authority to local during registration',()=>{
 const f=fixture();let reads=0;Object.defineProperty(f.definition.policy,'scope',{enumerable:true,get(){return ++reads===1?'private_sync':'local'}});rejected(f,'INVALID_POLICY');assert.equal(reads,0);
});
test('platform serialization hooks cannot switch an Android definition to web',()=>{
 const f=fixture();let hooks=0;f.definition.platform=['android'];Object.defineProperty(f.definition.platform,'toJSON',{value(){hooks++;return ['web']}});rejected(f,'INVALID_PLATFORM');assert.equal(hooks,0);
});
test('top-level identity getters are rejected without reads or partial registration',()=>{
 const f=fixture();let reads=0;Object.defineProperty(f.definition,'action_id',{enumerable:true,get(){reads++;return 'synthetic.definition'}});rejected(f,'INVALID_DEFINITION');assert.equal(reads,0);
});
test('handler getters are never invoked while capturing trusted function references',()=>{
 for(const key of ['guard','execute']){const f=fixture();let reads=0;Object.defineProperty(f.definition,key,{enumerable:true,get(){reads++;return ()=>true}});rejected(f,'INVALID_DEFINITION');assert.equal(reads,0)}
});
test('nested schema getters, hidden fields, symbols and non-JSON enums fail atomically',()=>{
 for(const mutate of [s=>Object.defineProperty(s,'minimum',{enumerable:true,get(){throw Error('SYNTHETIC_PRIVATE_DETAIL')}}),s=>Object.defineProperty(s,'hidden',{value:true}),s=>s[Symbol('synthetic')]=1,s=>s.enum=[undefined],s=>s.enum=[NaN]]){const f=fixture();mutate(f.definition.inputs);rejected(f,'INVALID_SCHEMA')}
});
test('normal metadata and handlers remain detached, frozen and owner gated',async()=>{
 const f=fixture(),original=f.definition.execute;const metadata=f.registry.register(f.definition);f.definition.execute=()=>false;f.definition.inputs.minimum=0;f.definition.platform[0]='android';f.definition.policy.scope='native';assert.equal(metadata.inputs.minimum,2);assert.equal(metadata.platform[0],'web');assert.equal(metadata.policy.scope,'local');assert.ok(Object.isFrozen(metadata.inputs));await assert.rejects(f.registry.invoke('synthetic.definition',1),{code:'INVALID_INPUT'});assert.equal(await f.registry.invoke('synthetic.definition',2),true);assert.deepEqual(f.calls,['guard','execute']);assert.equal(typeof original,'function');
 for(const scope of ['native','private_sync']){const x=fixture();x.definition.policy.scope=scope;x.registry.register(x.definition);await assert.rejects(x.registry.invoke('synthetic.definition',2),{code:'OWNER_GATE_REQUIRED'});assert.deepEqual(x.calls,[])}
});
test('maximum schema nesting with nested enum values remains supported',()=>{
 const f=fixture();let schema={type:'integer'},value=1;for(let i=0;i<12;i++){schema={type:'object',properties:{v:schema},required:['v'],additionalProperties:false};value={v:value}}schema.enum=[value];f.definition.inputs=schema;assert.doesNotThrow(()=>f.registry.register(f.definition));assert.ok(Object.isFrozen(f.registry.describe('synthetic.definition').inputs.enum[0]));
});
test('rejected metadata leaves its stable action ID available for a corrected definition',()=>{
 const f=fixture();Object.defineProperty(f.definition.inputs,'toJSON',{value:()=>({type:'integer',minimum:0})});rejected(f,'INVALID_SCHEMA');f.definition.inputs={type:'integer',minimum:2};assert.doesNotThrow(()=>f.registry.register(f.definition));assert.equal(f.registry.list().length,1);
});
