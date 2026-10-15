// Actual book creator and iframe lifecycle, real IndexedDB, synthetic records only.
const {chromium}=require('playwright'),fs=require('node:fs/promises'),path=require('node:path'),assert=require('node:assert/strict');
const proof={schema:'nw-book-reconcile-browser-v1',result:'NOT_TESTED',checks:[],scope:'Candidate book page only; no staging, 3D first frame, device or private remote'};
async function main(){
 const browser=await chromium.launch({headless:true});
 try{
  const context=await browser.newContext({viewport:{width:412,height:915}}),page=await context.newPage();let holdNext=false,outbound=0;
  await context.route('**/*',async route=>{
   const url=new URL(route.request().url());if(url.origin!=='https://nw-book-reconcile.invalid'){outbound++;return route.abort()}
   const name=path.basename(url.pathname);
   if(name==='host.html')return route.fulfill({contentType:'text/html',body:'<!doctype html><script>window.messages=[];addEventListener("message",e=>messages.push({data:e.data,origin:e.origin}));</script><iframe id="creator" src="/book-creation.html"></iframe>'});
   if(!['book-creation.html','object-craft.js','object-backup.js','object-outbox.js','object-reconciler.js'].includes(name))return route.abort();
   let body=await fs.readFile(path.resolve(__dirname,'../../living-library/live',name),'utf8');
   if(name==='book-creation.html'&&holdNext){holdNext=false;body=body.replace("(()=>{'use strict';", "LivingObjectOutbox.ObjectOutbox.open=()=>new Promise(()=>{});window.__capturePaused=true;\n(()=>{'use strict';")}
   return route.fulfill({contentType:name.endsWith('.js')?'text/javascript':'text/html',body});
  });
  const raw=()=>page.evaluate(()=>localStorage.getItem(LivingObjectCraft.KEY));
  const rows=()=>page.evaluate(async()=>{const q=await LivingObjectOutbox.ObjectOutbox.open();try{return await q.pending()}finally{q.close()}});
  const waitCount=n=>page.waitForFunction(n=>document.querySelector('#queueStatus').textContent===`本機待同步 ${n} 筆；尚未上傳雲端。`,n);
  await page.goto('https://nw-book-reconcile.invalid/book-creation.html');await waitCount(0);
  await page.evaluate(()=>{let n=0;const C=LivingObjectCraft,r={randomUUID:()=>`synthetic-${++n}`};C.createBlankCard(localStorage,r);const card=C.createBlankCard(localStorage,r);C.recycleCard(localStorage,card.id);C.createBlankBook(localStorage,r,'Synthetic prior book');localStorage.setItem('synthetic-gba-sentinel','keep')});
  const original=await raw();await page.reload();await waitCount(3);assert.equal(await raw(),original);
  let queued=await rows();assert.equal(queued.length,3);assert.ok(queued.every(r=>r.sync_state==='pending_authorization'));
  assert.equal(JSON.parse(queued.find(r=>r.object_id==='local.card.synthetic-2').payload_text).status,'recycled');
  proof.checks.push({case:'book creator reopen captures existing cards, book and recycle state without changing original saves',result:'PASS'});
  await context.setOffline(true);await page.locator('#name').fill('Synthetic offline book');await page.locator('#create').click();await waitCount(4);
  const saved=JSON.parse(await raw()).objects.at(-1);queued=await rows();assert.equal(saved.title,'Synthetic offline book');assert.equal(saved.revision,1);assert.equal(queued.find(r=>r.object_id===saved.id).payload_text,JSON.stringify(saved));
  assert.equal(await page.locator('#bookModel').textContent(),saved.title);assert.equal(await page.locator('#count').textContent(),'2/12');
  proof.checks.push({case:'actual offline create keeps unique book ID and local preview while preparing same-record outbox intent',result:'PASS'});
  await page.evaluate(()=>{window.__put=IDBObjectStore.prototype.put;window.failedWrites=0;IDBObjectStore.prototype.put=function(...a){if(this.name==='pending'){failedWrites++;throw Error('synthetic-secret')}return __put.apply(this,a)}});
  await page.locator('#name').fill('Synthetic queue failure book');await page.locator('#create').click();await page.waitForFunction(()=>document.querySelector('#queueStatus').textContent.includes('待補齊'));
  const failedRaw=await raw(),failedBook=JSON.parse(failedRaw).objects.at(-1);assert.equal(failedBook.title,'Synthetic queue failure book');assert.equal((await rows()).length,4);assert.equal(await page.evaluate(()=>failedWrites),1);
  assert.match(await page.locator('#status').textContent(),/已創造/);assert.doesNotMatch(await page.locator('#queueStatus').textContent(),/synthetic-secret/);
  proof.checks.push({case:'queue quota failure is bounded, leaves successfully saved book intact and reports unsent repair pending',result:'PASS'});
  await page.evaluate(()=>{IDBObjectStore.prototype.put=__put});await page.locator('#localQueue summary').click();
  const rescue=page.locator('#queueBook'),bounds=await rescue.boundingBox();assert.ok(bounds.width>=44&&bounds.height>=44);assert.doesNotMatch(await rescue.textContent(),/[\p{L}\p{N}]/u);assert.ok(await rescue.getAttribute('aria-label'));
  await rescue.click();await waitCount(5);assert.equal(await raw(),failedRaw);assert.equal((await rows()).find(r=>r.object_id===failedBook.id).payload_text,JSON.stringify(failedBook));
  const keys=(await rows()).map(r=>r.idempotency_key).sort();await rescue.click();await waitCount(5);assert.deepEqual((await rows()).map(r=>r.idempotency_key).sort(),keys);
  proof.checks.push({case:'icon-only accessible manual rescue captures failed book once without changing source or duplicating intents',result:'PASS'});
  await context.setOffline(false);holdNext=true;await page.goto('https://nw-book-reconcile.invalid/host.html');
  let frame=page.frameLocator('#creator');await frame.locator('#name').fill('Synthetic interrupted book');await frame.locator('#create').click();
  await page.waitForFunction(()=>messages.length===1);
  const message=await page.evaluate(()=>messages[0]);assert.deepEqual(message,{data:{kind:'living-library-local-objects-updated'},origin:'https://nw-book-reconcile.invalid'});
  const interrupted=await frame.locator('body').evaluate(()=>localStorage.getItem(LivingObjectCraft.KEY)),interruptedBook=JSON.parse(interrupted).objects.at(-1);
  await page.evaluate(()=>document.querySelector('#creator').remove());await page.evaluate(()=>{const f=document.createElement('iframe');f.id='creator';f.src='/book-creation.html';document.body.append(f)});
  frame=page.frameLocator('#creator');await frame.locator('#queueStatus').filter({hasText:'本機待同步 6 筆'}).waitFor();
  const recovered=await frame.locator('body').evaluate(async()=>{const q=await LivingObjectOutbox.ObjectOutbox.open();try{return {rows:await q.pending(),raw:localStorage.getItem(LivingObjectCraft.KEY),sentinel:localStorage.getItem('synthetic-gba-sentinel')}}finally{q.close()}});
  assert.equal(recovered.raw,interrupted);assert.equal(recovered.sentinel,'keep');assert.equal(recovered.rows.find(r=>r.object_id===interruptedBook.id).payload_text,JSON.stringify(interruptedBook));
  proof.checks.push({case:'original parent notification retained; iframe destroyed during paused queue open recovers saved book on new iframe',result:'PASS'});
  await frame.locator('body').evaluate(()=>{window.__set=Storage.prototype.setItem;Storage.prototype.setItem=function(key,value){if(key===LivingObjectCraft.KEY)throw Error('synthetic-source-write-denied');return __set.call(this,key,value)}});
  await frame.locator('#name').fill('Synthetic must not exist');await frame.locator('#create').click();await frame.locator('#status').filter({hasText:'synthetic-source-write-denied'}).waitFor();
  const afterFailure=await frame.locator('body').evaluate(async()=>{const q=await LivingObjectOutbox.ObjectOutbox.open();try{return {rows:await q.pending(),raw:localStorage.getItem(LivingObjectCraft.KEY),bookCount:LivingObjectCraft.listBooks(localStorage).length}}finally{q.close()}});
  assert.equal(afterFailure.raw,interrupted);assert.equal(afterFailure.bookCount,4);assert.deepEqual(afterFailure.rows,recovered.rows);assert.equal(await page.evaluate(()=>messages.length),1);
  proof.checks.push({case:'source save failure creates no new book, no outbox intent and no success notification',result:'PASS'});
  await frame.locator('body').evaluate(()=>{Storage.prototype.setItem=__set;localStorage.setItem(LivingObjectCraft.KEY,'{synthetic-malformed')});
  await page.evaluate(()=>document.querySelector('#creator').src='/book-creation.html?reopen=1');frame=page.frameLocator('#creator');
  await frame.locator('#create:disabled').waitFor();await frame.locator('#queueStatus').filter({hasText:'待補齊'}).waitFor();
  assert.equal(await frame.locator('body').evaluate(()=>localStorage.getItem(LivingObjectCraft.KEY)),'{synthetic-malformed');assert.equal(await page.evaluate(()=>messages.length),1);assert.equal(outbound,0);
  proof.checks.push({case:'malformed original save stays untouched on reopen, creation stops and no network upload occurs',result:'PASS'});
  await context.close();proof.result='PASS';
 }finally{await browser.close()}
}
main().catch(e=>{proof.result='FAIL';proof.error=e.stack;process.exitCode=1}).finally(async()=>{const out=path.resolve(process.env.NW_BROWSER_OUTPUT||'/tmp/nw-browser-proof');await fs.mkdir(out,{recursive:true});await fs.writeFile(path.join(out,'book-reconcile.json'),JSON.stringify(proof,null,2)+'\n');console.log(JSON.stringify(proof,null,2))});
