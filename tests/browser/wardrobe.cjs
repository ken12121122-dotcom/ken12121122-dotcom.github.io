/* 換衣世界：真實瀏覽器 + 實際 index.html + 實際 Anna GLB。
 * 語音以假的 SpeechRecognition 注入「きがえ」文字；這不是麥克風或手機驗收。
 * Software GPU 政策只在本測試回應中略過，以驗證功能，不代表手機 GPU 效能。 */
const {chromium}=require('playwright'),fs=require('node:fs/promises'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..'),out=process.env.NW_BROWSER_OUTPUT||path.join(root,'test-evidence');
const mime={'.js':'text/javascript','.html':'text/html','.json':'application/json','.css':'text/css','.glb':'model/gltf-binary'};
const threeDir=process.env.NW_THREE_DIR||path.dirname(path.dirname(require.resolve('three')));
(async()=>{
 await fs.mkdir(out,{recursive:true});
 const server=http.createServer(async(req,res)=>{try{const u=new URL(req.url,'http://localhost'),name=path.resolve(root,'.'+decodeURIComponent(u.pathname));if(!name.startsWith(root+path.sep))throw Error('path');const data=await fs.readFile(name);res.writeHead(200,{'Content-Type':mime[path.extname(name)]||'application/octet-stream'});res.end(data)}catch{res.writeHead(404);res.end('missing')}});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const browser=await chromium.launch({headless:true,...(process.env.NW_BROWSER_EXE?{executablePath:process.env.NW_BROWSER_EXE}:{})});
 const report={phone:'NOT_TESTED',microphone:'NOT_TESTED (fake SpeechRecognition transcript)',checks:[],errors:[]};
 let page;
 try{
  const context=await browser.newContext({viewport:{width:412,height:860},deviceScaleFactor:1,reducedMotion:'reduce'});
  // Same pinned three@0.185.0 files, served from the local package by their CDN path.
  await context.route(/https:\/\/(cdn\.jsdelivr\.net\/npm|unpkg\.com)\/three@0\.185\.0\/(.*)$/,async route=>{
   const rel=new URL(route.request().url()).pathname.replace(/^\/(npm\/)?three@0\.185\.0\//,'');
   try{await route.fulfill({contentType:'text/javascript',body:await fs.readFile(path.join(threeDir,rel),'utf8')})}catch{await route.fulfill({status:404,body:''})}
  });
  await context.route('**/render-capability.js?*',route=>route.fulfill({contentType:'text/javascript',body:'window.LivingRenderCapability={isSoftwareRenderer:()=>false};'}));
  await context.route('https://api.github.com/**',route=>route.abort());
  page=await context.newPage();page.on('pageerror',e=>report.errors.push(e.message));
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
  await page.waitForFunction(()=>/きがえ/.test(document.getElementById('atlasVoiceState').textContent));
  const say=text=>page.evaluate(t=>{const r=window.__rec,k=r.__i=(r.__i||0)+1,results=[];results[k]=Object.assign([{transcript:t}],{isFinal:true});r.onresult({resultIndex:k,results})},text);
  await say('きがえ');
  await page.waitForFunction(()=>!document.getElementById('wardrobeWorld').hidden);
  assert.ok(await page.evaluate(()=>document.body.classList.contains('wardrobe-open')));
  assert.equal(await page.locator('#wardrobeName').innerText(),'守書人');
  await page.screenshot({path:path.join(out,'wardrobe-keeper.png')});
  report.checks.push('voice きがえ opens the dressing world; HUD hidden; starts at the worn outfit');

  await page.waitForTimeout(400);await say('Book');await page.waitForTimeout(300);
  assert.equal(await page.evaluate(()=>document.body.classList.contains('atlas-focused')),false,'Book is ignored while dressing');

  await page.locator('#wardrobeNext').click();
  assert.equal(await page.locator('#wardrobeName').innerText(),'安娜 Anna');
  assert.match(await page.locator('#wardrobeCredit').innerText(),/Jungle Jim.*CC-BY-4\.0/);
  await page.waitForFunction(()=>!document.getElementById('wardrobeConfirm').disabled,{timeout:120000});
  assert.equal(await page.locator('#wardrobeStatus').innerText(),'');
  await page.waitForTimeout(600);await page.screenshot({path:path.join(out,'wardrobe-anna.png')});
  report.checks.push('Anna GLB (meshopt + webp) loads through the rewritten GLTFLoader and shows CC-BY credit');

  // Back = no change.
  await page.locator('#wardrobeBack').click();w=await ward();
  assert.equal(w.open,false);assert.equal(w.applied,'keeper.default');assert.equal(w.defaultVisible,true);assert.equal(w.worn,null);
  report.checks.push('返回 leaves without changing the outfit');

  await page.waitForTimeout(400);await say('着替え');await page.waitForFunction(()=>!document.getElementById('wardrobeWorld').hidden);
  await page.locator('#wardrobeNext').click();await page.waitForFunction(()=>!document.getElementById('wardrobeConfirm').disabled);
  await page.locator('#wardrobeConfirm').click();
  w=await ward();assert.equal(w.applied,'anna.ipati.v1');assert.equal(w.worn,'anna.ipati.v1');assert.equal(w.defaultVisible,false);
  assert.equal(await page.evaluate(()=>localStorage.getItem('nw-avatar-wardrobe-v1')),'anna.ipati.v1');
  assert.equal(await page.evaluate(()=>document.body.classList.contains('wardrobe-open')),false);
  await page.waitForTimeout(500);await page.screenshot({path:path.join(out,'library-anna.png')});
  report.checks.push('換上 wears Anna under the same keeper root and saves the choice');

  // Saved outfit is restored after reload; original keeper stays the fallback.
  await page.reload();await page.waitForFunction(()=>window.LivingScene?.wardrobe?.()?.worn==='anna.ipati.v1',{timeout:120000});
  report.checks.push('reload restores Anna');
  await page.screenshot({path:path.join(out,'library-anna-reload.png')});

  assert.deepEqual(report.errors,[],'no page errors');
  console.log(JSON.stringify(report,null,1));
 }catch(e){
  if(page)await page.screenshot({path:path.join(out,'wardrobe-failure.png')}).catch(()=>{});
  console.error(JSON.stringify(report,null,1));throw e;
 }finally{await browser.close();server.close()}
})().catch(e=>{console.error(e);process.exit(1)});
