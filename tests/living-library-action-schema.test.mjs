import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {ActionRegistry,checkSchema,validate}=createRequire(import.meta.url)('../living-library/live/action-registry.js');
const object=properties=>({type:'object',properties,required:Object.keys(properties),additionalProperties:false});
const nested=()=>object({target:object({id:{type:'string',minLength:1},slot:{type:'integer',minimum:0,maximum:3}}),steps:{type:'array',items:{type:'string',enum:['return','feedback']}}});
const definition=inputs=>({action_id:'synthetic.schema',name:'Synthetic schema',kind:'effect',version:'0.1.0',inputs,outputs:{type:'boolean'},platform:['web'],policy:{scope:'local'},guard:()=>true,execute:()=>true});
test('falsy, non-array and empty enums are invalid definitions, not absent restrictions',()=>{
 for(const value of [false,null,0,'',undefined,{},[]])assert.throws(()=>checkSchema({type:'string',enum:value}),{code:'INVALID_SCHEMA'});
});
test('enum values must be lossless JSON, unique and satisfy the remaining schema',()=>{
 for(const value of [undefined,NaN,Infinity,()=>true,1n,new Date(0),new Array(1)])assert.throws(()=>checkSchema({type:'array',items:{type:'null'},enum:[[value]]}),{code:'INVALID_SCHEMA'});
 for(const schema of [{type:'string',enum:['x','x']},{type:'integer',enum:[1,1.5]},{type:'number',minimum:2,enum:[1]},{type:'string',minLength:1,enum:[' ']}])assert.throws(()=>checkSchema(schema),{code:'INVALID_SCHEMA'});
});
test('known constraints on the wrong type fail instead of silently doing nothing',()=>{
 for(const schema of [{type:'string',minimum:0},{type:'boolean',minLength:1},{type:'number',items:{type:'string'}},{type:'array',items:{type:'string'},required:[]},{type:'null',properties:{}},{type:'integer',additionalProperties:false}])assert.throws(()=>checkSchema(schema),{code:'INVALID_SCHEMA'});
});
test('required names must be unique strings referring to own declared properties',()=>{
 for(const required of [['x','x'],[1],['missing'],[null],new Array(1)])assert.throws(()=>checkSchema({...object({x:{type:'string'},'1':{type:'string'}}),required}),{code:'INVALID_SCHEMA'});
 assert.throws(()=>checkSchema({type:'object',properties:new Date(0),required:[],additionalProperties:false}),{code:'INVALID_SCHEMA'});
 assert.throws(()=>checkSchema(Object.create({type:'string'})),{code:'INVALID_SCHEMA'});
});
test('numeric ranges and lengths must be finite, correctly typed and internally ordered',()=>{
 for(const schema of [{type:'number',minimum:3,maximum:2},{type:'number',minimum:undefined},{type:'number',maximum:NaN},{type:'integer',minimum:'0'},{type:'string',minLength:-1},{type:'string',minLength:0.5}])assert.throws(()=>checkSchema(schema),{code:'INVALID_SCHEMA'});
 for(const schema of [{type:'integer',minimum:0,maximum:0},{type:'number',minimum:-2.5,maximum:2.5},{type:'string',minLength:0}])assert.doesNotThrow(()=>checkSchema(schema));
});
test('nested unsupported, malformed, cyclic and over-deep schemas fail closed',()=>{
 for(const bad of [{type:'string',enum:false},{type:'number',minimum:4,maximum:2},{type:'string',pattern:'x'}])assert.throws(()=>checkSchema(object({list:{type:'array',items:bad}})));
 const cycle={type:'array'};cycle.items=cycle;assert.throws(()=>checkSchema(cycle),{code:'INVALID_SCHEMA'});
 let deep={type:'boolean'};for(let i=0;i<14;i++)deep={type:'array',items:deep};assert.throws(()=>checkSchema(deep),{code:'INVALID_SCHEMA'});
});
test('valid nested schemas retain all input constraints and cannot admit extra fields',async()=>{
 const schema=nested(),registry=new ActionRegistry();let calls=0;registry.register({...definition(schema),execute:()=>{calls++;return true}});
 const valid={target:{id:'synthetic.book',slot:0},steps:['return','feedback']};assert.equal(await registry.invoke('synthetic.schema',valid),true);
 for(const invalid of [{...valid,target:{id:' ',slot:0}},{...valid,target:{id:'x',slot:4}},{...valid,target:{id:'x',slot:0.5}},{...valid,steps:['other']},{...valid,target:{id:'x',slot:0,extra:true}}])await assert.rejects(registry.invoke('synthetic.schema',invalid),{code:'INVALID_INPUT'});
 assert.equal(calls,1);assert.equal(registry.describe('synthetic.schema').inputs.properties.target.properties.slot.maximum,3);
});
test('invalid input or output schema registration is atomic and calls no handler',()=>{
 for(const field of ['inputs','outputs']){
  const registry=new ActionRegistry();let calls=0;
  assert.throws(()=>registry.register({...definition({type:'boolean'}),[field]:{type:'string',enum:false},guard:()=>{calls++;return true},execute:()=>{calls++;return true}}),{code:'INVALID_SCHEMA'});
  assert.equal(registry.describe('synthetic.schema'),null);assert.equal(registry.list().length,0);assert.equal(calls,0);
  assert.doesNotThrow(()=>registry.register(definition({type:'boolean'})));
 }
});
test('JSON object enum equality ignores key insertion order and rejects duplicate representations',()=>{
 const schema={...object({a:{type:'integer'},b:{type:'integer'}}),enum:[{a:1,b:2}]};
 assert.doesNotThrow(()=>checkSchema(schema));assert.equal(validate({b:2,a:1},schema),true);
 assert.throws(()=>checkSchema({...schema,enum:[{a:1,b:2},{b:2,a:1}]}),{code:'INVALID_SCHEMA'});
});
