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
  const readNames=()=>page.locator('#eyeScanNames li').evaluateAll(nodes=>nodes.map(n=>({name:n.textContent,id:n.dataset.scanId,selected:n.hasAttribute('data-selected')})));
  await page.locator('#eyeRadius').fill('20');await page.locator('#eyeRadius').dispatchEvent('input');
  await page.waitForTimeout(1400);
  const discovered=await inspect(),count=discovered.scanned.length;
  assert.ok(count>=5,'live scanner discovers authored geometry');
  assert.deepEqual(discovered.selected,discovered.scanned.map(o=>o.id),'all scanned objects auto-selected');
  assert.ok(!discovered.scanned.some(o=>o.id==='scene.keeper'),'never scan avatar');
  for(const id of ['#eyeScope','#eyeSummon','#eyeSelectAll','#eyeCollect'])assert.equal(await page.locator(id).count(),0,'removed scanner action: '+id);
  await page.waitForFunction(n=>document.querySelectorAll('#eyeScanNames li').length===n,count);
  let names=await readNames();
  assert.equal(names.length,count,'every scanned object has a right-side row');
  assert.ok(names.every(x=>x.id&&x.name.trim()),'real names or descriptive fallback');
  assert.ok(names.some(x=>x.id==='scene.floor'&&x.name==='地板'),'Chinese geometry label');
  assert.ok(names.some(x=>x.name.includes('之書')),'book names from original library');
  assert.match(await page.locator('#eyeCount').innerText(),new RegExp('掃描 '+count+' 件物件'));
  assert.doesNotMatch(await page.locator('#eyeCount').innerText(),/書本|其他物件|已選/,'scan count has no type distinctions or click selection');
  const visual=await page.locator('#eyeScanReadout').evaluate(n=>({background:getComputedStyle(n).backgroundColor,border:getComputedStyle(n).borderTopWidth}));
  assert.ok(visual.background==='rgba(0, 0, 0, 0)'||visual.background==='transparent','no panel background');
  assert.equal(visual.border,'0px','no panel frame');
  report.checks.push('right-hand frameless overlay lists '+count+' object names and count, without avatar or action buttons');

  // The live scanner set is automatically selected. No manual tap/mark state.
  const viewport=page.viewportSize();
  const candidates=discovered.scanned.filter(o=>o.x>25&&o.x<viewport.width-195&&o.y>210&&o.y<viewport.height-35).map(o=>({x:o.x,y:o.y}));
  for(let x=70;x<viewport.width-190;x+=90)for(let y=330;y<viewport.height-40;y+=90)candidates.push({x,y});
  let chosen=null,chosenId=null,chosenKind=null;
  for(const point of candidates){
   await page.mouse.move(point.x,point.y);await page.mouse.down();
   const inspected=await inspect();await page.mouse.up();
   if(inspected.hold?.type==='scan'){
    chosen=point;chosenId=inspected.hold.targetId;
    chosenKind=inspected.scanned.find(o=>o.id===chosenId)?.kind;break;
   }
  }
  assert.ok(chosen&&chosenId&&chosenKind,'a real scanned object can start a hold');
  const automatic=await inspect();
  assert.deepEqual(automatic.selected,automatic.scanned.map(o=>o.id),'every scanned target auto-selected');
  assert.doesNotMatch(await page.locator('#eyeCount').innerText(),/已選|已點選/);
  assert.equal((await readNames()).filter(x=>x.selected).length,0,'no selection markers');
  const hold=async ms=>{
   await page.mouse.move(chosen.x,chosen.y);await page.mouse.down();
   const snapshot=await inspect();
   assert.equal(snapshot.hold?.type,'scan');
   assert.deepEqual(snapshot.hold.scanIds,snapshot.scanned.map(o=>o.id));
   if(ms)await page.waitForTimeout(ms);
   else await page.waitForFunction(()=>document.querySelector('#truthEyeContractConfirm').open,null,{timeout:15000});
   await page.mouse.up();
   return snapshot;
  };
  await hold(180);
  assert.equal(await page.locator('#truthEyeContractConfirm').evaluate(e=>e.open),false,'short tap cannot contract');
  const afterTap=await inspect();
  assert.deepEqual(afterTap.selected,afterTap.scanned.map(o=>o.id),'tap cannot add or remove scan members');
  await hold();await page.locator('#truthEyeCancel').click();
  assert.deepEqual((await inspect()).packed,[],'cancel preserves world and save');
  const finalScan=await hold(),expected=finalScan.hold.scanIds;
  assert.match(await page.locator('#truthEyeConfirmText').innerText(),new RegExp(expected.length+' 件'));
  assert.doesNotMatch(await page.locator('#truthEyeConfirmText').innerText(),/書本|其他物件/,'all members are generic objects');
  await page.locator('#truthEyeApprove').click();
  await page.waitForFunction(()=>!LivingScene.truthEye.inspect().bundleBusy,null,{timeout:15000});
  const saved=await page.evaluate(()=>JSON.parse(localStorage.getItem('living-library-player-objects-v1')));
  const card=saved.objects.find(c=>c.scanContract);
  assert.ok(card);assert.equal(card.id,'local.card.medium');
  assert.deepEqual(card.scanContract.members.map(r=>r.id),expected,'only the exact scan snapshot is bound');
  assert.equal(saved.objects.length,3,'never create extra cards');
  assert.equal((await inspect()).packed.length,expected.length);
  report.checks.push('scanner set equals implicit selection; long hold binds exact visible snapshot without clicks');
  await page.screenshot({path:path.join(out,'object-longpress-packed.png')});

  await page.reload();await page.waitForFunction(()=>!!window.LivingScene?.truthEye,{timeout:45000});await page.waitForTimeout(700);
  if(await page.locator('#llUpdateDialog').evaluate(e=>e.open))await page.locator('#llUpdateDone').click();
  assert.equal((await inspect()).packed.length,expected.length,'same package persists after reload');
  const candidate=(await inspect()).cards.find(c=>c.id==='local.card.medium');
  assert.ok(candidate,'original bound card still available to restore');
  await page.mouse.move(candidate.x,candidate.y);await page.mouse.down();
  await page.waitForFunction(()=>document.querySelector('#truthEyeContractConfirm').open,null,{timeout:15000});
  await page.mouse.up();await page.locator('#truthEyeApprove').click();
  await page.waitForFunction(()=>!LivingScene.truthEye.inspect().bundleBusy,null,{timeout:15000});
  await page.waitForTimeout(550);
  assert.equal((await inspect()).packed.length,0,'card releases same world object');
  assert.ok((await inspect()).objects.some(x=>x.id===chosenId&&x.visible),'materialized object is physically present');
  assert.ok((await inspect()).scanned.some(x=>x.id===chosenId),'scanner still discovers materialized geometry');
  assert.ok((await readNames()).some(x=>x.id===chosenId),'world-form member remains visible in live scan readout');
  const owner=await page.evaluate(({id,kind})=>LivingScanBundle.claims(localStorage).get(kind+':'+id),{id:chosenId,kind:chosenKind});
  assert.equal(owner,'local.card.medium','member remains owned; independent re-binding is forbidden until spell unbind');
  report.checks.push('materialization restores visible geometry, scanner detects it, and persistent ownership still blocks rebinding');

  // Materialization is a form switch, NEVER a contract release.
  const restoredViaGesture=await page.evaluate(()=>{
   const B=LivingScanBundle,storage=localStorage;
   const card=LivingObjectCraft.listCards(storage).find(c=>c.id==='local.card.medium');
   return {blank:B.blank(card),claims:[...B.claims(storage)],contract:card.scanContract||null};
  });
  assert.equal(restoredViaGesture.blank,false,'3-second card materialization keeps original card bound');
  assert.equal(restoredViaGesture.contract.form,'world');
  assert.equal(restoredViaGesture.claims.length,expected.length,'all scanned world objects remain owned until spell is cast');
  report.checks.push('long-hold card switches world form without unbinding');

  // Only the one-shot unbinding spell releases the world-form contract.
  const unbound=await page.evaluate(()=>{
   const B=LivingScanBundle,C=LivingObjectCraft,storage=localStorage;
   const card=C.listCards(storage).find(c=>c.id==='local.card.medium');
   const spell=C.listCards(storage).find(c=>c.id==='local.card.spell');
   const before=B.claims(storage).size;
   const result=C.unbindAndAbsorbSpell(storage,{
    runeId:card.runeBinding.runeId,targetId:card.id,targetRevision:card.revision,
    spellId:spell.id,spellRevision:spell.revision});
   return {oldCount:before,newCard:result.target,spellId:result.spellId,remaining:[...B.claims(storage)],
    spellStillExists:C.read(storage).objects.some(x=>x.id===spell.id)};
  });
  assert.equal(unbound.oldCount,expected.length);
  assert.equal(unbound.newCard.title,'空白卡片');
  assert.equal(unbound.newCard.description,'');assert.equal(unbound.newCard.cardType,'');
  assert.equal(unbound.newCard.runeBinding,undefined);assert.equal(unbound.newCard.scanContract,undefined);
  assert.deepEqual(unbound.remaining,[]);
  assert.equal(unbound.spellId,'local.card.spell');
  assert.equal(unbound.spellStillExists,false,'spent spell record is permanently erased');
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
  // Regression: a historical 27-member world contract (including the
  // formerly scanable avatar) must be explicitly selectable and releasable.
  // No live user save or hard-coded object release is involved.
  const historical=await page.evaluate(({medium,spell,permanent})=>{
   const C=LivingObjectCraft,all=LivingScene.truthEye.inspect().objects;
   const usable=all.filter(o=>o.id!=='scene.keeper'&&
    (o.kind==='book'||(o.kind==='scene'&&/^scene\\.[a-z0-9.-]+$/.test(o.id))));
   const seen=new Set(),refs=[];
   for(const o of usable){const key=o.kind+':'+o.id;if(seen.has(key))continue;
    seen.add(key);refs.push({kind:o.kind,id:o.id});if(refs.length===26)break}
   if(refs.length!==26)throw Error('Need 26 physical members to simulate historical 27');
   refs.push({kind:'scene',id:'scene.keeper'});
   const eye=C.listCards(localStorage).find(c=>c.id==='local.card.eye');
   const legacy={...medium,title:'掃描收納卡',cardType:'收納契約',
    description:'真實之眼收納 27 件物件；原始身分保留。',
    runeBinding:{runeId:'scan.bundle.'+medium.id,state:'bound'},
    scanContract:{version:1,artifactId:'scan.bundle.'+medium.id,form:'world',members:refs}};
   const locked={...permanent,title:'登錄永久卡',permanentCard:true,
    runeBinding:{runeId:'generic.another-contract',state:'bound'}};
   localStorage.setItem('living-library-player-objects-v1',
    JSON.stringify({version:1,objects:[eye,legacy,spell,locked]}));
   return {members:refs.length,owner:legacy.id,spell:spell.id,permanent:locked.id};
  },{medium:blank('legacy27'),spell:{...blank('spell27'),title:'解除契約'},permanent:blank('permanent-proof')});
  await page.reload();await page.waitForFunction(()=>!!window.LivingScene?.truthEye,{timeout:45000});
  await page.waitForTimeout(850);
  if(await page.locator('#llUpdateDialog').evaluate(e=>e.open))await page.locator('#llUpdateDone').click();
  const before=await page.evaluate(()=>({
   owners:[...LivingScanBundle.claims(localStorage)],
   cards:LivingScene.truthEye.inspect().cards,
   originals:LivingObjectCraft.listCards(localStorage)
  }));
  assert.equal(before.owners.filter(([,owner])=>owner===historical.owner).length,27,
   'historical avatar-containing contract is still persisted before user confirms unbind');
  assert.equal(before.originals.find(c=>c.id===historical.permanent).permanentCard,true);
  const spellMesh=before.cards.find(c=>c.id===historical.spell);
  assert.ok(spellMesh,'explicit unbinding spell stays visible on player orbit');
  const cast=async()=>{
   await page.mouse.move(spellMesh.x,spellMesh.y);await page.mouse.down();
   await page.waitForFunction(()=>document.querySelector('#runeUnbindDialog').open,null,{timeout:15000});
   await page.mouse.up();
  };
  await cast();
  assert.equal(await page.locator('#runeUnbindConfirm').isDisabled(),true,
   'even one matching contract requires explicit user selection');
  assert.ok(await page.locator('#runeUnbindTargetPicker option[value="'+historical.owner+'"]').count(),
   'historical 27-object contract is a generic original-card candidate');
  assert.equal(await page.locator('#runeUnbindTargetPicker option[value="'+historical.permanent+'"]').count(),0,
   'a permanent original is never eligible for ordinary unbinding');
  await page.locator('#runeUnbindTargetPicker').selectOption(historical.owner);
  assert.match(await page.locator('#runeUnbindMembers').innerText(),/27 件物件/);
  await page.locator('#runeUnbindCancel').click();
  const cancelled=await page.evaluate(id=>({
   stillBound:LivingObjectCraft.listCards(localStorage).some(c=>c.id===id&&!!c.scanContract),
   claims:LivingScanBundle.claims(localStorage).size,
   spellStillPresent:LivingObjectCraft.listCards(localStorage).some(c=>c.id==='local.card.spell27')
  }),historical.owner);
  assert.ok(cancelled.stillBound&&cancelled.spellStillPresent);
  assert.equal(cancelled.claims,27,'cancel does not mutate the user's contract');
  await cast();
  await page.locator('#runeUnbindTargetPicker').selectOption(historical.owner);
  assert.equal(await page.locator('#runeUnbindTargetId').innerText(),historical.owner);
  await page.locator('#runeUnbindConfirm').click();
  const completed=await page.evaluate(({owner,spell,permanent})=>{
   const C=LivingObjectCraft,B=LivingScanBundle,all=C.listCards(localStorage);
   return {card:all.find(c=>c.id===owner),spellExists:all.some(c=>c.id===spell),
    permanent:all.find(c=>c.id===permanent),
    owned:[...B.claims(localStorage)],avatarVisible:!!LivingScene.truthEye.inspect().objects.find(o=>o.id==='scene.keeper')};
  },historical);
  assert.equal(completed.card.scanContract,undefined);
  assert.equal(completed.card.runeBinding,undefined);
  assert.equal(completed.card.title,'空白卡片');
  assert.equal(completed.spellExists,false,'confirmed spell consumed exactly once');
  assert.deepEqual(completed.owned,[],'27 historical members are fully released');
  assert.ok(completed.permanent?.permanentCard&&completed.permanent?.runeBinding,
   'unrelated permanent card remains unchanged');
  await page.reload();await page.waitForFunction(()=>!!window.LivingScene?.truthEye,{timeout:45000});
  const afterReload=await page.evaluate(()=>({
   owner:LivingObjectCraft.listCards(localStorage).find(c=>c.id==='local.card.legacy27'),
   claims:[...LivingScanBundle.claims(localStorage)]
  }));
  assert.ok(afterReload.owner&&!afterReload.owner.scanContract&&!afterReload.owner.runeBinding);
  assert.deepEqual(afterReload.claims,[],'unbinding persists through reload');
  report.checks.push('synthetic legacy 27-member world group including avatar: selectable, cancelled safely, confirmed atomically, and released after reload; permanent card untouched');
  assert.deepEqual(report.errors,[]);report.status='PASS';
 }catch(e){report.status='FAIL';report.failure=e.stack;if(page)await page.screenshot({path:path.join(out,'failure.png')}).catch(()=>{});throw e}
 finally{await fs.writeFile(path.join(out,'scan-bundle-browser.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));await browser.close();server.closeAllConnections();await new Promise(r=>server.close(r))}
})().catch(e=>{console.error(e);process.exitCode=1});
