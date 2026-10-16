// Candidate source is intercepted locally. This does not deploy or modify Pages.
const {chromium}=require('playwright');
const fs=require('node:fs/promises');
const path=require('node:path');
const assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..');
const origin='https://ken12121122-dotcom.github.io';
const catalog=require('../../living-library/live/public-catalog.js').buildCatalog();
const contract=require('../../living-library/public-capabilities.json');
const out=path.resolve(process.env.NW_BROWSER_OUTPUT||'/tmp/nw-browser-proof');
const report={schema:'nw-p0-browser-proof-v1',commit:process.env.GITHUB_SHA||null,
 observed_at:new Date().toISOString(),isolation:'Browser intercept serves candidate repository bytes; no deployment',checks:[]};
const mime={'.html':'text/html','.js':'text/javascript','.json':'application/json','.css':'text/css','.svg':'image/svg+xml','.png':'image/png','.wasm':'application/wasm'};
async function context(browser,forcedFallback){
 const ctx=await browser.newContext({viewport:{width:412,height:915},reducedMotion:'reduce'});
 await ctx.route(origin+'/**',async route=>{
  const url=new URL(route.request().url());
  let name=path.resolve(root,'.'+decodeURIComponent(url.pathname));
  if(!name.startsWith(root+path.sep))return route.abort();
  try{
   if((await fs.stat(name)).isDirectory())name=path.join(name,'index.html');
   await route.fulfill({status:200,contentType:mime[path.extname(name)]||'application/octet-stream',body:await fs.readFile(name)});
  }catch{await route.fulfill({status:404,body:'Candidate fixture: file not found'});}
 });
 if(forcedFallback){
  await ctx.route('https://cdn.jsdelivr.net/**',route=>route.abort());
  await ctx.route('https://unpkg.com/**',route=>route.abort());
 }
 // No private data or credentials in this runner; external service calls are not tests.
 await ctx.route('https://api.github.com/**',route=>route.abort());
 return ctx;
}
async function dismissUpdate(page){
 const dialog=page.locator('#llUpdateDialog');
 // First-load fingerprint check is asynchronous; wait for it to settle.
 await page.waitForFunction(()=>document.querySelector('#llUpdateDialog')?.open,{timeout:15000});
 if(await dialog.isVisible())await page.locator('#llUpdateDone').click();
}
async function main(){
 await fs.mkdir(out,{recursive:true});
 const browser=await chromium.launch({headless:true,...(process.env.CHROME_PATH?{executablePath:process.env.CHROME_PATH}:{})});
 try{
  // Observe the unmodified renderer policy. Software GPU rejection stays enabled.
  const ctx=await context(browser,false),page=await ctx.newPage();
  const logs=[];page.on('console',msg=>{if(msg.type()==='error')logs.push(msg.text())});
  await page.goto(origin+'/living-library/live/index.html',{waitUntil:'domcontentloaded'});
  try{await page.waitForFunction(()=>window.LivingScene||document.querySelector('#fallback')?.classList.contains('show'),{timeout:45000})}
  catch{logs.push('3D startup did not settle within the 45s observation budget')}
  const observed=await page.evaluate(()=>({ready:!!window.LivingScene,diagnostic:document.querySelector('#fallbackDiag').textContent,release:document.querySelector('meta[name="living-library-release"]').content}));
  const sceneFailure=!observed.ready&&/SCENE_BUILD|SCENE_RESIZE|FIRST_RENDER/.test(observed.diagnostic);
  if(sceneFailure)process.exitCode=1;
  report.checks.push({id:'actual_3d_startup',result:sceneFailure?'FAIL':observed.ready?'PASS':'NOT_TESTED',...observed,logs,
   limitation:observed.ready?'First render only; gestures and Android not verified':'Renderer unavailable under unchanged production policy; not proof of a phone defect'});
  await page.screenshot({path:path.join(out,'startup.png')});
  await ctx.close();
  const fallback=await context(browser,true),p=await fallback.newPage();
  await p.goto(origin+'/living-library/live/index.html',{waitUntil:'domcontentloaded'});
  await p.locator('#fallback.show').waitFor({timeout:15000});
  await dismissUpdate(p);
  for(const button of await p.locator('button[id]').all()){
   assert.ok((await button.getAttribute('aria-label'))?.trim(),'Missing accessible action name');
   assert.doesNotMatch(await button.textContent(),/[\p{L}\p{N}]/u,'Action button contains visible words');
  }
  for(const [id,icon,label] of [['prevBtn','‹','前一本'],['openBtn','▤','展開書頁'],['nextBtn','›','下一本']]){
   const button=p.locator('#'+id);
   assert.equal(await button.innerText(),icon);
   assert.equal(await button.getAttribute('aria-label'),label);
   const bounds=await button.boundingBox();
   assert.ok(bounds.width>=44&&bounds.height>=44,'Touch target shrank: '+id);
  }
  const firstTitle=await p.locator('#focusTitle').innerText();
  await p.getByRole('button',{name:'下一本',exact:true}).click();
  assert.notEqual(await p.locator('#focusTitle').innerText(),firstTitle);
  await p.getByRole('button',{name:'前一本',exact:true}).click();
  assert.equal(await p.locator('#focusTitle').innerText(),firstTitle);
  await p.getByRole('button',{name:'展開書頁',exact:true}).click();
  await p.getByRole('button',{name:'關閉書頁',exact:true}).click();
  report.checks.push({id:'icon_first_controls',result:'PASS',scope:'Icon-only labels retain accessible action names, >=44px touch targets and previous/next/open/close behavior; content names preserved.'});
  assert.equal(await p.locator('.brand').innerText(),'✧');
  const cleanToggle=p.locator('#cleanViewToggle');
  await p.evaluate(()=>localStorage.setItem('nw-clean-save-sentinel','preserve'));
  await cleanToggle.click();
  assert.equal(await p.locator('#catalog').isVisible(),false);
  assert.equal(await p.locator('.bottom').isVisible(),false);
  assert.equal(await cleanToggle.getAttribute('aria-label'),'顯示操作介面');
  assert.equal(await p.locator('#fallbackOpen').isVisible(),true);
  await p.reload({waitUntil:'domcontentloaded'});
  await p.waitForFunction(()=>document.body.classList.contains('clean-view'));
  await p.locator('#fallback.show').waitFor();
  await dismissUpdate(p);
  // Restore remains available on a phone, without relying on a keyboard.
  await p.locator('#cleanViewToggle').click();
  assert.equal(await p.locator('#catalog').isVisible(),true);
  assert.equal(await p.locator('.bottom').isVisible(),true);
  assert.equal(await p.evaluate(()=>localStorage.getItem('nw-clean-save-sentinel')),'preserve');
  await p.locator('#cleanViewToggle').click();
  await p.keyboard.press('Escape');
  assert.equal(await p.locator('#cleanViewToggle').getAttribute('aria-pressed'),'false');
  report.checks.push({id:'clean_view_restore_reload',result:'PASS',scope:'Brand text removed; panels toggle/reload/restore/Escape; fallback and unrelated storage preserved. No 3D/device acceptance.'});
  assert.equal(await p.locator('#bookList button').count(),15);
  assert.equal(await p.locator('#walkPad').isVisible(),false);
  report.checks.push({id:'forced_engine_failure_fallback',result:'PASS',books:15,joystick_hidden:true});
  for(const capability of contract.capabilities){
   const b=catalog.books.find(b=>b.id===capability.book);
   // The mobile catalog is collapsed by default.
   if(await p.locator('#catalogHead').getAttribute('aria-expanded')==='false')await p.locator('#catalogHead').click();
   await p.getByRole('button',{name:'選取'+b.name,exact:true}).click();
   // Selecting a book in the fallback list already opens its drawer.
   if(!await p.locator('#drawerWrap').evaluate(el=>el.classList.contains('open')))await p.locator('#fallbackOpen').click();
   await p.locator('[data-card-id="'+capability.id+'"]').click();
   await p.locator('#readerBack').waitFor({state:'visible'});
   if(capability.external){
    assert.equal(await p.locator('#sourcePage a').getAttribute('href'),capability.url);
   }else{
    const frame=p.locator('#readerFrame');
    // Live has its own relative copies (e.g. ./gpt-library-bridge.html).
    // Resolve against the actual live document, not the domain root/dev entry.
    const expected=new URL(catalog.cards[capability.id].url,p.url());
    assert.equal(new URL(await frame.getAttribute('src'),p.url()).href,expected.href);
    const child=await frame.elementHandle().then(el=>el.contentFrame());
    await child.waitForURL(url=>url.origin===expected.origin&&url.pathname===expected.pathname);
    await child.waitForLoadState('domcontentloaded');
    assert.ok((await child.locator('body').innerText()).trim().length>0,capability.id+' rendered no content');
   }
   await p.locator('#readerBack').click();
   assert.equal(await p.locator('#readerShell').isVisible(),false);
   assert.equal(await p.locator('#readerFrame').getAttribute('src'),null);
   await p.locator('#closeDrawer').click();
   report.checks.push({id:capability.id,result:'PASS',scope:capability.external?'External link target only; no sign-in':'Public card click, rendered iframe and return; underlying application actions not exercised'});
  }
  // Focus presentation hides obstructing panels without overwriting the user's clean-view preference.
  const cleanBefore=await p.evaluate(()=>localStorage.getItem('nw-library-clean-view-v1'));
  await p.evaluate(()=>document.body.classList.add('atlas-focused'));
  for(const selector of ['#catalog','.bottom','.top','#cleanViewToggle'])assert.equal(await p.locator(selector).isVisible(),false);
  await p.evaluate(()=>document.body.classList.remove('atlas-focused'));
  assert.equal(await p.locator('#catalog').isVisible(),true);assert.equal(await p.locator('.bottom').isVisible(),true);
  assert.equal(await p.evaluate(()=>localStorage.getItem('nw-library-clean-view-v1')),cleanBefore);
  report.checks.push({id:'atlas_focus_overlay_visibility',result:'PASS',scope:'Actual CSS focus class hides catalog/navigation and restores them without changing clean-view preference; geometry/state tested separately.'});
  // End-to-end regression: real System Book -> same-origin iframe slider ->
  // guarded parent handler -> actual shared preference object -> acknowledged value.
  if(await p.locator('#catalogHead').getAttribute('aria-expanded')==='false')await p.locator('#catalogHead').click();
  await p.getByRole('button',{name:'選取系統之書',exact:true}).click();
  if(!await p.locator('#drawerWrap').evaluate(el=>el.classList.contains('open')))
   await p.locator('#fallbackOpen').click();
  await p.locator('[data-card-id="public.system.controls"]').dispatchEvent('click');
  const prefsFrame=await p.locator('#readerFrame').elementHandle().then(el=>el.contentFrame());
  await prefsFrame.waitForURL(url=>url.pathname.endsWith('/living-library/live/system-controls.html'));
  await prefsFrame.locator('#cardSummonDuration').waitFor();
  await prefsFrame.locator('#cardSummonDuration').focus();
  await prefsFrame.locator('#cardSummonDuration').press('End');
  await p.waitForFunction(()=>window.LivingLibrary?.controls?.cardSummonDuration===4500);
  await prefsFrame.waitForFunction(()=>document.querySelector('#prefStatus')?.textContent.includes('無 3D 場景'));
  assert.equal(await p.evaluate(()=>JSON.parse(localStorage.getItem('living-library-controls-v1')).cardSummonDuration),4500);
  await prefsFrame.locator('#cardSize').focus();
  await prefsFrame.locator('#cardSize').press('End');
  await p.waitForFunction(()=>window.LivingLibrary?.controls?.cardSize===1.2);
  await prefsFrame.locator('#cardOrbitSpeed').focus();
  await prefsFrame.locator('#cardOrbitSpeed').press('Home');
  await p.waitForFunction(()=>window.LivingLibrary?.controls?.cardOrbitSpeed===0);
  for(const [key,value,press] of [['atlasDistance',.8,'Home'],['atlasHeight',3.6,'End'],['atlasSize',1.4,'End'],['atlasSeparation',1.8,'End'],['atlasFocusDistance',.75,'Home'],['atlasCardSize',.65,'Home']]){
   await prefsFrame.locator('#'+key).focus();await prefsFrame.locator('#'+key).press(press);
   await p.waitForFunction(([k,v])=>window.LivingLibrary?.controls?.[k]===v,[key,value]);
   assert.equal(await p.evaluate(k=>JSON.parse(localStorage.getItem('living-library-controls-v1'))[k],key),value);
  }
  const state=await p.evaluate(()=>{
   const value=window.LivingLibrary.controls;
   return {size:value.cardSize,orbit:value.cardOrbitDistance,speed:value.cardOrbitSpeed,
    duration:value.cardSummonDuration,bookCount:window.LivingLibrary.books.length};
  });
  assert.deepEqual(state,{size:1.2,orbit:1,speed:0,duration:4500,bookCount:15});
  await p.locator('#readerBack').click();
  await p.locator('#closeDrawer').click();
  await p.getByRole('button',{name:'選取系統之書',exact:true}).click();
  if(!await p.locator('#drawerWrap').evaluate(el=>el.classList.contains('open')))
   await p.locator('#fallbackOpen').click();
  await p.locator('[data-card-id="public.system.controls"]').dispatchEvent('click');
  const restoredFrame=await p.locator('#readerFrame').elementHandle().then(el=>el.contentFrame());
  await restoredFrame.waitForURL(url=>url.pathname.endsWith('/living-library/live/system-controls.html'));
  assert.equal(await restoredFrame.locator('#cardSummonDuration').inputValue(),'4500');
  await restoredFrame.locator('#resetCardParams').click();
  await p.waitForFunction(()=>window.LivingLibrary?.controls?.cardSize===1&&window.LivingLibrary?.controls?.cardSummonDuration===2850&&window.LivingLibrary?.controls?.cardOrbitSpeed===1);
  await restoredFrame.waitForFunction(()=>document.querySelector('#prefStatus')?.textContent.includes('無 3D 場景'));
  assert.equal(await restoredFrame.locator('#atlasDistance').inputValue(),'0.8');
  assert.equal(await p.evaluate(()=>LivingLibrary.controls.atlasSize),1.4,'card reset preserves atlas settings');
  await restoredFrame.locator('#resetAtlasParams').click();
  await p.waitForFunction(()=>LivingLibrary.controls.atlasDistance===1.35&&LivingLibrary.controls.atlasSize===1);
  report.checks.push({id:'system_book_3d_preferences_ack_fallback',result:'PASS',
   scope:'Actual system card iframe, slider changes, parent validated action, exact limits, confirmed fallback message, persistence, re-open and reset; no GPU.'});
  await p.locator('#readerBack').click();
  await p.locator('#closeDrawer').click();
  await p.getByRole('button',{name:'選取遊戲之書',exact:true}).click();
  if(!await p.locator('#drawerWrap').evaluate(el=>el.classList.contains('open')))await p.locator('#fallbackOpen').click();
  // Existing Game Book card links to interactive rules, not the world atlas.
  await p.locator('[data-card-id="public.gaming.atlas"]').dispatchEvent('click');
  const rulesFrame=await p.locator('#readerFrame').elementHandle().then(el=>el.contentFrame());
  await rulesFrame.waitForURL(url=>url.pathname.endsWith('/living-library/live/atlas-rulebook-review.html'));
  assert.match(await rulesFrame.locator('h1').textContent(),/一本圖鑑，一個存在/);
  assert.ok(await rulesFrame.locator('.rule').count()>0,'interactive rules available inside reader');

  // The published rule preview remains unchanged; its new issue register is a local-only candidate.
  await rulesFrame.locator('[data-tab="progress"]').click();
  await rulesFrame.locator('#progressRows tr').first().waitFor({timeout:15000});
  assert.equal(await rulesFrame.locator('#progressRows tr').count(),15);
  // P1 preview only prepares an ordinary card upgrade application, before numbering.
  await rulesFrame.locator('#atlasUpgradeCardId').fill('local.card.abc-123');
  await rulesFrame.locator('#atlasUpgradeTitle').fill('初代法陣');
  await rulesFrame.locator('#atlasUpgradeReason').fill('這張卡具有獨特的世界意義，應先審查是否有資格永久收錄。');
  await rulesFrame.locator('#atlasCheckUpgrade').click();
  const upgradeResult=JSON.parse(await rulesFrame.locator('#atlasUpgradeJson').textContent());
  assert.equal(upgradeResult.application_ready,true);
  assert.equal(upgradeResult.stage,'upgrade_application_draft_ready');
  assert.equal(upgradeResult.card_id,'local.card.abc-123');
  assert.equal(upgradeResult.review_status,'generated');
  assert.equal(upgradeResult.upgrade_executed,false);
  assert.equal(upgradeResult.l2_certified,false);
  assert.equal(upgradeResult.identity_issued,false);
  assert.equal(upgradeResult.permanent_number,null);
  assert.equal(upgradeResult.may_assign_number,false);
  report.checks.push({id:'atlas_p1_upgrade_application_before_number',result:'PASS',
   scope:'Ordinary card upgrade draft can be checked, while approval, L2 certification and permanent numbering remain disabled.'});
  const issueRow=rulesFrame.locator('#progressRows tr').filter({hasText:'ATLAS-010'});
  await issueRow.locator('select').selectOption('in_progress');
  await issueRow.locator('textarea').fill('候選註記：等待動態分頁整合；此項尚未正式實作。');
  await issueRow.locator('textarea').press('Tab');
  const stored=await rulesFrame.evaluate(()=>JSON.parse(localStorage.getItem('nw-atlas-progress-local-v1')));
  assert.equal(stored.edits['ATLAS-010'].status,'in_progress');
  assert.match(stored.edits['ATLAS-010'].note,/等待動態分頁整合/);
  await rulesFrame.locator('#progressReload').click();
  await rulesFrame.locator('#progressRows tr').first().waitFor();
  assert.equal(await rulesFrame.locator('#progressRows tr').filter({hasText:'ATLAS-010'}).locator('select').inputValue(),'in_progress');
  await rulesFrame.waitForFunction(()=>document.querySelector('#progressNotice')?.textContent.includes('只存本機'),{timeout:15000});
  assert.match(await rulesFrame.locator('#progressNotice').innerText(),/只存本機/);
  report.checks.push({id:'atlas_progress_candidate_local_persistence',result:'PASS',
   scope:'Evidence ledger loads 15 rows; local-only status and note persist through refetch; source records and formal approvals remain unchanged.'});

  await p.locator('#readerBack').click();
  assert.equal(await p.locator('#drawerTitle').textContent(),'遊戲之書');
  report.checks.push({id:'game_atlas_card_opens_rules_preview',result:'PASS',scope:'Existing gaming card embeds draft page and returns; created card book still accesses its 18-slot 2D reader.'});
  await p.locator('[data-card-id="public.gaming.book.creation"]').dispatchEvent('click');
  const forge=await p.locator('#readerFrame').elementHandle().then(el=>el.contentFrame());
  await forge.waitForURL(url=>url.pathname.endsWith('/living-library/live/book-creation.html'));
  const booksBefore=await p.evaluate(()=>LivingLibrary.books.length);
  await forge.locator('#bookType').selectOption('atlas');await forge.locator('#name').fill('測試九加九卡冊');
  await forge.getByRole('button',{name:'施放書本創造術',exact:true}).click();
  await p.waitForFunction(n=>LivingLibrary.books.length===n+1,booksBefore);
  const forgedId=await p.evaluate(()=>LivingObjectCraft.listBooks(localStorage).find(b=>b.title==='測試九加九卡冊')?.id);
  assert.ok(forgedId);assert.equal(await p.evaluate(id=>LivingLibrary.books.find(b=>b.id===id).openingMode,forgedId),'atlas');
  await forge.getByRole('button',{name:'開啟測試九加九卡冊',exact:true}).click();
  await p.waitForFunction(()=>document.querySelector('#drawerTitle').textContent==='測試九加九卡冊');
  await p.locator('[data-card-id="public.gaming.atlas"]').dispatchEvent('click');
  const forgedFrame=await p.locator('#readerFrame').elementHandle().then(el=>el.contentFrame());
  await forgedFrame.waitForURL(url=>url.searchParams.get('book')===forgedId);
  assert.equal(await forgedFrame.locator('.slot').count(),18);assert.equal(await forgedFrame.locator('#bookPicker').inputValue(),forgedId);
  await p.locator('#readerBack').click();await p.locator('#closeDrawer').click();
  report.checks.push({id:'book_forge_template_collection',result:'PASS',scope:'Real template choice creates an independently identified 18-slot card book; its own reader selects that exact container.'});
  // Placement preferences must never wipe unrelated player data on reload.
  await p.evaluate(()=>localStorage.setItem('nw-p0-save-sentinel','synthetic-preserve-me'));
  await p.reload({waitUntil:'domcontentloaded'});
  assert.equal(await p.evaluate(()=>localStorage.getItem('nw-p0-save-sentinel')),'synthetic-preserve-me');
  report.checks.push({id:'unrelated_storage_preserved',result:'PASS',scope:'Synthetic sentinel; not a real GBA-save test'});
  await fallback.close();
 }finally{await browser.close()}
}
main().catch(error=>{report.checks.push({id:'runner_failure',result:'FAIL',error:error.stack});process.exitCode=1})
 .finally(async()=>{await fs.mkdir(out,{recursive:true});await fs.writeFile(path.join(out,'report.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report,null,2))});
