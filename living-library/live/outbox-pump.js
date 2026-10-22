/* Unloaded P5 preparation. Caller supplies an independently authorized transport.
 * No fetch, credentials, background timer, scene hook or private endpoint here. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingOutboxPump=api;})(typeof self!=='undefined'?self:this,function(){
 'use strict';
 const running=new WeakMap();
 const result=(status,items=[])=>Object.freeze({status,items:Object.freeze(items.map(x=>Object.freeze(x)))});
 const identity=row=>Object.freeze({spell_id:row.spell_id,local_revision:row.local_revision,content_hash:row.content_hash,idempotency_key:row.idempotency_key,attempts:row.attempts});
 class OutboxPump{
  #store;#transport;#clock;#timeout;#limit;
  constructor(store,transport,{clock=Date.now,timeout_ms=15000,batch_limit=8}={}){
   if(!store||!['due','get','acknowledge','recordFailure'].every(k=>typeof store[k]==='function')||typeof transport!=='function'||typeof clock!=='function'||!Number.isInteger(timeout_ms)||timeout_ms<1||timeout_ms>60000||!Number.isInteger(batch_limit)||batch_limit<1||batch_limit>32)throw Error('INVALID_PUMP_CONFIG');
   this.#store=store;this.#transport=transport;this.#clock=clock;this.#timeout=timeout_ms;this.#limit=batch_limit;
  }
  async run({signal}={}){
   if(signal?.aborted)return result('CANCELLED');
   if(running.has(this.#store))return result('BUSY');
   const token={unsettled:null,ending:false};running.set(this.#store,token);
   const release=()=>{if(token.ending&&!token.unsettled&&running.get(this.#store)===token)running.delete(this.#store)};
   const items=[];
   try{
    const rows=await this.#store.due({now:this.#clock()});
    for(const row of rows.slice(0,this.#limit)){
     if(signal?.aborted)return result('CANCELLED',items);
     const t=identity(row),current=await this.#store.get(t.spell_id);
     if(signal?.aborted)return result('CANCELLED',items);
     if(current?.local_revision!==t.local_revision||current?.content_hash!==t.content_hash){items.push({result:'STALE_OUTBOX'});continue}
     const controller=new AbortController();let timer,abort,stop;
     const stopped=new Promise(resolve=>{stop=resolve});
     abort=()=>{stop({kind:'CANCELLED'});controller.abort()};
     signal?.addEventListener('abort',abort,{once:true});
     if(signal?.aborted)abort();
     timer=setTimeout(()=>{stop({kind:'TIMEOUT'});controller.abort()},this.#timeout);
     const request=Promise.resolve().then(()=>{
      if(controller.signal.aborted)throw Error('CANCELLED');
      return this.#transport(Object.freeze({...t,payload:row.payload}),{signal:controller.signal});
     });
     token.unsettled=request;
     const settled=()=>{if(token.unsettled===request)token.unsettled=null;release()};
     request.then(settled,settled);
     let outcome;
     try{outcome=await Promise.race([request.then(receipt=>({kind:'RECEIPT',receipt}),()=>({kind:'TRANSPORT_FAILED'})),stopped])}
     finally{clearTimeout(timer);signal?.removeEventListener('abort',abort)}
     if(signal?.aborted||outcome.kind==='CANCELLED')return result('CANCELLED',items);
     if(outcome.kind==='RECEIPT'){
      let applied;
      try{applied=await this.#store.acknowledge(t,outcome.receipt,{now:this.#clock()})}
      catch(error){if(error?.code!=='INVALID_SYNC_RECEIPT')return result('STORAGE_ERROR',items)}
      if(applied?.applied||applied?.reason==='STALE_OUTBOX'){items.push({result:applied.reason});continue}
      // A missing/malformed/mismatched receipt is not proof of delivery.
     }
     const failed=await this.#store.recordFailure(t,{now:this.#clock()});
     items.push({result:failed.reason});
     if(outcome.kind==='TIMEOUT')return result('TIMEOUT',items);
    }
    return result(rows.length?'COMPLETED':'NO_WORK',items);
   }catch{return result('STORAGE_ERROR',items)}
   finally{
    token.ending=true;release();
    // If a transport ignores abort, retain single-flight until it settles.
    // Late responses never acknowledge data; a later retry reuses the same key.
   }
  }
 }
 return Object.freeze({OutboxPump});
});
