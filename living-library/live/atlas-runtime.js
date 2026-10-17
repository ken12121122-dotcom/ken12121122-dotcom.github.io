/* Main-scene integration. Uses the existing renderer, card IDs, smoke and reader. */
(function(root){'use strict';
root.LivingAtlasRuntime={create({THREE,scene,camera,canvas,keeper,cards,craft,storage,smoke,toast,invalidate,openCard,observeCard,focusCard,collectionEntity,setCollectionActive,controls={},reduced=false}){
 const State=root.LivingAtlasState,KEY='living-library-atlas-view-v1';
 let tuning=State.normalizeSettings(controls),BASE=.36*tuning.atlasSize;
 let saved={collection:'outer',collectionOpen:true};try{const raw=storage.getItem(KEY);if(raw){const parsed=JSON.parse(raw);if(parsed?.version!==1||!['outer','inner','shelf'].includes(parsed.collection))throw Error('卡冊位置資料版本不相容');saved=parsed}}catch(e){toast(e.message);saved={collection:'outer',readonly:true}}
 function persist(m=collection){if(m!==collection){try{craft.setBookView(storage,m.id,m.view.collection,m.view.collectionOpen!==false)}catch(e){toast('位置保存失敗：'+e.message)}return}if(saved.readonly)return;try{storage.setItem(KEY,JSON.stringify({version:1,collection:saved.collection,collectionOpen:saved.collectionOpen!==false}))}catch(e){toast('位置保存失敗：'+e.message)}}
 const atlas=root.LivingAtlasModel.create(THREE),collection=root.LivingAtlasModel.create(THREE,true);
 const models=new Map([[State.ATLAS,atlas],[State.COLLECTION,collection]]);
 function prepareModel(id,m){m.id=id;m.group.userData.atlasId=id;m.group.scale.setScalar(BASE);m.group.visible=false;m.open=0;m.desired=false;m.animation=null;m.puffs=null;m.center=new THREE.Vector3();m.rotation=new THREE.Quaternion();m.setOpen(0);scene.add(m.group);m.group.traverse(o=>{if(o.isMesh)o.userData.atlasBook=id});return m}
 for(const [id,m] of models)prepareModel(id,m);collection.view=saved;
 let focus=null,returning=null,pointer=null,slotHighlight=null,audio=null,disposed=false,lastFocusId=null,screenMode='card';
 let uploadCardId=null,uploadStatus={state:'idle',code:''};
 let copyIndex=0,copySourceCache=[],copyStamp=null,copyConfirmSelection=null;
 const copyDialog=document.getElementById('atlasCopyDialog'),
  copySourceName=document.getElementById('atlasCopySource');
 function copySourcesCached(){
  const stamp=storage.getItem(craft.KEY);
  if(stamp!==copyStamp){
   copyStamp=stamp;
   try{copySourceCache=craft.copySources?.(storage)||[]}
   catch(error){copySourceCache=[];toast('讀取複製來源失敗：'+error.message)}
   copyIndex=Math.max(0,Math.min(copyIndex,Math.max(0,copySourceCache.length-1)));
  }
  return copySourceCache;
 }
 function paintGba(reader){
  const blank=!!reader&&craft.isBlankCopyMedium?.(reader.record);
  if(screenMode==='copy'&&!blank)screenMode='card';
  atlas.screen(reader?.record||null,screenMode,!!focus?.gba,uploadStatus,
   {items:blank?copySourcesCached():[],index:copyIndex});
 }
 function cancelCopyConfirmation(){copyConfirmSelection=null;if(copyDialog?.open)copyDialog.close()}
 function presentCopyConfirmation(reader){
  if(!reader||!craft.isBlankCopyMedium?.(reader.record))return toast('讀卡格需要未使用的空白卡');
  const source=copySourcesCached()[copyIndex];
  if(!source)return toast('請先將可複製的普通卡片放進左頁九格');
  if(copyDialog.open)return;
  copyConfirmSelection={blankId:reader.id,blankRevision:reader.record.revision,
   sourceId:source.id,sourceRevision:source.revision};
  copySourceName.textContent=source.title;
  copyDialog.showModal();
 }
 copyDialog?.addEventListener('cancel',()=>{copyConfirmSelection=null});
 document.getElementById('atlasCopyCancel')?.addEventListener('click',cancelCopyConfirmation);
 document.getElementById('atlasCopyConfirm')?.addEventListener('click',()=>{
  const selection=copyConfirmSelection;if(!selection)return;
  cancelCopyConfirmation();
  try{
   const result=craft.copyIntoBlankCard(storage,selection);
   screenMode='card';copyIndex=0;copyStamp=null;
   refresh();
   toast('✧ 複製完成：'+result.title+'（保留空白卡原有 ID）');
  }catch(error){toast('複製未完成：'+String(error?.message||error))}
  invalidate();
 });
 const ray=new THREE.Raycaster(),uv=new THREE.Vector2();
 const toolbar=document.getElementById('atlasActions'),exit=document.getElementById('atlasFocusExit'),close=document.getElementById('atlasCollectionClose'),shelf=document.getElementById('atlasCollectionShelf'),switcher=document.getElementById('atlasFocusSwitch');
 function innerCollections(){return [...models.values()].filter(m=>m!==atlas&&m.view.collection==='inner')}
 function syncUI(){const target=focus?.id;toolbar.hidden=!target;close.hidden=!State.isCollection(target);shelf.hidden=close.hidden;switcher.hidden=!target||(focus?.ids.length||0)<2;
  stageData();}
 function stageData(){document.body?.classList?.toggle('atlas-focused',!!focus||!!returning);canvas.parentElement.dataset.atlas=atlas.desired?'summoned':'hidden';canvas.parentElement.dataset.atlasFocus=focus?.id||'';canvas.parentElement.dataset.collection=saved.collection}
 function audioStart(){try{const AC=root.AudioContext||root.webkitAudioContext;if(AC&&!audio)audio=new AC();if(audio?.state==='suspended')audio.resume().catch(()=>{})}catch{}}
 function sound(show){audioStart();if(!audio)return;try{const n=audio.currentTime,o=audio.createOscillator(),g=audio.createGain();o.type=show?'sine':'triangle';o.frequency.setValueAtTime(show?245:360,n);o.frequency.exponentialRampToValueAtTime(show?760:85,n+.23);g.gain.setValueAtTime(.001,n);g.gain.exponentialRampToValueAtTime(.12,n+.025);g.gain.exponentialRampToValueAtTime(.001,n+.3);o.connect(g).connect(audio.destination);o.start(n);o.stop(n+.32)}catch{}}
 function setInnerPose(m){const peer=[...models.values()].find(x=>x!==m&&x.group.visible);const direction=peer?.group.visible?new THREE.Vector3(0,0,-1).applyQuaternion(peer.rotation):camera.position.clone().sub(keeper.position);direction.y=0;if(direction.lengthSq()<.1)direction.set(0,0,1);direction.normalize();const sideways=new THREE.Vector3().crossVectors(new THREE.Vector3(0,1,0),direction);
  const yaw=Math.atan2(direction.x,direction.z);m.direction=direction.clone();m.side=m===atlas?-1:1+2*innerCollections().filter(x=>x!==m&&x.group.visible).length;m.offset=direction.multiplyScalar(tuning.atlasDistance).addScaledVector(sideways,m.side*tuning.atlasSeparation);m.rotation.setFromAxisAngle(new THREE.Vector3(0,1,0),yaw+Math.PI);
  // Both books share the same page-facing orientation for cross-book drag.
  if(peer?.group.visible)m.rotation.copy(peer.rotation);
 }
 function begin(m,show,interrupt=false){if(m.animation&&!interrupt)return;const fromOpen=m.group.visible?m.open:0,fromScale=m.group.visible?m.group.scale.x/BASE:0;if(m.puffs)smoke.remove(m.puffs);m.puffs=null;m.group.visible=true;if(!m.offset)setInnerPose(m);m.animation={show,start:performance.now(),fromOpen,fromScale};sound(show);if(show)m.puffs=smoke.create(m.center.clone(),false);invalidate()}
 function toggle(){cancelPointer();audioStart();atlas.desired=!atlas.desired;begin(atlas,atlas.desired,true);syncUI()}
 function summon(){if(!atlas.desired)toggle();else focusBook(State.ATLAS)}
 function syncCollections(){
  let records;try{const stamp=storage.getItem(craft.KEY);if(stamp===syncCollections.stamp)return;records=craft.listBooks(storage).filter(b=>b.openingMode==='atlas');syncCollections.stamp=stamp}catch(e){if(!syncCollections.failed)toast(e.message);syncCollections.failed=true;return}
  for(const b of records)if(!models.has(b.id)){
   const m=prepareModel(b.id,root.LivingAtlasModel.create(THREE,true,b.title));m.view={collection:['outer','inner','shelf'].includes(b.viewMode)?b.viewMode:(b.active===false?'shelf':'outer'),collectionOpen:b.viewOpen!==false};models.set(b.id,m);
   if(m.view.collection==='inner'){m.desired=m.view.collectionOpen;setInnerPose(m);m.group.visible=true;m.open=m.desired?1:0;m.setOpen(m.open)}
  }
 }
 function placeCollection(id=State.COLLECTION){syncCollections();const m=models.get(id);if(!m||m===atlas)return;cancelPointer();m.view.collection='inner';m.view.collectionOpen=true;persist(m);m.desired=true;setInnerPose(m);setCollectionActive(true,id);if(!m.animation)begin(m,true);if(focus&&focus.id!==id){focus.ids=[focus.id,id];m.center.set(keeper.position.x+m.offset.x,tuning.atlasHeight,keeper.position.z+m.offset.z);m.group.position.copy(m.center);focus.layoutFrom=new Map(focus.ids.map(key=>[key,models.get(key).group.position.clone()]));focus.from=camera.position.clone();focus.rotation=camera.quaternion.clone();focus.keeper=keeper.position.clone();focus.started=performance.now()}syncUI();invalidate()}
 function foldCollection(toShelf=false,id=focus?.id||State.COLLECTION){const m=models.get(id);if(!m||m===atlas)return;cancelPointer();m.view.collectionOpen=false;persist(m);m.desired=false;m.afterClose=toShelf?'shelf':'closed';if(!m.animation)begin(m,false);syncUI();invalidate()}
 function present(m){return m.group.visible||m.focusHidden}
 function focusMembers(){return (focus?.ids||[]).map(id=>models.get(id)).filter(m=>m&&present(m)&&(m.id===focus.id||m.open>.98))}
 function enterFocus(id){const m=models.get(id);if(!m||!present(m)||m.animation)return;
  const available=[...models.values()].filter(x=>x!==m&&present(x)&&x.open>.98);
  const old=focus;
  const peer=available.find(x=>x.id===(old&&old.id!==id?old.id:lastFocusId))||available.find(x=>old?.ids.includes(x.id))||available[0];
  const ids=[id,...(peer?[peer.id]:[])],layoutFrom=new Map(ids.map(key=>[key,models.get(key).group.position.clone()]));
  if(!focus){focus={id,from:camera.position.clone(),rotation:camera.quaternion.clone(),keeper:keeper.position.clone(),started:performance.now()}}else{focus={...focus,id,started:performance.now(),from:camera.position.clone(),rotation:camera.quaternion.clone(),keeper:keeper.position.clone()}}
  focus.ids=ids;focus.layoutFrom=layoutFrom;focus.gba=false;lastFocusId=id;
  returning=null;syncUI();invalidate();
 }
 function exitFocus(){if(!focus)return;const f=focus;focus=null;returning={from:camera.position.clone(),rotation:camera.quaternion.clone(),target:f.originalPosition||f.from.clone(),quaternion:f.originalQuaternion||f.rotation.clone(),keeper:f.originalKeeper||f.keeper.clone(),layout:new Map(f.ids.map(id=>[id,models.get(id).group.position.clone()])),layoutKeeper:keeper.position.clone(),start:performance.now()};syncUI();invalidate()}
 // Preserve the overview snapshot even when portrait focus switches books.
 function focusBook(id){const before=focus;if(!before){enterFocus(id);if(focus){focus.originalPosition=focus.from.clone();focus.originalQuaternion=focus.rotation.clone();focus.originalKeeper=focus.keeper.clone()}}else{const original={originalPosition:before.originalPosition,originalQuaternion:before.originalQuaternion,originalKeeper:before.originalKeeper};enterFocus(id);Object.assign(focus,original)}}
 // START toggles a closer camera centered solely on the right-side GBA reader.
 // Returning with B restores normal book focus; the existing × exits to overview.
 function focusGba(){
  if(focus?.gba){
   focus.gba=false;focus.from=camera.position.clone();focus.rotation=camera.quaternion.clone();focus.keeper=keeper.position.clone();
   focus.layoutFrom=new Map(focus.ids.map(id=>[id,models.get(id).group.position.clone()]));
   focus.started=performance.now();syncUI();invalidate();return;
  }
  const m=models.get(State.ATLAS);if(!present(m)||m.open<.98||m.animation)return;
  if(!focus||focus.id!==State.ATLAS)focusBook(State.ATLAS);
  if(!focus||focus.id!==State.ATLAS)return;
  focus.gba=true;focus.from=camera.position.clone();focus.rotation=camera.quaternion.clone();
  focus.keeper=keeper.position.clone();focus.layoutFrom=new Map(focus.ids.map(id=>[id,models.get(id).group.position.clone()]));
  focus.started=performance.now();syncUI();invalidate();
 }
 exit.addEventListener('click',()=>focus?.gba?focusGba():exitFocus());close.addEventListener('click',()=>foldCollection(false));shelf.addEventListener('click',()=>foldCollection(true));switcher.addEventListener('click',()=>focusBook(focus?.ids.find(id=>id!==focus.id)||focus?.id));
 function rayAt(e){const r=canvas.getBoundingClientRect();uv.set((e.clientX-r.left)/r.width*2-1,-(e.clientY-r.top)/r.height*2+1);ray.setFromCamera(uv,camera);scene.updateMatrixWorld(true)}
 function hits(e){rayAt(e);const active=[...models.values()].filter(m=>m.group.visible&&!m.animation);const hits=[];
  for(const m of active)for(const h of ray.intersectObject(m.group,true))hits.push({...h,book:m.id});
  for(const m of models.values())if(m!==atlas&&m.view.collection!=='inner'){const outer=collectionEntity(m.id);if(outer?.visible)for(const h of ray.intersectObject(outer,true))hits.push({...h,book:m.id,outer:true})}
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
   const isAtlas=bookHit.book===State.ATLAS&&!bookHit.outer;
   const pageHit=isAtlas?models.get(State.ATLAS).group.worldToLocal(bookHit.point.clone()):null;
   // Overview: any atlas tap enters regular TWO-page focus.
   // First focus: tap the RIGHT GBA console to enter the second closer focus.
   // Its buttons cannot trigger actions until that second focus is active.
   const enterGba=!!(focus?.id===State.ATLAS&&!focus.gba&&pageHit&&pageHit.x>.12);
   const activeGba=!!(focus?.id===State.ATLAS&&focus.gba&&isAtlas);
   pointer={id:e.pointerId,kind:enterGba?'gba-entry':activeGba?(control?'control':'gba-surface'):'book',
    book:bookHit.book,outer:!!bookHit.outer,control,x:e.clientX,y:e.clientY,moved:false,start:performance.now()};
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
  // Do not advertise a locked left-side slot as a legal green drop target.
  const allowed=!pointer?.entry||!craft.canStoreBoundCard||
   craft.canStoreBoundCard(pointer.entry.record,target.id,target.slot);
  if(!occupied&&allowed){slotHighlight=target.well;slotHighlight.material=slotHighlight.userData.highlightMaterial}}
 function dragWorld(e,position){rayAt(e);const normal=camera.getWorldDirection(new THREE.Vector3()),plane=new THREE.Plane().setFromNormalAndCoplanarPoint(normal,position);return ray.ray.intersectPlane(plane,new THREE.Vector3())}
 function pointerMove(e){if(!pointer)return !!focus;if(e.pointerId!==pointer.id)return true;const p=pointer;
  if(Math.hypot(e.clientX-p.x,e.clientY-p.y)>8)p.moved=true;
  if(p.kind==='card'&&p.moved){const v=dragWorld(e,p.position);if(v){p.entry.group.position.copy(v);p.entry.group.quaternion.copy(camera.quaternion);p.entry.group.scale.setScalar(BASE*1.3*tuning.atlasCardSize)}highlight(hoveredSlot(e))}
  if(p.kind==='book'&&State.isCollection(p.book)&&p.moved&&performance.now()-p.start>=330){
   if(!p.dragging){p.dragging=true;const m=models.get(p.book),obj=p.outer?collectionEntity(p.book):m.group;p.position=obj.position.clone();p.object=obj;m.dragging=true;if(!p.outer){p.openBefore=m.open;m.dragFold={start:performance.now(),from:m.open}}}
   const v=dragWorld(e,p.position);if(v)p.object.position.copy(v);
  }
  invalidate();return true;
 }
 function pointerUp(e,cancel=false){if(!pointer)return !!focus;if(e.pointerId!==pointer.id)return true;const p=pointer;pointer=null;highlight(null);
  if(p.kind==='card'){
   if(p.moved&&!cancel){const slot=hoveredSlot(e);try{craft.placeCard(storage,p.entry.id,slot?slot.id:null,slot?.slot??null);refresh()}catch(err){toast(err.message)}}
   else if(!cancel){if(p.entry.record.containerId){toast('拖曳可移動卡片；右側 ● 開啟讀卡內頁')}else focusCard(p.entry.id)}
   p.entry.group.position.copy(p.position);p.entry.group.quaternion.copy(p.quaternion);p.entry.group.scale.setScalar(p.scale);
   }else if(p.kind==='gba-entry'&&!p.moved&&!cancel){focusGba()}
   else if(p.kind==='control'&&!p.moved&&!cancel){
    const key=p.control;
    const reader=[...cards.values()].find(c=>State.readerActive(c.record));
    if(key==='START'||key==='B'){focusGba()}
    else if(!reader){toast('先將卡片放入右側讀卡區')}
    else if(key==='SELECT'){
     const modes=craft.isBlankCopyMedium?.(reader.record)?['card','observe','copy']:['card','observe'];
     screenMode=modes[(modes.indexOf(screenMode)+1)%modes.length];
     paintGba(reader);
    }else if(key==='LEFT'||key==='RIGHT'||key==='L'||key==='R'){
     screenMode=screenMode==='card'?'observe':'card';
     paintGba(reader);
    }else if(key==='UP'||key==='DOWN'){
     if(screenMode==='copy'){
      const length=copySourcesCached().length;
      if(length)copyIndex=(copyIndex+(key==='DOWN'?1:length-1))%length;
     }else screenMode=key==='UP'?'card':'observe';
     paintGba(reader);
    }else if(key==='A'){
     if(screenMode==='copy')presentCopyConfirmation(reader);
     else if(screenMode==='observe'&&typeof observeCard==='function')observeCard(reader.id);
     else openCard(reader.id);
    }
   }
  else if(p.kind==='book'){
   const m=models.get(p.book);if(m){m.dragging=false;m.dragFold=null;if(cancel&&p.openBefore!==undefined){m.open=p.openBefore;m.setOpen(m.open)}}
   if(p.dragging&&!cancel){const position=p.object.position,dist=Math.hypot(position.x-keeper.position.x,position.z-keeper.position.z);if(dist<3.6)placeCollection(p.book);else foldCollection(true,p.book)}
   else if(!p.moved&&!cancel){if(p.outer)placeCollection(p.book);else if(m.open<.1&&State.isCollection(p.book)){m.desired=true;m.view.collectionOpen=true;persist(m);begin(m,true)}else focusBook(p.book)}
   if(p.dragging&&cancel&&p.object)p.object.position.copy(p.position);
  }
  invalidate();return true;
 }
 function cancelPointer(id){if(pointer&&(id===undefined||id===pointer.id))pointerUp({pointerId:pointer.id},true)}
 function refresh(){root.LivingScene?.refreshFloatingCards?.();invalidate()}
 function applyFocusLayout(now){
  if(focus){
   const members=focusMembers(),chosen=focus.gba?[models.get(State.ATLAS)]:(root.innerWidth>root.innerHeight?members:[models.get(focus.id)]);
   const center=new THREE.Vector3();for(const m of chosen)center.add(m.center);if(chosen.length)center.multiplyScalar(1/chosen.length);
   const side=new THREE.Vector3(1,0,0).applyQuaternion(models.get(focus.id).group.quaternion);
   chosen.sort((a,b)=>a.center.dot(side)-b.center.dot(side));
   const u=reduced?1:Math.min(1,(now-focus.started)/850),ease=u*u*(3-2*u),gap=Math.max(tuning.atlasSeparation,BASE*2.7+.08);
   for(let i=0;i<chosen.length;i++){const m=chosen[i],target=center.clone().addScaledVector(side,(i-(chosen.length-1)/2)*2*gap),from=(focus.layoutFrom.get(m.id)||m.center).clone().add(keeper.position.clone().sub(focus.keeper));m.group.position.copy(from).lerp(target,ease)}
   for(const m of models.values())if(m.group.visible&&!chosen.includes(m)){m.focusHidden=true;m.group.visible=false}
  }else if(returning){
   const u=reduced?1:Math.min(1,(now-returning.start)/850),ease=u*u*(3-2*u);
   for(const m of models.values())if(m.group.visible){const from=returning.layout.get(m.id);if(from)m.group.position.copy(from).add(keeper.position.clone().sub(returning.layoutKeeper)).lerp(m.center,ease);else if(u<1){m.focusHidden=true;m.group.visible=false}}
  }
 }
 function updateCamera(now){
  if(focus){const m=models.get(focus.id);if(!m.group.visible){exitFocus();return}
   const visible=focus.gba?[m]:(root.innerWidth>root.innerHeight?focusMembers():[m]);
   const bounds=new THREE.Box3();for(const x of visible)bounds.union(new THREE.Box3().setFromObject(x.group));
   const center=bounds.getCenter(new THREE.Vector3());
   // Measure in the book frame, independent of world yaw.
   const side=new THREE.Vector3(1,0,0).applyQuaternion(m.group.quaternion),positions=visible.map(x=>x.group.position.dot(side));
   const width=focus.gba?2.66*BASE:5.4*BASE+Math.max(...positions)-Math.min(...positions),height=3.7*BASE,tan=Math.tan(THREE.MathUtils.degToRad(camera.fov)*.5);
   const viewportHeight=Math.max(160,root.innerHeight),top=72,bottom=16,usable=Math.max(.3,(viewportHeight-top-bottom)/viewportHeight);
   const dist=(Math.max(height/(2*tan*usable*.9),width/(2*tan*camera.aspect*.9))+.15)*tuning.atlasFocusDistance;
   m.group.updateMatrixWorld(true);
   // Camera targets the actual right page, not a duplicated UI overlay.
   const pageCenter=focus.gba?new THREE.Vector3(1.34,0,.20).applyMatrix4(m.group.matrixWorld):center;
   const aim=pageCenter.clone().add(new THREE.Vector3(0,(top-bottom)/viewportHeight*dist*tan,0));
   const normal=new THREE.Vector3(0,0,1).applyQuaternion(m.group.quaternion),target=aim.clone().addScaledVector(normal,dist);
   const q=new THREE.Quaternion().setFromRotationMatrix(new THREE.Matrix4().lookAt(target,aim,new THREE.Vector3(0,1,0)));
   const t=reduced?1:Math.min(1,(now-focus.started)/850),u=t*t*(3-2*t);camera.position.copy(focus.from).lerp(target,u);camera.quaternion.copy(focus.rotation).slerp(q,u);
  }else if(returning){const r=returning,target=r.target.clone().add(keeper.position.clone().sub(r.keeper));const t=reduced?1:Math.min(1,(now-r.start)/850),u=t*t*(3-2*t);camera.position.copy(r.from).lerp(target,u);camera.quaternion.copy(r.rotation).slerp(r.quaternion,u);if(t>=1)returning=null}
 }
 function applyTuning(){
  const next=State.normalizeSettings(controls);if(Object.keys(next).every(k=>next[k]===tuning[k]))return;
  tuning=next;BASE=.36*tuning.atlasSize;
  for(const m of models.values()){
   if(m.direction){const side=new THREE.Vector3().crossVectors(new THREE.Vector3(0,1,0),m.direction);m.offset=m.direction.clone().multiplyScalar(tuning.atlasDistance).addScaledVector(side,m.side*tuning.atlasSeparation)}
   if(!m.animation)m.group.scale.setScalar(BASE);
  }
  restoreFocusCamera();invalidate();
 }
 function tick(now){
  for(const m of models.values())if(m.focusHidden){m.group.visible=true;m.focusHidden=false}
  applyTuning();
  syncCollections();
  for(const m of models.values())if(m!==atlas){const entity=collectionEntity(m.id);if(entity&&!m.dragging)entity.visible=m.view.collection!=='inner'&&!focus&&!returning;}
  for(const m of models.values()){
   if(!m.group.visible)continue;
   if(!m.dragging){m.center.set(keeper.position.x+(m.offset?.x||0),tuning.atlasHeight,keeper.position.z+(m.offset?.z||0));m.group.position.copy(m.center);m.group.quaternion.copy(m.rotation)}
   if(m.dragFold){m.open=m.dragFold.from*(1-Math.min(1,(now-m.dragFold.start)/350));m.setOpen(m.open)}
   if(m.animation){const a=m.animation,age=now-a.start;let complete=false;
    if(a.show){const t=reduced?1:Math.min(1,age/1100);m.group.scale.setScalar(BASE*Math.max(.001,a.fromScale+(1-a.fromScale)*Math.min(1,t/.35)));m.open=a.fromOpen+(1-a.fromOpen)*Math.max(0,Math.min(1,(t-.35)/.65));if(m.puffs)smoke.update(m.puffs,m.center,age,1100);complete=t>=1}
    else if(m!==atlas&&m.afterClose==='closed'){const t=reduced?1:Math.min(1,age/650);m.open=a.fromOpen*(1-t);m.group.scale.setScalar(BASE);complete=t>=1}
    else{const t=reduced?1:Math.min(1,age/1000);m.open=a.fromOpen*(1-Math.min(1,t/.45));if(t>=.45&&!m.puffs)m.puffs=smoke.create(m.center.clone(),false);if(m.puffs)smoke.update(m.puffs,m.center,Math.max(0,age-450),550);m.group.scale.setScalar(BASE*a.fromScale*Math.max(.001,t<.5?1:1-(t-.5)*2));complete=t>=1}
    m.setOpen(m.open);
    if(complete){if(m.puffs)smoke.remove(m.puffs);m.puffs=null;m.animation=null;m.group.scale.setScalar(BASE);
     if(!a.show){if(m!==atlas&&m.afterClose==='closed'){m.group.visible=true;m.open=0;m.setOpen(0)}else{m.group.visible=false;if(m!==atlas){m.view.collection='shelf';persist(m);setCollectionActive(false,m.id)}}if(focus?.id===m.id)exitFocus()}
     if(m===atlas&&m.desired!==a.show)begin(m,m.desired);
     if(m!==atlas&&m.desired!==a.show&&m.desired)begin(m,true);
     syncUI();
    }
   }
  }
  applyFocusLayout(now);
  for(const entry of cards.values()){
   const record=entry.record;if(pointer?.entry?.id===entry.id&&pointer.moved)continue;
   if(!record.containerId)continue;const m=models.get(record.containerId),target=m?.targets.find(t=>t.index===record.slotIndex);
   entry.group.visible=!!target&&m.group.visible&&m.open>.03;
   if(entry.group.visible){target.node.updateWorldMatrix(true,false);target.node.getWorldPosition(entry.group.position);target.node.getWorldQuaternion(entry.group.quaternion);entry.group.scale.setScalar(m.group.scale.x*1.3*tuning.atlasCardSize)}
  }
  const reader=[...cards.values()].find(c=>State.readerActive(c.record));
  if(uploadCardId!==reader?.id){uploadCardId=reader?.id||null;uploadStatus={state:'idle',code:''}}
  paintGba(reader);
  updateCamera(now);stageData();
 }
 // Called only from the explicitly authorized per-card sender. Nothing is persisted.
 function reportGithubUpload(cardId,state,code=''){
  const reader=[...cards.values()].find(c=>State.readerActive(c.record));
  if(!reader||reader.id!==cardId)return false;
  if(!['idle','auth','sending','ok','error'].includes(state))return false;
  uploadCardId=cardId;uploadStatus={state,code:String(code||'').slice(0,45)};
  paintGba(reader);invalidate();return true;
 }
 function active(){return !!focus||!!returning||!!pointer||[...models.values()].some(m=>!!m.animation)}
 function restoreFocusCamera(){if(focus){focus.from=camera.position.clone();focus.rotation=camera.quaternion.clone();focus.started=performance.now()}}
 function showReaderReturn(){restoreFocusCamera();invalidate()}
 function dispose(){disposed=true;cancelCopyConfirmation();cancelPointer();for(const m of models.values()){if(m.puffs)smoke.remove(m.puffs);m.dispose();scene.remove(m.group)}audio?.close().catch(()=>{})}
 if(saved.collection==='inner'){collection.desired=saved.collectionOpen!==false;setInnerPose(collection);collection.group.visible=true;collection.open=collection.desired?1:0;collection.setOpen(collection.open)}
 if(saved.collection==='shelf')setCollectionActive(false,State.COLLECTION);
 syncCollections();
 syncUI();
 root.addEventListener('pagehide',e=>{if(!e.persisted)dispose();else cancelPointer()});
 return {toggle,summon,placeCollection,focus:focusBook,exitFocus,tick,active,pointerDown,pointerMove,pointerUp,cancelPointer,
  isDraggingCard:id=>pointer?.entry?.id===id&&pointer.moved,isCollectionDragging:(id=State.COLLECTION)=>!!models.get(id)?.dragging,isCollection:id=>State.isCollection(id)&&models.has(id),
  get movementYaw(){const v=new THREE.Vector3(0,0,1).applyQuaternion(camera.quaternion);return Math.atan2(v.x,v.z)},get inFocus(){return !!focus||!!returning},get focusId(){return focus?.id},get collectionMode(){return saved.collection},showReaderReturn,audioStart,reportGithubUpload,
  inspect(){return {focusBooks:focus?(root.innerWidth>root.innerHeight?focusMembers().map(m=>m.id):[focus.id]):[],collections:[...models.values()].filter(m=>m!==atlas).map(m=>({id:m.id,mode:m.view.collection,open:m.open,visible:m.group.visible,position:m.group.position.toArray()})),tuning:{...tuning},atlasPosition:atlas.group.position.toArray(),atlasScale:atlas.group.scale.x,atlasVisible:atlas.group.visible,atlasDesired:atlas.desired,atlasOpen:atlas.open,screenMode,gbaFocus:!!focus?.gba,uploadState:uploadStatus.state,copyMode:screenMode==='copy',copyIndex,copyOptions:copySourceCache.length,atlasFacingKeeper:new THREE.Vector3(0,0,1).applyQuaternion(atlas.group.quaternion).dot(keeper.position.clone().sub(atlas.center).setY(0).normalize()),collectionMode:saved.collection,collectionOpen:collection.open,focus:focus?.id||null,animation:!!atlas.animation||!!collection.animation}},dispose};
}};
})(typeof self!=='undefined'?self:this);
