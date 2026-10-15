/* Main-scene integration. Uses the existing renderer, card IDs, smoke and reader. */
(function(root){'use strict';
root.LivingAtlasRuntime={create({THREE,scene,camera,canvas,keeper,cards,craft,storage,smoke,toast,invalidate,openCard,focusCard,collectionEntity,setCollectionActive,controls={},reduced=false}){
 const State=root.LivingAtlasState,KEY='living-library-atlas-view-v1';
 let tuning=State.normalizeSettings(controls),BASE=.36*tuning.atlasSize;
 let saved={collection:'outer',collectionOpen:true};try{const raw=storage.getItem(KEY);if(raw){const parsed=JSON.parse(raw);if(parsed?.version!==1||!['outer','inner','shelf'].includes(parsed.collection))throw Error('卡冊位置資料版本不相容');saved=parsed}}catch(e){toast(e.message);saved={collection:'outer',readonly:true}}
 function persist(){if(saved.readonly)return;try{storage.setItem(KEY,JSON.stringify({version:1,collection:saved.collection,collectionOpen:saved.collectionOpen!==false}))}catch(e){toast('位置保存失敗：'+e.message)}}
 const atlas=root.LivingAtlasModel.create(THREE),collection=root.LivingAtlasModel.create(THREE,true);
 const models=new Map([[State.ATLAS,atlas],[State.COLLECTION,collection]]);
 for(const [id,m] of models){m.id=id;m.group.userData.atlasId=id;m.group.scale.setScalar(BASE);m.group.visible=false;m.open=0;m.desired=false;m.animation=null;m.puffs=null;m.center=new THREE.Vector3();m.rotation=new THREE.Quaternion();m.setOpen(0);scene.add(m.group);m.group.traverse(o=>{if(o.isMesh)o.userData.atlasBook=id})}
 let focus=null,returning=null,pointer=null,slotHighlight=null,audio=null,disposed=false;
 const ray=new THREE.Raycaster(),uv=new THREE.Vector2();
 const toolbar=document.getElementById('atlasActions'),exit=document.getElementById('atlasFocusExit'),close=document.getElementById('atlasCollectionClose'),shelf=document.getElementById('atlasCollectionShelf'),switcher=document.getElementById('atlasFocusSwitch');
 function syncUI(){const target=focus?.id;toolbar.hidden=!target;close.hidden=target!==State.COLLECTION;shelf.hidden=target!==State.COLLECTION;switcher.hidden=!target||!atlas.desired||saved.collection!=='inner';
  stageData();}
 function stageData(){canvas.parentElement.dataset.atlas=atlas.desired?'summoned':'hidden';canvas.parentElement.dataset.atlasFocus=focus?.id||'';canvas.parentElement.dataset.collection=saved.collection}
 function audioStart(){try{const AC=root.AudioContext||root.webkitAudioContext;if(AC&&!audio)audio=new AC();if(audio?.state==='suspended')audio.resume().catch(()=>{})}catch{}}
 function sound(show){audioStart();if(!audio)return;try{const n=audio.currentTime,o=audio.createOscillator(),g=audio.createGain();o.type=show?'sine':'triangle';o.frequency.setValueAtTime(show?245:360,n);o.frequency.exponentialRampToValueAtTime(show?760:85,n+.23);g.gain.setValueAtTime(.001,n);g.gain.exponentialRampToValueAtTime(.12,n+.025);g.gain.exponentialRampToValueAtTime(.001,n+.3);o.connect(g).connect(audio.destination);o.start(n);o.stop(n+.32)}catch{}}
 function setInnerPose(m){const peer=m===atlas?collection:atlas;const direction=peer.group.visible?new THREE.Vector3(0,0,-1).applyQuaternion(peer.rotation):camera.position.clone().sub(keeper.position);direction.y=0;if(direction.lengthSq()<.1)direction.set(0,0,1);direction.normalize();const sideways=new THREE.Vector3().crossVectors(new THREE.Vector3(0,1,0),direction);
  const yaw=Math.atan2(direction.x,direction.z);m.direction=direction.clone();m.offset=direction.multiplyScalar(tuning.atlasDistance).addScaledVector(sideways,(m===atlas?-1:1)*tuning.atlasSeparation);m.rotation.setFromAxisAngle(new THREE.Vector3(0,1,0),yaw+Math.PI);
  // Both books share the same page-facing orientation for cross-book drag.
  if(peer.group.visible)m.rotation.copy(peer.rotation);
 }
 function begin(m,show,interrupt=false){if(m.animation&&!interrupt)return;const fromOpen=m.group.visible?m.open:0,fromScale=m.group.visible?m.group.scale.x/BASE:0;if(m.puffs)smoke.remove(m.puffs);m.puffs=null;m.group.visible=true;if(!m.offset)setInnerPose(m);m.animation={show,start:performance.now(),fromOpen,fromScale};sound(show);if(show)m.puffs=smoke.create(m.center.clone(),false);invalidate()}
 function toggle(){cancelPointer();audioStart();atlas.desired=!atlas.desired;begin(atlas,atlas.desired,true);syncUI()}
 function summon(){if(!atlas.desired)toggle();else focusBook(State.ATLAS)}
 function placeCollection(){cancelPointer();saved.collection='inner';saved.collectionOpen=true;persist();collection.desired=true;setInnerPose(collection);setCollectionActive(true);if(!collection.animation)begin(collection,true);syncUI();invalidate()}
 function foldCollection(toShelf=false){cancelPointer();saved.collectionOpen=false;persist();collection.desired=false;collection.afterClose=toShelf?'shelf':'closed';if(!collection.animation)begin(collection,false);syncUI();invalidate()}
 function enterFocus(id){const m=models.get(id);if(!m?.group.visible||m.animation)return;
  if(!focus){focus={id,from:camera.position.clone(),rotation:camera.quaternion.clone(),keeper:keeper.position.clone(),started:performance.now()}}else{focus={...focus,id,started:performance.now(),from:camera.position.clone(),rotation:camera.quaternion.clone()}}
  returning=null;syncUI();invalidate();
 }
 function exitFocus(){if(!focus)return;const f=focus;focus=null;returning={from:camera.position.clone(),rotation:camera.quaternion.clone(),target:f.originalPosition||f.from.clone(),quaternion:f.originalQuaternion||f.rotation.clone(),keeper:f.originalKeeper||f.keeper.clone(),start:performance.now()};syncUI();invalidate()}
 // Preserve the overview snapshot even when portrait focus switches books.
 function focusBook(id){const before=focus;if(!before){enterFocus(id);if(focus){focus.originalPosition=focus.from.clone();focus.originalQuaternion=focus.rotation.clone();focus.originalKeeper=focus.keeper.clone()}}else{const original={originalPosition:before.originalPosition,originalQuaternion:before.originalQuaternion,originalKeeper:before.originalKeeper};enterFocus(id);Object.assign(focus,original)}}
 exit.addEventListener('click',exitFocus);close.addEventListener('click',()=>foldCollection(false));shelf.addEventListener('click',()=>foldCollection(true));switcher.addEventListener('click',()=>focusBook(focus?.id===State.ATLAS?State.COLLECTION:State.ATLAS));
 function rayAt(e){const r=canvas.getBoundingClientRect();uv.set((e.clientX-r.left)/r.width*2-1,-(e.clientY-r.top)/r.height*2+1);ray.setFromCamera(uv,camera);scene.updateMatrixWorld(true)}
 function hits(e){rayAt(e);const active=[...models.values()].filter(m=>m.group.visible&&!m.animation);const hits=[];
  for(const m of active)for(const h of ray.intersectObject(m.group,true))hits.push({...h,book:m.id});
  const outer=collectionEntity();if(saved.collection!=='inner'&&outer)for(const h of ray.intersectObject(outer,true))hits.push({...h,book:State.COLLECTION,outer:true});
  return hits.sort((a,b)=>a.distance-b.distance);
 }
 function cardHit(e){rayAt(e);const targets=[...cards.values()].filter(c=>c.group.visible&&!c.summon&&!c.recycle).flatMap(c=>c.group.children);return ray.intersectObjects(targets,false)[0]}
 function pointerDown(e){if(disposed)return false;if(pointer)return true;
  const hit=cardHit(e),bookHit=hits(e)[0];
  if(hit&&(!bookHit||hit.distance<=bookHit.distance+.06)){
   const entry=cards.get(hit.object.userData.localCardId);if(!entry)return false;
   pointer={id:e.pointerId,kind:'card',entry,x:e.clientX,y:e.clientY,moved:false,position:entry.group.position.clone(),quaternion:entry.group.quaternion.clone(),scale:entry.group.scale.x};
  }else if(bookHit){
   const control=bookHit.object.userData.atlasControl;
   pointer={id:e.pointerId,kind:control?'control':'book',book:bookHit.book,outer:!!bookHit.outer,control,x:e.clientX,y:e.clientY,moved:false,start:performance.now()};
  }else return !!focus;
  canvas.setPointerCapture?.(e.pointerId);return true;
 }
 function hoveredSlot(e){rayAt(e);let found=null;
  for(const m of models.values())if(m.group.visible&&m.open>.98&&!m.animation){const h=ray.intersectObjects(m.targets.map(t=>t.well),false)[0];if(h&&(!found||h.distance<found.distance))found={id:m.id,slot:h.object.userData.atlasSlot,distance:h.distance,well:h.object}}
  return found;
 }
 function highlight(target){if(slotHighlight)slotHighlight.material=slotHighlight.userData.originalMaterial;
  slotHighlight=null;if(!target)return;const occupied=[...cards.values()].some(c=>c.id!==pointer?.entry?.id&&c.record.containerId===target.id&&c.record.slotIndex===target.slot);
  target.well.userData.originalMaterial??=target.well.material;if(!target.well.userData.highlightMaterial){target.well.userData.highlightMaterial=target.well.material.clone();target.well.userData.highlightMaterial.color.set('#49d6b5')}
  if(!occupied){slotHighlight=target.well;slotHighlight.material=slotHighlight.userData.highlightMaterial}}
 function dragWorld(e,position){rayAt(e);const normal=camera.getWorldDirection(new THREE.Vector3()),plane=new THREE.Plane().setFromNormalAndCoplanarPoint(normal,position);return ray.ray.intersectPlane(plane,new THREE.Vector3())}
 function pointerMove(e){if(!pointer)return !!focus;if(e.pointerId!==pointer.id)return true;const p=pointer;
  if(Math.hypot(e.clientX-p.x,e.clientY-p.y)>8)p.moved=true;
  if(p.kind==='card'&&p.moved){const v=dragWorld(e,p.position);if(v){p.entry.group.position.copy(v);p.entry.group.quaternion.copy(camera.quaternion);p.entry.group.scale.setScalar(BASE*1.3*tuning.atlasCardSize)}highlight(hoveredSlot(e))}
  if(p.kind==='book'&&p.book===State.COLLECTION&&p.moved&&performance.now()-p.start>=330){
   if(!p.dragging){p.dragging=true;const obj=p.outer?collectionEntity():collection.group;p.position=obj.position.clone();p.object=obj;collection.dragging=true}
   const v=dragWorld(e,p.position);if(v)p.object.position.copy(v);
  }
  invalidate();return true;
 }
 function pointerUp(e,cancel=false){if(!pointer)return !!focus;if(e.pointerId!==pointer.id)return true;const p=pointer;pointer=null;highlight(null);
  if(p.kind==='card'){
   if(p.moved&&!cancel){const slot=hoveredSlot(e);try{craft.placeCard(storage,p.entry.id,slot?slot.id:null,slot?.slot??null);refresh()}catch(err){toast(err.message)}}
   else if(!cancel){if(p.entry.record.containerId){toast('拖曳可移動卡片；右側 ● 開啟讀卡內頁')}else focusCard(p.entry.id)}
   p.entry.group.position.copy(p.position);p.entry.group.quaternion.copy(p.quaternion);p.entry.group.scale.setScalar(p.scale);
  }else if(p.kind==='control'&&!p.moved&&!cancel){const card=[...cards.values()].find(c=>State.readerActive(c.record));if(card)openCard(card.id);else toast('先將卡片放入右側讀卡區')}
  else if(p.kind==='book'){
   collection.dragging=false;
   if(p.dragging&&!cancel){const position=p.object.position,dist=Math.hypot(position.x-keeper.position.x,position.z-keeper.position.z);if(dist<3.6)placeCollection();else foldCollection(true)}
   else if(!p.moved&&!cancel){if(p.outer)placeCollection();else if(collection.open<.1&&p.book===State.COLLECTION){collection.desired=true;saved.collectionOpen=true;persist();begin(collection,true)}else focusBook(p.book)}
   if(p.dragging&&cancel&&p.object)p.object.position.copy(p.position);
  }
  invalidate();return true;
 }
 function cancelPointer(id){if(pointer&&(id===undefined||id===pointer.id))pointerUp({pointerId:pointer.id},true)}
 function refresh(){root.LivingScene?.refreshFloatingCards?.();invalidate()}
 function updateCamera(now){
  if(focus){const m=models.get(focus.id);if(!m.group.visible){exitFocus();return}
   const visible=root.innerWidth>root.innerHeight?[...models.values()].filter(x=>x.group.visible&&x.open>.98):[m];
   const bounds=new THREE.Box3();for(const x of visible)bounds.union(new THREE.Box3().setFromObject(x.group));
   const center=bounds.getCenter(new THREE.Vector3()),size=bounds.getSize(new THREE.Vector3());
   // Measure in the book frame, independent of world yaw.
   const width=5.4*BASE+(visible.length>1?2*tuning.atlasSeparation:0),height=3.7*BASE,tan=Math.tan(THREE.MathUtils.degToRad(camera.fov)*.5);
   const dist=(Math.max(height/(2*tan*.76),width/(2*tan*camera.aspect*.82))+.25)*tuning.atlasFocusDistance;
   const normal=new THREE.Vector3(0,0,1).applyQuaternion(m.group.quaternion),target=center.clone().addScaledVector(normal,dist);
   const q=new THREE.Quaternion().setFromRotationMatrix(new THREE.Matrix4().lookAt(target,center,new THREE.Vector3(0,1,0)));
   const t=reduced?1:Math.min(1,(now-focus.started)/850),u=t*t*(3-2*t);camera.position.copy(focus.from).lerp(target,u);camera.quaternion.copy(focus.rotation).slerp(q,u);
  }else if(returning){const r=returning,target=r.target.clone().add(keeper.position.clone().sub(r.keeper));const t=reduced?1:Math.min(1,(now-r.start)/850),u=t*t*(3-2*t);camera.position.copy(r.from).lerp(target,u);camera.quaternion.copy(r.rotation).slerp(r.quaternion,u);if(t>=1)returning=null}
 }
 function applyTuning(){
  const next=State.normalizeSettings(controls);if(Object.keys(next).every(k=>next[k]===tuning[k]))return;
  tuning=next;BASE=.36*tuning.atlasSize;
  for(const m of models.values()){
   if(m.direction){const side=new THREE.Vector3().crossVectors(new THREE.Vector3(0,1,0),m.direction);m.offset=m.direction.clone().multiplyScalar(tuning.atlasDistance).addScaledVector(side,(m===atlas?-1:1)*tuning.atlasSeparation)}
   if(!m.animation)m.group.scale.setScalar(BASE);
  }
  restoreFocusCamera();invalidate();
 }
 function tick(now){
  applyTuning();
  const entity=collectionEntity();if(entity&&!collection.dragging)entity.visible=saved.collection!=='inner';
  for(const m of models.values()){
   if(!m.group.visible)continue;
   if(!m.dragging){m.center.set(keeper.position.x+(m.offset?.x||0),tuning.atlasHeight,keeper.position.z+(m.offset?.z||0));m.group.position.copy(m.center);m.group.quaternion.copy(m.rotation)}
   if(m.animation){const a=m.animation,age=now-a.start;let complete=false;
    if(a.show){const t=reduced?1:Math.min(1,age/1100);m.group.scale.setScalar(BASE*Math.max(.001,a.fromScale+(1-a.fromScale)*Math.min(1,t/.35)));m.open=a.fromOpen+(1-a.fromOpen)*Math.max(0,Math.min(1,(t-.35)/.65));if(m.puffs)smoke.update(m.puffs,m.center,age,1100);complete=t>=1}
    else if(m===collection&&m.afterClose==='closed'){const t=reduced?1:Math.min(1,age/650);m.open=a.fromOpen*(1-t);m.group.scale.setScalar(BASE);complete=t>=1}
    else{const t=reduced?1:Math.min(1,age/1000);m.open=a.fromOpen*(1-Math.min(1,t/.45));if(t>=.45&&!m.puffs)m.puffs=smoke.create(m.center.clone(),false);if(m.puffs)smoke.update(m.puffs,m.center,Math.max(0,age-450),550);m.group.scale.setScalar(BASE*a.fromScale*Math.max(.001,t<.5?1:1-(t-.5)*2));complete=t>=1}
    m.setOpen(m.open);
    if(complete){if(m.puffs)smoke.remove(m.puffs);m.puffs=null;m.animation=null;m.group.scale.setScalar(BASE);
     if(!a.show){if(m===collection&&m.afterClose==='closed'){m.group.visible=true;m.open=0;m.setOpen(0)}else{m.group.visible=false;if(m===collection){saved.collection='shelf';persist();setCollectionActive(false)}}if(focus?.id===m.id)exitFocus()}
     if(m===atlas&&m.desired!==a.show)begin(m,m.desired);
     if(m===collection&&m.desired!==a.show&&m.desired)begin(m,true);
     syncUI();
    }
   }
  }
  for(const entry of cards.values()){
   const record=entry.record;if(pointer?.entry?.id===entry.id&&pointer.moved)continue;
   if(!record.containerId)continue;const m=models.get(record.containerId),target=m?.targets.find(t=>t.index===record.slotIndex);
   entry.group.visible=!!target&&m.group.visible&&m.open>.03;
   if(entry.group.visible){target.node.updateWorldMatrix(true,false);target.node.getWorldPosition(entry.group.position);target.node.getWorldQuaternion(entry.group.quaternion);entry.group.scale.setScalar(m.group.scale.x*1.3*tuning.atlasCardSize)}
  }
  atlas.screen([...cards.values()].find(c=>State.readerActive(c.record))?.record||null);
  updateCamera(now);
 }
 function active(){return !!focus||!!returning||!!pointer||[...models.values()].some(m=>!!m.animation)}
 function restoreFocusCamera(){if(focus){focus.from=camera.position.clone();focus.rotation=camera.quaternion.clone();focus.started=performance.now()}}
 function showReaderReturn(){restoreFocusCamera();invalidate()}
 function dispose(){disposed=true;cancelPointer();for(const m of models.values()){if(m.puffs)smoke.remove(m.puffs);m.dispose();scene.remove(m.group)}audio?.close().catch(()=>{})}
 if(saved.collection==='inner'){collection.desired=saved.collectionOpen!==false;setInnerPose(collection);collection.group.visible=true;collection.open=collection.desired?1:0;collection.setOpen(collection.open)}
 if(saved.collection==='shelf')setCollectionActive(false);
 syncUI();
 root.addEventListener('pagehide',e=>{if(!e.persisted)dispose();else cancelPointer()});
 return {toggle,summon,placeCollection,focus:focusBook,exitFocus,tick,active,pointerDown,pointerMove,pointerUp,cancelPointer,
  isDraggingCard:id=>pointer?.entry?.id===id&&pointer.moved,isCollectionDragging:()=>!!collection.dragging,
  get movementYaw(){const v=new THREE.Vector3(0,0,1).applyQuaternion(camera.quaternion);return Math.atan2(v.x,v.z)},get inFocus(){return !!focus||!!returning},get focusId(){return focus?.id},get collectionMode(){return saved.collection},showReaderReturn,audioStart,
  inspect(){return {tuning:{...tuning},atlasPosition:atlas.group.position.toArray(),atlasScale:atlas.group.scale.x,atlasVisible:atlas.group.visible,atlasDesired:atlas.desired,atlasOpen:atlas.open,atlasFacingKeeper:new THREE.Vector3(0,0,1).applyQuaternion(atlas.group.quaternion).dot(keeper.position.clone().sub(atlas.center).setY(0).normalize()),collectionMode:saved.collection,collectionOpen:collection.open,focus:focus?.id||null,animation:!!atlas.animation||!!collection.animation}},dispose};
}};
})(typeof self!=='undefined'?self:this);
