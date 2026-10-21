/* Card life-cycle browser proof (synthetic local data, real IndexedDB).
 * No remote sync, no live player save and no 3D/GPU acceptance implied. */
const {chromium}=require('playwright');
const fs=require('node:fs/promises'),path=require('node:path'),assert=require('node:assert/strict');
const origin='https://nw-card-life.invalid';
const liveDir=path.resolve(__dirname,'../../living-library/live');
const proof={schema:'nw-card-life-browser-v1',result:'NOT_TESTED',checks:[]};
async function main(){
 const browser=await chromium.launch({headless:true});
 try{
  const context=await browser.newContext();
  await context.route('**/*',async route=>{
   const u=new URL(route.request().url());
   if(u.origin!==origin)return route.abort();
   const name=u.pathname.slice(1)||'card-creation.html';
   if(!/^[a-z0-9-]+\.(?:js|html)$/.test(name))return route.abort();
   try{
    const body=await fs.readFile(path.join(liveDir,name),'utf8');
    await route.fulfill({status:200,contentType:name.endsWith('.html')?'text/html; charset=utf-8':'text/javascript; charset=utf-8',
     body:name==='card-creation.html'
      ?body.replace('<script src="./object-craft.js',
        '<script src="./contract-policy.js"></script>\n<script src="./object-craft.js')
      :body});
   }catch{return route.abort()}
  });
  const page=await context.newPage(),pageErrors=[];
  page.on('pageerror',e=>pageErrors.push(e.message));
  await page.goto(origin+'/card-creation.html',{waitUntil:'load'});
  await page.locator('#createCard').click();
  const originalId=await page.evaluate(()=>window.LivingObjectCraft.listCards(localStorage)[0].id);
  await page.locator('#editName').fill('合成來源卡');
  await page.locator('#editDescription').fill('測試 GBA 格式下載');
  await page.locator('#editDownloadable').check();
  await page.locator('#saveCard').click();
  const changed=await page.evaluate(id=>window.LivingObjectCraft.listCards(localStorage).find(c=>c.id===id),originalId);
  assert.equal(changed.id,originalId);assert.equal(changed.title,'合成來源卡');
  assert.equal(changed.downloadTemplate,true);assert.equal(changed.revision,2);
  proof.checks.push('create / edit / opt-in / revision / same ID');
  await page.locator('#createCard').click();
  const copiedId=await page.evaluate(sourceId=>{
   const C=window.LivingObjectCraft,blank=C.listCards(localStorage).find(c=>c.id!==sourceId&&c.title==='空白卡片');
   C.placeCard(localStorage,blank.id,'local.atlas.compendium',9);
   const ready=C.listCards(localStorage).find(c=>c.id===blank.id);
   const source=C.listDownloadableTemplates(localStorage).find(x=>x.id===sourceId);
   const made=C.forgeBlankFromTemplate(localStorage,{blankId:ready.id,blankRevision:ready.revision,
    sourceId:source.id,sourceRevision:source.revision});
   if(made.title!=='合成來源卡'||made.id!==blank.id||made.ability!==null)throw Error('forge failed');
   C.placeCard(localStorage,made.id,null);
   return made.id;
  },originalId);
  assert.notEqual(copiedId,originalId);
  proof.checks.push('GBA downloads presentation only, preserves independent card ID and no ability');
  const queued=await page.evaluate(async()=>{
   const Q=window.LivingObjectOutbox.ObjectOutbox,queue=await Q.open();
   try{return await queue.capture(localStorage)}finally{queue.close()}
  });
  assert.ok(queued.queued>=2);
  // Select the original via real UI, not via an alternate shortcut.
  await page.getByRole('button',{name:'編輯合成來源卡'}).first().click();
  await page.locator('#destroyCard').click();
  await page.locator('#destroyInput').fill('銷毀');
  await page.evaluate(()=>{
   const proto=window.LivingObjectOutbox.ObjectOutbox.prototype;
   window.syntheticForget=proto.forget;
   proto.forget=async function(){throw Error('SYNTHETIC_IDB_PURGE_FAILURE')};
  });
  await page.locator('#confirmDestroy').click();
  await page.getByText('離線佇列清理尚未完成',{exact:false}).waitFor();
  const pending=await page.evaluate(id=>{
   const C=window.LivingObjectCraft;
   return {exists:C.read(localStorage).objects.some(c=>c.id===id),
    purge:C.pendingCardOutboxPurges(localStorage),original:localStorage.getItem(C.KEY)}
  },originalId);
  assert.equal(pending.exists,false);
  assert.deepEqual(pending.purge[0].ids,[originalId,copiedId]);
  proof.checks.push('injected IndexedDB failure after actual deletion yields durable journal, no false rollback message');
  const gated=await page.evaluate(async()=>{
   const Q=window.LivingObjectOutbox.ObjectOutbox,q=await Q.open();let code;
   try{await q.capture(localStorage)}catch(e){code=e.code}finally{q.close()}return code;
  });
  assert.equal(gated,'OBJECT_OUTBOX_DESTRUCTION_CLEANUP_PENDING');
  proof.checks.push('new capture is blocked until old deleted card is purged from queue');
  await page.reload({waitUntil:'load'});
  await page.waitForFunction(()=>!window.LivingObjectCraft.pendingCardOutboxPurges(localStorage).length);
  const cleaned=await page.evaluate(async({id,copy})=>{
   const C=window.LivingObjectCraft,Q=window.LivingObjectOutbox.ObjectOutbox,q=await Q.open();
   try{
    const rows=await q.pending(localStorage),objects=C.read(localStorage).objects;
    return {stale:rows.some(x=>x.object_id===id||x.object_id===copy),
     retainedCopy:objects.find(x=>x.id===copy),original:objects.some(x=>x.id===id),ledger:C.pendingCardOutboxPurges(localStorage)};
   }finally{q.close()}
  },{id:originalId,copy:copiedId});
  assert.equal(cleaned.original,false);assert.equal(cleaned.stale,false);
  assert.equal(cleaned.retainedCopy.id,copiedId);assert.equal(cleaned.retainedCopy.copyInfo,undefined);
  assert.equal(cleaned.ledger.length,0);
  proof.checks.push('reload automatically recovers IDB pending + versions without returning original card');
  await page.getByRole('button',{name:'編輯合成來源卡'}).click();
  await page.locator('#removePhoto').click();await page.locator('#confirmFormatCard').click();
  const formatted=await page.evaluate(id=>{
   const C=window.LivingObjectCraft,c=C.listCards(localStorage).find(c=>c.id===id);
   return {id:c.id,title:c.title,filled:C.inspectCard(localStorage,c.id).stats.filled};
  },copiedId);
  assert.equal(formatted.id,copiedId);assert.equal(formatted.title,'空白卡片');assert.equal(formatted.filled,11);
  proof.checks.push('complete card format clears content to 11 filled fields, preserves original copied ID');
  await page.locator('#createCard').click();
  const scanId=await page.evaluate(()=>window.LivingObjectCraft.listCards(localStorage).at(-1).id);
  await page.addScriptTag({url:origin+'/scan-bundle.js'});
  const scan=await page.evaluate(id=>{
   const C=window.LivingObjectCraft,B=window.LivingScanBundle;
   const card=C.listCards(localStorage).find(c=>c.id===id),allowed=[{kind:'book',id:'gaming',protected:false},{kind:'scene',id:'scene.workbench',protected:false}];
   const bound=B.bind(localStorage,{cardId:id,revision:card.revision,
    members:allowed.map(r=>({kind:r.kind,id:r.id}))},allowed);
   if(bound.scanContract.members.length!==2)throw Error('members mismatch');
   const result=B.release(localStorage,{cardId:id,revision:bound.revision,form:'card'},allowed);
   return {id:result.card.id,title:result.card.title,memberCount:result.members.length,claims:B.claims(localStorage).size};
  },scanId);
  assert.deepEqual(scan,{id:scanId,title:'空白卡片',memberCount:2,claims:0});
  proof.checks.push('2-object scan binding and release: same blank card, original members freed');
  await page.reload({waitUntil:'load'});
  await page.getByRole('button',{name:'編輯空白卡片'}).filter({hasText:scanId.slice(-8)}).click();
  await page.locator('#recycleCard').click();await page.locator('#confirmRecycle').click();
  assert.ok((await page.evaluate(()=>window.LivingObjectCraft.listRecycledCards(localStorage).length))>=1);
  await page.locator('#recycleBin summary').click();
  await page.locator('#recycleList button[aria-label^="永久銷毀"]').first().click();
  await page.locator('#destroyInput').fill('銷毀');await page.locator('#confirmDestroy').click();
  await page.waitForFunction(id=>!window.LivingObjectCraft.read(localStorage).objects.some(x=>x.id===id),scanId);
  proof.checks.push('recycle-bin permanent destroy removes recycled original after typed confirmation');
  assert.deepEqual(pageErrors,[],'no uncaught page errors');
  await context.close();proof.result='PASS';
 }finally{await browser.close()}
}
main().catch(e=>{proof.result='FAIL';proof.error=e.stack;process.exitCode=1}).finally(async()=>{
 const dest=path.resolve(process.env.NW_BROWSER_OUTPUT||'/tmp/nw-browser-proof');
 await fs.mkdir(dest,{recursive:true});
 await fs.writeFile(path.join(dest,'card-lifecycle.json'),JSON.stringify(proof,null,2)+'\n');
 console.log(JSON.stringify(proof,null,2));
});
