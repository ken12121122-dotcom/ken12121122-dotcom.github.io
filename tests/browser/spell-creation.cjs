// Actual candidate book/iframe UI and real IndexedDB; synthetic creations only.
const {chromium}=require('playwright');
const fs=require('node:fs/promises'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..'),origin='https://ken12121122-dotcom.github.io';
const proof={schema:'nw-spell-authoring-browser-v1',observed_at:new Date().toISOString(),result:'NOT_TESTED',checks:[],scope:'Local intercepted candidate; no deployment, 3D/device/voice or remote acceptance'};
async function main(){
 const browser=await chromium.launch({headless:true});
 try{
  const ctx=await browser.newContext({viewport:{width:412,height:915},reducedMotion:'reduce'});
  await ctx.route('**/*',async r=>{
   const u=new URL(r.request().url());if(u.origin!==origin)return r.abort();
   const name=path.resolve(root,'.'+u.pathname);if(!name.startsWith(root+path.sep))return r.abort();
   try{await r.fulfill({contentType:name.endsWith('.js')?'text/javascript':name.endsWith('.json')?'application/json':'text/html',body:await fs.readFile(name)})}catch{await r.fulfill({status:404,body:'fixture missing'})}
  });
  const page=await ctx.newPage();page.on('dialog',d=>d.accept());
  await page.goto(origin+'/living-library/live/index.html?book=gaming');
  await page.locator('#llUpdateDialog[open]').waitFor();await page.locator('#llUpdateDone').click();
  await page.locator('.codex-slot[data-card-id="public.gaming.spell.creation"]').click();
  let frame=page.frameLocator('#readerFrame');await frame.locator('#fields:not([disabled])').waitFor();
  assert.equal(new URL(page.url()).pathname,'/living-library/live/index.html');assert.equal(await frame.locator('#spellName').inputValue(),'歸書術');
  for(const b of await frame.locator('button').all()){assert.doesNotMatch(await b.innerText(),/[\p{L}\p{N}]/u);assert.ok(await b.getAttribute('aria-label'));const box=await b.boundingBox();assert.ok(box.width>=44&&box.height>=44)}
  proof.checks.push({case:'existing Game Book opens icon-only authoring in the same reader',result:'PASS'});
  await page.evaluate(()=>{localStorage.setItem('living-library-player-objects-v1','synthetic-source-sentinel');localStorage.setItem('synthetic-gba-save','preserve')});
  await frame.locator('#spellName').fill('合成歸書草稿');await frame.locator('#spellDescription').fill('合成測試用途');await frame.locator('#incantations').fill('萬書歸位\n回原書架');await frame.locator('#saveDraft').click();
  await frame.locator('#status').filter({hasText:'本機已保存'}).waitFor();assert.equal(await frame.locator('#revision').innerText(),'本機版本 1');
  const id=await frame.locator('#savedDrafts').inputValue();assert.match(id,/^spell\.local\./);
  const actual=page.frames().find(f=>f.url().endsWith('/spell-creation.html'));
  async function rows(f=actual){return f.evaluate(async()=>{const s=await LivingSpellStore.SpellStore.open(LivingSpellDraft.catalog);try{return {list:await s.list(),queue:await s.pending()}}finally{s.close()}})}
  let data=await rows();assert.equal(data.list.length,1);assert.equal(data.queue.length,1);assert.equal(data.list[0].card.enabled,false);assert.equal(data.list[0].card.spell_id,id);assert.equal(data.queue[0].content_hash,data.list[0].content_hash);
  assert.equal(await actual.evaluate(()=>typeof LivingSpellDraft.catalog.run),'undefined');
  proof.checks.push({case:'real form saves disabled spell and pending intent in one transaction without fake handlers',result:'PASS'});
  await page.locator('#readerBack').click();await page.locator('.codex-slot[data-card-id="public.gaming.spell.creation"]').click();frame=page.frameLocator('#readerFrame');await frame.locator('#fields:not([disabled])').waitFor();
  await frame.locator('#savedDrafts').selectOption(id);await frame.locator('#loadDraft').click();await frame.locator('#status').filter({hasText:'已讀取本機草稿'}).waitFor();assert.equal(await frame.locator('#spellName').inputValue(),'合成歸書草稿');assert.equal(await frame.locator('#incantations').inputValue(),'萬書歸位\n回原書架');
  proof.checks.push({case:'closing and reopening the book restores the same saved identity and metadata',result:'PASS'});
  const editor=page.frames().find(f=>f.url().endsWith('/spell-creation.html'));let editorOutbound=0;page.on('request',r=>{if(r.frame()===editor&&!r.url().startsWith(origin+'/living-library/live/'))editorOutbound++});
  await ctx.setOffline(true);await frame.locator('#spellName').fill('離線修改');await frame.locator('#saveDraft').click();await frame.locator('#revision').filter({hasText:'本機版本 2'}).waitFor();data=await rows(editor);assert.equal(data.queue.length,1);assert.equal(data.queue[0].payload.name,'離線修改');
  await frame.locator('#saveDraft').click();await frame.locator('#status').filter({hasText:'本機已保存'}).waitFor();data=await rows(editor);assert.equal(data.list[0].local_revision,2);assert.equal(data.queue.length,1);await ctx.setOffline(false);
  proof.checks.push({case:'offline editing and repeated save retain one ID and one coalesced intent',result:'PASS'});
  await frame.locator('#incantations').fill('重複\n重複');await frame.locator('#saveDraft').click();await frame.locator('#status[data-state=error]').waitFor();data=await rows(editor);assert.equal(data.list[0].local_revision,2);await frame.locator('#incantations').fill('萬書歸位\n回原書架');
  proof.checks.push({case:'invalid duplicate incantations do not write or claim success',result:'PASS'});
  await editor.evaluate(()=>{window.originalPut=IDBObjectStore.prototype.put;IDBObjectStore.prototype.put=function(...args){if(this.name==='outbox')throw Error('synthetic quota');return originalPut.apply(this,args)}});
  await frame.locator('#spellName').fill('儲存失敗仍保留文字');await frame.locator('#saveDraft').click();await frame.locator('#status').filter({hasText:'保存失敗'}).waitFor();await editor.evaluate(()=>{IDBObjectStore.prototype.put=originalPut});data=await rows(editor);assert.equal(data.list[0].card.name,'離線修改');assert.equal(data.queue[0].local_revision,2);assert.equal(await frame.locator('#spellName').inputValue(),'儲存失敗仍保留文字');
  proof.checks.push({case:'quota failure rolls back card and queue while retaining editable text',result:'PASS'});
  await editor.evaluate(async id=>{const s=await LivingSpellStore.SpellStore.open(LivingSpellDraft.catalog);try{const row=await s.get(id);await s.save({...row.card,name:'另一頁新版'},{expected_revision:2})}finally{s.close()}},id);
  await frame.locator('#saveDraft').click();await frame.locator('#status').filter({hasText:'另一個頁面已保存新版'}).waitFor();data=await rows(editor);assert.equal(data.list[0].local_revision,3);assert.equal(data.list[0].card.name,'另一頁新版');assert.equal(await frame.locator('#spellName').inputValue(),'儲存失敗仍保留文字');
  proof.checks.push({case:'stale editor cannot overwrite a separately committed revision',result:'PASS'});
  await frame.locator('#loadDraft').click();await frame.locator('#status').filter({hasText:'已讀取本機草稿'}).waitFor();assert.equal(await frame.locator('#spellName').inputValue(),'另一頁新版');
  const saved=await page.evaluate(()=>({objects:localStorage.getItem('living-library-player-objects-v1'),gba:localStorage.getItem('synthetic-gba-save')}));assert.equal(saved.objects,'synthetic-source-sentinel');assert.equal(saved.gba,'preserve');assert.equal(editorOutbound,0);assert.equal(await editor.evaluate(()=>typeof LivingSpellExecutor),'undefined');
  proof.checks.push({case:'source ObjectCraft and unrelated saves preserved; no editor external transport or executor',result:'PASS'});
  const out=path.resolve(process.env.NW_BROWSER_OUTPUT||'/tmp/nw-browser-proof');await fs.mkdir(out,{recursive:true});await editor.locator('body').screenshot({path:path.join(out,'spell-creation.png')});
  await page.reload();await page.locator('#fallback.show').waitFor();
  if(await page.locator('#llUpdateDialog[open]').isVisible())await page.locator('#llUpdateDone').click();
  await page.locator('.codex-slot[data-card-id="public.gaming.spell.creation"]').click();frame=page.frameLocator('#readerFrame');await frame.locator('#fields:not([disabled])').waitFor();
  await frame.locator('#savedDrafts').selectOption(id);await frame.locator('#loadDraft').click();await frame.locator('#status').filter({hasText:'已讀取本機草稿'}).waitFor();assert.equal(await frame.locator('#spellName').inputValue(),'另一頁新版');assert.equal(await frame.locator('#revision').innerText(),'本機版本 3');
  proof.checks.push({case:'full library reload retains exact spell identity and latest revision',result:'PASS'});
  await frame.locator('#newDraft').click();await frame.locator('#spellName').fill('第二張合成草稿');await frame.locator('#saveDraft').click();await frame.locator('#status').filter({hasText:'本機已保存'}).waitFor();
  const secondId=await frame.locator('#savedDrafts').inputValue();assert.notEqual(secondId,id);const reopened=page.frames().find(f=>f.url().endsWith('/spell-creation.html'));data=await rows(reopened);assert.equal(data.list.length,2);assert.equal(data.queue.length,2);assert.equal(data.list.find(r=>r.spell_id===id).local_revision,3);
  proof.checks.push({case:'new draft has a separate stable ID without replacing an existing spell',result:'PASS'});
  const unavailable=await ctx.newPage();await unavailable.addInitScript(()=>Object.defineProperty(window,'indexedDB',{value:undefined}));await unavailable.goto(origin+'/living-library/live/spell-creation.html');await unavailable.locator('#status').filter({hasText:'無法開啟本機資料'}).waitFor();assert.equal(await unavailable.locator('#saveDraft').isDisabled(),true);assert.equal(await unavailable.evaluate(()=>localStorage.getItem('living-library-player-objects-v1')),'synthetic-source-sentinel');
  proof.checks.push({case:'unavailable IndexedDB disables saving without deleting or fabricating success',result:'PASS'});
  await ctx.close();proof.result='PASS';
 }finally{await browser.close()}
}
main().catch(e=>{proof.result='FAIL';proof.error=e.stack;process.exitCode=1}).finally(async()=>{const out=path.resolve(process.env.NW_BROWSER_OUTPUT||'/tmp/nw-browser-proof');await fs.mkdir(out,{recursive:true});await fs.writeFile(path.join(out,'spell-creation.json'),JSON.stringify(proof,null,2)+'\n');console.log(JSON.stringify(proof,null,2))});
