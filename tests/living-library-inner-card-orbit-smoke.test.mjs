import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const C=require('../living-library/live/card-placement.js');
const Books=require('../living-library/live/book-placement.js');
const read=path=>readFile(new URL('../'+path,import.meta.url),'utf8');
test('all 64 new card slots remain on the inner lane between avatar and book orbit',()=>{
 assert.equal(C.MAX_CARDS,64);
 assert.equal(C.RADII.length,C.HEIGHTS.length);
 const minBookRadius=2.78*.82+.12;
 for(const time of [0,3,120]){
  for(let i=0;i<C.MAX_CARDS;i++){
   const p=C.orbitPosition(i,time),r=Math.hypot(p.x,p.z);
   assert.ok(Math.abs(p.radius-r)<1e-10,'card moves on a circular inner orbit');
   assert.ok(r>1.3,'must not overlap the avatar centre');
   assert.ok(r<minBookRadius-.55,'must remain well inside the lowest book ring');
   assert.ok(p.y>=2.1,'cards hover off the floor');
  }
 }
 assert.ok(C.RADII.every(x=>x<minBookRadius-.55));
 assert.equal(Books.orbitPosition(0,0).ring,0);
});
test('cards have separate angular spacing across all tiers, with stable IDs across rotations',()=>{
 for(let r=0,offset=0;r<C.CAPACITIES.length;offset+=C.CAPACITIES[r++]){
  const n=C.CAPACITIES[r],points=Array.from({length:n},(_,i)=>C.orbitPosition(offset+i,0));
  for(let i=0;i<n;i++){
   const a=points[i],b=points[(i+1)%n];
   const distance=Math.hypot(a.x-b.x,a.z-b.z);
   assert.ok(distance>.65,'small cards (.45 wide) need breathing space');
  }
 }
 assert.throws(()=>C.orbitPosition(-1),/invalid/);
 assert.throws(()=>C.orbitPosition(64),/invalid/);
});
test('new creation event exits its iframe book before visible smoke in the original 3D scene',async()=>{
 const html=await read('living-library/live/index.html');
 const creator=await read('living-library/live/card-creation.html');
 assert.match(creator,/notify\('card-created',card\.id\)/);
 assert.match(creator,/notify\('card-edited',card\.id\)/);
 assert.match(html,/event\.source!==\$\('readerFrame'\)\.contentWindow/);
 assert.match(html,/readerCard\?\.id==='public\.gaming\.card\.creation'/);
 assert.match(html,/floatingCards\(localStorage\)\.some\(card=>card\.id===id\)/);
 assert.match(html,/closeBook\(\); \/\/ Reveal the real 3D scene immediately/);
 assert.match(html,/scene\.startCardSummon\(id\)/);
 assert.match(html,/function startCardSummon\(id\)/);
 assert.match(html,/createCardSmoke\(origin\)/);
 assert.match(html,/new THREE\.Sprite\(material\)/);
 assert.match(html,/function hasCardSummon\(\)/);
 assert.match(html,/hasCardSummon\(\)/);
 assert.match(html,/CardPlacement\.orbitPosition\(i,currentOrbitTime\)/);
 assert.match(html,/BoxGeometry\(\.45,\.66,\.025\)/);
 assert.match(html,/refreshFloatingCards,startCardSummon,exitCardFocus/);
 const p=await read('living-library/live/card-placement.js');
 assert.match(p,/Independent|INNER|Inner/);
 assert.doesNotMatch(p,/6\.35|7\.42|8\.48/);
});
test('original book placement math is not changed',()=>{
 assert.equal(Books.orbitPosition(0,0).ring,0);
 assert.equal(Books.ringCount(14),2);
 assert.equal(C.MAX_CARDS,64);
});
