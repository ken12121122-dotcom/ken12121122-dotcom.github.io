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

test('floating titles and bookshelf spine names use the same book name source',async()=>{
 const s=threeModule(await text());
 assert.ok(s.includes("function bookTitle(b){return String(b.name"),'one source of names per book');
 assert.ok(s.includes('map:orbitTitleTex(b)'), 'floating tag generated from book model');
 assert.ok(s.includes('map:spineTitleTex(b)'), 'physical spine generated from same book model');
 assert.ok(s.includes('map:coverTex(b)'), 'cover uses same book record');
 assert.ok(s.includes('new THREE.Sprite(new THREE.SpriteMaterial'), '3D floating billboard, not a separate DOM list');
 assert.ok(s.includes('spinePanel.position.set(-.363,0,0)'), 'title is printed onto left-hand side of book');
 assert.ok(s.includes('spinePanel.rotation.y=-Math.PI/2'), 'spine title faces out after shelving');
 assert.ok(s.includes("g.userData.orbitLabel.visible=o.mode==='orbit'||!!o.flight"),'label follows flying book, not shelved book');
});
test('shelf book rotates its labelled spine toward aisle while preserving free camera',async()=>{
 const s=threeModule(await text());
 const placement=s.slice(s.indexOf('function targetFor(o,t){'),s.indexOf('function updateOrbit(',s.indexOf('function targetFor(o,t){')));
 assert.ok(placement.includes('new THREE.Vector3(0,1,0),Math.PI/2'),'books physically turn 90 degrees for shelving');
 assert.ok(placement.includes('const shelf=shelves[slot.shelfIndex]'),'physical shelf position remains authoritative');
 assert.ok(s.includes('Math.sin(theta)*horizontal'),'no fixed shelf camera');
 assert.ok(!s.includes('shelfViewIndex'),'locked shelf tour must never return');
});
test('GEN2 projection rename refreshes orbit label, spine, and cover in place',async()=>{
 const s=threeModule(await text());
 assert.ok(s.includes('function syncBookTitles(o,b)'), 'missing live rename updater');
 assert.ok(s.includes('syncBookTitles(o,b);'),'existing real mesh must be updated on projection rebuild');
 const fn=s.slice(s.indexOf('function syncBookTitles(o,b)'),s.indexOf('function releaseBook(',s.indexOf('function syncBookTitles(o,b)')));
 for(const layer of ['coverTex(b)','spineTitleTex(b)','orbitTitleTex(b)']){
  assert.ok(fn.includes(layer),'name source not reused for '+layer);
 }
 assert.ok(fn.includes('u.titleKey===key'),'unchanged names must not allocate new GPU textures every animation frame');
 assert.ok(s.includes('mat.map?.dispose();mat.dispose?.()'),'GPU textures released if books are removed');
 assert.ok(s.includes('!m.isSprite'),'must not dispose Three.js shared Sprite geometry');
});
test('name badges can be selected without breaking existing clickable books and legacy book counts',async()=>{
 const s=threeModule(await text());
 assert.ok(s.includes('orbitLabel.userData.bookId=b.id'),'sprite points to canonical book ID');
 assert.ok(s.includes('(x.isMesh||x.isSprite)&&x.userData.bookId'),'raycaster must accept labels and books');
 assert.ok(s.includes('window.LivingLibrary.summonBook(id)'),'shelved books remain summonable');
 const catalog=Catalog.buildCatalog();
 const gpt=catalog.books.find(b=>b.id==='gpt');
 const android=catalog.books.find(b=>b.id==='android');
 assert.ok(gpt?.name==='GPT 之書','GPT book title should remain intact');
 assert.ok(android?.name==='安卓之書','Android book title should remain intact');
 assert.ok(catalog.books.length>=14,'existing catalog entries must not be removed for labels');
});
