import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
const require=createRequire(import.meta.url);
const Catalog=require('../living-library/public-catalog.js');
const text=()=>readFile(new URL('../living-library/index.html',import.meta.url),'utf8');
function threeModule(html){
 const inline=[...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)]
  .map(m=>m[1]).find(x=>x.includes('await import('));
 assert.ok(inline,'3D controller missing');return inline;
}

test('printed book cover and real shelf spine share book title; no hovering text boxes',async()=>{
 const s=threeModule(await text());
 assert.ok(s.includes("function bookTitle(b){return String(b.name"),'one source of names per book');
 assert.ok(s.includes('map:spineTitleTex(b)'), 'spine title generated from same book model');
 assert.ok(s.includes('map:coverTex(b)'), 'front cover generated from same book model');
 assert.ok(!s.includes('new THREE.Sprite('), 'do not draw title billboards above floating books');
 assert.ok(!s.includes('function orbitTitleTex('), 'old floating banner texture must be removed');
 assert.ok(!s.includes('orbitLabel'), 'never attach nameplate to book mesh');
 assert.ok(s.includes('spinePanel.position.set(-.363,0,0)'), 'title is printed onto actual side of book');
 assert.ok(s.includes('spinePanel.rotation.y=-Math.PI/2'), 'spine title faces aisle');
});
test('shelf book rotates its labelled spine toward aisle while preserving free camera',async()=>{
 const s=threeModule(await text());
 const placement=s.slice(s.indexOf('function targetFor(o,t){'),s.indexOf('function updateOrbit(',s.indexOf('function targetFor(o,t){')));
 assert.ok(placement.includes('new THREE.Vector3(0,1,0),Math.PI/2'),'books physically turn 90 degrees for shelving');
 assert.ok(placement.includes('const shelf=shelves[slot.shelfIndex]'),'physical shelf position remains authoritative');
 assert.ok(s.includes('Math.sin(theta)*horizontal'),'no fixed shelf camera');
 assert.ok(!s.includes('shelfViewIndex'),'locked shelf tour must never return');
});
test('GEN2 projection rename refreshes printed front cover and side spine in place',async()=>{
 const s=threeModule(await text());
 assert.ok(s.includes('function syncBookTitles(o,b)'), 'missing live rename updater');
 assert.ok(s.includes('syncBookTitles(o,b);'),'existing real mesh must be updated on projection rebuild');
 const fn=s.slice(s.indexOf('function syncBookTitles(o,b)'),s.indexOf('function releaseBook(',s.indexOf('function syncBookTitles(o,b)')));
 for(const layer of ['coverTex(b)','spineTitleTex(b)']){
  assert.ok(fn.includes(layer),'name source not reused for '+layer);
 }
 assert.ok(fn.includes('u.titleKey===key'),'unchanged names must not allocate new GPU textures every animation frame');
 assert.ok(s.includes('mat.map?.dispose();mat.dispose?.()'),'GPU textures released if books are removed');
});
test('printed covers stay clickable with legacy books and shelf summons',async()=>{
 const s=threeModule(await text());
 assert.ok(s.includes('m.userData.bookId=b.id'),'printed book mesh keeps canonical ID');
 assert.ok(s.includes('x.isMesh&&x.userData.bookId'),'raycaster must select real books, not extra billboards');
 assert.ok(s.includes('window.LivingLibrary.summonBook(id)'),'shelved books remain summonable');
 const catalog=Catalog.buildCatalog();
 const gpt=catalog.books.find(b=>b.id==='gpt');
 const android=catalog.books.find(b=>b.id==='android');
 assert.ok(gpt?.name==='GPT 之書','GPT book title should remain intact');
 assert.ok(android?.name==='安卓之書','Android book title should remain intact');
 assert.ok(catalog.books.length>=14,'existing catalog entries must not be removed for labels');
});

test('large high-contrast book cover typography uses dynamic title fitting',async()=>{
 const s=threeModule(await text());
 const cover=s.slice(s.indexOf('function coverTex(b){'),s.indexOf('const coverMaterials=',s.indexOf('function coverTex(b){')));
 assert.ok(cover.includes('c.width=512;c.height=768'), 'increase cover texture resolution');
 assert.ok(cover.includes('const maxWidth=424'), 'scale long titles within cover');
 assert.ok(cover.includes('ctx.measureText(line).width>maxWidth'), 'long titles must auto-fit');
 assert.ok(cover.includes('ctx.strokeText(line,256,y,maxWidth)'), 'dark stroke for high contrast');
 assert.ok(cover.includes("ctx.fillStyle='#fff9e8'"), 'warm white primary title');
 assert.ok(cover.includes("ctx.strokeStyle='#07101d'"), 'near-black outline');
 assert.ok(cover.includes('b.color||'), 'existing book category color is preserved as accent');
 assert.ok(cover.includes("ctx.font='900 '+size+'px"), 'main book title uses dynamic large heavy font');
 assert.ok(!cover.includes('fillText(bookTitle(b),128,203'), 'old tiny centered book title must be removed');
});
