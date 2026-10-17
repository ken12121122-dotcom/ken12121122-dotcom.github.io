import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const Catalog=require('../living-library/live/public-catalog.js');
const Inventory=require('../living-library/live/world-object-inventory.js');
const read=p=>readFile(new URL('../'+p,import.meta.url),'utf8');
const mockCraft={
 getRuneBoundCard:()=>({id:'local.card.origin',runeBinding:{runeId:'local.rune.origin',state:'sealed'}}),
 listBooks:()=>[{id:'local.book.test',title:'自製魔法書',active:true}],
 floatingCards:()=>[{id:'local.card.test',title:'測試漂浮卡'}]
};
test('System Book has a unique read-only 3D inventory card and uses same-origin reader',async()=>{
 const data=Catalog.buildCatalog();
 assert.ok(data.bookCards.system.includes('public.system.world.inventory'));
 assert.equal(data.bookCards.system.filter(x=>x==='public.system.world.inventory').length,1);
 const card=data.cards['public.system.world.inventory'];
 assert.equal(card.title,'3D物件盤點');
 assert.equal(card.url,'./3d-object-inventory.html');
 assert.equal(card.kind,'public_function');
 const [html,index]=await Promise.all([read('living-library/live/3d-object-inventory.html'),read('living-library/live/index.html')]);
 assert.match(index,/new URL\('\.\/3d-object-inventory\.html',current\)\.pathname/);
 assert.match(html,/<h1>3D 物件盤點<\/h1>/);
 assert.doesNotMatch(html,/<\s*(?:button|input|form|select|textarea)\b/i);
 assert.doesNotMatch(html,/localStorage\.(?:setItem|removeItem|clear)/);
 assert.match(html,/textContent=value/,'display free-form local names with textContent rather than HTML');
});
test('inventory counts only authored semantic world groups, not individual construction meshes',()=>{
 const fixed=Inventory.GROUPS;
 assert.equal(fixed.length,10);
 assert.equal(fixed.reduce((n,x)=>n+x.count,0),81);
 assert.equal(new Set(fixed.map(x=>x.id)).size,fixed.length);
 assert.ok(fixed.some(x=>x.id==='scene.shelves'&&x.count===7));
 assert.ok(fixed.some(x=>x.id==='scene.lamps'&&x.count===5));
 assert.ok(fixed.some(x=>x.id==='local.rune.origin'&&x.capability==='conditional'));
 const snap=Inventory.build({catalog:Catalog,craft:mockCraft,storage:{}});
 assert.equal(snap.supported,1);
 assert.equal(snap.sceneInstances,81);
 assert.equal(snap.rows.filter(x=>x.kind.includes('書本')).length,17);
 assert.equal(snap.rows.filter(x=>x.kind.includes('卡片')).length,1);
 assert.equal(snap.rows.find(x=>x.id==='local.rune.origin').state,'已封印，可具現化為原卡');
 assert.equal(snap.rows.find(x=>x.id==='local.book.test').capability,'不適用（已是書本）');
 assert.ok(snap.rows.filter(x=>x.capability.startsWith('已實作')).every(x=>x.id==='local.rune.origin'));
});
test('rune state follows original binding and never invents materialization for other meshes',()=>{
 assert.equal(Inventory.runeState(null),'未締結契約');
 assert.equal(Inventory.runeState({runeBinding:{state:'bound'}}),'已締結契約，待封印');
 assert.equal(Inventory.runeState({runeBinding:{state:'sealed'}}),'已封印，可具現化為原卡');
 assert.equal(Inventory.runeState({runeBinding:{state:'cardified'}}),'已具現化為原卡');
 assert.equal(Inventory.runeState(null,false),'本機卡片資料無法讀取');
 const unreadable=Inventory.build({catalog:Catalog,craft:{getRuneBoundCard:()=>{throw Error('corrupt')}},storage:{}});
 assert.equal(unreadable.readable,false);
 assert.equal(unreadable.rows.find(x=>x.id==='local.rune.origin').state,'本機卡片資料無法讀取');
 assert.equal(unreadable.rows.filter(x=>x.kind==='3D 書本').length,15);
});
test('inventory page scripts remain syntactically valid',async()=>{
 const h=await read('living-library/live/3d-object-inventory.html');
 const scripts=[...h.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)].filter(m=>m[2].trim());
 assert.equal(scripts.length,1);
 for(const m of scripts)assert.doesNotThrow(()=>new Function(m[2]));
});
