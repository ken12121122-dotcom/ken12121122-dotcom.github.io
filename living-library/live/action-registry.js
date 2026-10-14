/* Candidate local action core. Not loaded by the production scene yet. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingActionRegistry=api;})(typeof self!=='undefined'?self:this,function(){
 'use strict';
 const idPattern=/^[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+$/;
 const kinds=new Set(['event','condition','effect']);
 const types=new Set(['object','array','string','number','integer','boolean','null']);
 const fail=code=>{const e=new Error(code);e.code=code;throw e};
 function checkSchema(schema,depth=0){
  if(depth>12||!schema||typeof schema!=='object'||Array.isArray(schema)||!types.has(schema.type))fail('INVALID_SCHEMA');
  const supported=new Set(['type','properties','required','additionalProperties','items','enum','minLength','minimum','maximum']);
  if(Object.keys(schema).some(key=>!supported.has(key)))fail('UNSUPPORTED_SCHEMA_KEYWORD');
  if(schema.type==='object'){
   if(schema.additionalProperties!==false)fail('OPEN_OBJECT_SCHEMA');
   if(!schema.properties||typeof schema.properties!=='object'||Array.isArray(schema.properties))fail('INVALID_SCHEMA');
   if(!Array.isArray(schema.required)||schema.required.some(key=>!Object.hasOwn(schema.properties,key)))fail('INVALID_SCHEMA');
   Object.values(schema.properties).forEach(s=>checkSchema(s,depth+1));
  }
  if(schema.type==='array')checkSchema(schema.items,depth+1);
  if(schema.enum&&(!Array.isArray(schema.enum)||!schema.enum.length))fail('INVALID_SCHEMA');
  for(const key of ['minLength','minimum','maximum'])if(schema[key]!==undefined&&!Number.isFinite(schema[key]))fail('INVALID_SCHEMA');
  if(schema.minLength!==undefined&&(!Number.isInteger(schema.minLength)||schema.minLength<0))fail('INVALID_SCHEMA');
 }
 function validate(value,schema,depth=0){
  if(depth>12)return false;
  const type=value===null?'null':Array.isArray(value)?'array':typeof value;
  if(schema.type==='integer'?!Number.isInteger(value):type!==schema.type)return false;
  if(typeof value==='number'&&(!Number.isFinite(value)||(schema.minimum!==undefined&&value<schema.minimum)||(schema.maximum!==undefined&&value>schema.maximum)))return false;
  if(typeof value==='string'&&schema.minLength!==undefined&&value.trim().length<schema.minLength)return false;
  if(schema.enum&&!schema.enum.some(x=>JSON.stringify(x)===JSON.stringify(value)))return false;
  if(schema.type==='object'){
   if(Object.getPrototypeOf(value)!==Object.prototype&&Object.getPrototypeOf(value)!==null)return false;
   if(Object.keys(value).some(k=>!Object.hasOwn(schema.properties,k)))return false;
   if(schema.required.some(k=>!Object.hasOwn(value,k)))return false;
   if(!Object.entries(value).every(([k,v])=>validate(v,schema.properties[k],depth+1)))return false;
  }
  if(schema.type==='array'&&!value.every(v=>validate(v,schema.items,depth+1)))return false;
  return true;
 }
 const copy=value=>JSON.parse(JSON.stringify(value));
 function freeze(value){Object.freeze(value);for(const v of Object.values(value))if(v&&typeof v==='object')freeze(v);return value}
 class ActionRegistry{
  #actions=new Map();#active=new Set();#platform;
  constructor({platform='web'}={}){if(!['web','android'].includes(platform))fail('INVALID_PLATFORM');this.#platform=platform}
  register(definition){
   const d=definition;
   if(!d||!idPattern.test(d.action_id)||!kinds.has(d.kind)||typeof d.name!=='string'||!d.name.trim()||!/^\d+\.\d+\.\d+$/.test(d.version||''))fail('INVALID_DEFINITION');
   if(this.#actions.has(d.action_id))fail('DUPLICATE_ACTION');
   if(typeof d.guard!=='function'||typeof d.execute!=='function')fail('MISSING_HANDLER');
   if(!Array.isArray(d.platform)||!d.platform.length||d.platform.some(p=>!['web','android'].includes(p)))fail('INVALID_PLATFORM');
   if(!['local','private_sync','native'].includes(d.policy?.scope))fail('INVALID_POLICY');
   checkSchema(d.inputs);checkSchema(d.outputs);
   if(d.kind==='condition'&&d.outputs.type!=='boolean')fail('INVALID_CONDITION_OUTPUT');
   if(d.target_type!==undefined&&!idPattern.test(d.target_type))fail('INVALID_TARGET_TYPE');
   const metadata=freeze(copy({action_id:d.action_id,name:d.name,kind:d.kind,version:d.version,inputs:d.inputs,outputs:d.outputs,platform:d.platform,policy:{scope:d.policy.scope},...(d.target_type?{target_type:d.target_type}:{})}));
   this.#actions.set(d.action_id,{metadata,guard:d.guard,execute:d.execute});
   return metadata;
  }
  describe(id){return this.#actions.get(id)?.metadata||null}
  list(){return [...this.#actions.values()].map(a=>a.metadata)}
  async invoke(id,input,context={}){
   const action=this.#actions.get(id);if(!action)fail('MISSING_ACTION');
   const d=action.metadata;
   if(!d.platform.includes(this.#platform))fail('UNSUPPORTED_PLATFORM');
   // A saved card cannot grant private synchronization or native authority.
   if(d.policy.scope!=='local')fail('OWNER_GATE_REQUIRED');
   if(this.#active.has(id))fail('ACTION_REENTRY');
   if(!validate(input,d.inputs))fail('INVALID_INPUT');
   // Handlers receive a detached, immutable payload. Never retain mutable caller data.
   const payload=freeze(copy(input));
   this.#active.add(id);
   try{
    if(await action.guard(payload,context)!==true)fail('GUARD_REJECTED');
    const output=await action.execute(payload,context);
    if(!validate(output,d.outputs))fail('INVALID_OUTPUT');
    return freeze(copy(output));
   }finally{this.#active.delete(id)}
  }
 }
 return Object.freeze({ActionRegistry,checkSchema,validate});
});
