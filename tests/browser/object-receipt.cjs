// Synthetic receiver only. Correlation validation is not authentication or real sync.
const {chromium}=require('playwright'),fs=require('node:fs/promises'),path=require('node:path'),assert=require('node:assert/strict');
const proof={schema:'nw-object-receipt-browser-v1',result:'NOT_TESTED',checks:[],scope:'Explicit local receipt settlement with synthetic receiver; no UI sender, network, credentials or remote verification'};
async function main(){
 const browser=await chromium.launch({headless:true});
 try{
  const context=await browser.newContext(),page=await context.newPage();let outbound=0;
  await context.route('**/*',r=>{if(r.request().url()==='https://nw-object-receipt.invalid/')return r.fulfill({contentType:'text/html',body:'<!doctype html><title>Synthetic receiver</title>'});outbound++;return r.abort()});
  async function boot(){await page.goto('https://nw-object-receipt.invalid/');for(const n of ['object-craft','object-backup','object-outbox'])await page.addScriptTag({path:path.resolve(__dirname,'../../living-library/live/'+n+'.js')});await page.evaluate(async()=>{window.C=LivingObjectCraft;window.q=await LivingObjectOutbox.ObjectOutbox.open({database:'nw-object-outbox-receipt-test'});window.receipt=row=>({schema:'nw.object-receipt.v1',object_id:row.object_id,local_revision:row.local_revision,content_hash:row.content_hash,idempotency_key:row.idempotency_key,remote_revision:'synthetic-r1'})})}
  await boot();
  const first=await page.evaluate(async()=>{let n=0;const r={randomUUID:()=>`synthetic-${++n}`};C.createBlankCard(localStorage,r);C.createBlankBook(localStorage,r,'Synthetic book');await q.capture(localStorage);const rows=await q.pending();window.card=rows.find(r=>r.object_id.startsWith('local.card.'));window.book=rows.find(r=>r.object_id.startsWith('local.book.'));const before=localStorage.getItem(C.KEY);let code;try{await q.acknowledge(card,{...receipt(card),content_hash:'0'.repeat(64),idempotency_key:card.object_id+':1:'+ '0'.repeat(64)})}catch(e){code=e.code}return {code,before,after:localStorage.getItem(C.KEY),rows:await q.pending()}});
  assert.equal(first.code,'OBJECT_OUTBOX_RECEIPT_MISMATCH');assert.equal(first.rows.length,2);assert.equal(first.before,first.after);
  proof.checks.push({case:'mismatched content hash receipt cannot clear pending object or modify source',result:'PASS'});
  const stale=await page.evaluate(async()=>{C.editCard(localStorage,card.object_id,{title:'Synthetic newer edit'});await q.capture(localStorage);const other=await LivingObjectOutbox.ObjectOutbox.open({database:'nw-object-outbox-receipt-test'});try{return {result:await other.acknowledge(card,receipt(card)),rows:await q.pending()}}finally{other.close()}});
  assert.equal(stale.result.result,'STALE_ATTEMPT');assert.equal(stale.rows.find(r=>r.object_id===first.rows.find(r=>r.object_id.startsWith('local.card.')).object_id).local_revision,2);
  proof.checks.push({case:'delayed old acknowledgement from independent connection preserves newer offline edit',result:'PASS'});
  const rollback=await page.evaluate(async()=>{window.current=(await q.pending()).find(r=>r.object_id===card.object_id);const del=IDBObjectStore.prototype.delete;let code;IDBObjectStore.prototype.delete=function(...a){if(this.name==='pending')throw Error('synthetic-secret');return del.apply(this,a)};try{await q.acknowledge(current,receipt(current))}catch(e){code=e.code}finally{IDBObjectStore.prototype.delete=del}return {code,rows:await q.pending()}});
  assert.equal(rollback.code,'OBJECT_OUTBOX_RECEIPT_WRITE_FAILED');assert.deepEqual(rollback.rows,stale.rows);
  proof.checks.push({case:'receipt deletion failure aborts transaction and retains exact pending intent for retry',result:'PASS'});
  const mock=await page.evaluate(async()=>{const remote=new Map();let writes=0;const receive=async row=>{if(!remote.has(row.idempotency_key)){writes++;remote.set(row.idempotency_key,row.payload_text)}return receipt(row)};const before=localStorage.getItem(C.KEY);const ack=await receive(current);await receive(current);const applied=await q.acknowledge(current,ack),replay=await q.acknowledge(current,ack);return {writes,applied,replay,before,after:localStorage.getItem(C.KEY),remaining:await q.pending()}});
  assert.equal(mock.writes,1);assert.equal(mock.applied.result,'LOCAL_RECEIPT_APPLIED');assert.equal(mock.replay.result,'NO_PENDING_MATCH');assert.equal(mock.remaining.length,1);assert.equal(mock.before,mock.after);
  proof.checks.push({case:'synthetic receiver deduplicates same object key; exact receipt removes only matching local intent and replay is harmless',result:'PASS'});
  await page.evaluate(()=>q.close());await boot();
  const reloaded=await page.evaluate(async()=>{const capture=await q.capture(localStorage),rows=await q.pending();return {capture,rows}});assert.equal(reloaded.capture.queued,0);assert.equal(reloaded.rows.length,1);
  proof.checks.push({case:'reload retains version ledger; unchanged acknowledged source does not requeue while unrelated book remains pending',result:'PASS'});
  const invalid=await page.evaluate(async()=>{const row=(await q.pending())[0];let getterCalls=0,code;const ack=receipt(row);Object.defineProperty(ack,'content_hash',{get(){getterCalls++;return row.content_hash}});try{await q.acknowledge(row,ack)}catch(e){code=e.code}const before=await q.pending();let missing;try{await q.acknowledge(row,{...receipt(row),remote_revision:''})}catch(e){missing=e.code}return {getterCalls,code,missing,before,after:await q.pending()}});
  assert.equal(invalid.getterCalls,0);assert.equal(invalid.code,'OBJECT_OUTBOX_INVALID_RECEIPT');assert.equal(invalid.missing,'OBJECT_OUTBOX_INVALID_RECEIPT');assert.deepEqual(invalid.before,invalid.after);
  proof.checks.push({case:'accessor and invalid remote revision receipts rejected without invoking getter or touching queue',result:'PASS'});
  const next=await page.evaluate(async()=>{const id=C.listCards(localStorage)[0].id;C.editCard(localStorage,id,{title:'Synthetic revision three'});const before=localStorage.getItem(C.KEY),result=await q.capture(localStorage);return {result,rows:await q.pending(),before,after:localStorage.getItem(C.KEY)}});
  assert.equal(next.result.queued,1);assert.equal(next.rows.find(r=>r.object_id.startsWith('local.card.')).local_revision,3);assert.equal(next.before,next.after);assert.equal(outbound,0);
  proof.checks.push({case:'new edit after acknowledged version produces a new local intent; no automatic transport or external request',result:'PASS'});
  await page.evaluate(()=>q.close());await context.close();proof.result='PASS';
 }finally{await browser.close()}
}
main().catch(e=>{proof.result='FAIL';proof.error=e.stack;process.exitCode=1}).finally(async()=>{const out=path.resolve(process.env.NW_BROWSER_OUTPUT||'/tmp/nw-browser-proof');await fs.mkdir(out,{recursive:true});await fs.writeFile(path.join(out,'object-receipt.json'),JSON.stringify(proof,null,2)+'\n');console.log(JSON.stringify(proof,null,2))});
