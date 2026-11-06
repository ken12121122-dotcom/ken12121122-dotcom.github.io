/* Isolated candidate executor. Scene/voice adapters are deliberately not loaded. */
(function(root,factory){if(typeof module==='object'&&module.exports)module.exports=factory(require('./spell-contract.js'),require('./action-registry.js'));else root.LivingSpellExecutor=factory(root.LivingSpellContract,root.LivingActionRegistry);})(typeof self!=='undefined'?self:this,function(Contract,Registry){
 'use strict';
 const safeCodes=new Set(['SPELL_CANCELLED','MISSING_ACTION','UNSUPPORTED_PLATFORM','OWNER_GATE_REQUIRED','ACTION_REENTRY','INVALID_INPUT','INVALID_OUTPUT','GUARD_REJECTED','STALE_INTERACTION']);
 class SpellExecutor{
  #registry;#active=new Set();
  constructor(registry){this.#registry=registry}
  async run(card,{incantation,context={},signal}={}){
   // Read data descriptors before the contract can access any caller getters.
   // Validate and execute the same detached immutable JSON configuration.
   let spell;try{spell=Registry.snapshotJSON(card)}catch{const error=new Error('INVALID_CARD_SCHEMA');error.code='INVALID_CARD_SCHEMA';throw error}
   Contract.validateSpell(spell,this.#registry);
   const completed=[],attempted=[];let pendingEffect=null;
   const receipt=(status,code=null)=>Object.freeze({spell_id:spell.spell_id,version:spell.version,status,error_code:code,completed_effects:Object.freeze([...completed]),attempted_effects:Object.freeze([...attempted]),uncertain_effect:pendingEffect,runtime_verified:false});
   if(!spell.enabled)return receipt('disabled');
   if(typeof incantation!=='string'||!spell.incantations.some(x=>x.trim()===incantation.trim()))return receipt('not_matched');
   if(this.#active.has(spell.spell_id)){const e=new Error('SPELL_REENTRY');e.code='SPELL_REENTRY';throw e}
   this.#active.add(spell.spell_id);
   const runes=new Map(spell.runes.map(r=>[r.rune_id,r]));
   const checkAbort=()=>{if(signal?.aborted){const e=new Error('SPELL_CANCELLED');e.code='SPELL_CANCELLED';throw e}};
   const invoke=async ref=>{checkAbort();const r=runes.get(ref);return this.#registry.invoke(r.action_id,r.parameters,context)};
   try{
    checkAbort();
    // Validate the detached configuration before any guard or handler runs.
    // Drafts may still bind parameters later; invocation retains its own
    // validation and dynamic guards at the actual point of execution.
    for(const r of spell.runes)if(!Registry.validate(r.parameters,this.#registry.describe(r.action_id).inputs)){
     const error=new Error('INVALID_INPUT');error.code='INVALID_INPUT';throw error;
    }
    for(const ref of spell.conditions.refs)if(await invoke(ref)!==true)return receipt('conditions_not_met');
    for(const ref of spell.effects.refs){checkAbort();pendingEffect=ref;attempted.push(ref);await invoke(ref);completed.push(ref);pendingEffect=null}
    checkAbort();return receipt('success');
   }catch(error){
    // Record partial effects honestly. No invented rollback or raw error text,
    // which might contain target contents, credentials or voice transcripts.
    return receipt(completed.length?'partial_failure':'failed',safeCodes.has(error?.code)?error.code:'HANDLER_FAILED');
   }finally{this.#active.delete(spell.spell_id)}
  }
 }
 return Object.freeze({SpellExecutor});
});
