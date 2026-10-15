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
 assert.match(html,/scene\.startCardSummon\(id\); \/\/ Stage the invisible card before the first scene frame/);
 assert.match(html,/scene\.startCardSummon\(id\)/);
 assert.match(html,/function startCardSummon\(id\)/);
 assert.match(html,/createCardSmoke\(origin\)/);
 assert.match(html,/new THREE\.Sprite\(material\)/);
 assert.match(html,/function hasCardSummon\(\)/);
 assert.match(html,/hasCardSummon\(\)/);
 assert.match(html,/CardPlacement\.orbitPosition\(i,currentOrbitTime,controlPrefs\)/);
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

test('System Book contains bounded working game-parameter controls, not a new duplicate card',async()=>{
 const page=await read('living-library/live/system-controls.html');
 const live=await read('living-library/live/index.html');
 const registry=require('../living-library/live/public-catalog.js').buildCatalog();
 assert.ok(registry.bookCards.system.includes('public.system.controls'));
 assert.equal(registry.cards['public.system.controls'].url,'./system-controls.html');
 for(const key of ['cardSize','cardOrbitDistance','cardOrbitSpeed','cardSummonDuration']){
  assert.match(page,new RegExp('id="'+key+'"'));
  assert.match(page,new RegExp('id="'+key+'Value"'));
  assert.match(page,new RegExp("send\\('update-preferences'"));
  assert.match(live,new RegExp(key+':clampUnit'));
 }
 assert.match(page,/id="resetCardParams" aria-label="恢復卡片參數預設值"/);
 assert.match(page,/restoreCardValues\(cardParameterDefaults\)/);
 assert.match(live,/for\(const \[field,limits\] of Object\.entries/);
 assert.match(live,/const entry=items\[i\],p=CardPlacement\.orbitPosition\(i,currentOrbitTime,controlPrefs\)/);
 assert.match(live,/age\/duration/);
 assert.match(live,/\*controlPrefs\.cardSize/);
 assert.doesNotMatch(page,/github_pat_|sk-proj-/);
});
test('card orbit tuner clamps range, leaves core book orbit unchanged, and supports pausing',()=>{
 const original=Books.orbitPosition(0,0);
 const opts=C.normalizeOptions({cardSize:999,cardOrbitDistance:999,cardOrbitSpeed:-12,cardSummonDuration:1e9});
 assert.deepEqual(opts,{cardSize:1.2,cardOrbitDistance:1.06,cardOrbitSpeed:0,cardSummonDuration:4500});
 const frozen=C.orbitPosition(0,11,{cardOrbitSpeed:0});
 const later=C.orbitPosition(0,300,{cardOrbitSpeed:0});
 assert.ok(Math.abs(frozen.x-later.x)<1e-10 && Math.abs(frozen.z-later.z)<1e-10);
 const near=C.orbitPosition(0,0,{cardOrbitDistance:.88});
 const far=C.orbitPosition(0,0,{cardOrbitDistance:1.06});
 assert.ok(Math.hypot(near.x,near.z)<Math.hypot(far.x,far.z));
 assert.ok(Math.hypot(far.x,far.z)<2.78*.82+.12-.35);
 assert.deepEqual(Books.orbitPosition(0,0),original);
 assert.deepEqual(C.normalizeOptions({cardSize:Infinity,cardOrbitDistance:'2.0'}),C.DEFAULTS);
});
