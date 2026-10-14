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
  assert.equal(await p.locator('#bookList button').count(),14);
  assert.equal(await p.locator('#walkPad').isVisible(),false);
  report.checks.push({id:'forced_engine_failure_fallback',result:'PASS',books:14,joystick_hidden:true});
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
