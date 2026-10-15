// Real IndexedDB, actual ObjectCraft records, synthetic data only. No endpoint.
const {chromium}=require('playwright'),fs=require('node:fs/promises'),path=require('node:path'),assert=require('node:assert/strict');
const proof={schema:'nw-object-outbox-browser-v1',result:'NOT_TESTED',checks:[],scope:'Explicit isolated local capture; no scene hook, background worker, real remote, credentials or AI readback'};
async function main(){
 const browser=await chromium.launch({headless:true});
 try{
  const context=await browser.newContext(),page=await context.newPage();let outbound=0;
  await context.route('**/*',route=>{if(route.request().url()==='https://nw-object-queue.invalid/')return route.fulfill({contentType:'text/html',body:'<!doctype html><title>Synthetic object outbox</title>'});outbound++;return route.abort()});
  async function boot(){
   await page.goto('https://nw-object-queue.invalid/');
   for(const name of ['object-craft','object-backup','object-outbox'])await page.addScriptTag({path:path.resolve(__dirname,'../../living-library/live/'+name+'.js')});
   await page.evaluate(async()=>{window.C=LivingObjectCraft;window.Q=LivingObjectOutbox.ObjectOutbox;window.queue=await Q.open({database:'nw-object-outbox-browser-test'})});
  }
  await boot();
  const initial=await page.evaluate(async()=>{
   let n=0;const rng={randomUUID:()=>`synthetic-${++n}`};const card=C.createBlankCard(localStorage,rng);C.createBlankBook(localStorage,rng,'Synthetic book');
   const state=C.read(localStorage);state.objects[0].future={nested:['inert','preserve']};localStorage.setItem(C.KEY,JSON.stringify(state));localStorage.setItem('synthetic-gba-sentinel','keep');
   window.original=localStorage.getItem(C.KEY);const first=await queue.capture(localStorage),retry=await queue.capture(localStorage);
   return {first,retry,rows:await queue.pending(),source:localStorage.getItem(C.KEY),original};
  });
  assert.equal(initial.first.queued,2);assert.equal(initial.retry.queued,0);assert.equal(initial.retry.unchanged,2);assert.equal(initial.source,initial.original);
  assert.deepEqual(JSON.parse(initial.rows.find(r=>r.object_id==='local.card.synthetic-1').payload_text).future,{nested:['inert','preserve']});
  assert.ok(initial.rows.every(r=>r.sync_state==='pending_authorization'&&r.schema==='nw.object-outbox.v1'));
  proof.checks.push({case:'actual blank card/book IDs, versions and unknown object fields captured once without changing source',result:'PASS'});
  await page.evaluate(()=>queue.close());await boot();await context.setOffline(true);
  const offline=await page.evaluate(async()=>{
   window.oldRaw=localStorage.getItem(C.KEY);const old=C.listCards(localStorage)[0];C.editCard(localStorage,old.id,{title:'Synthetic offline edit'});C.recycleCard(localStorage,old.id);
   const before=localStorage.getItem(C.KEY),result=await queue.capture(localStorage);return {result,rows:await queue.pending(),before,after:localStorage.getItem(C.KEY),sentinel:localStorage.getItem('synthetic-gba-sentinel')};
  });
  assert.equal(offline.result.queued,1);assert.equal(offline.rows.length,2);assert.equal(offline.before,offline.after);assert.equal(offline.sentinel,'keep');
  const card=offline.rows.find(r=>r.object_id==='local.card.synthetic-1');assert.equal(card.local_revision,3);assert.equal(JSON.parse(card.payload_text).status,'recycled');assert.equal(JSON.parse(card.payload_text).placement,'recycled');
  proof.checks.push({case:'reload/offline edit/recycle coalesces latest same-ID intent and retains original saves',result:'PASS'});
  const conflicts=await page.evaluate(async()=>{
   const other=await Q.open({database:'nw-object-outbox-browser-test'});let stale,conflict;
   try{await other.capture({getItem:()=>oldRaw})}catch(e){stale=e.code}
   const state=C.read(localStorage);state.objects[0].title='Same revision divergent';const divergent=JSON.stringify(state);
   try{await other.capture({getItem:()=>divergent})}catch(e){conflict=e.code}
   other.close();return {stale,conflict,rows:await queue.pending()};
  });
  assert.equal(conflicts.stale,'OBJECT_OUTBOX_STALE_REVISION');assert.equal(conflicts.conflict,'OBJECT_OUTBOX_REVISION_CONFLICT');assert.deepEqual(conflicts.rows,offline.rows);
  proof.checks.push({case:'independent connection cannot overwrite newer revision or equal-revision different contents',result:'PASS'});
  const rollback=await page.evaluate(async()=>{
   C.restoreCard(localStorage,'local.card.synthetic-1');const before=localStorage.getItem(C.KEY),put=IDBObjectStore.prototype.put;let code;
   IDBObjectStore.prototype.put=function(...args){if(this.name==='pending')throw Error('synthetic-secret');return put.apply(this,args)};
   try{await queue.capture(localStorage)}catch(e){code=e.code}finally{IDBObjectStore.prototype.put=put}
   const unchanged=await queue.pending();const retry=await queue.capture(localStorage);return {code,unchanged,retry,before,after:localStorage.getItem(C.KEY),rows:await queue.pending()};
  });
  assert.equal(rollback.code,'OBJECT_OUTBOX_WRITE_FAILED');assert.deepEqual(rollback.unchanged,offline.rows);assert.equal(rollback.retry.queued,1);assert.equal(rollback.rows.find(r=>r.object_id==='local.card.synthetic-1').local_revision,4);assert.equal(rollback.before,rollback.after);
  proof.checks.push({case:'pending write failure rolls back version ledger atomically; same revision can retry without source loss',result:'PASS'});
  const changed=await page.evaluate(async()=>{
   const raw=localStorage.getItem(C.KEY);let reads=0,code;
   try{await queue.capture({getItem:()=>++reads===1?raw:null})}catch(e){code=e.code}
   const prior=await queue.pending();const absent=await queue.capture({getItem:()=>null});
   return {code,absent,before:prior,after:await queue.pending()};
  });
  assert.equal(changed.code,'OBJECT_OUTBOX_SOURCE_CHANGED');assert.equal(changed.absent.queued,0);assert.deepEqual(changed.before,changed.after);
  proof.checks.push({case:'changed source is rejected; absent storage never erases pending intents or infers deletion',result:'PASS'});
  const invalid=await page.evaluate(async()=>{
   const before=await queue.pending(),state=C.read(localStorage);state.objects.push({id:'public.synthetic',objectType:'card',templateId:'other',revision:1});let code;
   try{await queue.capture({getItem:()=>JSON.stringify(state)})}catch(e){code=e.code}
   return {code,before,after:await queue.pending()};
  });
  assert.equal(invalid.code,'OBJECT_OUTBOX_UNSUPPORTED_OBJECT');assert.deepEqual(invalid.before,invalid.after);
  proof.checks.push({case:'unsupported public/foreign object rejects whole capture rather than silently dropping or converting it',result:'PASS'});
  const race=await page.evaluate(async()=>{
   const other=await Q.open({database:'nw-object-outbox-browser-test'}),a=C.read(localStorage),b=JSON.parse(JSON.stringify(a));a.objects[0].revision=5;a.objects[0].title='Revision five';b.objects[0].revision=6;b.objects[0].title='Revision six';
   const results=await Promise.allSettled([queue.capture({getItem:()=>JSON.stringify(a)}),other.capture({getItem:()=>JSON.stringify(b)})]);other.close();
   return {results:results.map(r=>r.status==='fulfilled'?'PASS':r.reason.code),rows:await queue.pending()};
  });
  assert.ok(race.results.every(r=>['PASS','OBJECT_OUTBOX_STALE_REVISION'].includes(r)));assert.equal(race.rows.find(r=>r.object_id==='local.card.synthetic-1').local_revision,6);
  proof.checks.push({case:'competing IDB connections serialize batches and keep highest observed object revision',result:'PASS'});
  const mock=await page.evaluate(async()=>{
   const rows=await queue.pending(),ledger=new Map();let writes=0;
   const syntheticTransport=async row=>{if(!ledger.has(row.idempotency_key)){writes++;ledger.set(row.idempotency_key,row.payload_text)}return ledger.get(row.idempotency_key)};
   for(const row of rows){if(await syntheticTransport(row)!==row.payload_text)throw Error('mock mismatch');await syntheticTransport(row)}
   return {writes,count:rows.length,stillPending:(await queue.pending()).every(r=>r.sync_state==='pending_authorization')};
  });
  assert.equal(mock.writes,mock.count);assert.equal(mock.stillPending,true);assert.equal(outbound,0);
  proof.checks.push({case:'synthetic idempotent consumer reads object text twice with one mock write per key; queue stays pending authorization',result:'PASS'});
  await page.evaluate(()=>queue.close());await context.close();proof.result='PASS';
 }finally{await browser.close()}
}
main().catch(e=>{proof.result='FAIL';proof.error=e.stack;process.exitCode=1}).finally(async()=>{const out=path.resolve(process.env.NW_BROWSER_OUTPUT||'/tmp/nw-browser-proof');await fs.mkdir(out,{recursive:true});await fs.writeFile(path.join(out,'object-outbox.json'),JSON.stringify(proof,null,2)+'\n');console.log(JSON.stringify(proof,null,2))});
