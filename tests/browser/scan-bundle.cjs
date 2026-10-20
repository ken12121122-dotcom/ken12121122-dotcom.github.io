/* Real browser + actual live application + original Three.js geometries.
 * Isolated synthetic save. Software GPU policy is bypassed ONLY in intercepted
 * test response, to exercise functionality; this is not phone/GPU acceptance. */
const {chromium}=require('playwright'),fs=require('node:fs/promises'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
 const root=path.resolve(__dirname,'../..'),out=process.env.NW_BROWSER_OUTPUT||path.join(root,'test-evidence');
const mime={'.js':'text/javascript','.html':'text/html','.json':'application/json','.css':'text/css'};
const blank=id=>({id:'local.card.'+id,objectType:'card',templateId:'blank-card-v1',status:'draft',placement:'hover',title:'空白卡片',revision:1,description:'',image:null,cardNumber:'',cardType:'',containerId:null,ownerBookId:null});
const eye={...blank('eye'),title:'真實之眼',runeBinding:{runeId:'artifact.truth-eye',state:'bound'},truthEyeContract:{artifactId:'artifact.truth-eye',version:2,form:'world'}};
(async()=>{
 await fs.mkdir(out,{recursive:true});
 const server=http.createServer(async(req,res)=>{try{const u=new URL(req.url,'http://localhost'),name=path.resolve(root,'.'+decodeURIComponent(u.pathname));if(!name.startsWith(root+path.sep))throw Error('path');const data=await fs.readFile(name);res.writeHead(200,{'Content-Type':mime[path.extname(name)]||'application/octet-stream'});res.end(data)}catch{res.writeHead(404);res.end('missing')}});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const edge='C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
 const executablePath=process.env.NW_BROWSER_EXE||(process.platform==='win32'?edge:undefined);
 const browser=await chromium.launch({executablePath,headless:true});
 let page;
 const report={software_gpu_functional_test:true,test_renderer_throttle_ms:350,phone:'NOT_TESTED',checks:[],errors:[]};
 try{
  const context=await browser.newContext({viewport:{width:600,height:760},deviceScaleFactor:1,reducedMotion:'reduce'});
  await context.route(/https:\/\/(cdn.jsdelivr.net|unpkg.com)\/.*/,async route=>{
   const name=route.request().url().includes('three.core')?'three.core.min.js':'three.module.min.js';
   let vendor=path.join(__dirname,'vendor');try{await fs.access(path.join(vendor,name))}catch{vendor=path.dirname(require.resolve('three'))}
   let code=await fs.readFile(path.join(vendor,name),'utf8');
   if(name==='three.module.min.js'){
    const cls=code.match(/(\w+) as WebGLRenderer/)[1];
    code+=';const testOriginalRender='+cls+'.prototype.render;'+cls+'.prototype.render=function(s,c){s.updateMatrixWorld(true);c.updateMatrixWorld(true);const now=performance.now();if(!this.testPaintAt||now-this.testPaintAt>350){this.testPaintAt=now;return testOriginalRender.call(this,s,c)}};';
   }
   await route.fulfill({contentType:'text/javascript',body:code});
  });
  await context.route('**/render-capability.js?*',route=>route.fulfill({contentType:'text/javascript',body:'window.LivingRenderCapability={isSoftwareRenderer:()=>false};'}));
  page=await context.newPage();page.on('pageerror',e=>report.errors.push(e.message));
  await page.addInitScript(({eye,blank,spell})=>{if(!localStorage.getItem('scan-browser-test-seeded')){localStorage.setItem('living-library-player-objects-v1',JSON.stringify({version:1,objects:[eye,blank,spell]}));localStorage.setItem('scan-browser-test-seeded','1')}},{eye,blank:blank('medium'),spell:{...blank('spell'),title:'解除契約'}});
  const url='http://127.0.0.1:'+server.address().port+'/living-library/live/index.html';
  await page.goto(url);await page.waitForFunction(()=>!!window.LivingScene?.truthEye,{timeout:45000});
  console.log('3D scene ready');
  if(await page.locator('#llUpdateDialog').evaluate(e=>e.open))await page.locator('#llUpdateDone').click();
  const inspect=()=>page.evaluate(()=>LivingScene.truthEye.inspect());
  const readNames=()=>page.locator('#eyeScanNames li').evaluateAll(nodes=>nodes.map(n=>({name:n.textContent,id:n.dataset.scanId,selected:n.dataset.selected==='true'})));
  await page.locator('#eyeRadius').fill('20');await page.locator('#eyeRadius').dispatchEvent('input');
  await page.waitForTimeout(1400);
  const discovered=await inspect(),count=discovered.scanned.length;
  assert.ok(count>=5,'live scanner discovers authored geometry');
  assert.deepEqual(discovered.selected,[],'no automatic selection');
  assert.ok(!discovered.scanned.some(o=>o.id==='scene.keeper'),'never scan avatar');
  for(const id of ['#eyeScope','#eyeSummon','#eyeSelectAll','#eyeCollect'])assert.equal(await page.locator(id).count(),0,'removed scanner action: '+id);
  await page.waitForFunction(n=>document.querySelectorAll('#eyeScanNames li').length===n,count);
  let names=await readNames();
  assert.equal(names.length,count,'every scanned object has a right-side row');
  assert.ok(names.every(x=>x.id&&x.name.trim()),'real names or descriptive fallback');
  assert.ok(names.some(x=>x.id==='scene.floor'&&x.name==='地板'),'Chinese geometry label');
  assert.ok(names.some(x=>x.name.includes('之書')),'book names from original library');
  assert.match(await page.locator('#eyeCount').innerText(),new RegExp('掃描 '+count+' 件.*已選 0 件'));
  const visual=await page.locator('#eyeScanReadout').evaluate(n=>({background:getComputedStyle(n).backgroundColor,border:getComputedStyle(n).borderTopWidth}));
  assert.ok(visual.background==='rgba(0, 0, 0, 0)'||visual.background==='transparent','no panel background');
  assert.equal(visual.border,'0px','no panel frame');
  report.checks.push('right-hand frameless overlay lists '+count+' object names and count, without avatar or action buttons');

  // Discover the pointer position with a short tap, then CLEAR selection.
  // The contract must work via a single hold with zero preceding selections.
  const viewport=page.viewportSize();
  const candidates=discovered.scanned.filter(o=>o.x>25&&o.x<viewport.width-195&&o.y>210&&o.y<viewport.height-35).map(o=>({x:o.x,y:o.y}));
  for(let x=70;x<viewport.width-190;x+=90)for(let y=330;y<viewport.height-40;y+=90)candidates.push({x,y});
  let chosen=null,chosenId=null;
  for(const p of candidates){
   await page.mouse.click(p.x,p.y);await page.waitForTimeout(60);
   const scan=await inspect();
   if(scan.selected.length===1){chosen=p;chosenId=scan.selected[0];break}
   if(scan.selected.length>1)throw Error('scanner selected extra item without a player tap');
  }
  assert.ok(chosen,'a real tap on a scanned object selects it');
  assert.equal((await readNames()).filter(x=>x.selected).length,1,'short-tap optional multi-select still works');
  await page.mouse.click(chosen.x,chosen.y);
  assert.deepEqual((await inspect()).selected,[],'the object is NOT preselected');
  assert.match(await page.locator('#eyeCount').innerText(),/已選 0 件/);
  const hold=async ms=>{
   await page.mouse.move(chosen.x,chosen.y);await page.mouse.down();
   if(ms)await page.waitForTimeout(ms);
   else await page.waitForFunction(()=>document.querySelector('#truthEyeContractConfirm').open,null,{timeout:15000});
   await page.mouse.up();
  };
  await hold(180);
  assert.equal(await page.locator('#truthEyeContractConfirm').evaluate(e=>e.open),false,'brief tap cannot contract');
  assert.deepEqual((await inspect()).selected,[chosenId],'short tap marks object but is not required');
  await page.mouse.click(chosen.x,chosen.y);
  assert.deepEqual((await inspect()).selected,[],'selection cleared before direct hold');
  await hold();
  assert.equal(await page.locator('#truthEyeContractConfirm').evaluate(e=>e.open),true);
  assert.deepEqual((await inspect()).selected,[],'hold-only contract must not silently select objects');
  assert.match(await page.locator('#truthEyeConfirmText').innerText(),/1 件/);
  await page.locator('#truthEyeCancel').click();
  assert.deepEqual((await inspect()).packed,[],'canceled contract does not mutate originals');
  assert.deepEqual((await inspect()).selected,[],'cancellation preserves empty selection');
  await hold();await page.locator('#truthEyeApprove').click();
  await page.waitForFunction(()=>!LivingScene.truthEye.inspect().bundleBusy,null,{timeout:15000});
  const saved=await page.evaluate(()=>JSON.parse(localStorage.getItem('living-library-player-objects-v1')));
  const card=saved.objects.find(c=>c.scanContract);
  assert.ok(card);assert.equal(card.id,'local.card.medium');
  assert.deepEqual(card.scanContract.members.map(r=>r.id),[chosenId]);
  assert.equal(saved.objects.length,3,'no extra cards created');
  assert.equal((await inspect()).packed.length,1);
  report.checks.push('hold-only direct 3D object contract with zero prior taps, cancel, confirmation and original card retained');
  await page.screenshot({path:path.join(out,'object-longpress-packed.png')});

  await page.reload();await page.waitForFunction(()=>!!window.LivingScene?.truthEye,{timeout:45000});await page.waitForTimeout(700);
  if(await page.locator('#llUpdateDialog').evaluate(e=>e.open))await page.locator('#llUpdateDone').click();
  assert.equal((await inspect()).packed.length,1,'same package persists after reload');
  const candidate=(await inspect()).cards.find(c=>c.id==='local.card.medium');
  assert.ok(candidate,'original bound card still available to restore');
  await page.mouse.move(candidate.x,candidate.y);await page.mouse.down();
  await page.waitForFunction(()=>document.querySelector('#truthEyeContractConfirm').open,null,{timeout:15000});
  await page.mouse.up();await page.locator('#truthEyeApprove').click();
  await page.waitForFunction(()=>!LivingScene.truthEye.inspect().bundleBusy,null,{timeout:15000});
  await page.waitForTimeout(550);
  assert.equal((await inspect()).packed.length,0,'card releases same world object');
  assert.ok((await inspect()).scanned.some(x=>x.id===chosenId));
  assert.ok((await readNames()).some(x=>x.id===chosenId),'its name returns in scan readout');
  report.checks.push('reload, original-card materialization and restored object name/count remain correct');

  // The in-game "return objects" confirmation must now REALLY unbind.
  const restoredViaGesture=await page.evaluate(()=>{
   const B=LivingScanBundle,storage=localStorage;
   const card=LivingObjectCraft.listCards(storage).find(c=>c.id==='local.card.medium');
   return {blank:B.blank(card),claims:[...B.claims(storage)],contract:card.scanContract||null};
  });
  assert.ok(restoredViaGesture.blank,'3-second card release immediately restores the SAME original blank');
  assert.deepEqual(restoredViaGesture.claims,[],'no old owner memory remains after visible card release');
  assert.equal(restoredViaGesture.contract,null);
  report.checks.push('3D card release (not separate unbinding spell) frees all object identities, returns empty original');

  // Legacy world-form contracts must still work with the generic spell.
  const unbound=await page.evaluate(id=>{
   const B=LivingScanBundle,C=LivingObjectCraft,storage=localStorage;
   const card=C.listCards(storage).find(c=>c.id==='local.card.medium');
   const ref={kind:'scene',id},bound=B.bind(storage,{cardId:card.id,revision:card.revision,members:[ref]},[ref]);
   const world=B.setForm(storage,bound.id,bound.revision,'world',[ref]);
   const spell=C.listCards(storage).find(c=>c.id==='local.card.spell');
   const before=B.claims(storage).size;
   const result=C.unbindAndAbsorbSpell(storage,{
    runeId:world.runeBinding.runeId,targetId:world.id,targetRevision:world.revision,
    spellId:spell.id,spellRevision:spell.revision});
   return {oldCount:before,newCard:result.target,spellId:result.spellId,remaining:[...B.claims(storage)]};
  },chosenId);
  assert.equal(unbound.oldCount,1);
  assert.equal(unbound.newCard.title,'空白卡片');
  assert.equal(unbound.newCard.description,'');assert.equal(unbound.newCard.cardType,'');
  assert.equal(unbound.newCard.runeBinding,undefined);assert.equal(unbound.newCard.scanContract,undefined);
  assert.deepEqual(unbound.remaining,[]);
  assert.equal(unbound.spellId,'local.card.spell');
  await page.reload();await page.waitForFunction(()=>!!window.LivingScene?.truthEye,{timeout:45000});
  await page.waitForTimeout(650);
  if(await page.locator('#llUpdateDialog').evaluate(e=>e.open))await page.locator('#llUpdateDone').click();
  assert.equal((await inspect()).packed.length,0);
  assert.ok((await inspect()).scanned.some(x=>x.id===chosenId));
  assert.ok(await page.evaluate(()=>LivingScanBundle.blank(LivingObjectCraft.listCards(localStorage).find(c=>c.id==='local.card.medium'))));
  report.checks.push('legacy spell unbind remains supported for previously visible world-form contracts');

  // Older releases left a half-cleared card: preserve source save backup first,
  // then repair exactly the known autogenerated scan title/type/description.
  const legacyRaw=await page.evaluate(()=>{
   const key='living-library-player-objects-v1',state=JSON.parse(localStorage.getItem(key));
   const card=state.objects.find(x=>x.id==='local.card.medium');
   card.title='掃描收納卡';card.description='真實之眼收納 1 件物件；原始身分保留。';card.cardType='收納契約';
   localStorage.setItem(key,JSON.stringify(state));return localStorage.getItem(key);
  });
  await page.reload();await page.waitForFunction(()=>!!window.LivingScene?.truthEye,{timeout:45000});
  await page.waitForTimeout(500);
  const repaired=await page.evaluate(()=>{
   const card=LivingObjectCraft.listCards(localStorage).find(c=>c.id==='local.card.medium');
   return {card,blank:LivingScanBundle.blank(card),backup:localStorage.getItem(LivingScanBundle.BACKUP_KEY)};
  });
  assert.equal(repaired.backup,legacyRaw,'original legacy save retained as durable backup');
  assert.ok(repaired.blank,'automatic recovery makes old orphan usable as blank');
  assert.equal(repaired.card.title,'空白卡片');
  assert.equal((await inspect()).packed.length,0);
  report.checks.push('historical half-cleared scanned card auto-recovered with original localStorage backup');
  // Regression: the user's complete 15-book set must be freely scannable
  // again after releasing a single grouped original-card contract.
  const fifteen=await page.evaluate(()=>{
   const storage=localStorage,B=LivingScanBundle,C=LivingObjectCraft;
   const ids=LivingLibrary.books.filter(x=>['public','local'].includes(x.sourceLayer)).map(x=>x.id);
   if(ids.length!==15)throw Error('Expected the complete public 15-book catalog: '+ids.length);
   const refs=ids.map(id=>({kind:'book',id})),blank=C.listCards(storage).find(c=>c.id==='local.card.medium');
   if(!B.blank(blank))throw Error('Original card did not return blank');
   // First release directly from sealed card and rebind all fifteen.
   const sealed=B.bind(storage,{cardId:blank.id,revision:blank.revision,members:refs},refs);
   const freed=B.release(storage,{cardId:sealed.id,revision:sealed.revision,form:'card'},refs);
   if(!B.blank(freed.card)||B.claims(storage).size)throw Error('Release left a card-form 15-book owner');
   const bound=B.bind(storage,{cardId:freed.card.id,revision:freed.card.revision,members:refs},refs);
   const live=B.setForm(storage,bound.id,bound.revision,'world',refs);
   return {ids,refs,owner:live.id,revision:live.revision};
  });
  await page.waitForTimeout(850);
  const beforeBooks=await page.evaluate(ids=>({
   bookRecords:LivingLibrary.books.filter(x=>ids.includes(x.id)).map(x=>({id:x.id,active:x.active})),
   owned:[...LivingScanBundle.claims(localStorage)].filter(([id])=>id.startsWith('book:')).length
  }),fifteen.ids);
  assert.equal(beforeBooks.owned,15,'15 books have one valid grouped contract');
  const released=await page.evaluate(({owner,revision,refs})=>{
   const C=LivingObjectCraft,B=LivingScanBundle,storage=localStorage;
   const result=B.release(storage,{cardId:owner,revision,form:'world'},refs);
   LivingScene.refreshFloatingCards();
   LivingScene.truthEye.applyPacked();
   return {card:result.card,blank:B.blank(result.card),claims:[...B.claims(storage)],
    remaining:refs.filter(r=>B.claims(storage).has(r.kind+':'+r.id))};
  },fifteen);
  assert.ok(released.blank,'the 15-book original card must return to blank');
  assert.deepEqual(released.claims,[],'no book is bound to old owner after unbind');
  assert.deepEqual(released.remaining,[]);
  await page.locator('#eyeRadius').fill('20');await page.locator('#eyeRadius').dispatchEvent('input');
  await page.locator('#eyeAperture').fill('360');await page.locator('#eyeAperture').dispatchEvent('input');
  await page.waitForTimeout(900);
  const bookScan=await inspect(),bookIds=new Set(bookScan.scanned.filter(x=>x.kind==='book').map(x=>x.id));
  assert.equal(fifteen.ids.filter(id=>bookIds.has(id)).length,15,'all released 15 books are available in current scanner');
  const rebound=await page.evaluate(refs=>{
   const B=LivingScanBundle,storage=localStorage,card=LivingObjectCraft.listCards(storage).find(c=>c.id==='local.card.medium');
   return B.bind(storage,{cardId:card.id,revision:card.revision,members:refs},refs);
  },fifteen.refs);
  assert.equal(rebound.scanContract.members.length,15,'the same original card can rebind all books');
  await page.reload();await page.waitForFunction(()=>!!window.LivingScene?.truthEye,{timeout:45000});
  assert.equal((await inspect()).packed.filter(([id])=>id.startsWith('book:')).length,15);
  report.checks.push('all 15 original books: sealed release, rebind, world-form release, rescan, rebind to same blank card, reload');
  assert.deepEqual(report.errors,[]);report.status='PASS';
 }catch(e){report.status='FAIL';report.failure=e.stack;if(page)await page.screenshot({path:path.join(out,'failure.png')}).catch(()=>{});throw e}
 finally{await fs.writeFile(path.join(out,'scan-bundle-browser.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));await browser.close();server.closeAllConnections();await new Promise(r=>server.close(r))}
})().catch(e=>{console.error(e);process.exitCode=1});
