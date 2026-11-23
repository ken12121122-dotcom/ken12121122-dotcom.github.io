/* 換衣世界：真實瀏覽器 + 實際 index.html + 實際 Anna GLB。
 * 語音以假的 SpeechRecognition 注入「百面相」文字；這不是麥克風或手機驗收。
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
 const report={phone:'NOT_TESTED',microphone:'NOT_TESTED (fake SpeechRecognition transcript)',checks:[],errors:[]};
 let page;
 try{
  const context=await browser.newContext({viewport:{width:412,height:860},deviceScaleFactor:1,reducedMotion:'reduce'});await context.addInitScript(()=>{try{sessionStorage.setItem('nw-start-gate-started-v1','1')}catch{}}); // title screen is covered by start-gate.cjs

  // Same pinned three@0.185.0 files, served from the local package by their CDN path.
  await context.route(/https:\/\/(cdn\.jsdelivr\.net\/npm|unpkg\.com)\/three@0\.185\.0\/(.*)$/,async route=>{
   const rel=new URL(route.request().url()).pathname.replace(/^\/(npm\/)?three@0\.185\.0\//,'');
   try{await route.fulfill({contentType:rel.endsWith('.wasm')?'application/wasm':'text/javascript',body:await fs.readFile(path.join(threeDir,rel))})}catch{await route.fulfill({status:404,body:''})}
  });
  await context.route('**/render-capability.js?*',route=>route.fulfill({contentType:'text/javascript',body:'window.LivingRenderCapability={isSoftwareRenderer:()=>false};'}));
  await context.route('https://api.github.com/**',route=>route.abort());
  page=await context.newPage();page.on('pageerror',e=>report.errors.push(e.message));page.on('console',m=>{if(/wardrobe/.test(m.text()))console.log('PAGE',m.text(),m.location().url)});
  await page.addInitScript(()=>{window.SpeechRecognition=class{constructor(){window.__rec=this}start(){setTimeout(()=>this.onstart?.(),0)}abort(){}}});
  const url='http://127.0.0.1:'+server.address().port+'/living-library/live/index.html';
  await page.goto(url);await page.waitForFunction(()=>!!window.LivingScene?.wardrobe,{timeout:60000});
  if(await page.locator('#llUpdateDialog').evaluate(e=>e.open).catch(()=>false))await page.locator('#llUpdateDone').click();
  const ward=()=>page.evaluate(()=>LivingScene.wardrobe());
  let w=await ward();assert.equal(w.applied,'keeper.default');assert.equal(w.defaultVisible,true);
  report.checks.push('initial: original keeper worn, default parts visible');

  // Voice starts only from the existing character tap. Find the keeper on screen.
  const vp=page.viewportSize();let started=false;
  for(const [fx,fy] of [[.5,.5],[.5,.45],[.5,.55],[.5,.4],[.5,.6],[.45,.5],[.55,.5],[.5,.35],[.5,.65]]){
   await page.mouse.click(vp.width*fx,vp.height*fy);await page.waitForTimeout(250);
   if(await page.evaluate(()=>!!window.__rec)){started=true;break}
  }
  assert.ok(started,'tapping the character starts voice listening');
  assert.equal(await page.evaluate(()=>window.__rec.lang),'ja-JP');
  await page.waitForFunction(()=>/百面相.*転移解除/.test(document.getElementById('atlasVoiceState').textContent));
  const say=text=>page.evaluate(t=>{const r=window.__rec,k=r.__i=(r.__i||0)+1,results=[];results[k]=Object.assign([{transcript:t}],{isFinal:true});r.onresult({resultIndex:k,results})},text);
  await say('百面相');
  await page.waitForFunction(()=>!document.getElementById('wardrobeWorld').hidden);
  assert.ok(await page.evaluate(()=>document.body.classList.contains('wardrobe-open')));
  assert.equal(await page.locator('#wardrobeName').innerText(),'守書人');
  assert.match(await page.locator('#spellCast').innerText(),/百面相！\s*ひゃくめんそう/);
  assert.match(await page.locator('#wardrobeHint').innerText(),/転移解除/);
  await page.screenshot({path:path.join(out,'wardrobe-keeper.png')});
  report.checks.push('spell 百面相 opens the dressing world with the cast effect; HUD hidden; starts at the worn outfit');

  // Saying the entry spell again does NOT toggle out; only the one return spell leaves.
  await page.waitForTimeout(400);await say('百面相');await page.waitForTimeout(300);
  assert.equal(await page.locator('#wardrobeWorld').isHidden(),false);
  assert.match(await page.locator('#toast').innerText(),/已經在換衣世界/);
  await page.waitForTimeout(400);await say('転移 解除');await page.waitForFunction(()=>document.getElementById('wardrobeWorld').hidden);
  assert.match(await page.locator('#spellCast').innerText(),/転移解除！/);
  await page.waitForTimeout(400);await say('てんいかいじょ');await page.waitForTimeout(300);
  assert.match(await page.locator('#toast').innerText(),/已經在圖書館/);
  await page.waitForTimeout(400);await say('ひゃくめんそう');await page.waitForFunction(()=>!document.getElementById('wardrobeWorld').hidden);
  report.checks.push('repeat entry spell stays; universal return spell 転移解除 (spaced or kana) leaves; return at home only informs');

  await page.waitForTimeout(400);await say('Book');await page.waitForTimeout(300);
  assert.equal(await page.evaluate(()=>document.body.classList.contains('atlas-focused')),false,'Book is ignored while dressing');

  const glbRequests=[];page.on('request',r=>{if(r.url().endsWith('/anna.ipati.v1/anna.glb'))glbRequests.push(r.url())});
  page.on('dialog',d=>d.accept());
  await page.locator('#wardrobeNext').click();
  assert.equal(await page.locator('#wardrobeName').innerText(),'安娜 Anna');
  await page.waitForFunction(()=>/尚未下載/.test(document.getElementById('wardrobeStatus').textContent)).catch(async e=>{throw Error('status: '+await page.locator('#wardrobeStatus').innerText())});
  assert.match(await page.locator('#wardrobeStatus').innerText(),/18\.6 MB|19 MB/);
  assert.equal(await page.locator('#wardrobeDownload').isVisible(),true);assert.equal(await page.locator('#wardrobeConfirm').isDisabled(),true);
  assert.match(await page.locator('#wardrobeCredit').innerText(),/Jungle Jim.*CC-BY-4\.0（已修改）/);
  assert.equal(glbRequests.length,0,'nothing is downloaded until the player asks');
  await page.screenshot({path:path.join(out,'wardrobe-anna-download.png')});
  await page.locator('#wardrobeDownload').click();
  await page.waitForFunction(()=>!document.getElementById('wardrobeConfirm').disabled,{timeout:180000});
  assert.match(await page.locator('#wardrobeStatus').innerText(),/已下載・v1/);
  assert.equal(glbRequests.length,1);
  w=await ward();assert.equal(w.durable,true);assert.equal(w.packs['anna.ipati.v1'].version,1);
  await page.waitForTimeout(600);await page.screenshot({path:path.join(out,'wardrobe-anna.png')});
  report.checks.push('pack downloads once on request, SHA-256 verified, stored in Cache Storage; KTX2 + meshopt GLB renders with CC-BY credit');

  // Back = no change.
  await page.locator('#wardrobeBack').click();w=await ward();
  assert.equal(w.open,false);assert.equal(w.applied,'keeper.default');assert.equal(w.defaultVisible,true);assert.equal(w.worn,null);
  report.checks.push('返回 leaves without changing the outfit');

  await page.waitForTimeout(400);await say('ひゃくめんそう');await page.waitForFunction(()=>!document.getElementById('wardrobeWorld').hidden);
  await page.locator('#wardrobeNext').click();await page.waitForFunction(()=>!document.getElementById('wardrobeConfirm').disabled);
  await page.locator('#wardrobeConfirm').click();
  w=await ward();assert.equal(w.applied,'anna.ipati.v1');assert.equal(w.worn,'anna.ipati.v1');assert.equal(w.defaultVisible,false);
  assert.equal(await page.evaluate(()=>localStorage.getItem('nw-avatar-wardrobe-v1')),'anna.ipati.v1');
  assert.equal(await page.evaluate(()=>document.body.classList.contains('wardrobe-open')),false);
  await page.waitForTimeout(500);await page.screenshot({path:path.join(out,'library-anna.png')});
  report.checks.push('換上 wears Anna under the same keeper root and saves the choice');

  // Reload with the pack files AND manifest unreachable: restored from the phone only.
  await context.route(/\/assets\/characters\/packs\//,route=>route.abort());
  await page.reload();await page.waitForFunction(()=>window.LivingScene?.wardrobe?.()?.worn==='anna.ipati.v1',{timeout:120000});
  assert.equal(glbRequests.length,1,'no re-download after reload');
  await page.screenshot({path:path.join(out,'library-anna-reload.png')});
  report.checks.push('reload restores Anna offline from the stored pack (pack URLs blocked)');
  await context.unroute(/\/assets\/characters\/packs\//);

  // Delete the worn pack: falls back to the original keeper.
  // The library's own update notice may reappear after a reload; dismiss it like a player would.
  await page.waitForTimeout(1500);
  if(await page.locator('#llUpdateDialog').evaluate(e=>e.open).catch(()=>false))await page.locator('#llUpdateDone').click();
  // Voice starts only from the existing character tap; a tap elsewhere stops it, so check after every tap.
  const startVoice=async()=>{for(let round=0;round<3;round++)for(const [fx,fy] of [[.5,.5],[.5,.45],[.5,.55],[.5,.4],[.5,.6],[.45,.5],[.55,.5]]){
   await page.mouse.click(vp.width*fx,vp.height*fy);await page.waitForTimeout(400);if(await page.evaluate(()=>!!window.__rec?.onresult))return}
   throw Error('could not start voice by tapping the character')};
  await startVoice();await page.waitForTimeout(300);await say('百面相！');await page.waitForFunction(()=>!document.getElementById('wardrobeWorld').hidden);
  assert.equal(await page.locator('#wardrobeName').innerText(),'安娜 Anna');
  await page.waitForFunction(()=>!document.getElementById('wardrobeRemove').hidden);
  await page.locator('#wardrobeRemove').click();
  await page.waitForFunction(()=>/尚未下載/.test(document.getElementById('wardrobeStatus').textContent));
  w=await ward();assert.equal(w.applied,'keeper.default');assert.equal(w.worn,null);assert.equal(w.defaultVisible,true);assert.deepEqual(w.packs,{});
  assert.equal(await page.evaluate(()=>localStorage.getItem('nw-avatar-wardrobe-v1')),'keeper.default');
  report.checks.push('🗑 removes the pack; the worn outfit falls back to the original keeper');

  assert.deepEqual(report.errors,[],'no page errors');
  console.log(JSON.stringify(report,null,1));
 }catch(e){
  if(page)await page.screenshot({path:path.join(out,'wardrobe-failure.png')}).catch(()=>{});
  console.error(JSON.stringify(report,null,1));throw e;
 }finally{await browser.close();server.close()}
})().catch(e=>{console.error(e);process.exit(1)});
