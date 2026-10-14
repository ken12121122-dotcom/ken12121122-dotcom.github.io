/* Unloaded P1 candidate. Trusted event source only; no scene listeners or IO. */
(function(root,factory){if(typeof module==='object'&&module.exports)module.exports=factory(require('./action-registry.js'));else root.LivingCharacterTap=factory(root.LivingActionRegistry);})(typeof self!=='undefined'?self:this,function(Registry){
 'use strict';
 const ids=Object.freeze(['character.tap','ui.toast']);
 const fail=code=>{const e=new Error(code);e.code=code;throw e};
 const safeCodes=new Set(['DISPATCH_CANCELLED','DISPATCH_DISPOSED','MISSING_ACTION','UNSUPPORTED_PLATFORM','OWNER_GATE_REQUIRED','ACTION_REENTRY','INVALID_INPUT','INVALID_OUTPUT','GUARD_REJECTED']);
 class CharacterTapAdapter{
  #registry;#busy=false;#disposed=false;
  constructor(registry){
   if(typeof registry?.describe!=='function'||typeof registry?.invoke!=='function')fail('INVALID_REGISTRY');
   const actions=ids.map(id=>{const action=registry.describe(id);if(!action)fail('MISSING_ACTION');if(action.policy.scope!=='local')fail('OWNER_GATE_REQUIRED');if(action.outputs.type!=='boolean')fail('INVALID_ACTION_OUTPUT');return action});
   if(actions[0].kind!=='event'||actions[1].kind!=='effect')fail('WRONG_ACTION_KIND');
   if(actions[0].target_type&&actions[1].target_type&&actions[0].target_type!==actions[1].target_type)fail('TARGET_TYPE_MISMATCH');
   this.#registry=registry;
  }
  // The future scene owner must dispose/abort on invalidation. This module does
  // not raycast, move a character, show a joystick, or claim a genuine 3D tap.
  dispose(){this.#disposed=true}
  async dispatch(target_id,{signal}={}){
   const completed=[],attempted=[];let uncertain=null;
   const receipt=(status,error_code=null)=>Object.freeze({status,error_code,completed_actions:Object.freeze([...completed]),attempted_actions:Object.freeze([...attempted]),uncertain_action:uncertain,runtime_verified:false});
   if(this.#disposed)return receipt('disposed','DISPATCH_DISPOSED');
   if(this.#busy)return receipt('busy','ACTION_REENTRY');
   this.#busy=true;
   const check=()=>{if(this.#disposed)fail('DISPATCH_DISPOSED');if(signal?.aborted)fail('DISPATCH_CANCELLED');return true};
   try{
    check();
    if(typeof target_id!=='string'||!target_id.length||target_id.length>128||/\s/.test(target_id))fail('INVALID_INPUT');
    const payload=Object.freeze({target_id});
    // Configuration errors in feedback must not run the event first.
    for(const id of ids)if(!Registry.validate(payload,this.#registry.describe(id).inputs))fail('INVALID_INPUT');
    const context=Object.freeze({target_object_instance_id:target_id,source_action_id:'character.tap'});
    for(const id of ids){
     check();uncertain=id;attempted.push(id);
     const confirmed=await this.#registry.invoke(id,payload,context,check);
     completed.push(id);uncertain=null;check();
     if(confirmed!==true)return receipt(id==='character.tap'?'not_matched':'feedback_not_confirmed',id==='character.tap'?null:'FEEDBACK_NOT_CONFIRMED');
    }
    return receipt('success');
   }catch(error){return receipt(completed.length?'partial_failure':'failed',safeCodes.has(error?.code)?error.code:'HANDLER_FAILED')}
   finally{this.#busy=false}
  }
 }
 return Object.freeze({CharacterTapAdapter});
});
