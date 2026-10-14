/* Unloaded candidate adapter. Trusted local handlers only; no scene/voice IO. */
(function(root,factory){if(typeof module==='object'&&module.exports)module.exports=factory(require('./spell-contract.js'),require('./spell-executor.js'));else root.LivingInteractionDispatch=factory(root.LivingSpellContract,root.LivingSpellExecutor);})(typeof self!=='undefined'?self:this,function(Contract,Executor){
 'use strict';
 const fail=code=>{const error=new Error(code);error.code=code;throw error};
 class InteractionDispatcher{
  #registry;#session;#allowed;#used=new WeakSet();
  constructor({registry,session,allowedActions}={}){
   if(typeof registry?.describe!=='function'||typeof registry?.invoke!=='function'||typeof session?.isCurrent!=='function'||!Array.isArray(allowedActions)||!allowedActions.length)fail('INVALID_DISPATCH_OPTIONS');
   for(const id of allowedActions){const action=registry.describe(id);if(!action)fail('MISSING_ACTION');if(action.policy.scope!=='local')fail('OWNER_GATE_REQUIRED')}
   this.#registry=registry;this.#session=session;this.#allowed=new Set(allowedActions);
  }
  async run(card,{context,incantation,signal}={}){
   Contract.validateSpell(card,this.#registry);
   for(const rune of card.runes)if(!this.#allowed.has(rune.action_id))fail('ACTION_NOT_ALLOWED');
   if(!this.#session.isCurrent(context)||context.target_type!==card.target_type)fail('STALE_INTERACTION');
   if(this.#used.has(context))fail('CONTEXT_ALREADY_DISPATCHED');
   // Consume before any await: concurrent replay and failed attempts cannot retry
   // uncertain effects. A new user interaction is required for another attempt.
   this.#used.add(context);
   const checkpoint=()=>{
    if(signal?.aborted)fail('SPELL_CANCELLED');
    return this.#session.isCurrent(context);
   };
   const registry={describe:id=>this.#registry.describe(id),invoke:(id,input,ctx)=>{
    if(!this.#allowed.has(id))fail('ACTION_NOT_ALLOWED');
    return this.#registry.invoke(id,input,ctx,checkpoint);
   }};
   return new Executor.SpellExecutor(registry).run(card,{context,incantation,signal});
  }
 }
 return Object.freeze({InteractionDispatcher});
});
