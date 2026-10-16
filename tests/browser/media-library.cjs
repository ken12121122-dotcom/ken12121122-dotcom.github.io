const {chromium}=require('playwright');
const fs=require('node:fs/promises'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..'),origin='https://ken12121122-dotcom.github.io';
(async()=>{const browser=await chromium.launch({headless:true});try{
 const ctx=await browser.newContext();
 await ctx.route(origin+'/**',async route=>{
  const target=path.resolve(root,'.'+new URL(route.request().url).pathname);
  assert.ok(target.startsWith(root+path.sep));
  try{await route.fulfill({contentType:target.endsWith('.js')?'text/javascript':'text/html',body:await fs.readFile(target)})}
  catch{await route.fulfill({status:404,body:'Not found'})}
 });
 const page=await ctx.newPage(),errors=[];page.on('pageerror',err=>errors.push(err.message));
 await page.goto(origin+'/living-library/live/card-creation.html');
 const previous=await page.evaluate(()=>{localStorage.setItem('unrelated-game-data','unchanged');return localStorage.getItem('living-library-player-objects-v1')});
 await page.addScriptTag({url:origin+'/living-library/live/media-library.js'});
 const first=await page.evaluate(async()=>{
  const canvas=document.createElement('canvas');canvas.width=320;canvas.height=180;
  const c=canvas.getContext('2d');c.fillStyle='#456';c.fillRect(0,0,320,180);
  const blob=await new Promise(resolve=>canvas.toBlob(resolve,'image/jpeg',.78));
  const store=await NWMediaLibrary.open();
  const created=await store.putProcessed({blob,width:320,height:180});
  const duplicate=await store.putProcessed({blob,width:320,height:180});
  const meta=await store.getMetadata(created.asset_id);
  const read=await store.getBlob(created.asset_id);
  const all=await store.listMetadata();const summary=await store.stats();
  const missing=await store.getBlob('local.media.sha256.'+'f'.repeat(64));
  let invalid=null;try{await store.putProcessed({blob:new Blob(['<svg/>'],{type:'image/svg+xml'}),width:1,height:1})}catch(e){invalid=e.code}
  const hasNoDeleter=typeof store.delete==='undefined';
  store.close();let closed=null;try{await store.stats()}catch(e){closed=e.code}
  return {created,duplicate,meta,readType:read?.type,readSize:read?.size,all,summary,missing,invalid,closed,hasNoDeleter};
 });
 assert.equal(first.created.reused,false);assert.equal(first.duplicate.reused,true);
 assert.equal(first.created.asset_id,first.duplicate.asset_id);
 assert.equal(first.created.asset_id,first.meta.asset_id);
 assert.equal(first.readType,'image/jpeg');assert.equal(first.readSize,first.meta.byte_length);
 assert.equal(first.all.length,1);assert.equal(first.summary.count,1);
 assert.equal(first.summary.total_bytes,first.readSize);assert.equal(first.missing,null);
 assert.equal(first.invalid,'MEDIA_INVALID_IMAGE');assert.equal(first.closed,'MEDIA_LIBRARY_CLOSED');
 assert.equal(first.hasNoDeleter,true);
 await page.reload();await page.addScriptTag({url:origin+'/living-library/live/media-library.js'});
 const persisted=await page.evaluate(async()=>{
  const store=await NWMediaLibrary.open();const summary=await store.stats();
  const metadata=await store.listMetadata();const blob=await store.getBlob(metadata[0].asset_id);
  store.close();return {summary,blobSize:blob?.size};
 });
 assert.equal(persisted.summary.count,1);assert.equal(persisted.blobSize,first.readSize);
 assert.equal(await page.evaluate(()=>localStorage.getItem('living-library-player-objects-v1')),previous);
 assert.equal(await page.evaluate(()=>localStorage.getItem('unrelated-game-data')),'unchanged');
 assert.deepEqual(errors,[]);
 console.log('PASS: isolated IndexedDB media store: content dedupe, genuine JPEG bytes, metadata, persistence, limits, no card migration or deletion.');
 await ctx.close();
}finally{await browser.close()}})().catch(e=>{console.error(e);process.exit(1)});
