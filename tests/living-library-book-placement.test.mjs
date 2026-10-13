import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';

const require=createRequire(import.meta.url);
const L=require('../living-library/book-placement.js');
const C=require('../living-library/public-catalog.js');
const html=()=>readFile(new URL('../living-library/index.html',import.meta.url),'utf8');

test('orbit layout generates multiple real rings instead of limiting to 7 books',()=>{
 assert.equal(L.ringCount(0),0);
 assert.equal(L.ringCount(7),1);
 assert.equal(L.ringCount(8),2);
 assert.equal(L.ringCount(17),2);
 assert.equal(L.ringCount(18),3);
 assert.equal(L.ring(0).ring,0);
 assert.equal(L.ring(7).ring,1);
 assert.equal(L.ring(17).ring,2);
 const positions=[...Array(95)].map((_,i)=>L.orbitPosition(i,0));
 assert.ok(positions.every(p=>Number.isFinite(p.x)&&Number.isFinite(p.y)&&Number.isFinite(p.z)));
 assert.ok(new Set(positions.map(p=>p.ring)).size>=5);
 assert.notDeepEqual(positions[0],positions[7]);
});

test('public and synthetic GEN2 books share unlimited ring layout and unchanged identities',()=>{
 const catalog=C.buildCatalog();
 assert.equal(catalog.books.length,14);
 assert.ok(catalog.books.some(b=>b.id==='gpt'));
 const enabled=catalog.books.map(b=>({...b,active:true}));
 assert.equal(L.ringCount(enabled.filter(b=>b.active).length),2);
 assert.ok(enabled.every((b,i)=>L.orbitPosition(i).ring<2));
 assert.ok(catalog.bookCards.startup.includes('public.startup.tasks'));
});

test('175 unique slots correspond to actual bookshelf geometry on the stage',()=>{
 assert.equal(L.SHELF_CAPACITY,175);
 const slots=[];
 for(let i=0;i<L.SHELF_CAPACITY;i++){
  const s=L.shelfSlot(i);
  assert.ok(s&&s.shelfIndex>=0&&s.shelfIndex<7);
  assert.ok([0,1,2,3,4].includes(s.row));
  assert.ok(s.col>=0&&s.col<5,'five visible empty compartments in each shelf row');
  slots.push([s.shelfIndex,s.row,s.col].join(':'));
 }
 assert.equal(new Set(slots).size,slots.length);
 assert.equal(L.shelfSlot(L.SHELF_CAPACITY),null);
});

test('real 3D book object changes from orbit to shelf with curved flight and clickable return',async()=>{
 const s=await html();
 assert.ok(s.includes('src="book-placement.js'));
 assert.ok(s.includes('const Placement3D=window.LivingBookPlacement'));
 assert.ok(s.includes("b.active=active;storeBooks()"));
 assert.ok(!s.includes("身邊最多先環繞 7 本書"));
 assert.ok(s.includes("const position=shelf.localToWorld(new THREE.Vector3(slot.x,slot.y,slot.z))"));
 assert.ok(s.includes("g.position.y+=Math.sin(Math.PI*v)*.72"));
 assert.ok(s.includes("g.quaternion.copy(flight.rotation).slerp(target.rotation,v)"));
 assert.ok(s.includes("window.LivingLibrary.summonBook(id)"));
 assert.ok(s.includes("const targets=[];bookEntities.forEach"));
 assert.ok(s.includes("if(((isMotion()||hasFlight())&&!reduced)||hasWalkInput())"),'book-flight or walking keeps render loop alive');
 assert.ok(s.includes("if(flight&&!preferReduced)"));
 assert.ok(s.includes("closeBook();updateUI()"),'book closes before return animation starts');
});

test('bookshelves show empty cubbies without fabricated book spines; camera rotates freely around 360 degrees',async()=>{
 const s=await html();
 const shelfPart=s.slice(s.indexOf('function makeShelf('),s.indexOf('// Pillars and vaulted',s.indexOf('function makeShelf(')));
 assert.ok(shelfPart.includes('for(let row=0;row<5;row++)'));
 assert.ok(shelfPart.includes('for(let col=0;col<5;col++)'));
 assert.ok(!shelfPart.includes('for(let col=0;col<23;col++)'), 'legacy fake spine decoration forbidden');
 assert.ok(!shelfPart.includes('spine=box('), 'unused slots must be empty');
 assert.ok(!s.includes('shelfViewIndex'), 'no hard-coded locked shelf view');
 assert.ok(!s.includes('focusShelf('), 'no forced shelf camera');
 const cam=s.slice(s.indexOf('function cameraUpdate(){'),s.indexOf('cameraUpdate();',s.indexOf('function cameraUpdate(){')));
 assert.ok(cam.includes('Math.sin(theta)*horizontal'), '360-degree yaw must be driven by freely changing theta');
 assert.ok(!cam.includes('Math.max(-1.15'), 'do not clamp horizontal orbit to a narrow arc');
 assert.ok(s.includes('if(wasMoving){theta-=dx*.005'), 'drag freely rotates scene, including when inspecting shelves');
 assert.ok(s.includes('distance=THREE.MathUtils.clamp(distance+(pinch-sep)*.032,exploring?4.5:7.5,exploring?13:27)'), 'pinch zoom remains available in viewing and exploration modes');
 assert.ok(s.includes("const position=shelf.localToWorld(new THREE.Vector3(slot.x,slot.y,slot.z))"), 'real book has physical 3D shelf slot');
 assert.ok(s.includes('7 櫃 × 5 層 × 5 格'), 'show truthful physical shelf capacity label');
});
