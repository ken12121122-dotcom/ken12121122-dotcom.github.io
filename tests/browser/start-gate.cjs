/* 開場畫面：真實瀏覽器 + 實際 index.html。
 * 驗證：開場只有標題與 ▶；更新公告與雲端解鎖不會自動跳出，只變成標題上的小提示；
 * ▶ 進入後同一分頁重新整理不再出現；深層連結直接進入。
 * Software GPU 政策只在本測試回應中略過，以驗證功能，不代表手機 GPU 效能。 */
const {chromium}=require('playwright'),fs=require('node:fs/promises'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..'),out=process.env.NW_BROWSER_OUTPUT||path.join(root,'test-evidence');
const mime={'.js':'text/javascript','.html':'text/html','.json':'application/json','.css':'text/css','.glb':'model/gltf-binary','.webp':'image/webp'};
const threeDir=process.env.NW_THREE_DIR||path.dirname(path.dirname(require.resolve('three')));
(async()=>{
 await fs.mkdir(out,{recursive:true});
 const server=http.createServer(async(req,res)=>{try{const u=new URL(req.url,'http://localhost'),name=path.resolve(root,'.'+decodeURIComponent(u.pathname));if(!name.startsWith(root+path.sep))throw Error('path');const data=await fs.readFile(name);res.writeHead(200,{'Content-Type':mime[path.extname(name)]||'application/octet-stream'});res.end(data)}catch{res.writeHead(404);res.end('missing')}});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const browser=await chromium.launch({headless:true,...(process.env.NW_BROWSER_EXE?{executablePath:process.env.NW_BROWSER_EXE}:{})});
 const report={phone:'NOT_TESTED',checks:[],errors:[]};
 let page;
 try{
  const context=await browser.newContext({viewport:{width:412,height:860},deviceScaleFactor:1,reducedMotion:'reduce'});
  await context.route(/https:\/\/(cdn\.jsdelivr\.net\/npm|unpkg\.com)\/three@0\.185\.0\/(.*)$/,async route=>{
   const rel=new URL(route.request().url()).pathname.replace(/^\/(npm\/)?three@0\.185\.0\//,'');
   try{await route.fulfill({contentType:rel.endsWith('.wasm')?'application/wasm':'text/javascript',body:await fs.readFile(path.join(threeDir,rel))})}catch{await route.fulfill({status:404,body:''})}
  });
  await context.route('**/render-capability.js?*',route=>route.fulfill({contentType:'text/javascript',body:'window.LivingRenderCapability={isSoftwareRenderer:()=>false};'}));
  await context.route('https://api.github.com/**',route=>route.abort());
  page=await context.newPage();page.on('pageerror',e=>report.errors.push(e.message));
  const base='http://127.0.0.1:'+server.address().port+'/living-library/live/index.html';
  const gateOpen=()=>page.evaluate(()=>!document.getElementById('startGate').hidden);
  const dialogOpen=id=>page.evaluate(i=>document.getElementById(i).open,id);

  // First visit: title screen only. The first-visit update notice waits behind the ✦ chip.
  await page.goto(base);
  await page.waitForFunction(()=>!!window.LivingScene,{timeout:60000});
  assert.equal(await gateOpen(),true);
  await page.waitForFunction(()=>!document.getElementById('startGateUpdateRow').hidden,{timeout:20000});
  await page.waitForTimeout(800);
  assert.equal(await dialogOpen('llUpdateDialog'),false,'update notice never pops by itself');
  assert.equal(await dialogOpen('worldVaultUnlockDialog'),false);
  assert.match(await page.locator('#startGateReady').innerText(),/準備完成/);
  assert.match(await page.locator('#startGateAvatar').innerText(),/守書人/);
  assert.ok(await page.evaluate(()=>getComputedStyle(document.querySelector('.bottom')).visibility==='hidden'),'HUD hidden behind the title');
  await page.screenshot({path:path.join(out,'start-gate.png')});
  report.checks.push('first visit: title, ready status, current avatar; update notice held as a chip; no modal');

  // The chip opens the real update notice on demand; closing it keeps the title screen.
  await page.locator('#startGateUpdate').click();
  assert.equal(await dialogOpen('llUpdateDialog'),true);
  await page.locator('#llUpdateDone').click();
  assert.equal(await gateOpen(),true);
  report.checks.push('✦ chip opens the existing update dialog on demand');

  // A saved cloud credential is a ☁ chip, never a startup password prompt.
  await page.evaluate(async()=>{await LivingWorldCredentialVault.create().remember('github_pat_SYNTHETIC_BROWSER_ONLY','valid local password 123')});
  await page.reload();
  await page.waitForFunction(()=>!!window.LivingScene,{timeout:60000});
  assert.equal(await gateOpen(),true,'not started yet in this session, so the title shows again');
  await page.waitForFunction(()=>!document.getElementById('startGateVaultRow').hidden,{timeout:10000});
  await page.waitForTimeout(800);
  assert.equal(await dialogOpen('worldVaultUnlockDialog'),false,'no startup password prompt');
  await page.screenshot({path:path.join(out,'start-gate-vault-chip.png')});
  await page.locator('#startGateVault').click();
  assert.equal(await dialogOpen('worldVaultUnlockDialog'),true,'☁ chip opens the unlock dialog only when asked');
  await page.locator('#worldVaultUnlockSkip').click();
  assert.equal(await gateOpen(),true);
  report.checks.push('saved credential: ☁ chip only; unlock dialog opens on tap; skip keeps title');

  // ▶ enters; Enter does not open a book behind the title.
  await page.locator('#startGateGo').click();
  await page.waitForFunction(()=>document.getElementById('startGate').hidden);
  assert.equal(await page.evaluate(()=>document.body.classList.contains('start-gate-open')),false);
  assert.equal(await page.evaluate(()=>sessionStorage.getItem('nw-start-gate-started-v1')),'1');
  assert.ok(await page.evaluate(()=>getComputedStyle(document.querySelector('.bottom')).visibility==='visible'));
  await page.screenshot({path:path.join(out,'start-gate-entered.png')});
  report.checks.push('▶ enters the library and restores the HUD');

  // Same tab: an auto-update reload goes straight in, without the title or a password prompt.
  await page.reload();
  await page.waitForFunction(()=>!!window.LivingScene,{timeout:60000});
  await page.waitForTimeout(800);
  assert.equal(await gateOpen(),false);
  assert.equal(await dialogOpen('worldVaultUnlockDialog'),false);
  report.checks.push('same session reload: no title, no password prompt');

  // New tab, deep link: straight to the book.
  const tab=await context.newPage();tab.on('pageerror',e=>report.errors.push(e.message));
  await tab.goto(base+'?book=gaming');
  await tab.waitForFunction(()=>!!window.LivingStartGate,{timeout:30000});
  assert.equal(await tab.evaluate(()=>document.getElementById('startGate').hidden),true);
  await tab.close();
  report.checks.push('deep link ?book= skips the title');

  // New tab: Enter starts from the keyboard.
  const kb=await context.newPage();kb.on('pageerror',e=>report.errors.push(e.message));
  await kb.goto(base);
  await kb.waitForFunction(()=>!!window.LivingStartGate?.open,{timeout:30000});
  await kb.keyboard.press('Enter');
  await kb.waitForFunction(()=>document.getElementById('startGate').hidden);
  assert.equal(await kb.evaluate(()=>document.getElementById('drawerWrap').classList.contains('open')),false,'Enter did not open a book behind the title');
  await kb.close();
  report.checks.push('Enter starts the game and does not open a book');

  assert.deepEqual(report.errors,[],'no page errors');
  console.log(JSON.stringify(report,null,1));
 }catch(e){
  if(page)await page.screenshot({path:path.join(out,'start-gate-failure.png')}).catch(()=>{});
  console.error(JSON.stringify(report,null,1));throw e;
 }finally{await browser.close();server.close()}
})().catch(e=>{console.error(e);process.exit(1)});
