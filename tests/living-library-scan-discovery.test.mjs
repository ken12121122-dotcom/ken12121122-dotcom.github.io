import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const D=require('../living-library/live/scene-scan-discovery.js');
const B=require('../living-library/live/scan-bundle.js');
function node(type='Group',name=''){
 const n={type,name,isMesh:type==='Mesh',isLight:type==='Light',isPoints:false,isLine:false,isCamera:false,isSprite:false,
  children:[],parent:null,userData:{},position:{x:0,y:0,z:0},scale:{x:1,y:1,z:1},geometry:type==='Mesh'?{type:'BoxGeometry',parameters:{width:1,height:1,depth:1}}:null};
 n.add=function(o){o.parent=n;n.children.push(o);return o};return n;
}
function setup(){
 const scene=node('Scene'),keeper=scene.add(node('Group','Keeper'));keeper.add(node('Mesh','Body'));
 const floor=scene.add(node('Mesh','Floor'));floor.position.x=7;
 const shelf=scene.add(node('Group','Shelf'));shelf.add(node('Mesh','ShelfWall'));
 const book=scene.add(node('Group','ReaderBook'));book.add(node('Mesh','BookCover'));
 const eye=scene.add(node('Group','ScanEye'));eye.add(node('Mesh','Eye'));eye.userData.scanProtected=true;
 const card=scene.add(node('Group','PlayerCard'));card.add(node('Mesh','Card'));
 return {scene,keeper,floor,shelf,book,eye,card,
  legacyScenes:[{id:'scene.floor',kind:'scene',group:floor,groups:[floor]},{id:'scene.shelf.0',kind:'scene',group:shelf,groups:[shelf]}],
  books:[{id:'reference-book',group:book,b:{sourceLayer:'public'}}],floatingCards:[{group:card}]};
}
test('live geometry discovery is not a hardcoded target list; avatar and system graphics protected',()=>{
 const q=setup();
 const p={scene:q.scene,keeper:q.keeper,legacyScenes:q.legacyScenes,books:q.books,floatingCards:q.floatingCards};
 let got=D.discover(p);
 assert.ok(got.some(x=>x.id==='scene.floor'));assert.ok(got.some(x=>x.id==='scene.shelf.0'));
 assert.ok(got.some(x=>x.id==='reference-book'&&x.kind==='book'));
 assert.ok(!got.some(x=>x.group===q.keeper||x.group===q.eye||x.group===q.card));
 const invented=q.scene.add(node('Group','User-created machine'));invented.position.x=3;invented.add(node('Mesh','Gear'));
 got=D.discover(p);
 assert.ok(got.some(x=>x.group===invented),'new 3D object discovered without registerScene or allowlist');
 const match=got.find(x=>x.group===invented);assert.match(match.id,/^scene\.auto\./);
 assert.equal(match.kind,'scene');
 assert.ok(!got.some(x=>x.group===q.keeper));
});
test('structurally stable dynamic objects produce stable IDs across equivalent scene rebuilds',()=>{
 function create(){const q=setup();const gear=q.scene.add(node('Group','GearMachine'));gear.position.x=4;gear.add(node('Mesh','Gear'));return D.discover({scene:q.scene,keeper:q.keeper,legacyScenes:q.legacyScenes,books:q.books,floatingCards:q.floatingCards}).find(x=>x.group===gear)?.id}
 assert.equal(create(),create());
});
test('moving and animating anonymous objects never change scan identity in the same scene',()=>{
 const q=setup(),m=q.scene.add(node('Group','AnimatedArtifact'));
 const mesh=m.add(node('Mesh','AnimatedMesh'));
 const opts={scene:q.scene,keeper:q.keeper,legacyScenes:q.legacyScenes,books:q.books,floatingCards:q.floatingCards};
 const first=D.discover(opts).find(x=>x.group===m).id;
 m.position.x+=13;m.position.y-=2;mesh.position.z+=5;m.scale.x=2.2;
 const second=D.discover(opts).find(x=>x.group===m).id;
 assert.equal(first,second);
});
test('future user-authored persistent IDs are accepted without predefined scanner entries',()=>{
 const q=setup(),m=q.scene.add(node('Group','Original statue'));m.add(node('Mesh'));m.userData.scanObjectId='scene.statue.user-defined';
 const all=D.discover({scene:q.scene,keeper:q.keeper,legacyScenes:[],books:[]});
 assert.ok(all.some(x=>x.id==='scene.statue.user-defined'));
});
test('new scan contracts fail closed when avatar or protected objects are submitted',()=>{
 const blank={id:'local.card.blank',objectType:'card',templateId:'blank-card-v1',status:'draft',placement:'hover',title:'空白卡片',revision:1};
 let saved=JSON.stringify({version:1,objects:[blank]});
 const store={getItem:()=>saved,setItem(k,v){saved=v}};
 const binding=refs=>B.bind(store,{cardId:blank.id,revision:1,members:refs},refs);
 const before=saved;
 assert.throws(()=>binding([{kind:'scene',id:'scene.keeper'}]),/玩家角色/);
 assert.equal(saved,before);
 assert.throws(()=>binding([{kind:'scene',id:'scene.auto.future',protected:true}]),/不可用|掃描物件/);
 assert.equal(saved,before);
 const good={kind:'scene',id:'scene.auto.future'};
 assert.equal(binding([good]).scanContract.members[0].id,good.id);
});
test('scanner has no extra action buttons and names/count render as unframed live text',async()=>{
 const fs=await import('node:fs/promises');
 const source=await fs.readFile(new URL('../living-library/live/truth-eye-world.js',import.meta.url),'utf8');
 assert.doesNotMatch(source,/eyeSummon|eyeSelectAll|eyeCollect|collectButton/);
 assert.match(source,/id='eyeScanReadout'/);
 assert.match(source,/id='eyeScanNames'/);
 assert.match(source,/background:none!important;border:0!important/);
 assert.match(source,/scanName\(o\)/);
 assert.match(source,/if\(id&&start\('books',e\.pointerId,e\.clientX,e\.clientY,null,id\)\)/);
 assert.match(source,/const scanTargets=type==='books'\?targets\(\):null/);
 assert.match(source,/const members=scanned\.map\(o=>\(\{kind:o\.kind,id:o\.id\}\)\)/);
 assert.match(source,/selected:scannedTargets\(\)\.map\(o=>o\.id\)/);
 assert.doesNotMatch(source,/selected=new Set\(|dataset\.selected|已選 |已標記/);
 assert.match(source,/renderScanNames\(t\)/);
});
test('no preset book/library selection UI or scope filter survives in v2 source',async()=>{
 const fs=await import('node:fs/promises');
 const html=await fs.readFile(new URL('../living-library/live/index.html',import.meta.url),'utf8');
 const code=await fs.readFile(new URL('../living-library/live/truth-eye-world.js',import.meta.url),'utf8');
 assert.match(html,/scene-scan-discovery\.js/);
 assert.match(html,/LivingSceneScanDiscovery\.discover/);
 assert.doesNotMatch(code,/eyeScope|cfg\.scope|<option value="all">/);
 assert.match(code,/!o\.protected/);
});

test('hidden child geometry and protected helpers do not become scan members',()=>{
 const q=setup();
 const hidden=q.scene.add(node('Group','InvisibleLegacy'));
 const mesh=hidden.add(node('Mesh','PreviouslyRenderedObject'));mesh.visible=false;
 const protectedRoot=q.scene.add(node('Group','InternalHelper'));
 protectedRoot.userData.scanProtected=true;protectedRoot.add(node('Mesh','Internal'));
 const p={scene:q.scene,keeper:q.keeper,legacyScenes:q.legacyScenes,books:q.books,floatingCards:q.floatingCards};
 const found=D.discover(p);
 assert.ok(!found.some(x=>x.group===hidden||x.group===protectedRoot),'only physically visible unprotected geometry');
 mesh.visible=true;
 assert.ok(D.discover(p).some(x=>x.group===hidden),'visible new geometry becomes scannable without preset registry');
});
