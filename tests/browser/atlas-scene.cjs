// Actual Three.js geometry/raycast checks without a renderer or a test-only application path.
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),THREE=require('three');
const craft=require('../../living-library/live/object-craft.js'),State=require('../../living-library/live/atlas-state.js');
const root=require('node:path').resolve(__dirname,'../..');let clock=0;
function element(){return {hidden:false,dataset:{},listeners:{},addEventListener(k,fn){this.listeners[k]=fn},click(){this.listeners.click?.()},getContext(){return context},width:0,height:0}}
const gradient={addColorStop(){}},context=new Proxy({createLinearGradient:()=>gradient,createRadialGradient:()=>gradient},{get(o,k){return k in o?o[k]:(()=>{})},set(o,k,v){o[k]=v;return true}});
const els=new Map();const doc={getElementById:id=>{if(!els.has(id))els.set(id,element());return els.get(id)},createElement:()=>element()};
const host={document:doc,innerWidth:900,innerHeight:650,performance:{now:()=>clock},addEventListener(){},console};host.self=host;
for(const name of ['atlas-state.js','atlas-model.js','atlas-runtime.js'])vm.runInNewContext(fs.readFileSync(root+'/living-library/live/'+name,'utf8'),host);
const scene=new THREE.Scene(),camera=new THREE.PerspectiveCamera(48,900/650,.1,110),keeper=new THREE.Group();keeper.position.set(0,.46,0);scene.add(keeper);camera.position.set(0,3,12);camera.lookAt(0,2,0);camera.updateMatrixWorld();
const backing=new Map(),storage={getItem:k=>backing.get(k)??null,setItem:(k,v)=>backing.set(k,v)},cards=new Map(),canvas={parentElement:element(),getBoundingClientRect:()=>({left:0,top:0,width:900,height:650}),setPointerCapture(){}};
const outer=new THREE.Group();outer.add(new THREE.Mesh(new THREE.BoxGeometry(.9,1.3,.2),new THREE.MeshBasicMaterial()));outer.position.set(4,3,0);scene.add(outer);
let smokes=0,removed=0,opened=[],messages=[];host.LivingScene={refreshFloatingCards:refresh,syncRender(){}};
function refresh(){for(const r of craft.listCards(storage)){if(cards.has(r.id))cards.get(r.id).record=r;else{const g=new THREE.Group(),mesh=new THREE.Mesh(new THREE.BoxGeometry(.45,.66,.025),new THREE.MeshBasicMaterial());mesh.userData.localCardId=r.id;g.add(mesh);g.position.set(0,2,4);scene.add(g);cards.set(r.id,{id:r.id,record:r,group:g})}}}
const runtime=host.LivingAtlasRuntime.create({THREE,scene,camera,canvas,keeper,cards,craft,storage,smoke:{create(){smokes++;return []},update(){},remove(){removed++}},toast:x=>messages.push(x),invalidate(){},openCard:id=>opened.push(id),focusCard(){},collectionEntity:()=>outer,setCollectionActive(){},reduced:false});
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
runtime.dispose();assert.equal(smokes,removed);console.log('PASS: real Three.js raycasts for 10 atlas slots, all 18 collection slots and cross-book transfer, reader, smoke, close/save and smooth focus return. No renderer/device assertion.');
