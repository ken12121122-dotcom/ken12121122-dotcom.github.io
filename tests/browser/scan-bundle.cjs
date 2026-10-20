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
  await page.locator('#eyeSummon').click();
  await page.locator('#eyeRadius').fill('20');await page.locator('#eyeRadius').dispatchEvent('input');
  await page.waitForTimeout(1600);
  console.log('summoned');
  const bookCount=await page.evaluate(()=>LivingLibrary.books.filter(b=>['public','local'].includes(b.sourceLayer)).length);
  const snap=await inspect(),count=snap.scanned.length;
  assert.ok(count>=bookCount,'all visible authored books discoverable alongside world geometry');
  assert.equal(snap.scanned.filter(o=>o.kind==='book').length,bookCount);
  assert.ok(snap.scanned.some(o=>o.kind==='scene'),'scanner sees real scene geometry without selecting a category');
  assert.ok(!snap.scanned.some(o=>o.id==='scene.keeper'),'the player avatar must never be scanned');
  assert.equal(await page.locator('#eyeScope').count(),0,'no hard-coded book/library category dropdown');
  assert.equal(snap.selected.length,0,'scanner cannot preselect objects for the player');
  report.checks.push('live book and scene discovery, no category dropdown, no avatar, no auto-selection: '+count);
  await page.locator('#eyeSelectAll').click();assert.equal((await inspect()).selected.length,count);
  const hold=async selector=>{const b=await page.locator(selector).boundingBox();await page.mouse.move(b.x+b.width/2,b.y+b.height/2);await page.mouse.down();await page.waitForFunction(()=>document.querySelector('#truthEyeContractConfirm').open,null,{timeout:15000});await page.mouse.up()};
  // Early release cancels without touching the blank.
  let b=await page.locator('#eyeCollect').boundingBox();await page.mouse.move(b.x+b.width/2,b.y+b.height/2);await page.mouse.down();await page.waitForTimeout(200);await page.mouse.up();assert.equal(await page.locator('#truthEyeContractConfirm').evaluate(e=>e.open),false);
  await hold('#eyeCollect');assert.equal(await page.locator('#truthEyeContractConfirm').evaluate(e=>e.open),true);
  await page.locator('#truthEyeCancel').click();assert.equal((await inspect()).packed.length,0);
  report.checks.push('early release and confirmation cancellation preserve originals');
  console.log('cancel verified');
  await hold('#eyeCollect');await page.locator('#truthEyeApprove').click();
  await page.waitForFunction(()=>!LivingScene.truthEye.inspect().bundleBusy);
  const saved=await page.evaluate(()=>JSON.parse(localStorage.getItem('living-library-player-objects-v1')));
  const packed=saved.objects.find(c=>c.scanContract);assert.equal(packed.id,'local.card.medium');assert.equal(packed.scanContract.members.length,count);assert.equal(saved.objects.length,3);
  assert.equal((await inspect()).packed.length,count);assert.equal((await inspect()).scanned.length,0);
  assert.equal((await inspect()).objects.filter(o=>o.kind==='book'&&o.visible).length,0);
  report.checks.push('actual 3-second hold, confirm, collection animation, original medium and all member IDs persisted');
  console.log('packed');
  await page.screenshot({path:path.join(out,'books-packed.png')});
  await page.reload();await page.waitForFunction(()=>!!window.LivingScene?.truthEye);await page.waitForTimeout(800);
  if(await page.locator('#llUpdateDialog').evaluate(e=>e.open))await page.locator('#llUpdateDone').click();
  assert.equal((await inspect()).packed.length,count);assert.equal((await inspect()).scanned.length,0);
  report.checks.push('reload retains packed objects without creating cards or books');
  // Actual canvas pointer at original-card projected center, via the existing
  // atlas/card handler (not a direct call to a binder or long-press function).
  const holdCard=async id=>{const p=(await inspect()).cards.find(c=>c.id===id);assert.ok(p,'original card is visible');await page.mouse.move(p.x,p.y);await page.mouse.down();await page.waitForFunction(()=>document.querySelector('#truthEyeContractConfirm').open||document.querySelector('#runeUnbindDialog').open,null,{timeout:15000});await page.mouse.up()};
  const memberPoint=async(kind='book')=>{const snap=await page.evaluate(()=>LivingScene.truthEye.inspect({pickPoints:true}));const p=snap.objects.find(o=>o.kind===kind&&o.id!=='cardCollection'&&o.memberPickable&&o.x>20&&o.x<420&&o.y>90&&o.y<650);assert.ok(p,'a real unobstructed bound '+kind+' member is visible: '+JSON.stringify(snap.objects.filter(o=>o.kind===kind)));return p};
  const holdMember=async(kind)=>{const p=await memberPoint(kind);await page.mouse.move(p.x,p.y);await page.mouse.down();await page.waitForTimeout(250);const h=(await inspect()).hold;assert.equal(h?.type,'bound-bundle');assert.equal(h.highlightCount,0,'early hold does not highlight');assert.equal((await inspect()).ritualVisible,false,'no hexagram before three seconds');await page.waitForFunction(()=>LivingScene.truthEye.inspect().hold?.phase==='animation',null,{timeout:10000});const charged=await inspect();assert.equal(charged.ritualVisible,true,'hexagram starts only after completed hold');assert.ok(charged.hold.highlightCount>1,'completed hold highlights all members');await page.mouse.up();await page.waitForFunction(()=>document.querySelector('#truthEyeContractConfirm').open,null,{timeout:15000});await page.mouse.up()};
  await holdCard('local.card.medium');
  assert.equal(await page.locator('#truthEyeContractConfirm').evaluate(e=>e.open),true,'original card canvas hold opens restoration');
  await page.locator('#truthEyeApprove').click();await page.waitForFunction(()=>!LivingScene.truthEye.inspect().bundleBusy);await page.waitForTimeout(500);
  assert.equal((await inspect()).packed.length,0);assert.equal((await inspect()).scanned.length,count);
  const assertCollectionShelved=async()=>{const c=await page.evaluate(()=>({model:LivingScene.atlas.inspect().collections.find(c=>c.id==='local.atlas.collection'),book:LivingLibrary.books.find(b=>b.id==='cardCollection'),entity:LivingScene.truthEye.inspect().objects.find(o=>o.id==='cardCollection')}));assert.equal(c.model.mode,'shelf');assert.equal(c.model.visible,false,'inner atlas model is absent');assert.equal(c.book.active,false,'original collection book is shelved');assert.equal(c.entity.visibleGroups,1,'exactly one original collection representation remains');};
  await assertCollectionShelved();
  report.checks.push('actual canvas hold on same original card restores every original book');
  await page.screenshot({path:path.join(out,'books-restored.png')});
  assert.ok(!(await inspect()).cards.some(c=>c.id==='local.card.medium'),'materialized original card disappears');
  await page.locator('#eyeRadius').fill('2');await page.locator('#eyeRadius').dispatchEvent('input');
  const tapped=await memberPoint();await page.mouse.click(tapped.x,tapped.y);await page.waitForTimeout(350);
  assert.ok(await page.locator('#drawerWrap').evaluate(e=>e.classList.contains('open')),'short tap retains normal book function');await page.locator('#closeDrawer').click();
  await holdMember();await page.locator('#truthEyeCancel').click();assert.ok(!(await inspect()).cards.some(c=>c.id==='local.card.medium'));assert.equal((await inspect()).ritualVisible,false,'cancel does not restart the hexagram');
  await holdMember();
  assert.equal(await page.locator('#truthEyeContractConfirm').evaluate(e=>e.open),true);
  await page.locator('#truthEyeApprove').click();await page.waitForFunction(()=>!LivingScene.truthEye.inspect().bundleBusy);await page.waitForTimeout(300);
  assert.equal((await inspect()).packed.length,count);
  assert.ok((await inspect()).cards.some(c=>c.id==='local.card.medium'),'same original card returns after group cardification');
  await page.locator('#eyeRadius').fill('20');await page.locator('#eyeRadius').dispatchEvent('input');
  report.checks.push('materialized card disappears; short tap opens original book; member hold highlights entire group, cancel preserves world; same card returns even with scanner radius reduced');console.log('repacked');
  await holdCard('local.card.spell');await page.waitForTimeout(700);
  assert.equal(await page.locator('#runeUnbindDialog').evaluate(e=>e.open),true,'existing解除契約 card selects collection');
  assert.equal(await page.evaluate(()=>LivingScene.unbindSpell.inspect().selection?.targetId),'local.card.medium');
  await page.locator('#runeUnbindConfirm').click();
  await page.waitForFunction(()=>!LivingObjectCraft.listCards(localStorage).find(c=>c.id==='local.card.medium').scanContract);await page.waitForTimeout(350);
  assert.equal((await inspect()).packed.length,0);assert.equal((await inspect()).scanned.length,count);
  const spellState=await page.evaluate(()=>LivingObjectCraft.listCards(localStorage).find(c=>c.id==='local.card.spell'));
  assert.equal(spellState.containment.kind,'consumed_spell');
  report.checks.push('existing解除契約 spell restores all books, preserves original medium, consumes original spell and removes ownership');console.log('unbound');
  // Second stage: create a fresh synthetic blank using the existing card engine.
  const worldCard=await page.evaluate(()=>{const c=LivingObjectCraft.createBlankCard(localStorage,crypto);LivingScene.refreshFloatingCards();return c.id});
  assert.equal(await page.locator('#eyeScope').count(),0);await page.waitForTimeout(500);
  const worldSnapshot=await inspect(),worldCount=worldSnapshot.scanned.length;
  assert.ok(worldCount>=bookCount);assert.ok(worldSnapshot.scanned.some(o=>o.id==='scene.floor'));assert.ok(!worldSnapshot.scanned.some(o=>o.id==='scene.keeper'));assert.ok(worldSnapshot.objects.some(o=>o.id==='scene.keeper'));
  await page.locator('#eyeSelectAll').click();await hold('#eyeCollect');
  assert.equal(await page.locator('#truthEyeContractConfirm').evaluate(e=>e.open),true);
  await page.locator('#truthEyeApprove').click();await page.waitForFunction(()=>!LivingScene.truthEye.inspect().bundleBusy);await page.waitForTimeout(300);
  let now=await inspect();assert.equal(now.packed.length,worldCount);assert.equal(now.objects.filter(o=>o.visible&&o.id!=='scene.keeper').length,0);
  report.checks.push('player explicitly selects world geometry to collect into one original blank: '+worldCount);console.log('world packed');
  await page.screenshot({path:path.join(out,'world-packed.png')});
  await page.reload();await page.waitForFunction(()=>!!window.LivingScene?.truthEye);await page.waitForTimeout(500);
  if(await page.locator('#llUpdateDialog').evaluate(e=>e.open))await page.locator('#llUpdateDone').click();
  now=await inspect();assert.equal(now.packed.length,worldCount);assert.equal(now.objects.filter(o=>o.visible&&o.id!=='scene.keeper').length,0);
  await holdCard(worldCard);assert.equal(await page.locator('#truthEyeContractConfirm').evaluate(e=>e.open),true);
  await page.locator('#truthEyeApprove').click();await page.waitForFunction(()=>!LivingScene.truthEye.inspect().bundleBusy);await page.waitForTimeout(300);
  now=await inspect();assert.equal(now.packed.length,0);assert.equal(now.scanned.length,worldCount);assert.ok(worldSnapshot.scanned.every(ref=>now.objects.some(o=>o.kind===ref.kind&&o.id===ref.id&&o.visible)),'every actually scanned member restores; hidden unselected system helpers are not forced visible');
  report.checks.push('world contract restores scene and books; the protected player avatar never enters the contract');
  await page.screenshot({path:path.join(out,'world-restored.png')});
  assert.ok(!now.cards.some(c=>c.id===worldCard),'entire-library original card disappears in world form');
  await page.reload();await page.waitForFunction(()=>!!window.LivingScene?.truthEye);await page.waitForTimeout(500);
  if(await page.locator('#llUpdateDialog').evaluate(e=>e.open))await page.locator('#llUpdateDone').click();
  assert.ok(!(await inspect()).cards.some(c=>c.id===worldCard),'world-form card stays absent after reload');
  await assertCollectionShelved();
  await holdMember('scene');await page.locator('#truthEyeApprove').click();await page.waitForFunction(()=>!LivingScene.truthEye.inspect().bundleBusy);await page.waitForTimeout(300);
  assert.equal((await inspect()).packed.length,worldCount);
  await holdCard(worldCard);await page.locator('#truthEyeApprove').click();await page.waitForFunction(()=>!LivingScene.truthEye.inspect().bundleBusy);await page.waitForTimeout(300);
  assert.ok(!(await inspect()).cards.some(c=>c.id===worldCard),'world-form original is hidden before解除契約');
  const worldSpell=await page.evaluate(()=>{let c=LivingObjectCraft.createBlankCard(localStorage,crypto);c=LivingObjectCraft.editCard(localStorage,c.id,{title:'解除契約'});LivingScene.refreshFloatingCards();return c.id});
  await page.waitForTimeout(500); // Let the new original card enter its actual orbit before projecting the pointer.
  // Adjacent orbit slots can overlap in the front camera. Use the real free
  // camera gesture to expose the spell instead of clicking through the card in front.
  const beforeDrag=(await inspect()).cards.find(c=>c.id===worldSpell);
  await page.mouse.move(120,510);await page.mouse.down();await page.mouse.move(330,510,{steps:12});await page.mouse.up();await page.waitForTimeout(400);
  const afterDrag=(await inspect()).cards.find(c=>c.id===worldSpell);
  assert.ok(Math.hypot(afterDrag.x-beforeDrag.x,afterDrag.y-beforeDrag.y)>1,'dragging a bound floor still rotates the real camera');
  await holdCard(worldSpell);
  assert.equal(await page.evaluate(()=>LivingScene.unbindSpell.inspect().selection?.targetId),worldCard);
  await page.locator('#runeUnbindConfirm').click();
  await page.waitForFunction(id=>!LivingObjectCraft.listCards(localStorage).find(c=>c.id===id).scanContract,worldCard);await page.waitForTimeout(350);
  now=await inspect();assert.equal(now.packed.length,0);assert.equal(now.scanned.length,worldCount);assert.ok(worldSnapshot.scanned.every(ref=>now.objects.some(o=>o.kind===ref.kind&&o.id===ref.id&&o.visible)),'every actually scanned member restores; hidden unselected system helpers are not forced visible');
  assert.ok(now.cards.some(c=>c.id===worldCard),'解除契約 restores the original blank card from hidden world form');
  report.checks.push('world-form card remains absent after reload; actual member hold repacks whole library into same card; existing解除契約 targets hidden original through its unique world anchor and restores every original');
  assert.deepEqual(report.errors,[]);report.status='PASS';
 }catch(e){report.status='FAIL';report.failure=e.stack;if(page)await page.screenshot({path:path.join(out,'failure.png')}).catch(()=>{});throw e}
 finally{await fs.writeFile(path.join(out,'scan-bundle-browser.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));await browser.close();server.closeAllConnections();await new Promise(r=>server.close(r))}
})().catch(e=>{console.error(e);process.exitCode=1});
