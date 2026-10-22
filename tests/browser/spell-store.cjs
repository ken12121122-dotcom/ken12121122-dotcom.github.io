// Real Chromium IndexedDB with synthetic cards; no deployed or phone acceptance.
const {chromium}=require('playwright'),fs=require('node:fs/promises'),path=require('node:path'),assert=require('node:assert/strict');
const proof={schema:'nw-spell-store-browser-v1',result:'NOT_TESTED',checks:[],scope:'Isolated real IndexedDB; synthetic card and outbox only, no network transport, scene, deployed UI or device claim'};
async function main(){
 const browser=await chromium.launch({headless:true});
 try{
  const context=await browser.newContext(),page=await context.newPage();let outbound=0;
  await page.route('**/*',route=>{if(route.request().url()==='https://nw-store.invalid/')return route.fulfill({contentType:'text/html',body:'<!doctype html><textarea id="draft"></textarea><button id="save">Save synthetic spell</button><output id="result"></output>'});outbound++;return route.abort()});
  async function boot(){
   await page.goto('https://nw-store.invalid/');
   for(const file of ['action-registry','spell-contract','spell-store'])await page.addScriptTag({path:path.resolve(__dirname,'../../living-library/live/'+file+'.js')});
   await page.evaluate(async()=>{
    const registry=new LivingActionRegistry.ActionRegistry();
    for(const [action_id,kind] of [['book.drag_active','condition'],['book.synthetic_effect','effect']])registry.register({action_id,name:action_id,kind,version:'0.1.0',target_type:'library.book_instance',inputs:{type:'object',properties:{},required:[],additionalProperties:false},outputs:{type:'boolean'},platform:['web'],policy:{scope:'local'},guard:()=>{throw Error('must not execute')},execute:()=>{throw Error('must not execute')}});
    window.store=await LivingSpellStore.SpellStore.open(registry,{database:'nw-rune-creations-browser-test'});window.registry=registry;
    window.card={schema:'nw.spell.v1',spell_id:'spell.synthetic.saved',version:'0.1.0',name:'Synthetic spell',description:'Fixture only',incantations:['synthetic phrase'],enabled:false,target_type:'library.book_instance',runes:['book.drag_active','book.synthetic_effect'].map((action_id,i)=>({schema:'nw.rune.v1',rune_id:'r'+i,action_id,action_version:'0.1.0',label:action_id,parameters:{}})),conditions:{operator:'AND',refs:['r0']},effects:{operator:'SEQUENCE',refs:['r1']},connections:[{from:'r0',to:'r1',operator:'AND'}]};
    document.getElementById('draft').value=JSON.stringify(card);document.getElementById('save').onclick=async()=>{try{window.saved=await store.save(JSON.parse(document.getElementById('draft').value));document.getElementById('result').textContent='local saved; pending sync'}catch(e){document.getElementById('result').textContent=e.code}};
   });
  }
  await boot();await page.evaluate(()=>localStorage.setItem('synthetic-gba-sentinel','keep'));await page.locator('#save').click();await page.waitForFunction(()=>window.saved?.local_revision===1);
  let rows=await page.evaluate(async()=>({saved:await store.get(card.spell_id),queue:await store.pending()}));assert.equal(rows.queue.length,1);assert.equal(rows.saved.content_hash,rows.queue[0].content_hash);assert.equal(rows.saved.sync_state,'pending_sync');proof.checks.push({case:'click saves card and outbox atomically',result:'PASS'});
  await boot();rows=await page.evaluate(async()=>({saved:await store.get(card.spell_id),queue:await store.pending(),sentinel:localStorage.getItem('synthetic-gba-sentinel')}));assert.equal(rows.saved.local_revision,1);assert.equal(rows.queue.length,1);assert.equal(rows.sentinel,'keep');proof.checks.push({case:'reload preserves card/outbox and unrelated storage',result:'PASS'});
  await context.setOffline(true);
  const offline=await page.evaluate(async()=>{const old=await store.get(card.spell_id);const retry=await store.save(old.card);card.name='Edited offline';const updated=await store.save(card,{expected_revision:1});return {retry:retry.local_revision,revision:updated.local_revision,queue:await store.pending()}});assert.equal(offline.retry,1);assert.equal(offline.revision,2);assert.equal(offline.queue.length,1);assert.equal(offline.queue[0].payload.name,'Edited offline');proof.checks.push({case:'offline edits coalesce and exact retries do not duplicate',result:'PASS'});await context.setOffline(false);
  const conflict=await page.evaluate(async()=>{const other=await LivingSpellStore.SpellStore.open(registry,{database:'nw-rune-creations-browser-test'});let code;try{await other.save({...card,name:'Stale edit'},{expected_revision:1})}catch(e){code=e.code}other.close();return {code,current:await store.get(card.spell_id),queue:await store.pending()}});assert.equal(conflict.code,'LOCAL_REVISION_CONFLICT');assert.equal(conflict.current.local_revision,2);assert.equal(conflict.queue[0].payload.name,'Edited offline');proof.checks.push({case:'stale second connection cannot overwrite durable revision',result:'PASS'});
  const atomic=await page.evaluate(async()=>{const original=IDBObjectStore.prototype.put;let code;IDBObjectStore.prototype.put=function(...args){if(this.name==='outbox')throw new DOMException('synthetic quota','QuotaExceededError');return original.apply(this,args)};try{await store.save({...card,name:'Failed save'},{expected_revision:2})}catch(e){code=e.code}finally{IDBObjectStore.prototype.put=original}return {code,current:await store.get(card.spell_id),queue:await store.pending()}});assert.equal(atomic.code,'LOCAL_SAVE_FAILED');assert.equal(atomic.current.local_revision,2);assert.equal(atomic.queue[0].local_revision,2);proof.checks.push({case:'outbox write failure rolls back both stores',result:'PASS'});
  const invalid=await page.evaluate(async()=>{const bad={...card,javascript:'synthetic'};let code;try{await store.save(bad,{expected_revision:2})}catch(e){code=e.code}return {code,queue:await store.pending()}});assert.equal(invalid.code,'INVALID_CARD_SCHEMA');assert.equal(invalid.queue.length,1);assert.equal(outbound,0);proof.checks.push({case:'invalid cards rejected with zero external transport',result:'PASS'});
  await page.evaluate(()=>{
   window.ticketOf=row=>({spell_id:row.spell_id,local_revision:row.local_revision,content_hash:row.content_hash,idempotency_key:row.idempotency_key,attempts:row.attempts});
   window.receiptOf=t=>({spell_id:t.spell_id,local_revision:t.local_revision,content_hash:t.content_hash,idempotency_key:t.idempotency_key,remote_revision:'mock.commit.001'});
  });
  const mismatch=await page.evaluate(async()=>{const t=ticketOf((await store.pending())[0]);const result=await store.acknowledge(t,{...receiptOf(t),content_hash:'0'.repeat(64)});return {result,rows:await store.pending(),card:await store.get(t.spell_id)}});
  assert.equal(mismatch.result.reason,'ACK_MISMATCH');assert.equal(mismatch.rows.length,1);assert.equal(mismatch.card.sync_state,'pending_sync');proof.checks.push({case:'mismatched hash receipt cannot clear pending intent',result:'PASS'});
  const retry=await page.evaluate(async()=>{const t=ticketOf((await store.pending())[0]);window.oldTicket=t;const failed=await store.recordFailure(t,{now:1000});const duplicate=await store.recordFailure(t,{now:1000});return {failed,duplicate,before:await store.due({now:1999}),at:await store.due({now:2000})}});
  assert.equal(retry.failed.next_attempt_at,2000);assert.equal(retry.duplicate.reason,'STALE_OUTBOX');assert.equal(retry.before.length,0);assert.equal(retry.at[0].attempts,1);proof.checks.push({case:'failure persists backoff and duplicate failure does not advance attempts',result:'PASS'});
  // Reload real IndexedDB before a second failed delivery and recovery.
  await page.reload();
  for(const file of ['action-registry','spell-contract','spell-store'])await page.addScriptTag({path:path.resolve(__dirname,'../../living-library/live/'+file+'.js')});
  await page.evaluate(async()=>{
   const registry=new LivingActionRegistry.ActionRegistry();
   for(const [action_id,kind] of [['book.drag_active','condition'],['book.synthetic_effect','effect']])registry.register({action_id,name:action_id,kind,version:'0.1.0',target_type:'library.book_instance',inputs:{type:'object',properties:{},required:[],additionalProperties:false},outputs:{type:'boolean'},platform:['web'],policy:{scope:'local'},guard:()=>false,execute:()=>false});
   window.registry=registry;window.store=await LivingSpellStore.SpellStore.open(registry,{database:'nw-rune-creations-browser-test'});
   window.ticketOf=row=>({spell_id:row.spell_id,local_revision:row.local_revision,content_hash:row.content_hash,idempotency_key:row.idempotency_key,attempts:row.attempts});
   window.receiptOf=t=>({spell_id:t.spell_id,local_revision:t.local_revision,content_hash:t.content_hash,idempotency_key:t.idempotency_key,remote_revision:'mock.commit.001'});
  });
  const recovered=await page.evaluate(async()=>{
   const before=await store.due({now:1999});const row=(await store.pending())[0];const failed=await store.recordFailure(ticketOf(row),{now:2000});const waiting=await store.due({now:3999});const t=ticketOf((await store.due({now:4000}))[0]);
   // Synthetic remote fixture only: no HTTP and no real private data.
   const mockRemote=async attempt=>receiptOf(attempt);const result=await store.acknowledge(t,await mockRemote(t),{now:4000});
   return {before,failed,waiting,result,rows:await store.pending(),card:await store.get(t.spell_id),duplicate:await store.acknowledge(t,receiptOf(t),{now:4000}),t};
  });
  assert.equal(recovered.before.length,0);assert.equal(recovered.failed.next_attempt_at,4000);assert.equal(recovered.waiting.length,0);assert.equal(recovered.result.reason,'ACKNOWLEDGED');assert.equal(recovered.rows.length,0);assert.equal(recovered.card.local_revision,2);assert.equal(recovered.card.sync_state,'remote_acknowledged');assert.equal(recovered.duplicate.reason,'STALE_OUTBOX');proof.checks.push({case:'reload retains retry schedule and synthetic remote recovery acknowledges exactly once',result:'PASS'});
  const stale=await page.evaluate(async old=>{
   const prior=await store.get(old.spell_id);const edit=await store.save({...prior.card,name:'Newer offline revision'},{expected_revision:2});const ignored=await store.acknowledge(old,receiptOf(old));return {ignored,edit,rows:await store.pending()};
  },recovered.t);
  assert.equal(stale.ignored.reason,'STALE_OUTBOX');assert.equal(stale.rows[0].local_revision,3);assert.equal(stale.edit.sync_state,'pending_sync');proof.checks.push({case:'delayed old acknowledgement cannot remove newer offline edit',result:'PASS'});
  const rollback=await page.evaluate(async()=>{
   const t=ticketOf((await store.pending())[0]);const original=IDBObjectStore.prototype.delete;let code;
   IDBObjectStore.prototype.delete=function(...args){if(this.name==='outbox')throw Error('synthetic failure');return original.apply(this,args)};
   try{await store.acknowledge(t,receiptOf(t))}catch(e){code=e.code}finally{IDBObjectStore.prototype.delete=original}
   let invalid;try{await store.acknowledge(t,null)}catch(e){invalid=e.code}
   return {code,invalid,rows:await store.pending(),card:await store.get(t.spell_id)};
  });
  assert.equal(rollback.code,'OUTBOX_UPDATE_FAILED');assert.equal(rollback.invalid,'INVALID_SYNC_RECEIPT');assert.equal(rollback.rows.length,1);assert.equal(rollback.card.sync_state,'pending_sync');assert.equal(outbound,0);proof.checks.push({case:'acknowledgement write failure rolls back card and outbox with no external transport',result:'PASS'});
  await page.addScriptTag({path:path.resolve(__dirname,'../../living-library/live/outbox-pump.js')});
  const pumpRetry=await page.evaluate(async()=>{
   let now=10000,calls=0;const pump=new LivingOutboxPump.OutboxPump(store,async request=>{calls++;if(calls===1)throw Error('synthetic-secret');return receiptOf(request)},{clock:()=>now});
   const failed=await pump.run();const before=await pump.run();now=11000;const recovered=await pump.run();
   return {failed,before,recovered,calls,rows:await store.pending(),card:await store.get('spell.synthetic.saved')};
  });
  assert.equal(pumpRetry.failed.items[0].result,'RETRY_SCHEDULED');assert.equal(pumpRetry.before.status,'NO_WORK');assert.equal(pumpRetry.recovered.items[0].result,'ACKNOWLEDGED');assert.equal(pumpRetry.calls,2);assert.equal(pumpRetry.rows.length,0);assert.equal(pumpRetry.card.sync_state,'remote_acknowledged');assert.doesNotMatch(JSON.stringify(pumpRetry.failed),/synthetic-secret/);proof.checks.push({case:'pump delivers through injected mock, persists failed delivery and retries only when due',result:'PASS'});
  const pumpCancel=await page.evaluate(async()=>{
   const prior=await store.get('spell.synthetic.saved');await store.save({...prior.card,name:'Cancel fixture'},{expected_revision:3});
   let resolve,entered;const started=new Promise(r=>entered=r),c=new AbortController();const pump=new LivingOutboxPump.OutboxPump(store,request=>{entered();return new Promise(r=>resolve=()=>r(receiptOf(request)))},{clock:()=>20000});
   const run=pump.run({signal:c.signal});await started;c.abort();const cancelled=await run;const blocked=await new LivingOutboxPump.OutboxPump(store,async r=>receiptOf(r)).run();resolve();await new Promise(r=>setTimeout(r,0));
   return {cancelled,blocked,rows:await store.pending(),card:await store.get(prior.spell_id)};
  });
  assert.equal(pumpCancel.cancelled.status,'CANCELLED');assert.equal(pumpCancel.blocked.status,'BUSY');assert.equal(pumpCancel.rows[0].attempts,0);assert.equal(pumpCancel.card.sync_state,'pending_sync');proof.checks.push({case:'cancelled in-flight pump ignores late receipt and retains one-flight guard until settled',result:'PASS'});
  const pumpStale=await page.evaluate(async()=>{
   let resolve,entered;const started=new Promise(r=>entered=r);const pump=new LivingOutboxPump.OutboxPump(store,request=>{entered();return new Promise(r=>resolve=()=>r(receiptOf(request)))},{clock:()=>30000});
   const run=pump.run();await started;const prior=await store.get('spell.synthetic.saved');await store.save({...prior.card,name:'New edit while sending'},{expected_revision:4});resolve();const result=await run;
   return {result,rows:await store.pending(),card:await store.get(prior.spell_id)};
  });
  assert.equal(pumpStale.result.items[0].result,'STALE_OUTBOX');assert.equal(pumpStale.rows[0].local_revision,5);assert.equal(pumpStale.card.card.name,'New edit while sending');proof.checks.push({case:'offline edit during transport cannot be acknowledged by the old send',result:'PASS'});
  const pumpTimeout=await page.evaluate(async()=>{
   let resolve,now=40000,writes=0;const ledger=new Map();
   const mock=request=>{if(!ledger.has(request.idempotency_key)){writes++;ledger.set(request.idempotency_key,receiptOf(request))}return ledger.get(request.idempotency_key)};
   const pump=new LivingOutboxPump.OutboxPump(store,request=>{const receipt=mock(request);return new Promise(r=>resolve=()=>r(receipt))},{clock:()=>now,timeout_ms:15});
   const timed=await pump.run();const busy=await pump.run();resolve();await new Promise(r=>setTimeout(r,0));const waiting=await store.pending();
   now=41000;const recovered=await new LivingOutboxPump.OutboxPump(store,async r=>mock(r),{clock:()=>now}).run();
   return {timed,busy,waiting,recovered,writes,rows:await store.pending(),sentinel:localStorage.getItem('synthetic-gba-sentinel')};
  });
  assert.equal(pumpTimeout.timed.status,'TIMEOUT');assert.equal(pumpTimeout.busy.status,'BUSY');assert.equal(pumpTimeout.waiting[0].attempts,1);assert.equal(pumpTimeout.recovered.items[0].result,'ACKNOWLEDGED');assert.equal(pumpTimeout.writes,1);assert.equal(pumpTimeout.rows.length,0);assert.equal(pumpTimeout.sentinel,'keep');assert.equal(outbound,0);proof.checks.push({case:'timeout plus late response recovers with same idempotency key and one synthetic remote write',result:'PASS'});
  await page.evaluate(()=>store.close());await context.close();proof.result='PASS';
 }finally{await browser.close()}
}
main().catch(e=>{proof.result='FAIL';proof.error=e.stack;process.exitCode=1}).finally(async()=>{const out=path.resolve(process.env.NW_BROWSER_OUTPUT||'/tmp/nw-browser-proof');await fs.mkdir(out,{recursive:true});await fs.writeFile(path.join(out,'spell-store.json'),JSON.stringify(proof,null,2)+'\n');console.log(JSON.stringify(proof,null,2))});
