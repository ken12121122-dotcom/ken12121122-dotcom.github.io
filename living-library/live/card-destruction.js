/* Crash-recoverable permanent card deletion. No remote endpoint.
 * The atomic ObjectCraft write removes an original card AND records a
 * short-lived outbox cleanup obligation. Completing IndexedDB cleanup
 * retires that obligation; failed cleanup can be replayed after reload.
 */
(function(root,factory){
 const api=factory(
  typeof module==='object'&&module.exports?require('./object-craft.js'):root.LivingObjectCraft,
  typeof module==='object'&&module.exports?require('./object-outbox.js'):root.LivingObjectOutbox);
 if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LivingCardDestruction=api;
})(typeof self!=='undefined'?self:globalThis,function(Craft,Outbox){
 'use strict';
 function deps(){
  if(!Craft?.destroyCard||!Craft.pendingCardOutboxPurges||
     !Craft.completeCardOutboxPurge||!Outbox?.ObjectOutbox)
   throw Error('CARD_DESTROY_DEPENDENCY_MISSING');
 }
 async function reconcile(storage){
  deps();
  const jobs=Craft.pendingCardOutboxPurges(storage);
  if(!jobs.length)return {cleaned:0,pending:0};
  const queue=await Outbox.ObjectOutbox.open();
  let cleaned=0;
  try{
   for(const job of jobs){
    // Only purge entries in the exact journal written WITH the card removal.
    // A restored/recreated original makes Craft reject the old cleanup.
    Craft.pendingCardOutboxPurges(storage);
    if(Craft.read(storage).objects.some(x=>x.id===job.id))
     throw Error('CARD_DESTROY_ORIGINAL_REAPPEARED');
    await queue.forget(job.ids);
    Craft.completeCardOutboxPurge(storage,job.id);
    cleaned++;
   }
   return {cleaned,pending:Craft.pendingCardOutboxPurges(storage).length};
  }finally{queue.close()}
 }
 async function destroy(storage,{id,revision,confirmation,expectedSnapshot,expectedCopyIds}){
  deps();
  if(confirmation!=='銷毀')throw Error('必須輸入「銷毀」才能永久刪除');
  const plan=Craft.planDestroyCard(storage,id,revision);
  if(expectedCopyIds&&JSON.stringify(plan.affectedCopyIds)!==JSON.stringify(expectedCopyIds))
   throw Error('卡片複製關聯已變更，已停止銷毀');
  if(typeof expectedSnapshot!=='string'||storage.getItem(Craft.KEY)!==expectedSnapshot)
   throw Error('存檔在確認期間已變更，未刪除卡片');
  // Do not delete the source if IndexedDB cannot be opened at all.
  const queue=await Outbox.ObjectOutbox.open();
  let result;
  try{
   result=Craft.destroyCard(storage,{
    id,expectedRevision:revision,confirmation,expectedSnapshot,queueCleanup:true
   });
   try{
    // Failures HERE must never be reported as a failed card deletion:
    // the source was already removed atomically with the durable journal.
    const job=Craft.pendingCardOutboxPurges(storage).find(x=>x.id===id);
    if(!job)throw Error('CARD_DESTROY_JOURNAL_MISSING');
    await queue.forget(job.ids);
    Craft.completeCardOutboxPurge(storage,id);
    return {...result,cleanup:'complete'};
   }catch(error){
    return {...result,cleanup:'pending',cleanupError:String(error?.message||error)};
   }
  }finally{queue.close()}
 }
 return Object.freeze({destroy,reconcile});
});
