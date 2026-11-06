/* Candidate transient context binding. No scene, microphone, storage or network IO. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingInteractionSession=api;})(typeof self!=='undefined'?self:this,function(){
 'use strict';
 const fail=code=>{const e=new Error(code);e.code=code;throw e};
 const validId=v=>typeof v==='string'&&v.length>0&&v.length<=128&&!/\s/.test(v);
 class TargetInteractionSession{
  #now;#ttl;#prefix;#serial=0;#generation=0;#grab=null;#voice=null;#tokens=new WeakMap();#contexts=new WeakMap();
  constructor({now=()=>performance.now(),idFactory=()=>crypto.randomUUID(),maxAgeMs=8000}={}){
   if(typeof now!=='function'||typeof idFactory!=='function'||!Number.isFinite(maxAgeMs)||maxAgeMs<1||maxAgeMs>60000)fail('INVALID_SESSION_OPTIONS');
   const prefix=idFactory();if(!validId(prefix)||prefix.length>80)fail('INVALID_SESSION_ID');
   this.#now=now;this.#ttl=maxAgeMs;this.#prefix=prefix;
  }
  #id(){return this.#prefix+'.'+(++this.#serial)}
  #time(){const value=this.#now();if(!Number.isFinite(value))fail('INVALID_CLOCK');return value}
  current(){return this.#grab}
  beginGrab({target_id,target_type='library.book_instance',pointer_id}={}){
   if(!validId(target_id)||!validId(target_type)||!/^[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+$/.test(target_type)||!Number.isSafeInteger(pointer_id)||pointer_id<0)fail('INVALID_TARGET');
   this.#time();
   this.#generation++;this.#voice=null;
   this.#grab=Object.freeze({target_object_instance_id:target_id,target_type,pointer_id,gesture_session_id:this.#id(),gesture_state:'drag_active'});
   return this.#grab;
  }
  endGrab(pointer_id){
   if(!this.#grab||this.#grab.pointer_id!==pointer_id)return false;
   this.cancel();return true;
  }
  // Call on pointercancel, blur, pagehide or opening a drawer. Wiring comes later.
  cancel(){this.#generation++;this.#grab=null;this.#voice=null}
  captureVoiceContext(){
   if(!this.#grab)fail('NO_ACTIVE_TARGET');
   const time=this.#time(),token=Object.freeze({voice_session_id:this.#id()});
   this.#tokens.set(token,{generation:this.#generation,target:this.#grab,started:time,used:false});
   this.#voice=token;return token;
  }
  // Adapters must recheck after an asynchronous boundary, immediately before
  // dispatching an effect. A prior match is not a permanent authorization.
  isCurrent(context){
   if(!context||typeof context!=='object')return false;
   const record=this.#contexts.get(context);if(!record)return false;
   if(record.generation!==this.#generation||record.target!==this.#grab||record.token!==this.#voice)return false;
   let time;try{time=this.#time()}catch{return false}
   return time>=record.started&&time-record.started<=this.#ttl;
  }
  resolveVoiceResult(token,{transcript,incantations}={}){
   const reject=reason=>Object.freeze({matched:false,reason,context:null,runtime_verified:false});
   if(!token||typeof token!=='object')return reject('INVALID_SESSION');
   const record=this.#tokens.get(token);if(!record)return reject('INVALID_SESSION');
   if(record.used)return reject('ALREADY_CONSUMED');
   record.used=true;
   if(record.generation!==this.#generation||record.target!==this.#grab||token!==this.#voice)return reject('STALE_INTERACTION');
   let time;try{time=this.#time()}catch{return reject('INVALID_CLOCK')}
   if(time<record.started||time-record.started>this.#ttl)return reject('EXPIRED_SESSION');
   if(typeof transcript!=='string'||!Array.isArray(incantations)||!incantations.length||incantations.some(v=>typeof v!=='string'||!v.trim()))return reject('INVALID_INCANTATION');
   if(!incantations.some(v=>v.trim()===transcript.trim()))return reject('NOT_MATCHED');
   // Do not retain/return transcript or audio. This is a correlation receipt,
   // not a permission grant or proof that any game effect has executed.
   const context=Object.freeze({...record.target,voice_session_id:token.voice_session_id});
   this.#contexts.set(context,{...record,token});
   return Object.freeze({matched:true,reason:'MATCHED',context,runtime_verified:false});
  }
 }
 return Object.freeze({TargetInteractionSession});
});
