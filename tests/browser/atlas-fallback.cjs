const {chromium}=require('playwright'),fs=require('node:fs/promises'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..'),origin='https://ken12121122-dotcom.github.io';
(async()=>{const browser=await chromium.launch({headless:true});try{
 const ctx=await browser.newContext({viewport:{width:412,height:915}});await ctx.route(origin+'/**',async r=>{const u=new URL(r.request().url());const file=path.resolve(root,'.'+u.pathname);assert.ok(file.startsWith(root+path.sep));try{await r.fulfill({contentType:file.endsWith('.js')?'text/javascript':'text/html',body:await fs.readFile(file)})}catch{await r.fulfill({status:404,body:'Not found'})}});
 const p=await ctx.newPage();await p.goto(origin+'/living-library/live/atlas-controls.html');
 const id=await p.evaluate(()=>{const c=LivingObjectCraft.createBlankCard(localStorage,crypto);LivingObjectCraft.editCard(localStorage,c.id,{title:'測試收藏卡',description:'原卡資料'});return c.id});await p.reload();
 await p.getByRole('button',{name:'測試收藏卡',exact:true}).click();await p.getByRole('button',{name:'讀卡區：空',exact:true}).click();assert.match(await p.locator('#screen').innerText(),/測試收藏卡/);
 await p.reload();assert.match(await p.locator('#screen').innerText(),/測試收藏卡/);
 await p.getByRole('button',{name:'讀卡區：測試收藏卡',exact:true}).click();await p.getByRole('button',{name:'卡片之書',exact:true}).click();assert.equal(await p.locator('.slot').count(),18);await p.getByRole('button',{name:'卡槽 18：空',exact:true}).click();
 assert.equal(await p.evaluate(()=>LivingObjectCraft.listCards(localStorage)[0].containerId),'local.atlas.collection');await p.reload();await p.getByRole('button',{name:'卡片之書',exact:true}).click();await p.getByRole('button',{name:'卡槽 18：測試收藏卡',exact:true}).click();await p.getByRole('button',{name:'選取卡片回到軌道',exact:true}).click();
 const card=await p.evaluate(()=>LivingObjectCraft.listCards(localStorage)[0]);assert.equal(card.id,id);assert.equal(card.containerId,null);assert.equal(card.description,'原卡資料');
 console.log('PASS: real browser 2D atlas/collection transfer, reload persistence and orbit return; original card identity/data retained.');await ctx.close();
}finally{await browser.close()}})().catch(e=>{console.error(e);process.exit(1)});
