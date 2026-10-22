/* Candidate local action core. Not loaded by the production scene yet. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingActionRegistry=api;})(typeof self!=='undefined'?self:this,function(){
 'use strict';
 const idPattern=/^[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+$/;
 const kinds=new Set(['event','condition','effect']);
 const types=new Set(['object','array','string','number','integer','boolean','null']);
 const fail=code=>{const e=new Error(code);e.code=code;throw e};
 const record=value=>value!==null&&typeof value==='object'&&!Array.isArray(value)&&(Object.getPrototypeOf(value)===Object.prototype||Object.getPrototypeOf(value)===null);
 const dense=value=>Array.isArray(value)&&Object.keys(value).length===value.length&&Array.from({length:value.length},(_,i)=>Object.hasOwn(value,i)).every(Boolean);
 // Canonical JSON keys reject values which would change during metadata copy
 // (undefined, holes, non-finite numbers, class instances, cycles, etc.).
 function jsonKey(value,depth=0){
  if(depth>12)fail('INVALID_SCHEMA');
  if(value===null||typeof value==='string'||typeof value==='boolean')return JSON.stringify(value);
  if(typeof value==='number'){if(!Number.isFinite(value))fail('INVALID_SCHEMA');return JSON.stringify(value)}
  if(Array.isArray(value)){if(!dense(value))fail('INVALID_SCHEMA');return '['+value.map(v=>jsonKey(v,depth+1)).join(',')+']'}
  if(record(value))return '{'+Object.keys(value).sort().map(k=>JSON.stringify(k)+':'+jsonKey(value[k],depth+1)).join(',')+'}';
  fail('INVALID_SCHEMA');
 }
 function checkSchema(schema,depth=0){
  if(depth>12||!record(schema)||!Object.hasOwn(schema,'type')||!types.has(schema.type))fail('INVALID_SCHEMA');
  const supported=new Set(['type','properties','required','additionalProperties','items','enum','minLength','minimum','maximum']);
  if(Object.keys(schema).some(key=>!supported.has(key)))fail('UNSUPPORTED_SCHEMA_KEYWORD');
  const applicable={object:['properties','required','additionalProperties'],array:['items'],string:['minLength'],number:['minimum','maximum'],integer:['minimum','maximum'],boolean:[],null:[]};
  const allowed=new Set(['type','enum',...applicable[schema.type]]);
  if(Object.keys(schema).some(key=>!allowed.has(key)))fail('INVALID_SCHEMA');
  if(schema.type==='object'){
   if(schema.additionalProperties!==false)fail('OPEN_OBJECT_SCHEMA');
   if(!record(schema.properties))fail('INVALID_SCHEMA');
   if(!dense(schema.required)||schema.required.some(key=>typeof key!=='string'||!Object.hasOwn(schema.properties,key))||new Set(schema.required).size!==schema.required.length)fail('INVALID_SCHEMA');
   Object.values(schema.properties).forEach(s=>checkSchema(s,depth+1));
  }
  if(schema.type==='array')checkSchema(schema.items,depth+1);
  for(const key of ['minLength','minimum','maximum'])if(Object.hasOwn(schema,key)&&!Number.isFinite(schema[key]))fail('INVALID_SCHEMA');
  if(schema.minLength!==undefined&&(!Number.isInteger(schema.minLength)||schema.minLength<0))fail('INVALID_SCHEMA');
  if(schema.minimum!==undefined&&schema.maximum!==undefined&&schema.minimum>schema.maximum)fail('INVALID_SCHEMA');
  if(Object.hasOwn(schema,'enum')){
   if(!dense(schema.enum)||!schema.enum.length)fail('INVALID_SCHEMA');
   const keys=schema.enum.map(value=>jsonKey(value));
   if(new Set(keys).size!==keys.length)fail('INVALID_SCHEMA');
   const base={...schema};delete base.enum;
   if(schema.enum.some(value=>!validate(value,base)))fail('INVALID_SCHEMA');
  }
 }
 function validate(value,schema,depth=0){
  if(depth>12)return false;
  const type=value===null?'null':Array.isArray(value)?'array':typeof value;
  if(schema.type==='integer'?!Number.isInteger(value):type!==schema.type)return false;
  if(typeof value==='number'&&(!Number.isFinite(value)||(schema.minimum!==undefined&&value<schema.minimum)||(schema.maximum!==undefined&&value>schema.maximum)))return false;
  if(typeof value==='string'&&schema.minLength!==undefined&&value.trim().length<schema.minLength)return false;
  if(schema.enum){try{const key=jsonKey(value);if(!schema.enum.some(x=>jsonKey(x)===key))return false}catch{return false}}
  if(schema.type==='object'){
   if(Object.getPrototypeOf(value)!==Object.prototype&&Object.getPrototypeOf(value)!==null)return false;
   if(Object.keys(value).some(k=>!Object.hasOwn(schema.properties,k)))return false;
   if(schema.required.some(k=>!Object.hasOwn(value,k)))return false;
   if(!Object.entries(value).every(([k,v])=>validate(v,schema.properties[k],depth+1)))return false;
  }
  if(schema.type==='array'&&!value.every(v=>validate(v,schema.items,depth+1)))return false;
  return true;
 }
 // Payloads are data, not objects with executable serialization/getter hooks.
 // Read descriptors once and validate the resulting immutable snapshot itself.
 function dataSnapshot(value,depth=0,active=new WeakSet(),maxDepth=12){
  if(depth>maxDepth)fail('INVALID_JSON_VALUE');
  if(value===null||typeof value==='string'||typeof value==='boolean')return value;
  if(typeof value==='number'){if(!Number.isFinite(value))fail('INVALID_JSON_VALUE');return value}
  if(typeof value!=='object')fail('INVALID_JSON_VALUE');
  const array=Array.isArray(value);
  if(array?Object.getPrototypeOf(value)!==Array.prototype:!record(value))fail('INVALID_JSON_VALUE');
  if(active.has(value))fail('INVALID_JSON_VALUE');
  active.add(value);
  try{
   const descriptors=Object.getOwnPropertyDescriptors(value),keys=Reflect.ownKeys(descriptors);
   const length=array?descriptors.length.value:0;
   if(array&&keys.length!==length+1)fail('INVALID_JSON_VALUE');
   const entries=[];
   for(const key of keys){
    if(array&&key==='length')continue;
    const descriptor=descriptors[key];
    if(typeof key!=='string'||!descriptor.enumerable||!Object.hasOwn(descriptor,'value'))fail('INVALID_JSON_VALUE');
    if(array&&(!/^(0|[1-9]\d*)$/.test(key)||Number(key)>=length))fail('INVALID_JSON_VALUE');
    entries.push([key,dataSnapshot(descriptor.value,depth+1,active,maxDepth)]);
   }
   // fromEntries preserves a literal own __proto__ field without prototype writes.
   return Object.freeze(array?entries.map(([,item])=>item):Object.fromEntries(entries));
  }finally{active.delete(value)}
 }
 function checkedSnapshot(value,schema,code){
  let snapshot;try{snapshot=dataSnapshot(value)}catch{fail(code)}
  if(!validate(snapshot,schema))fail(code);
  return snapshot;
 }
 function definitionSnapshot(definition){
  if(!record(definition))fail('INVALID_DEFINITION');
  const descriptors=Object.getOwnPropertyDescriptors(definition),d={};
  // Trusted handlers are captured once, separately from JSON metadata. Never
  // read a caller getter or invoke serialization while checking a definition.
  for(const key of ['action_id','name','kind','version','inputs','outputs','platform','policy','target_type','guard','execute']){
   const descriptor=descriptors[key];if(!descriptor)continue;
   if(!Object.hasOwn(descriptor,'value'))fail('INVALID_DEFINITION');
   if(key==='guard'||key==='execute'){d[key]=descriptor.value;continue}
   if(key==='target_type'&&descriptor.value===undefined)continue;
   const code=key==='inputs'||key==='outputs'?'INVALID_SCHEMA':key==='platform'?'INVALID_PLATFORM':key==='policy'?'INVALID_POLICY':'INVALID_DEFINITION';
   // Schema nesting includes properties envelopes plus nested enum values;
   // checkSchema still applies the existing logical depth-12 constraints.
   try{d[key]=dataSnapshot(descriptor.value,0,new WeakSet(),48)}catch{fail(code)}
  }
  return d;
 }
 class ActionRegistry{
  #actions=new Map();#active=new Set();#platform;
  constructor({platform='web'}={}){if(!['web','android'].includes(platform))fail('INVALID_PLATFORM');this.#platform=platform}
  register(definition){
   const d=definitionSnapshot(definition);
   if(!d||!idPattern.test(d.action_id)||!kinds.has(d.kind)||typeof d.name!=='string'||!d.name.trim()||!/^\d+\.\d+\.\d+$/.test(d.version||''))fail('INVALID_DEFINITION');
   if(this.#actions.has(d.action_id))fail('DUPLICATE_ACTION');
   if(typeof d.guard!=='function'||typeof d.execute!=='function')fail('MISSING_HANDLER');
   if(!Array.isArray(d.platform)||!d.platform.length||d.platform.some(p=>!['web','android'].includes(p)))fail('INVALID_PLATFORM');
   if(!['local','private_sync','native'].includes(d.policy?.scope))fail('INVALID_POLICY');
   checkSchema(d.inputs);checkSchema(d.outputs);
   if(d.kind==='condition'&&d.outputs.type!=='boolean')fail('INVALID_CONDITION_OUTPUT');
   if(d.target_type!==undefined&&!idPattern.test(d.target_type))fail('INVALID_TARGET_TYPE');
   const metadata=Object.freeze({action_id:d.action_id,name:d.name,kind:d.kind,version:d.version,inputs:d.inputs,outputs:d.outputs,platform:d.platform,policy:Object.freeze({scope:d.policy.scope}),...(d.target_type?{target_type:d.target_type}:{})});
   this.#actions.set(d.action_id,{metadata,guard:d.guard,execute:d.execute});
   return metadata;
  }
  describe(id){return this.#actions.get(id)?.metadata||null}
  list(){return [...this.#actions.values()].map(a=>a.metadata)}
  async invoke(id,input,context={},beforeExecute){
   // Optional trusted adapter checkpoint can only deny, never bypass guards.
   if(beforeExecute!==undefined&&typeof beforeExecute!=='function')fail('INVALID_CHECKPOINT');
   const action=this.#actions.get(id);if(!action)fail('MISSING_ACTION');
   const d=action.metadata;
   if(!d.platform.includes(this.#platform))fail('UNSUPPORTED_PLATFORM');
   // A saved card cannot grant private synchronization or native authority.
   if(d.policy.scope!=='local')fail('OWNER_GATE_REQUIRED');
   if(this.#active.has(id))fail('ACTION_REENTRY');
   // Handlers receive a detached, immutable payload. Never retain mutable caller data.
   const payload=checkedSnapshot(input,d.inputs,'INVALID_INPUT');
   this.#active.add(id);
   try{
    if(await action.guard(payload,context)!==true)fail('GUARD_REJECTED');
    // No await between this synchronous check and entering the handler.
    if(beforeExecute!==undefined&&beforeExecute()!==true)fail('STALE_INTERACTION');
    const output=await action.execute(payload,context);
    return checkedSnapshot(output,d.outputs,'INVALID_OUTPUT');
   }finally{this.#active.delete(id)}
  }
 }
 // A card adds an envelope around payloads. Keep payload's existing depth-12
 // validation unchanged while allowing bounded JSON configuration snapshots.
 const snapshotJSON=value=>dataSnapshot(value,0,new WeakSet(),20);
 return Object.freeze({ActionRegistry,checkSchema,validate,snapshotJSON});
});
