// Actual Three.js geometry/raycast checks without a renderer or a test-only application path.
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),THREE=require('three');
const craft=require('../../living-library/live/object-craft.js'),State=require('../../living-library/live/atlas-state.js');
const root=require('node:path').resolve(__dirname,'../..');let clock=0;
function element(){return {hidden:false,dataset:{},listeners:{},addEventListener(k,fn){this.listeners[k]=fn},click(){this.listeners.click?.()},getContext(){return context},width:0,height:0}}
const gradient={addColorStop(){}},context=new Proxy({createLinearGradient:()=>gradient,createRadialGradient:()=>gradient},{get(o,k){return k in o?o[k]:(()=>{})},set(o,k,v){o[k]=v;return true}});
const bodyClasses=new Set();const els=new Map();const doc={body:{classList:{toggle(k,on){if(on)bodyClasses.add(k);else bodyClasses.delete(k)}}},getElementById:id=>{if(!els.has(id))els.set(id,element());return els.get(id)},createElement:()=>element()};
const host={document:doc,innerWidth:900,innerHeight:650,performance:{now:()=>clock},addEventListener(){},console};host.self=host;
for(const name of ['atlas-state.js','compendium-card.js','atlas-model.js','atlas-runtime.js'])vm.runInNewContext(fs.readFileSync(root+'/living-library/live/'+name,'utf8'),host);
// The left leaf must fold through the page-facing (+Z) half-space, never behind the cover.
for(const collection of [false,true]){
 const model=host.LivingAtlasModel.create(THREE,collection),leaf=model.targets.find(t=>t.index===0).node;
 model.setOpen(1);model.group.updateMatrixWorld(true);const open=leaf.getWorldPosition(new THREE.Vector3());assert.ok(open.x<0);
 model.setOpen(.5);model.group.updateMatrixWorld(true);const half=leaf.getWorldPosition(new THREE.Vector3());assert.ok(half.z>1,'left page folds inward over the right page');
 model.setOpen(0);model.group.updateMatrixWorld(true);const closed=leaf.getWorldPosition(new THREE.Vector3());assert.ok(closed.z<0);assert.ok(closed.x>0,'left leaf reaches right-hand closed pose');model.dispose();
}
const scene=new THREE.Scene(),camera=new THREE.PerspectiveCamera(48,900/650,.1,110),keeper=new THREE.Group();keeper.position.set(0,.46,0);scene.add(keeper);camera.position.set(0,3,12);camera.lookAt(0,2,0);camera.updateMatrixWorld();
const backing=new Map(),storage={getItem:k=>backing.get(k)??null,setItem:(k,v)=>backing.set(k,v)},cards=new Map(),canvas={parentElement:element(),getBoundingClientRect:()=>({left:0,top:0,width:900,height:650}),setPointerCapture(){}};
const outer=new THREE.Group();outer.add(new THREE.Mesh(new THREE.BoxGeometry(.9,1.3,.2),new THREE.MeshBasicMaterial()));outer.position.set(4,3,0);scene.add(outer);
const entities=new Map([[State.COLLECTION,outer]]);
let smokes=0,removed=0,opened=[],observed=[],messages=[];host.LivingScene={refreshFloatingCards:refresh,syncRender(){}};
function refresh(){for(const r of craft.listCards(storage)){if(cards.has(r.id))cards.get(r.id).record=r;else{const g=new THREE.Group(),mesh=new THREE.Mesh(new THREE.BoxGeometry(.45,.66,.025),new THREE.MeshBasicMaterial());mesh.userData.localCardId=r.id;g.add(mesh);g.position.set(0,2,4);scene.add(g);cards.set(r.id,{id:r.id,record:r,group:g})}}}
const controls={};const runtimeOptions={controls,THREE,scene,camera,canvas,keeper,cards,craft,storage,smoke:{create(){smokes++;return []},update(){},remove(){removed++}},toast:x=>messages.push(x),invalidate(){},openCard:id=>opened.push(id),observeCard:id=>observed.push(id),focusCard(){},collectionEntity:id=>entities.get(id),setCollectionActive(){},reduced:false};const runtime=host.LivingAtlasRuntime.create(runtimeOptions);
function tick(ms){clock+=ms;runtime.tick(clock);scene.updateMatrixWorld(true);camera.updateMatrixWorld(true)}
function group(id){return scene.children.find(x=>x.userData.atlasId===id)}
function meshSlot(id,index){let mesh;group(id).traverse(x=>{if(x.userData.atlasSlot===index)mesh=x});return mesh}
function point(obj){scene.updateMatrixWorld(true);camera.updateMatrixWorld(true);const p=obj.getWorldPosition(new THREE.Vector3()).project(camera);return {clientX:(p.x+1)*450,clientY:(1-p.y)*325,pointerId:1}}
function moveTo(id,index,card){const from=point(card.group),to=point(meshSlot(id,index));assert.equal(runtime.pointerDown(from),true);runtime.pointerMove({...to,clientX:to.clientX+1});runtime.pointerUp(to);tick(1)}
assert.equal(runtime.inspect().atlasVisible,false);runtime.toggle();tick(250);assert.equal(runtime.inspect().atlasOpen,0);tick(1200);assert.equal(runtime.inspect().atlasOpen,1);assert.ok(runtime.inspect().atlasFacingKeeper>.8,'open pages face the character rather than the overview camera');assert.equal(smokes,removed);
// A new Book command reverses the current transition rather than waiting for it.
runtime.toggle();tick(180);runtime.toggle();tick(1200);assert.equal(runtime.inspect().atlasVisible,true);assert.equal(runtime.inspect().atlasOpen,1);
// Repeated Book commands queue the final desired state without losing card placement.
runtime.toggle();tick(300);assert.ok(runtime.inspect().atlasOpen<1);assert.equal(runtime.inspect().atlasVisible,true);tick(1400);assert.equal(runtime.inspect().atlasVisible,false);runtime.toggle();tick(1900);
runtime.focus(State.ATLAS);tick(900);runtime.exitFocus();tick(900);
const card=craft.createBlankCard(storage,{randomUUID:()=> 'scene-card'});refresh();
for(let i=0;i<10;i++){
 if(i)craft.placeCard(storage,card.id,null);refresh();const entry=cards.get(card.id);entry.group.visible=true;entry.group.position.set(0,2,4);entry.group.quaternion.copy(camera.quaternion);
 moveTo(State.ATLAS,i,entry);assert.equal(craft.listCards(storage)[0].slotIndex,i,'raycast/drop into slot '+i);assert.equal(craft.listCards(storage)[0].containerId,State.ATLAS);
}
// Reader can only open the card physically occupying the right reader slot.
runtime.focus(State.ATLAS);tick(900);let confirm;group(State.ATLAS).traverse(x=>{if(x.userData.atlasControl==='confirm')confirm=x});runtime.pointerDown(point(confirm));runtime.pointerUp(point(confirm));assert.deepEqual(opened,[card.id]);
const control=name=>{let target;group(State.ATLAS).traverse(x=>{if(x.userData.atlasControl===name)target=x});return target};
const tapControl=name=>{const e=point(control(name));runtime.pointerDown(e);runtime.pointerUp(e);tick(1)};
const beforeObservation=storage.getItem(craft.KEY);
tapControl('right');assert.equal(runtime.inspect().screenMode,'observe');
tapControl('confirm');assert.deepEqual(observed,[card.id]);assert.equal(storage.getItem(craft.KEY),beforeObservation,'AI read must not change source');
tapControl('up');assert.equal(runtime.inspect().screenMode,'card');
tapControl('confirm');assert.deepEqual(opened,[card.id,card.id],'existing reader confirm remains reachable');
runtime.placeCollection();tick(1900);assert.equal(runtime.inspect().collectionOpen,1);assert.equal(outer.visible,false);
for(let i=0;i<18;i++){moveTo(State.COLLECTION,i,cards.get(card.id));assert.equal(craft.listCards(storage)[0].slotIndex,i)}assert.equal(craft.listCards(storage)[0].containerId,State.COLLECTION);assert.equal(craft.listCards(storage)[0].slotIndex,17);
runtime.exitFocus();tick(900);
// Focus is an actual interpolated camera; exit returns to the pre-focus pose.
const before=camera.position.clone();runtime.focus(State.ATLAS);tick(400);const halfway=camera.position.clone();assert.ok(halfway.distanceTo(before)>.01);tick(500);const focused=camera.position.clone();const heading=new THREE.Vector3(0,0,1).applyQuaternion(camera.quaternion);assert.ok(Math.abs(runtime.movementYaw-Math.atan2(heading.x,heading.z))<1e-9);assert.ok(halfway.distanceTo(focused)>.01);
host.innerWidth=412;host.innerHeight=915;camera.aspect=412/915;camera.updateProjectionMatrix();tick(200);assert.ok(Number.isFinite(camera.position.z));runtime.exitFocus();tick(900);assert.ok(camera.position.distanceTo(before)<1e-6);
// Collection close leaves the physical book in the inner orbit and preserves cards.
runtime.focus(State.COLLECTION);tick(900);doc.getElementById('atlasCollectionClose').click();tick(700);assert.equal(runtime.inspect().collectionMode,'inner');assert.equal(runtime.inspect().collectionOpen,0);assert.equal(group(State.COLLECTION).visible,true);assert.equal(craft.listCards(storage)[0].slotIndex,17);
// Atlas hide/show never creates a copy of an existing card.
runtime.toggle();tick(1700);assert.equal(runtime.inspect().atlasVisible,false);runtime.toggle();tick(1900);assert.equal(craft.listCards(storage).length,1);assert.equal(cards.size,1);
// Releasing a different joystick finger must not cancel an in-progress card drag.
tick(900);craft.placeCard(storage,card.id,null);refresh();const free=cards.get(card.id);free.group.visible=true;free.group.position.set(0,2,4);free.group.quaternion.copy(camera.quaternion);const down=point(free.group);runtime.pointerDown(down);runtime.pointerMove({...down,clientX:down.clientX+20});assert.equal(runtime.isDraggingCard(card.id),true);runtime.cancelPointer(2);assert.equal(runtime.isDraggingCard(card.id),true);runtime.cancelPointer(1);assert.equal(runtime.isDraggingCard(card.id),false);assert.equal(craft.listCards(storage)[0].containerId,null);
// Persisted settings drive the actual model, slots and focused camera without touching card data.
const recordBefore=JSON.stringify(craft.listCards(storage));
runtime.focus(State.ATLAS);tick(900);const focusBefore=camera.position.clone();
Object.assign(controls,{atlasDistance:.8,atlasHeight:3.1,atlasSize:1.2,atlasSeparation:1.4,atlasFocusDistance:.8,atlasCardSize:.7});tick(1);tick(900);
const changed=runtime.inspect();assert.equal(changed.atlasScale,.36*1.2);assert.equal(changed.atlasPosition[1],3.1);assert.ok(Math.abs(Math.hypot(changed.atlasPosition[0]-keeper.position.x,changed.atlasPosition[2]-keeper.position.z)-Math.hypot(.8,1.4))<1e-8);assert.ok(camera.position.distanceTo(focusBefore)>.1);
craft.placeCard(storage,card.id,State.ATLAS,0);refresh();tick(1);assert.ok(Math.abs(cards.get(card.id).group.scale.x-.36*1.2*1.3*.7)<1e-9);
craft.placeCard(storage,card.id,null);refresh();assert.equal(JSON.stringify(craft.listCards(storage).map(({revision,updatedAt,...r})=>r)),JSON.stringify(JSON.parse(recordBefore).map(({revision,updatedAt,...r})=>r)),'tuning retains card contents and identity');
// Books forged with the atlas template are separate physical 18-slot containers.
host.innerWidth=915;host.innerHeight=412;camera.aspect=915/412;camera.updateProjectionMatrix();
const forgedA=craft.createCardBook(storage,{randomUUID:()=> 'forged-a'},'第一本卡冊'),forgedB=craft.createCardBook(storage,{randomUUID:()=> 'forged-b'},'第二本卡冊');
for(const b of [forgedA,forgedB]){const g=new THREE.Group();g.add(new THREE.Mesh(new THREE.BoxGeometry(.9,1.3,.2),new THREE.MeshBasicMaterial()));g.position.set(5,3,2);scene.add(g);entities.set(b.id,g)}
runtime.exitFocus();tick(900);runtime.placeCollection(forgedA.id);tick(1900);assert.equal(group(forgedA.id).visible,true);assert.equal(entities.get(forgedA.id).visible,false);
runtime.focus(forgedA.id);tick(900);
for(let i=0;i<18;i++){moveTo(forgedA.id,i,cards.get(card.id));assert.equal(craft.listCards(storage)[0].containerId,forgedA.id);assert.equal(craft.listCards(storage)[0].slotIndex,i)}
runtime.exitFocus();tick(900);runtime.placeCollection(forgedB.id);tick(1900);runtime.focus(forgedB.id);tick(900);moveTo(forgedB.id,8,cards.get(card.id));assert.equal(craft.listCards(storage)[0].containerId,forgedB.id);
// Fold only the focused forged book, leave it in the inner orbit, then reopen it.
doc.getElementById('atlasCollectionClose').click();tick(900);assert.equal(craft.listBooks(storage).find(b=>b.id===forgedB.id).viewOpen,false);assert.equal(group(forgedA.id).visible,true);
runtime.placeCollection(forgedB.id);tick(1900);runtime.focus(forgedB.id);tick(900);doc.getElementById('atlasCollectionShelf').click();tick(1900);assert.equal(craft.listBooks(storage).find(b=>b.id===forgedB.id).viewMode,'shelf');assert.equal(group(forgedB.id).visible,false);assert.equal(cards.get(card.id).group.visible,false);
// Lifting an open forged book folds it; cancellation restores the original open pose.
runtime.focus(forgedA.id);tick(900);const lift=point(meshSlot(forgedA.id,0));runtime.pointerDown(lift);tick(400);runtime.pointerMove({...lift,clientX:lift.clientX+24});tick(175);assert.equal(runtime.isCollectionDragging(forgedA.id),true);assert.ok(runtime.inspect().collections.find(b=>b.id===forgedA.id).open<1);runtime.cancelPointer(1);tick(1);assert.equal(runtime.inspect().collections.find(b=>b.id===forgedA.id).open,1);assert.equal(runtime.isCollectionDragging(forgedA.id),false);
// Landscape focus is limited to the chosen pair, compact and clear of the top controls.
runtime.exitFocus();tick(900);Object.assign(controls,State.DEFAULTS);tick(1);runtime.placeCollection(forgedB.id);tick(1900);
const objectBytes=storage.getItem(craft.KEY),viewBytes=storage.getItem('living-library-atlas-view-v1');
const originalPoses=new Map([State.ATLAS,State.COLLECTION,forgedA.id,forgedB.id].map(id=>[id,group(id).position.clone()]));
host.innerWidth=900;host.innerHeight=360;camera.aspect=900/360;camera.updateProjectionMatrix();runtime.focus(forgedB.id);tick(900);
const chosen=runtime.inspect().focusBooks;assert.equal(chosen.length,2);assert.ok(chosen.includes(forgedB.id));assert.ok(chosen.includes(forgedA.id));assert.equal(group(State.ATLAS).visible,false);assert.equal(bodyClasses.has('atlas-focused'),true);
for(const id of chosen){const bounds=new THREE.Box3().setFromObject(group(id));for(const x of [bounds.min.x,bounds.max.x])for(const y of [bounds.min.y,bounds.max.y])for(const z of [bounds.min.z,bounds.max.z]){const ndc=new THREE.Vector3(x,y,z).project(camera);assert.ok(Math.abs(ndc.x)<1,'focused pages fit horizontally');assert.ok(ndc.y<1-2*60/360,'focused pages stay below toolbar');assert.ok(ndc.y>-.98,'focused pages fit vertically')}}
host.innerWidth=360;host.innerHeight=800;camera.aspect=360/800;camera.updateProjectionMatrix();tick(900);assert.deepEqual(Array.from(runtime.inspect().focusBooks),[forgedB.id]);assert.equal(group(forgedA.id).visible,false);assert.equal(group(forgedB.id).visible,true);
host.innerWidth=900;host.innerHeight=360;camera.aspect=900/360;camera.updateProjectionMatrix();tick(900);assert.equal(runtime.inspect().focusBooks.length,2);
runtime.exitFocus();tick(400);assert.equal(bodyClasses.has('atlas-focused'),true);tick(500);assert.equal(bodyClasses.has('atlas-focused'),false);for(const [id,pos] of originalPoses)assert.ok(group(id).position.distanceTo(pos)<1e-7,'exit restores original book position');assert.equal(storage.getItem(craft.KEY),objectBytes);assert.equal(storage.getItem('living-library-atlas-view-v1'),viewBytes);
runtime.focus(forgedB.id);tick(900);doc.getElementById('atlasCollectionShelf').click();tick(1900);
runtime.dispose();assert.equal(smokes,removed);console.log('PASS: real Three.js raycasts for 10 atlas slots, all 18 collection slots and cross-book transfer, reader, smoke, close/save and smooth focus return. No renderer/device assertion.');

// Rebuild the runtime from the same saved records, then reopen the same book/card.
const restored=host.LivingAtlasRuntime.create(runtimeOptions);clock+=1900;restored.tick(clock);assert.equal(scene.children.find(x=>x.userData.atlasId===forgedA.id).visible,true);assert.equal(scene.children.find(x=>x.userData.atlasId===forgedB.id).visible,false);restored.placeCollection(forgedB.id);clock+=1900;restored.tick(clock);assert.equal(cards.get(card.id).group.visible,true);assert.equal(craft.listCards(storage)[0].containerId,forgedB.id);assert.equal(craft.listCards(storage)[0].slotIndex,8);restored.dispose();assert.equal(smokes,removed);
console.log('PASS: two forged physical card books, all 18 slots, cross-book transfer, independent close/shelf, reload and original Card ID.');
