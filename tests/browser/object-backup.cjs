// Actual candidate in-book page, synthetic localStorage and download/file picker.
// No deployed UI, scene, native download manager or device acceptance claimed.
const {chromium}=require('playwright'),fs=require('node:fs/promises'),path=require('node:path'),assert=require('node:assert/strict');
const proof={schema:'nw-object-backup-browser-v1',result:'NOT_TESTED',checks:[]};
async function main(){
 const browser=await chromium.launch({headless:true});
 try{
  const context=await browser.newContext({viewport:{width:412,height:915},acceptDownloads:true}),page=await context.newPage();let outbound=0;
  await context.route('**/*',async route=>{
   const url=new URL(route.request().url());
   if(url.origin!=='https://nw-backup.invalid'){outbound++;return route.abort()}
   const name=path.basename(url.pathname);
   if(!['card-creation.html','object-craft.js','object-backup.js','object-outbox.js','object-reconciler.js'].includes(name))return route.abort();
   return route.fulfill({contentType:name.endsWith('.js')?'text/javascript':'text/html',body:await fs.readFile(path.resolve(__dirname,'../../living-library/live',name))});
  });
  await page.goto('https://nw-backup.invalid/card-creation.html');
  const raw=await page.evaluate(()=>{
   let n=0;const random={randomUUID:()=>`synthetic-${++n}`},C=LivingObjectCraft;
   const card=C.createBlankCard(localStorage,random);C.editCard(localStorage,card.id,{title:'Synthetic only'});C.createBlankBook(localStorage,random,'Synthetic book');C.recycleCard(localStorage,card.id);
   localStorage.setItem('synthetic-gba-sentinel','keep');return localStorage.getItem(C.KEY);
  });
  await page.reload();await page.locator('#localBackup summary').click();
  for(const id of ['exportObjects','inspectObjects','queueObjects']){
   const button=page.locator('#'+id),bounds=await button.boundingBox();
   assert.doesNotMatch(await button.textContent(),/[\p{L}\p{N}]/u);assert.ok(await button.getAttribute('aria-label'));assert.ok(bounds.width>=44&&bounds.height>=44);
  }
  const downloading=page.waitForEvent('download');await page.locator('#exportObjects').click();const download=await downloading;
  assert.equal(download.suggestedFilename(),'living-library-creations-backup-v1.json');
  const text=await fs.readFile(await download.path(),'utf8');assert.equal(JSON.parse(text).source_text,raw);
  proof.checks.push({case:'real candidate page icon downloads full synthetic book/card/recycle backup',result:'PASS'});
  await context.setOffline(true);
  await page.locator('#backupFile').setInputFiles({name:'synthetic-backup.json',mimeType:'application/json',buffer:Buffer.from(text)});
  await page.waitForFunction(()=>document.querySelector('#backupStatus').textContent.includes('檢查通過：'));
  assert.match(await page.locator('#backupStatus').textContent(),/卡片 1（含回收 1）、書本 1/);
  proof.checks.push({case:'offline file inspection validates counts without restoring or sending',result:'PASS'});
  const bad=JSON.parse(text);bad.source_text=bad.source_text.replace('Synthetic book','Altered book');
  await page.locator('#backupFile').setInputFiles({name:'synthetic-tampered.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(bad))});
  await page.waitForFunction(()=>document.querySelector('#backupStatus').textContent.includes('檢查未通過'));
  assert.deepEqual(await page.evaluate(()=>({raw:localStorage.getItem(LivingObjectCraft.KEY),sentinel:localStorage.getItem('synthetic-gba-sentinel')})),{raw,sentinel:'keep'});assert.equal(outbound,0);
  proof.checks.push({case:'tampered file rejected with exact source and unrelated saves untouched, zero external requests',result:'PASS'});
  await page.waitForFunction(()=>document.querySelector('#queueStatus').textContent==='本機待同步 2 筆；尚未上傳雲端。');
  await page.locator('#queueObjects').click();
  await page.waitForFunction(()=>document.querySelector('#backupStatus').textContent.includes('本機待同步 2 筆；本次更新 0 筆'));
  await page.reload();await page.locator('#localBackup summary').click();await page.locator('#queueObjects').click();
  await page.waitForFunction(()=>document.querySelector('#backupStatus').textContent.includes('本機待同步 2 筆；本次更新 0 筆'));
  const queued=await page.evaluate(async()=>{const q=await LivingObjectOutbox.ObjectOutbox.open();try{return {rows:await q.pending(),raw:localStorage.getItem(LivingObjectCraft.KEY),sentinel:localStorage.getItem('synthetic-gba-sentinel')}}finally{q.close()}});
  assert.equal(queued.rows.length,2);assert.ok(queued.rows.every(r=>r.sync_state==='pending_authorization'));assert.equal(queued.raw,raw);assert.equal(queued.sentinel,'keep');assert.equal(outbound,0);
  proof.checks.push({case:'actual creator icon prepares durable local intents offline, reload retry deduplicates and clearly remains not uploaded',result:'PASS'});
  await context.close();proof.result='PASS';
 }finally{await browser.close()}
}
main().catch(e=>{proof.result='FAIL';proof.error=e.stack;process.exitCode=1}).finally(async()=>{const out=path.resolve(process.env.NW_BROWSER_OUTPUT||'/tmp/nw-browser-proof');await fs.mkdir(out,{recursive:true});await fs.writeFile(path.join(out,'object-backup.json'),JSON.stringify(proof,null,2)+'\n');console.log(JSON.stringify(proof,null,2))});
