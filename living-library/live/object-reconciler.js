/* Candidate local-only creator reconciliation. No source writer or transport.
 * At most two captures per trigger burst; failure waits for a new user/lifecycle
 * trigger. Closing the creator may interrupt capture; reopening reconciles it. */
(function(root,factory){
 if(typeof module==='object'&&module.exports)module.exports=factory(require('./object-outbox.js'));
 else root.LivingObjectReconciler=factory(root.LivingObjectOutbox);
})(typeof self!=='undefined'?self:this,function(Outbox){
 'use strict';
 function create({storage,onStatus=()=>{},open=()=>Outbox.ObjectOutbox.open()}){
  let stopped=false,dirty=false,flight=null;
  const report=value=>{if(!stopped){try{onStatus(Object.freeze(value))}catch{}}};
  async function run(){
   let outcome={state:'RECONCILE_REQUIRED'};
   for(let pass=0;pass<2&&dirty&&!stopped;pass++){
    dirty=false;let queue;
    report({state:'PREPARING'});
    try{
     queue=await open();if(stopped)break;
     const result=await queue.capture(storage);if(stopped)break;
     const count=(await queue.pending()).length;
     outcome={state:dirty?'RECONCILE_REQUIRED':'LOCAL_PENDING',count,queued:result.queued};
    }catch(e){
     // Only a concurrently changed source gets one immediate fresh snapshot.
     // Quota, unavailable storage, conflicts and unknown failures never spin.
     if(e?.code==='OBJECT_OUTBOX_SOURCE_CHANGED'&&pass===0)dirty=true;
     else dirty=false;
     outcome={state:'RECONCILE_REQUIRED'};
    }finally{try{queue?.close()}catch{}}
    report(outcome);
   }
   dirty=false;
   return Object.freeze(stopped?{state:'STOPPED'}:outcome);
  }
  return Object.freeze({
   request(){
    if(stopped)return Promise.resolve(Object.freeze({state:'STOPPED'}));
    dirty=true;
    if(!flight)flight=Promise.resolve().then(run).finally(()=>{flight=null});
    return flight;
   },
   dispose(){stopped=true;dirty=false}
  });
 }
 return Object.freeze({create});
});
