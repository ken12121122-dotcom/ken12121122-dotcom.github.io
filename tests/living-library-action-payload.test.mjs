import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {ActionRegistry}=createRequire(import.meta.url)('../living-library/live/action-registry.js');
const object=properties=>({type:'object',properties,required:Object.keys(properties),additionalProperties:false});
function fixture(inputs,outputs={type:'boolean'},execute=()=>true){
 const registry=new ActionRegistry(),calls={guard:0,execute:0};
 registry.register({action_id:'synthetic.payload',name:'Synthetic payload',kind:'effect',version:'0.1.0',inputs,outputs,platform:['web'],policy:{scope:'local'},guard:()=>{calls.guard++;return true},execute:value=>{calls.execute++;return execute(value)}});
 return {registry,calls,invoke:value=>registry.invoke('synthetic.payload',value)};
}
test('sparse and extended arrays cannot change shape between validation and handler entry',async()=>{
 const f=fixture({type:'array',items:{type:'boolean'}}),hole=new Array(1),extra=[true];extra.hidden='synthetic';
 for(const value of [hole,extra])await assert.rejects(f.invoke(value),{code:'INVALID_INPUT'});
 assert.deepEqual(f.calls,{guard:0,execute:0});assert.equal(await f.invoke([true,false]),true);
});
test('input accessors are rejected without calling getters or handlers',async()=>{
 let reads=0;const input=Object.defineProperty({},'target',{enumerable:true,get(){reads++;return reads===1?'synthetic.a':'synthetic.b'}});
 const f=fixture(object({target:{type:'string'}}));await assert.rejects(f.invoke(input),{code:'INVALID_INPUT'});
 assert.equal(reads,0);assert.deepEqual(f.calls,{guard:0,execute:0});
});
test('hidden serialization hooks, hidden fields and symbol fields cannot alter the snapshot',async()=>{
 let serializations=0;const hooked=Object.defineProperty({target:'synthetic.a'},'toJSON',{value(){serializations++;return {target:'synthetic.b'}}});
 const hidden=Object.defineProperty({target:'synthetic.a'},'hidden',{value:'synthetic'}),symbol={target:'synthetic.a',[Symbol('synthetic')]:true};
 const f=fixture(object({target:{type:'string'}}));
 for(const value of [hooked,hidden,symbol])await assert.rejects(f.invoke(value),{code:'INVALID_INPUT'});
 assert.equal(serializations,0);assert.deepEqual(f.calls,{guard:0,execute:0});
});
test('non-JSON, cyclic, class and over-deep inputs fail with stable redacted input errors',async()=>{
 const f=fixture(object({value:{type:'null'}})),cycle={};cycle.value=cycle;
 let deep=null;for(let i=0;i<14;i++)deep={value:deep};
 for(const value of [undefined,()=>true,Symbol('synthetic'),1n,NaN,Infinity,new Date(0),new Map(),cycle,deep])await assert.rejects(f.invoke({value}),{code:'INVALID_INPUT'});
 assert.deepEqual(f.calls,{guard:0,execute:0});
});
test('valid nested JSON is copied once before the guard await and deeply frozen',async()=>{
 let received;const input={target:{id:'synthetic.a'},steps:[true,false]},schema=object({target:object({id:{type:'string'}}),steps:{type:'array',items:{type:'boolean'}}});
 const f=fixture(schema,{type:'boolean'},value=>{received=value;return true}),pending=f.invoke(input);
 input.target.id='synthetic.b';input.steps[0]=false;assert.equal(await pending,true);
 assert.deepEqual(received,{target:{id:'synthetic.a'},steps:[true,false]});assert.notEqual(received,input);assert.ok(Object.isFrozen(received.target));assert.ok(Object.isFrozen(received.steps));assert.throws(()=>{received.steps.push(true)},TypeError);
});
test('shared JSON references and null-prototype records remain safe without changing key identity',async()=>{
 const child=Object.assign(Object.create(null),{id:'synthetic.a'}),input=Object.assign(Object.create(null),{first:child,second:child});
 const schema=object({first:object({id:{type:'string'}}),second:object({id:{type:'string'}})});
 const f=fixture(schema,{type:'boolean'},value=>{assert.deepEqual(value.first,value.second);assert.notEqual(value.first,child);return true});assert.equal(await f.invoke(input),true);
 const protoInput=JSON.parse('{"__proto__":"synthetic"}'),properties=JSON.parse('{"__proto__":{"type":"string"}}');
 const g=fixture(object(properties),{type:'boolean'},value=>Object.hasOwn(value,'__proto__')&&value.__proto__==='synthetic');assert.equal(await g.invoke(protoInput),true);assert.equal({}.synthetic,undefined);
});
test('output accessors, hooks and sparse arrays cannot become a different validated receipt',async()=>{
 let reads=0,serializations=0;
 const getter=Object.defineProperty({},'target',{enumerable:true,get(){reads++;return 'synthetic.a'}});
 const hook=Object.defineProperty({target:'synthetic.a'},'toJSON',{value(){serializations++;return {target:'synthetic.b'}}});
 for(const value of [getter,hook]){
  let output=value;const f=fixture({type:'boolean'},object({target:{type:'string'}}),()=>output);
  await assert.rejects(f.invoke(true),{code:'INVALID_OUTPUT'});output={target:'synthetic.a'};assert.deepEqual(await f.invoke(true),output);
 }
 const f=fixture({type:'boolean'},{type:'array',items:{type:'boolean'}},()=>new Array(1));await assert.rejects(f.invoke(true),{code:'INVALID_OUTPUT'});
 assert.equal(reads,0);assert.equal(serializations,0);
});
test('valid output is detached and immutable, including shared nested values',async()=>{
 const output={steps:[true]},f=fixture({type:'boolean'},object({steps:{type:'array',items:{type:'boolean'}}}),()=>output);
 const result=await f.invoke(true);output.steps[0]=false;assert.deepEqual(result,{steps:[true]});assert.ok(Object.isFrozen(result.steps));assert.notEqual(result,output);
});
