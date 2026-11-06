// Actual creator HTML, real IndexedDB/localStorage, synthetic records only.
const {chromium}=require('playwright'),fs=require('node:fs/promises'),path=require('node:path'),assert=require('node:assert/strict');
const proof={schema:'nw-object-reconcile-browser-v1',result:'NOT_TESTED',checks:[],scope:'Candidate creator only; no staging, 3D, device, private transport or real remote'};
async function main(){
 const browser=await chromium.launch({headless:true});
 try{
  const context=await browser.newContext({viewport:{width:412,height:915}}),page=await context.newPage();let outbound=0,holdNext=false;
  await context.route('**/*',async route=>{
   const url=new URL(route.request().url());if(url.origin!=='https://nw-reconcile.invalid'){outbound++;return route.abort()}
   const name=path.basename(url.pathname);
   if(name==='host.html')return route.fulfill({contentType:'text/html',body:'<!doctype html><iframe id="creator" src="/card-creation.html"></iframe>'});
   if(name==='peer.html')return route.fulfill({contentType:'text/html',body:'<!doctype html><title>synthetic peer</title>'});
   if(!['card-creation.html','object-craft.js','object-backup.js','object-outbox.js','object-reconciler.js'].includes(name))return route.abort();
   let body=await fs.readFile(path.resolve(__dirname,'../../living-library/live',name),'utf8');
   if(name==='card-creation.html'&&holdNext){holdNext=false;body=body.replace('<script>\n(function(){','<script>LivingObjectOutbox.ObjectOutbox.open=()=>new Promise(()=>{});window.__capturePaused=true;</script><script>\n(function(){')}
   return route.fulfill({contentType:name.endsWith('.js')?'text/javascript':'text/html',body});
  });
  const source=()=>page.evaluate(()=>localStorage.getItem(LivingObjectCraft.KEY));
  const pending=()=>page.evaluate(async()=>{const q=await LivingObjectOutbox.ObjectOutbox.open();try{return await q.pending()}finally{q.close()}});
  const settled=()=>page.waitForFunction(()=>document.querySelector('#queueStatus').textContent.startsWith('本機待同步 '));
  await page.goto('https://nw-reconcile.invalid/card-creation.html');await settled();
  await page.evaluate(()=>{let n=0;const random={randomUUID:()=>`synthetic-${++n}`};LivingObjectCraft.createBlankCard(localStorage,random);LivingObjectCraft.createBlankBook(localStorage,random,'Synthetic book');localStorage.setItem('synthetic-gba-sentinel','keep')});
  const original=await source();await page.reload();await settled();
  let rows=await pending();assert.equal(rows.length,2);assert.equal(await source(),original);assert.ok(rows.every(r=>r.sync_state==='pending_authorization'));
  proof.checks.push({case:'reopening actual creator automatically captures existing card and book without touching source',result:'PASS'});
  await context.setOffline(true);await page.locator('#editName').fill('Synthetic offline edit');await page.locator('#saveCard').click();
  await page.waitForFunction(()=>document.querySelector('#queueStatus').textContent==='本機待同步 2 筆；尚未上傳雲端。');
  rows=await pending();assert.equal(rows.find(r=>r.object_id==='local.card.synthetic-1').local_revision,2);
  await page.locator('#recycleCard').click();await page.locator('#confirmRecycle').click();await settled();
  rows=await pending();assert.equal(JSON.parse(rows.find(r=>r.object_id==='local.card.synthetic-1').payload_text).status,'recycled');
  await page.locator('#recycleBin summary').click();await page.locator('#recycleList button').click();await settled();
  rows=await pending();assert.equal(rows.find(r=>r.object_id==='local.card.synthetic-1').local_revision,4);
  proof.checks.push({case:'offline save, recycle and restore prepare latest same-ID local intents through actual UI',result:'PASS'});
  await page.evaluate(()=>{window.__put=IDBObjectStore.prototype.put;window.__failedWrites=0;IDBObjectStore.prototype.put=function(...args){if(this.name==='pending'){window.__failedWrites++;throw Error('synthetic-secret')}return window.__put.apply(this,args)}});
  await page.locator('#cardList button').click();await page.locator('#editName').fill('Synthetic quota save');await page.locator('#saveCard').click();
  await page.waitForFunction(()=>document.querySelector('#queueStatus').textContent.includes('待補齊'));
  const failedRaw=await source();assert.equal(JSON.parse(failedRaw).objects[0].revision,5);assert.match(await page.locator('#status').textContent(),/已儲存/);
  assert.equal((await pending()).find(r=>r.object_id==='local.card.synthetic-1').local_revision,4);assert.equal(await page.evaluate(()=>window.__failedWrites),1);
  assert.doesNotMatch(await page.locator('#queueStatus').textContent(),/synthetic-secret/);
  await page.reload();await settled();assert.equal(await source(),failedRaw);assert.equal((await pending()).find(r=>r.object_id==='local.card.synthetic-1').local_revision,5);
  proof.checks.push({case:'injected queue quota failure preserves completed save, reports pending repair once and reopening catches up',result:'PASS'});
  await context.setOffline(false);const peer=await context.newPage();await peer.goto('https://nw-reconcile.invalid/peer.html');
  await peer.addScriptTag({path:path.resolve(__dirname,'../../living-library/live/object-craft.js')});
  await peer.evaluate(()=>LivingObjectCraft.setBookActive(localStorage,'local.book.synthetic-2',false));
  await page.waitForFunction(async()=>{const q=await LivingObjectOutbox.ObjectOutbox.open();try{return (await q.pending()).some(r=>r.object_id==='local.book.synthetic-2'&&r.local_revision===2)}finally{q.close()}});
  assert.equal(JSON.parse((await pending()).find(r=>r.object_id==='local.book.synthetic-2').payload_text).active,false);await peer.close();
  proof.checks.push({case:'same-origin storage event catches peer book revision without rewriting scene or source',result:'PASS'});
  const before=(await pending()).map(r=>r.idempotency_key).sort();await page.locator('#localBackup summary').click();await page.locator('#queueObjects').click();
  await page.waitForFunction(()=>document.querySelector('#backupStatus').textContent.includes('本次更新 0 筆'));
  assert.deepEqual((await pending()).map(r=>r.idempotency_key).sort(),before);
  proof.checks.push({case:'manual icon rescue remains available and repeated automatic/manual capture does not duplicate',result:'PASS'});
  holdNext=true;await page.goto('https://nw-reconcile.invalid/host.html');let frame=page.frames().find(f=>f.url().endsWith('/card-creation.html'));
  if(!frame)frame=await page.waitForEvent('frameattached');await page.frameLocator('#creator').locator('#editName').fill('Synthetic interrupted save');await page.frameLocator('#creator').locator('#saveCard').click();
  const interrupted=await page.evaluate(()=>localStorage.getItem('living-library-player-objects-v1'));assert.equal(JSON.parse(interrupted).objects[0].revision,6);
  await page.evaluate(()=>document.querySelector('#creator').remove());
  await page.evaluate(()=>{const f=document.createElement('iframe');f.id='creator';f.src='/card-creation.html';document.body.append(f)});
  await page.frameLocator('#creator').locator('#queueStatus').filter({hasText:'本機待同步 2 筆'}).waitFor();
  const reopened=await page.frameLocator('#creator').locator('body').evaluate(async()=>{const q=await LivingObjectOutbox.ObjectOutbox.open();try{return {rows:await q.pending(),raw:localStorage.getItem(LivingObjectCraft.KEY),sentinel:localStorage.getItem('synthetic-gba-sentinel')}}finally{q.close()}});
  assert.equal(reopened.raw,interrupted);assert.equal(reopened.sentinel,'keep');assert.equal(reopened.rows.find(r=>r.object_id==='local.card.synthetic-1').local_revision,6);assert.equal(outbound,0);
  proof.checks.push({case:'actual iframe destroyed during paused queue open retains save; fresh creator iframe repairs latest revision without remote requests',result:'PASS'});
  await context.close();proof.result='PASS';
 }finally{await browser.close()}
}
main().catch(e=>{proof.result='FAIL';proof.error=e.stack;process.exitCode=1}).finally(async()=>{const out=path.resolve(process.env.NW_BROWSER_OUTPUT||'/tmp/nw-browser-proof');await fs.mkdir(out,{recursive:true});await fs.writeFile(path.join(out,'object-reconcile.json'),JSON.stringify(proof,null,2)+'\n');console.log(JSON.stringify(proof,null,2))});
