import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';

const load=()=>readFile(new URL('../living-library/index.html',import.meta.url),'utf8');
function blocks(s){
 const start=s.indexOf('function targetFor(o,t){');
 const end=s.indexOf('function updateOrbit(',start);
 assert.ok(start>=0&&end>start,'real Three.js target function must exist');
 return s.slice(start,end);
}

test('active magical books orbit around the walking avatar, not the fixed world origin',async()=>{
 const code=blocks(await load());
 assert.ok(code.includes('p.x+bookOrbitAnchor.x'));
 assert.ok(code.includes('p.z+bookOrbitAnchor.z'));
 const run=new Function('mode','ax','az',[
  'const bookOrbitAnchor={x:ax,z:az};',
  'const selectedId="none";',
  'const THREE={Vector3:class {constructor(x,y,z){this.x=x;this.y=y;this.z=z}},Quaternion:class {setFromEuler(){return this}},Euler:class {},};',
  'const Placement3D={orbitPosition:()=>({x:2.3,y:2.55,z:1.4,angle:0,scale:.95}),shelfSlot:()=>({shelfIndex:0,x:1,y:3,z:2})};',
  'const shelves=[{updateWorldMatrix(){},localToWorld(v){v.x+=14;v.z-=11;return v},getWorldQuaternion(){return {multiply(){return this}}}}];',
  code,
  'return targetFor({mode,index:0,b:{id:"sample"}},0).position;',
 ].join('\n'));
 for(const mode of ['orbit','shelf']){
  const a=run(mode,0,0),b=run(mode,4,-3);
  if(mode==='orbit'){
   assert.equal(b.x-a.x,4,'active book follows avatar x exactly');
   assert.equal(b.z-a.z,-3,'active book follows avatar z exactly');
  }else{
   assert.equal(b.x,a.x,'archived book remains in its physical shelf slot');
   assert.equal(b.z,a.z,'archived book does not teleport with avatar');
  }
 }
});

test('only orbit anchor follows avatar and original floor summon sigil stays fixed',async()=>{
 const s=await load();
 assert.ok(s.includes('const bookOrbitAnchor=new THREE.Vector3(0,0,0)'));
 assert.ok(s.includes('bookOrbitAnchor.set(keeper.position.x,0,keeper.position.z)'));
 const ground=s.slice(s.indexOf('const floor=cyl('),s.indexOf('// Grand bookcases',s.indexOf('const floor=cyl(')));
 assert.ok(ground.includes('torus(i*1.9,.013,0,-.24,0,stone2,scene'), 'ground rings must attach to scene at origin');
 assert.ok(!ground.includes('bookOrbitAnchor'), 'world landmark must not become avatar child');
 assert.ok(!s.includes('keeper.add(floor)'), 'floor remains scene rooted');
 assert.ok(s.includes('g.position.copy(flight.position).lerp(target.position,v)'), 'shelving flight stays smooth');
 assert.ok(s.includes('window.LivingLibrary.summonBook(id)'), 'archived books are still summonable');
});

test('new orbit followers preserve existing free-view and real shelf movement',async()=>{
 const s=await load();
 assert.ok(s.includes('camera.position.set(look.x+Math.sin(theta)*horizontal'), 'camera follows avatar independently of book anchor');
 assert.ok(s.includes('showExploreMode(!exploring)'), 'explore mode can be exited');
 assert.ok(s.includes('const shelf=shelves[slot.shelfIndex]'), 'books still have real shelf locations');
 assert.ok(s.includes('let exploring=false'), 'exploration is opt in');
});
