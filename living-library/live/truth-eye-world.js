/* Truth Eye 3D world integration: no image planes; original card IDs only.
 * Stage 1: scanner + binding the scanner itself. Scanned collections use an original blank card with reversible identity references. */
(function(root){'use strict';
const ID='artifact.truth-eye',RAD=Math.PI/180,clamp=(n,a,b)=>Math.max(a,Math.min(b,n));
function model(T){
 const group=new T.Group();group.name='真實之眼';group.userData.objectId=ID;
 const gold=new T.MeshStandardMaterial({color:0xe8ae5e,metalness:.73,roughness:.24,emissive:0x65300d,emissiveIntensity:.24});
 const bright=new T.MeshStandardMaterial({color:0xffdb9e,metalness:.62,roughness:.20,emissive:0x603410,emissiveIntensity:.3});
 const blue=new T.MeshPhysicalMaterial({color:0x187bc1,metalness:.23,roughness:.1,clearcoat:1,emissive:0x116fc7,emissiveIntensity:.71});
 const dark=new T.MeshStandardMaterial({color:0x061b34,metalness:.28,roughness:.28,emissive:0x0d3051,emissiveIntensity:.3});
 const light=new T.MeshBasicMaterial({color:0x80edff,blending:T.AdditiveBlending,transparent:true,opacity:.82,depthWrite:false});
 const add=(parent,geom,material,x=0,y=0,z=0)=>{const m=new T.Mesh(geom,material);m.position.set(x,y,z);m.userData.objectId=ID;parent.add(m);return m};
 const torus=(parent,r,t,material,z=0)=>add(parent,new T.TorusGeometry(r,t,8,84),material,0,0,z);
 const body=new T.Group();group.add(body);
 const outline=new T.Shape();outline.moveTo(-1.93,0);outline.bezierCurveTo(-1,.92,1,.92,1.93,0);outline.bezierCurveTo(1,-.92,-1,-.92,-1.93,0);
 add(body,new T.ExtrudeGeometry(outline,{depth:.2,bevelEnabled:true,bevelThickness:.07,bevelSize:.07,bevelSegments:3,curveSegments:32}),gold,0,0,-.15);
 const inside=add(body,new T.ShapeGeometry(outline,32),dark,0,0,.16);inside.scale.set(.92,.86,1);
 const eyeball=add(body,new T.SphereGeometry(.79,40,28),blue,0,0,.22);eyeball.scale.set(1.24,.69,.53);
 const pupil=add(body,new T.SphereGeometry(.54,40,28),dark,0,0,.60);pupil.scale.set(1,1,.22);
 const iris=add(body,new T.SphereGeometry(.47,40,28),blue,0,0,.70);iris.scale.set(1,1,.15);
 for(const [r,t,m,z] of [[.59,.045,bright,.70],[.48,.026,light,.77],[.34,.02,bright,.80],[.26,.012,light,.82]])torus(body,r,t,m,z);
 const slit=add(body,new T.SphereGeometry(1,30,20),dark,0,0,.86);slit.scale.set(.13,.29,.04);
 const jewel=add(body,new T.OctahedronGeometry(.17),bright,0,0,.92);jewel.scale.set(.62,1.5,.33);
 for(const [x,y] of [[0,1.65],[0,-1.65],[-2.08,0],[2.08,0]]){
  const crystal=add(body,new T.OctahedronGeometry(.28),blue,x,y,.25);crystal.rotation.z=Math.PI/4;
  const spear=add(body,new T.ConeGeometry(.21,.72,4),bright,x,y+(.1*(y>=0?1:-1)),.05);
  spear.rotation.z=y<0?Math.PI:x>0?-Math.PI/2:x<0?Math.PI/2:0;
 }
 const orbits=[];
 for(const [r,rx,ry,rz,speed] of [[2.21,.37,.42,0,.12],[2.05,-.38,-.51,-.28,-.10],[2.43,.77,.18,.48,.08]]){
  const pivot=new T.Group();group.add(pivot);pivot.rotation.set(rx,ry,rz);
  const hoop=torus(pivot,r,.065,gold);hoop.scale.y=.76;
  const trim=torus(pivot,r+.09,.013,bright);trim.scale.y=.76;
  for(let i=0;i<18;i++){const a=i/18*2*Math.PI;
   const mark=add(pivot,new T.BoxGeometry(.065,.017,.017),i%4?gold:bright,r*Math.cos(a),r*.76*Math.sin(a),.07);mark.rotation.z=a;
  }
  orbits.push({pivot,rx,ry,rz,speed});
 }
 for(let i=0;i<8;i++){const a=i/8*Math.PI*2;add(group,new T.OctahedronGeometry(.14),i%2?blue:bright,2.55*Math.cos(a),1.76*Math.sin(a),0)}
 // Invisible, broad 3D interaction volume: tapping gold filigree should not
 // require hitting a thin triangle or precisely tracking the orbit animation.
 const hitTarget=new T.Mesh(new T.SphereGeometry(2.07,18,12),
  new T.MeshBasicMaterial({colorWrite:false,depthWrite:false,transparent:true,opacity:0}));
 hitTarget.name='truth-eye-gesture-hit-target';hitTarget.position.z=.08;
 group.add(hitTarget);
 group.scale.setScalar(.46);
 function tick(t,reduced){
  body.position.y=reduced?0:Math.sin(t)*.055;body.rotation.y=reduced?0:Math.sin(t*.38)*.08;
  orbits.forEach(o=>o.pivot.rotation.set(o.rx,o.ry+(reduced?0:t*o.speed),o.rz));
 }
 return {group,tick};
}
function create(opts){
 const {THREE:T,scene,keeper,camera,renderer,bookEntities,floatingCards,storage,craft,refreshCards,invalidate,toast,reducedMotion=false}=opts;
 if(!T||!scene||!keeper||!bookEntities||!floatingCards)throw Error('真實之眼需要既有 3D 場景');
 const B=root.LivingScanBundle;if(!B)throw Error('缺少掃描收納模組');
 const C=root.LivingTruthEyeContract;if(!C)throw Error('真實之眼卡片契約模組不存在');
 const built=model(T),eye=built.group;scene.add(eye);
 const eyeLight=new T.PointLight(0x5cc5eb,4,5);scene.add(eyeLight);
 let cfg={radius:8,aperture:360,direction:0,mode:'both',scope:'book',enabled:true};
 try{const s=JSON.parse(storage.getItem('living-truth-eye-scan-v1')||'null');
  if(s){cfg.radius=clamp(Number(s.radius)||8,2,20);cfg.aperture=clamp(Number(s.aperture)||360,15,360);
   cfg.direction=clamp(Number(s.direction)||0,0,360);cfg.scope=['book','scene','all'].includes(s.scope)?s.scope:'book';cfg.mode=['both','radar','ripple'].includes(s.mode)?s.mode:'both'}
 }catch(_){}
 let card=null;try{card=C.boundCard(storage)}catch(e){toast('讀卡失敗：'+e.message)}
 const field=new T.Group();scene.add(field);
 const cyan=new T.MeshBasicMaterial({color:0x8cf6eb,opacity:.51,transparent:true,depthWrite:false,blending:T.AdditiveBlending,side:T.DoubleSide});
 const amber=new T.MeshBasicMaterial({color:0xfce0a5,opacity:.42,transparent:true,depthWrite:false,blending:T.AdditiveBlending,side:T.DoubleSide});
 const ring=(parent,mat)=>{const m=new T.Mesh(new T.TorusGeometry(1,.013,5,96),mat);m.rotation.x=Math.PI/2;parent.add(m);return m};
 const border=ring(field,cyan),ripples=[ring(field,amber.clone()),ring(field,cyan.clone()),ring(field,amber.clone())];
 let fan=new T.Mesh(new T.CircleGeometry(1,72),new T.MeshBasicMaterial({color:0x5ddce0,opacity:.085,transparent:true,depthWrite:false,side:T.DoubleSide,blending:T.AdditiveBlending}));
 fan.rotation.x=-Math.PI/2;fan.position.y=.018;field.add(fan);
 const marks=new T.Group();scene.add(marks);
 const markMap=new Map(),selected=new Set(),caster=new T.Raycaster(),mouse=new T.Vector2(),tmp=new T.Vector3();
 // The SAME 3D hexagram animation is reused by the original ground rune.
 // For the Eye it faces the camera and floats around the actual 3D artifact.
 const Hexagram=root.LivingContractHexagram;
 if(!Hexagram)throw Error('缺少六芒星動畫模組');
 const hex=Hexagram.create({THREE:T,parent:scene,plane:'xy',radius:1.65});
 const ritual=hex.group;
 // The original ritual geometry is reused around the ORIGINAL orbit card
 // during card -> world manifestation. This is independent of the 3-second
 // hold ritual on the 3D Eye, so both transitions keep the approved artwork.
 const cardHex=Hexagram.create({THREE:T,parent:scene,plane:'xy',radius:.80});
 let cardCeremony=null,cardHold=null;
 const cardCaptured=new Set();
 let hold=null,serial=0,lastScan=0,transfer=null,pending=null,selectedUntil=0;
 let bundleTransfer=null,packed=new Map(),bundleError=null,packedRaw;
 const Directed=root.LivingDirectedTransfer;
 if(!Directed?.normalize||!Directed?.sample)throw Error('缺少既有卡片定向吸收動畫');
 // A consumed pointer remains consumed even after the 3-second timer completes.
 const heldPointers=new Set();
 const style=document.createElement('style');
 style.textContent='.truthEyeHud{position:fixed;z-index:9;right:250px;top:clamp(105px,19vh,170px);width:195px;max-width:45vw;color:#ffde9c;text-shadow:0 1px 7px #020711;font:11px system-ui;pointer-events:auto}.truthEyeHud button{cursor:pointer;color:#fff0c8;background:#142333b8;border:1px solid #bda1718c;border-radius:50%;width:39px;height:39px;font-size:22px;touch-action:none}.truthEyeHud header{display:flex;justify-content:flex-end;align-items:center;gap:6px}.truthEyeHud small{font-size:10px}.truthEyeHud main{padding-top:7px;display:grid;gap:8px;background:none!important}.truthEyeHud main[hidden]{display:none}.truthEyeHud label{display:grid;grid-template-columns:25px 1fr 33px;gap:5px;align-items:center}.truthEyeHud input{width:100%;accent-color:#e6c381;touch-action:pan-y}.truthEyeHud select{justify-self:end;border:1px solid #a98b5b;color:#fce8bf;background:#1c2b38d9;border-radius:4px}@media(min-width:651px) and (max-width:920px){.truthEyeHud{right:205px}}@media(max-width:650px){.truthEyeHud{right:6px;top:105px;width:165px}.truthEyeHud label{grid-template-columns:25px 1fr 27px;gap:2px}}';
 document.head.append(style);
 const host=document.createElement('section');host.className='truthEyeHud';host.setAttribute('aria-label','真實之眼');
 host.innerHTML='<header><button id="eyeToggle" aria-label="掃描開關" title="掃描開關">◉</button><small id="eyeStatus"></small></header><main id="eyeControls"><label>半徑<input id="eyeRadius" type="range" min="2" max="20" step=".5"><output id="eyeRadiusVal"></output></label><label>角度<input id="eyeAperture" type="range" min="15" max="360" step="5"><output id="eyeApertureVal"></output></label><label>方向<input id="eyeDirection" type="range" min="0" max="360" step="5"><output id="eyeDirectionVal"></output></label><select id="eyeMode" aria-label="掃描模式"><option value="both">複合掃描</option><option value="radar">雷達</option><option value="ripple">水波</option></select></main>';
 document.body.append(host);
 const tools=document.createElement('div');tools.className='truthEyeTools';
 tools.innerHTML='<button type="button" id="eyeSummon" aria-label="召喚全部書櫃書本" title="召喚全部書櫃書本">⇧</button><button type="button" id="eyeSelectAll" aria-label="全選掃描範圍內物件" title="全選掃描範圍內物件">⊙</button><button type="button" id="eyeCollect" aria-label="長按三秒收納選取物件" title="長按三秒收納選取物件">🔯</button><small id="eyeCount" role="status"></small>';
 host.querySelector('#eyeControls').append(tools);
 const extraStyle=document.createElement('style');extraStyle.textContent='.truthEyeTools{display:flex;flex-wrap:wrap;gap:5px}.truthEyeTools button{min-width:44px;min-height:44px}.truthEyeTools small{flex-basis:100%;line-height:1.5}.truthEyeTools button[data-holding="true"]{outline:2px solid #8cf6eb}';document.head.append(extraStyle);
 const scopeSelect=document.createElement('select');scopeSelect.id='eyeScope';scopeSelect.setAttribute('aria-label','掃描對象');
 scopeSelect.innerHTML='<option value="book">書本</option><option value="scene">圖書館場景</option><option value="all">書本與場景</option>';scopeSelect.value=cfg.scope;
 host.querySelector('#eyeControls').prepend(scopeSelect);
 scopeSelect.addEventListener('change',e=>{if(busy()){scopeSelect.value=cfg.scope;return}cfg.scope=e.target.value;selected.clear();apply();updateMarks()});
 const countLabel=tools.querySelector('#eyeCount'),collectButton=tools.querySelector('#eyeCollect');
 tools.querySelector('#eyeSummon').addEventListener('click',()=>{if(busy())return;opts.summonShelfBooks?.();invalidate()});
 tools.querySelector('#eyeSelectAll').addEventListener('click',()=>{if(busy())return;selected.clear();targets().forEach(o=>selected.add(o.id));updateMarks();toast('👁 已選取掃描範圍內 '+selected.size+' 件；長按 🔯 三秒收納');invalidate()});
 collectButton.addEventListener('pointerdown',e=>{if(e.button!==0||e.isPrimary===false||!selected.size||!field.visible||busy())return;heldPointers.add(e.pointerId);collectButton.setPointerCapture(e.pointerId);e.preventDefault();start('books',e.pointerId,e.clientX,e.clientY)});
 collectButton.addEventListener('pointermove',pointerMove);
 for(const event of ['pointerup','pointercancel','lostpointercapture'])collectButton.addEventListener(event,pointerEnd);
 collectButton.addEventListener('contextmenu',e=>e.preventDefault());
 function busy(){return !!(hold||cardHold||pending||transfer||bundleTransfer||dialog?.open)}
 function entries(){return (opts.getScanEntries?.()||bookEntities.filter(o=>['public','local'].includes(o.b.sourceLayer)).map(o=>({...o,kind:'book'})))}
 function refreshPacked(){
  try{const raw=storage.getItem(B.KEY);if(raw===packedRaw)return;packed=B.claims(storage,true);bundleError=null;packedRaw=raw}
  catch(e){if(!bundleError)toast(e.message);bundleError=e;packed=new Map()}
 }
 function isPacked(kind,id){return packed.has(kind+':'+id)}
 function applyPacked(){
  refreshPacked();
  if(bundleError)return;
  const mask=bundleTransfer?.releasing?new Map([...packed].filter(([,id])=>id!==bundleTransfer.cardId)):packed;
  for(const o of entries())if(o.kind==='book'&&mask.has(o.kind+':'+o.id))for(const g of o.groups||[o.group])g.visible=false;
  opts.applyPacked?.(mask);
 }
 function bundlePrompt(kind,record,members){
  if(busy())return false;
  pending={kind,cardId:record.id,cardRevision:record.revision,members};
  confirmLabel.textContent=kind==='bundle-bind'?'將掃描選取的 '+members.length+' 件物件收進這張空白卡？\n原物件 ID 保留，長按同一張收納卡可還原。':kind==='bundle-release'?'將此卡中的 '+record.scanContract.members.length+' 件原物件還原到世界？':'將此契約的原物件再次收回同一張卡？';
  dialog.showModal();invalidate();return true;
 }
 function startBundleTransfer(request){
  try{
   const record=craft.listCards(storage).find(c=>c.id===request.cardId),entry=floatingCards.get(request.cardId);
   if(bundleError||!record||record.revision!==request.cardRevision||!entry?.group.visible||entry.summon||entry.recycle||entry.drag||entry.focused||record.placement!=='hover'||record.containerId||record.ownerBookId)throw Error('原卡已改變或正在移動，請重新長按');
   const members=request.members||B.contract(record).members;
   const available=request.kind==='bundle-bind'?targets():entries();
   if(request.kind==='bundle-bind'&&(!B.blank(record)||B.nearest({x:keeper.position.x,z:keeper.position.z},cardEntries())!==record.id))throw Error('最近空白卡已改變');
   if(members.some(r=>!available.some(o=>o.id===r.id&&o.kind===r.kind)))throw Error('選取物件已離開掃描範圍或尚未載入');
   const releasing=request.kind==='bundle-release';
   const snapshots=members.flatMap(r=>{const o=entries().find(o=>o.id===r.id&&o.kind===r.kind);return (o.groups||[o.group]).map(group=>({group,position:group.position.clone(),scale:group.scale.clone(),visible:group.visible}))});
   entry.group.updateWorldMatrix(true,false);const dest=entry.group.getWorldPosition(new T.Vector3());
   const anchor={x:keeper.position.x,z:keeper.position.z};
   bundleTransfer={...request,members,releasing,snapshots,dest,anchor,started:performance.now(),duration:reducedMotion?400:1400};
   invalidate();
  }catch(e){toast('收納未開始：'+e.message);invalidate()}
 }
 function tickBundle(now){
  const job=bundleTransfer;if(!job)return;
  const progress=clamp((now-job.started)/job.duration,0,1);
  const u=progress*progress*(3-2*progress);
  for(const snap of job.snapshots){const g=snap.group;g.visible=true;
   const p=job.releasing?1-u:u;
   // Preserve each object's original local transform, including keeper visuals
   // whose control anchor must remain fixed throughout the absorption.
   const endpoint=g.parent?.worldToLocal(job.dest.clone())||job.dest;
   g.position.copy(snap.position).lerp(endpoint,p);g.position.y+=Math.sin(Math.PI*p)*.6;
   g.scale.copy(snap.scale).multiplyScalar(Math.max(.001,1-p));
  }
  if(progress<1)return;
  try{
   const available=entries();
   if(Math.hypot(keeper.position.x-job.anchor.x,keeper.position.z-job.anchor.z)>.15)throw Error('人物在收納期間移動，請重新掃描');
   if(job.kind==='bundle-bind')B.bind(storage,{cardId:job.cardId,revision:job.cardRevision,members:job.members},entries());
   else B.setForm(storage,job.cardId,job.cardRevision,job.releasing?'world':'card',available);
   selected.clear();refreshCards();toast(job.releasing?'✦ 原物件已從同一張卡還原':'✦ 已將 '+job.members.length+' 件原物件收進同一張卡');
  }catch(e){toast('收納未寫入，原物件保留：'+e.message)}
  for(const snap of job.snapshots){snap.group.position.copy(snap.position);snap.group.scale.copy(snap.scale);snap.group.visible=job.releasing||snap.visible}
  bundleTransfer=null;refreshPacked();invalidate();
 }
 refreshPacked();
 // Same confirmation gate as the ORIGINAL ground-rune flow; no direct card write.
 const dialog=document.createElement('dialog');
 dialog.id='truthEyeContractConfirm';
 dialog.setAttribute('aria-label','真實之眼契約確認');
 dialog.innerHTML='<div class="truth-eye-confirm"><h2>六芒星締結契約</h2><p id="truthEyeConfirmText"></p><div class="truth-eye-dialog-actions"><button type="button" id="truthEyeCancel">取消</button><button type="button" id="truthEyeApprove">確認</button></div></div>';
 const dialogStyle=document.createElement('style');
 dialogStyle.textContent='#truthEyeContractConfirm{max-width:min(390px,91vw);color:#fff0cd;background:#132232;border:1px solid #a68c62;border-radius:12px;padding:25px;box-shadow:0 12px 80px #02050bbd;font:14px/1.7 system-ui,sans-serif}#truthEyeContractConfirm::backdrop{background:#03080d66}#truthEyeContractConfirm h2{font-size:19px;font-weight:500;color:#f6d69f;margin:0 0 12px}#truthEyeContractConfirm p{margin:0 0 22px;white-space:pre-wrap}#truthEyeContractConfirm .truth-eye-dialog-actions{display:flex;justify-content:flex-end;gap:10px}#truthEyeContractConfirm button{border:1px solid #c9a870;border-radius:7px;padding:8px 17px;background:#263549;color:#fae5bb;cursor:pointer}';
 document.head.append(dialogStyle);document.body.append(dialog);
 const confirmLabel=dialog.querySelector('#truthEyeConfirmText');
 const cancelBtn=dialog.querySelector('#truthEyeCancel');
 const approveBtn=dialog.querySelector('#truthEyeApprove');
 const el=name=>host.querySelector('#eye'+name),control=el('Controls');
 const status=el('Status');
 function state(){try{card=C.boundCard(storage)}catch(e){toast(e.message);card=null}
  const form=card?.truthEyeContract?.form;
  eye.visible=!card||form==='world'||form==='sealed';eyeLight.visible=eye.visible;
  // Every world artifact exposes its binding through ONE standard scene marker.
  // Unbinding never needs to know this object's type, name or artifact ID.
  if(card&&['world','sealed'].includes(form))eye.userData.boundCardId=card.id;
  else delete eye.userData.boundCardId;
  field.visible=form==='world'&&cfg.enabled;marks.visible=field.visible;control.hidden=form!=='world';
  host.hidden=form!=='world';
  el('Toggle').hidden=form!=='world';
  status.textContent=cfg.enabled?'掃描中':'已暫停';
 }
 function apply(){for(const key of ['Radius','Aperture','Direction']){const name=key.toLowerCase(),number=cfg[name];
  el(key).value=String(number);el(key+'Val').textContent=number+(name==='radius'?'m':'°');
 }el('Mode').value=cfg.mode;
  fan.geometry.dispose();fan.geometry=new T.CircleGeometry(cfg.radius,Math.max(48,Math.round(cfg.aperture/3)),Math.PI/2-(cfg.direction+cfg.aperture/2)*RAD,cfg.aperture*RAD);
  fan.rotation.z=0;border.scale.setScalar(cfg.radius);
  try{storage.setItem('living-truth-eye-scan-v1',JSON.stringify(cfg))}catch(_){}
  invalidate();
 }
 for(const [k,name] of [['radius','Radius'],['aperture','Aperture'],['direction','Direction']]){
  el(name).addEventListener('input',e=>{if(busy()){apply();return}cfg[k]=Number(e.target.value);apply();updateMarks()});
 }
 el('Mode').addEventListener('change',e=>{if(busy()){apply();return}cfg.mode=e.target.value;apply()});
 el('Toggle').addEventListener('click',()=>{if(busy())return;cfg.enabled=!cfg.enabled;state();invalidate()});
 function hit(e,meshes){const rect=renderer.domElement.getBoundingClientRect();if(!rect.width||!rect.height)return null;
  mouse.set((e.clientX-rect.left)/rect.width*2-1,-(e.clientY-rect.top)/rect.height*2+1);
  caster.setFromCamera(mouse,camera);return caster.intersectObjects(meshes,true)[0]||null;
 }
 function inRange(o){
  if(o.anchor)tmp.copy(o.anchor);else o.group.getWorldPosition(tmp);const dx=tmp.x-keeper.position.x,dz=tmp.z-keeper.position.z;
  const direction=(Math.atan2(dx,-dz)/RAD-cfg.direction+540)%360-180;
  return Math.hypot(dx,dz)<=cfg.radius&&(cfg.aperture===360||Math.abs(direction)<=cfg.aperture/2);
 }
 function targets(){return field.visible&&!bundleError?entries().filter(o=>(cfg.scope==='all'||o.kind===cfg.scope)&&!isPacked(o.kind,o.id)&&o.group.visible&&!(o.groups||[o.group]).some(g=>g.userData.boundCardId)&&inRange(o)):[]}
 function at(e){if(!field.visible)return null;const list=[];
  for(const o of targets()){for(const g of o.groups||[o.group])g.traverse(m=>{if(m.isMesh){m.userData.truthEyeId=o.id;list.push(m)}});const marker=markMap.get(o.id);if(marker?.visible)marker.traverse(m=>{if(m.isMesh){m.userData.truthEyeId=o.id;list.push(m)}})}
  return hit(e,list)?.object.userData.truthEyeId||null;
 }
 function pick(e){const id=at(e);if(!id)return false;
  if(selected.has(id))selected.delete(id);else selected.add(id);
  toast('👁 已選取 '+selected.size+' 件，長按選取物件或 🔯 三秒可收進空白卡');invalidate();return true;
 }
 function updateMarks(){
  const t=targets(),set=new Set(t.map(o=>o.id));
   countLabel.textContent='掃描 '+t.length+' 件 · 選取 '+selected.size+' 件';
  for(const [id,g] of markMap)g.visible=set.has(id);
  for(const o of t){let m=markMap.get(o.id);
   if(!m){m=new T.Group();const r=ring(m,cyan.clone());r.scale.setScalar(.49);const volume=new T.Mesh(new T.SphereGeometry(.45,10,8),new T.MeshBasicMaterial({colorWrite:false,depthWrite:false,transparent:true,opacity:0}));m.add(volume);
    const j=new T.Mesh(new T.OctahedronGeometry(.10),amber.clone());j.position.y=.72;m.add(j);marks.add(m);markMap.set(o.id,m);}
   if(o.anchor)tmp.copy(o.anchor);else o.group.getWorldPosition(tmp);m.position.copy(tmp);m.position.y+=.7;
   m.scale.setScalar(selected.has(o.id)?1.65:1);
  }
  for(const id of [...selected])if(!set.has(id))selected.delete(id);
 }
 function stop(){
  if(hold){clearTimeout(hold.timer);hold=null}
  collectButton?.removeAttribute('data-holding');
  hex.clear();invalidate();
 }
 function clearCardCeremony(){
  cardCeremony=null;
  cardHex.clear();
 }
 function startCardCeremony(id){
  const entry=floatingCards.get(id);
  if(!entry||!entry.group.visible)return false;
  entry.group.updateWorldMatrix(true,false);
  const dimensions=new T.Box3().setFromObject(entry.group).getSize(new T.Vector3());
  const radius=Math.max(.58,Math.min(1.45,Math.hypot(dimensions.x,dimensions.y)*.72));
  cardHex.setRadius(radius);
  cardCeremony={cardId:id,phase:'holding',startedAt:performance.now(),
   radius,anchor:entry.group.getWorldPosition(new T.Vector3())};
  cardHex.group.position.copy(cardCeremony.anchor);
  cardHex.group.quaternion.copy(camera.quaternion);
  cardHex.update(0,performance.now());
  invalidate();
  return true;
 }
 function cancelCardHold(){
  if(cardHold){clearTimeout(cardHold.timer);cardHold=null}
  if(cardCeremony?.phase==='holding')clearCardCeremony();
  invalidate();
 }
 function finishCardHold(){
  const held=cardHold;
  if(!held||performance.now()-held.startedAt<3000)return false;
  clearTimeout(held.timer);cardHold=null;
  state();
  const record=craft.listCards(storage).find(x=>x.id===held.cardId);
  const entry=floatingCards.get(held.cardId);
  if(!record||record.revision!==held.revision||(!record.scanContract&&record.id!==card?.id)||
    (!record.scanContract&&record.truthEyeContract?.form!=='card')||record.placement!=='hover'||
    record.containerId||record.ownerBookId||!entry?.group.visible||
    entry.summon||entry.recycle){
   clearCardCeremony();toast('真實之眼契約卡已變更，請重新長按三秒');return false;
  }
  if(!cardCeremony||cardCeremony.cardId!==held.cardId)return false;
  cardCeremony.phase='selected';
  cardHex.update(1,performance.now());
  if(!(record.scanContract?bundlePrompt(record.scanContract.form==='card'?'bundle-release':'bundle-repack',record):prompt('materialize',record.id,record.revision,record.title))){
   clearCardCeremony();return false;
  }
  return true;
 }
 function cardPressStart(e,id){
  state();
  const bundleCard=craft.listCards(storage).find(c=>c.id===id&&c.scanContract);
  if(!bundleCard&&(!card||card.id!==id||card.truthEyeContract?.form!=='card'))return false;
  const pressed=bundleCard||card;
  if(pressed.placement!=='hover'||pressed.containerId||pressed.ownerBookId||pending||transfer||bundleTransfer||hold||dialog.open)return false;
  const entry=floatingCards.get(id);
  if(!entry||!entry.group.visible||entry.summon||entry.recycle||
   cardHold||e.isPrimary===false||e.button!==0||!Number.isInteger(e.pointerId))return false;
  if(!startCardCeremony(id))return false;
  cardCaptured.add(e.pointerId);
  cardHold={cardId:id,pointerId:e.pointerId,x:e.clientX,y:e.clientY,
   revision:pressed.revision,startedAt:performance.now(),timer:setTimeout(finishCardHold,3000)};
  invalidate();
  return true;
 }
 function cardPressMove(e,id,moved){
  if(!cardCaptured.has(e.pointerId))return false;
  if(cardHold?.pointerId===e.pointerId&&
   (moved||Math.hypot(e.clientX-cardHold.x,e.clientY-cardHold.y)>8))cancelCardHold();
  return true;
 }
 function cardPressEnd(e,id,cancel=false,moved=false){
  if(!cardCaptured.delete(e.pointerId))return false;
  if(cardHold?.pointerId===e.pointerId){
   if(!cancel&&!moved&&performance.now()-cardHold.startedAt>=3000)finishCardHold();
   else cancelCardHold();
  }
  return !cancel&&!moved;
 }
 function updateCardCeremony(now){
  if(!cardCeremony)return;
  const st=cardCeremony;
  if(st.phase==='holding'){
   if(!cardHold||cardHold.cardId!==st.cardId){clearCardCeremony();return}
  }else if(st.phase==='selected'){
   if(!pending||!['materialize','bundle-release','bundle-repack'].includes(pending.kind)||pending.cardId!==st.cardId){
    clearCardCeremony();return;
   }
  }else{clearCardCeremony();return}
  const entry=floatingCards.get(st.cardId);
  if(!entry||!entry.group.visible){clearCardCeremony();return}
  entry.group.updateWorldMatrix(true,false);
  st.anchor.copy(entry.group.getWorldPosition(new T.Vector3()));
  // Original six seals illuminate WHILE the finger is held, BEFORE the
  // materialization question. The 3D transfer is a separate subsequent step.
  const p=st.phase==='holding'?clamp((now-st.startedAt)/3000,0,1):1;
  cardHex.setRadius(st.radius);
  cardHex.update(p,now);
  cardHex.group.position.copy(st.anchor);
  cardHex.group.quaternion.copy(camera.quaternion);
 }
 function cancelPrompt(){
  const wasMaterialize=pending?.kind==='materialize';
  pending=null;
  if(dialog.open)dialog.close();
  if(wasMaterialize)clearCardCeremony();
  invalidate();
 }
 function prompt(kind,cardId,cardRevision,cardTitle=''){
  if(pending||transfer)return false;
  pending={kind,cardId,cardRevision};
  const description={
   seal:'是否將「'+cardTitle+'」與真實之眼建立契約？\n確認後原卡將飛入神器，動畫完成才封印。',
   cardify:'是否將已封印的真實之眼具現化為原本的契約卡？',
   materialize:'是否將「真實之眼」契約卡實體化為世界中的 3D 神器？',
   return:'是否將世界中的真實之眼收回原本的契約卡？'
  };
  confirmLabel.textContent=description[kind]||'確認此契約操作？';
  dialog.showModal();invalidate();return true;
 }
 cancelBtn.addEventListener('click',cancelPrompt);
 dialog.addEventListener('cancel',()=>{
  if(pending?.kind==='materialize')clearCardCeremony();
  pending=null;invalidate();
 });
 dialog.addEventListener('close',()=>{
  if(pending?.kind==='materialize')clearCardCeremony();
  if(pending)pending=null;invalidate();
 });
 function cardEntries(){
  return [...floatingCards.values()].map(e=>{
   e.group.updateWorldMatrix(true,false);
   const p=e.group.getWorldPosition(new T.Vector3());
   return {id:e.id,record:e.record,position:{x:p.x,z:p.z},
    visible:e.group.visible,isDragging:!!e.drag,isFocused:!!e.focused,
    isSummoning:!!e.summon,isRecycling:!!e.recycle};
  });
 }
 function chooseCard(){
  const result=C.nearest({x:keeper.position.x,z:keeper.position.z},cardEntries());
  if(result.status!=='selected'){
   toast('👁 '+result.reason+'；請把未使用的空白卡移到身旁軌道（已命名「真實之眼」的普通卡也可以）');
   return null;
  }
  const target=craft.listCards(storage).find(x=>x.id===result.cardId);
  if(!target){toast('👁 找不到原始空白卡，請重新進入世界');return null}
  return {cardId:target.id,cardRevision:target.revision};
 }
 function complete(token){
  if(!hold||hold.token!==token)return;
  const type=hold.type;stop();
  // Contract/movement state can change when a card enters or leaves the
  // compendium. Never act on a cached original revision.
  state();
  selectedUntil=performance.now()+1800;
  if(type==='books'){
   try{const ids=new Set(selected),members=targets().filter(o=>ids.has(o.id)).map(o=>({kind:o.kind,id:o.id}));
    if(!members.length||members.length!==ids.size)throw Error('選取物件已離開掃描範圍，請重新掃描');
    const id=B.nearest({x:keeper.position.x,z:keeper.position.z},cardEntries());
    const record=craft.listCards(storage).find(c=>c.id===id);bundlePrompt('bundle-bind',record,members);
   }catch(e){toast(e.message)}return;
  }
  if(type==='eye'){
   const target=chooseCard();
   if(!target)return;
   const record=craft.listCards(storage).find(c=>c.id===target.cardId);
   if(record)prompt('seal',record.id,record.revision,record.title);
   return;
  }
  if(!card)return;
  if(type==='eye-cardify'&&card.truthEyeContract?.form==='sealed'){
   prompt('cardify',card.id,card.revision,card.title);return;
  }
  if(type==='eye-return'&&card.truthEyeContract?.form==='world'){
   prompt('return',card.id,card.revision,card.title);return;
  }
 }
 function beginTransfer(){
  const request=pending;
  if(!request||transfer)return;
  pending=null;
  state();
  if(dialog.open)dialog.close();
  if(request.kind.startsWith('bundle-')){clearCardCeremony();startBundleTransfer(request);return;}
  try{
   const record=craft.listCards(storage).find(o=>o.id===request.cardId);
   if(!record||record.revision!==request.cardRevision)throw Error('契約卡已變更，請重新長按');
   const existing=floatingCards.get(record.id);
   if(!existing||existing.summon||existing.recycle)
    throw Error('契約卡暫時無法操作，請先讓它回到軌道');
   if(record.placement!=='hover'||record.containerId||record.ownerBookId)
    throw Error('原卡目前在書本／GBA 裡；請先將同一張卡拖回玩家軌道，再實體化');
   if(!existing.group.visible&&request.kind!=='cardify'&&request.kind!=='return')
    throw Error('原卡暫時不可見，請先將原卡召回玩家軌道');
   if(request.kind==='seal'){
    if(!C.isEligible(record)||C.boundCard(storage))throw Error('空白卡不符合契約資格');
    const candidate=chooseCard();
    if(!candidate||candidate.cardId!==record.id)throw Error('最近卡片已改變，請重新選取');
   }else{
    if(record.truthEyeContract?.artifactId!==C.ID)throw Error('找不到原契約');
    const expected={cardify:'sealed',materialize:'card',return:'world'}[request.kind];
    if(record.truthEyeContract?.form!==expected)throw Error('原契約形態已變更');
   }
   // Reuse the exact original directed 3D absorption trajectory.
   existing.group.updateWorldMatrix(true,false);
   // Card-form artifacts are intentionally invisible. Their mesh was therefore
   // NOT following the moving player while stored as a card. Re-anchor to the
   // player's CURRENT head before calculating the exact flight endpoint.
   if(request.kind==='materialize')
    eye.position.set(keeper.position.x,keeper.position.y+3.5,keeper.position.z);
   eye.updateWorldMatrix(true,false);
   const p=existing.group.getWorldPosition(new T.Vector3()),
    eyePos=eye.getWorldPosition(new T.Vector3());
   const fromCard=request.kind==='seal'||request.kind==='materialize';
   const from=fromCard?p:eyePos,to=fromCard?eyePos:p;
   transfer={...request,fromCard,
    cardX:p.clone(),eyeX:eyePos.clone(),
    plan:Directed.normalize({sourceId:fromCard?record.id:C.ID,targetId:fromCard?C.ID:record.id,
     from:{x:from.x,y:from.y,z:from.z},to:{x:to.x,y:to.y,z:to.z},
     startedAt:performance.now(),duration:reducedMotion?400:1500,
     initialScale:existing.group.scale.x||1})};
   // Selection has ALREADY finished before confirmation. Never postpone the
   // original six-pointed ritual into the card-to-world flight animation.
   if(request.kind==='materialize')clearCardCeremony();
   toast('✦ 原版契約：卡片定向吸收動畫啟動');
   invalidate();
  }catch(e){
   transfer=null;
   if(request?.kind==='materialize')clearCardCeremony();
   toast('無法啟動契約：'+String(e.message||e));invalidate();
  }
 }
 approveBtn.addEventListener('click',beginTransfer);
 function finishTransfer(){
  if(!transfer)return;
  const current=transfer;transfer=null;
  try{
   const actual=craft.listCards(storage).find(o=>o.id===current.cardId);
   if(!actual||actual.revision!==current.cardRevision)
    throw Error('卡片資料在動畫期間發生變更');
   if(current.kind==='seal'){
    C.bind(storage,actual.id,actual.revision);
   }else{
    const next=current.kind==='cardify'?'card':current.kind==='materialize'?'world':'card';
    C.setForm(storage,actual.id,actual.revision,next);
   }
   refreshCards();state();
   toast({seal:'✦ 已締結契約，原卡已封入真實之眼 · 長按眼睛可卡片化',
    cardify:'✦ 真實之眼已回到原卡 · 點選軌道卡片可實體化',
    materialize:'✦ 原卡已轉為世界 3D 真實之眼 · 掃描控制已啟動',
    return:'✦ 真實之眼已回到原卡，契約保持不變'}[current.kind]);
  }catch(error){
   if(current.kind==='materialize')clearCardCeremony();
   state();
   toast('契約未寫入，原卡資料保持不變：'+String(error?.message||error));
  }
  invalidate();
 }
 function start(type,id,x,y){
  if(hold||pending||transfer||bundleTransfer||dialog.open)return false;
  if(cardCeremony?.phase==='release')clearCardCeremony();
  const box=new T.Box3().makeEmpty();
  if(type==='books')for(const o of entries())if(selected.has(o.id))box.expandByObject(o.group);
  const center=box.isEmpty()?new T.Vector3(keeper.position.x,0,keeper.position.z):box.getCenter(new T.Vector3());
  const size=box.isEmpty()?new T.Vector3(2,0,2):box.getSize(new T.Vector3());
  const radius=type==='books'?Math.max(1.2,Math.hypot(size.x,size.z)*.62):1.65;
  hex.setRadius(radius);
  if(type==='books'){
   ritual.position.set(center.x,.43,center.z);
   ritual.rotation.set(-Math.PI/2,0,0);
  }else{
   eye.updateWorldMatrix(true,false);
   eye.getWorldPosition(ritual.position);
   ritual.quaternion.copy(camera.quaternion);
  }
  hex.update(0,performance.now());
  const token=++serial;
  hold={token,type,id,x,y,started:performance.now(),timer:setTimeout(()=>complete(token),3000)};
  if(type==='books')collectButton.dataset.holding='true';
  invalidate();return true;
 }
 function pointerDown(e){
  if(e.button!==0||e.isPrimary===false||hold||transfer||bundleTransfer||pending||dialog.open)return false;
  if(eye.visible&&hit(e,[eye])){
   heldPointers.add(e.pointerId);
   start(!card?'eye':card.truthEyeContract?.form==='sealed'?'eye-cardify':'eye-return',e.pointerId,e.clientX,e.clientY);
   return true;
  }
  const id=at(e);
  if(id&&selected.has(id)){
   heldPointers.add(e.pointerId);
   start('books',e.pointerId,e.clientX,e.clientY);
   return true;
  }
  return false;
 }
 function pointerMove(e){
  if(!heldPointers.has(e.pointerId)&&hold?.id!==e.pointerId)return false;
  if(hold?.id===e.pointerId&&Math.hypot(e.clientX-hold.x,e.clientY-hold.y)>
    (e.pointerType==='touch'||e.pointerType==='pen'?26:15))stop();
  return true;
 }
 function pointerEnd(e){
  const used=heldPointers.delete(e.pointerId);
  if(hold?.id===e.pointerId){
   if(e.type==='pointerup'&&performance.now()-hold.started>=3000)complete(hold.token);
   else stop();
   return true;
  }
  return used;
 }
 // No floating contract button. Contract on 3D eye; manifestation from orbit card.
 renderer.domElement.addEventListener('contextmenu',e=>{
  if(eye.visible&&hit(e,[eye]))e.preventDefault();
 });
 document.addEventListener('visibilitychange',()=>{
  if(document.hidden){stop();cancelCardHold();cardCaptured.clear();cancelPrompt()}
 });
 root.addEventListener('blur',()=>{stop();cancelCardHold();cardCaptured.clear()});
 function tick(now){const t=now/1000;
  tickBundle(now);
  const originalEye=new T.Vector3(keeper.position.x,keeper.position.y+3.50+(reducedMotion?0:.10*Math.sin(t*1.2)),keeper.position.z);
  if(eye.visible||transfer){
   eye.position.copy(originalEye);
   eye.scale.setScalar(.46);eye.visible=true;eyeLight.visible=true;
   eye.rotation.y=keeper.rotation.y+(reducedMotion?0:Math.sin(t*.42)*.13);
   eyeLight.position.copy(eye.position);built.tick(t,reducedMotion);
  }
  field.position.set(keeper.position.x,0,keeper.position.z);
  if(field.visible){
   fan.visible=cfg.mode!=='ripple';
   const w=(t*.24)%1;
   for(let i=0;i<ripples.length;i++){const u=(w+i/3)%1,m=ripples[i];
    m.visible=cfg.mode!=='radar';m.scale.setScalar(Math.max(.03,u*cfg.radius));m.material.opacity=(1-u)*.56;
   }
   if(now-lastScan>320){lastScan=now;updateMarks()}
  }
  if(hold){
   const p=clamp((now-hold.started)/3000,0,1);
   if(hold.type!=='books'){
    eye.getWorldPosition(ritual.position);
    ritual.quaternion.copy(camera.quaternion);
   }
   hex.update(p,now);
   if(p>=1)complete(hold.token);
  }else if(now<selectedUntil){
   // Original selection retains the seal briefly before opening confirmation.
   // We leave the art on its final lit stage only until the confirm appears.
   if(!dialog.open)hex.update(1,now);
   else hex.clear();
  }else if(ritual.visible)hex.clear();
  if(transfer){
   const current=transfer,sample=Directed.sample(current.plan,now);
   const entry=floatingCards.get(current.cardId);
   if(entry){
    entry.group.visible=true;entry.group.quaternion.copy(camera.quaternion);
    if(current.fromCard){
     entry.group.position.set(sample.position.x,sample.position.y,sample.position.z);
     entry.group.scale.setScalar(Math.max(.001,sample.scale));
    }else{
     entry.group.position.copy(current.cardX);
     entry.group.scale.setScalar(Math.max(.001,sample.progress*current.plan.initialScale));
    }
   }
   if(current.kind==='cardify'||current.kind==='return'){
    eye.visible=true;
    eye.position.set(sample.position.x,sample.position.y,sample.position.z);
    eye.scale.setScalar(Math.max(.001,.46*(1-sample.progress)));
   }else if(current.kind==='materialize'){
    eye.visible=true;eye.position.copy(current.eyeX);
    eye.scale.setScalar(Math.max(.001,.46*sample.progress));
   }
   if(sample.done)finishTransfer();
  }
  if(cardHold&&now-cardHold.startedAt>=3000)finishCardHold();
  updateCardCeremony(now);
 }
 function cardTap(id){
  // The compendium can move the same original card several times. Each move
  // increments its persisted revision without notifying this 3D artifact.
  // Read current data BEFORE presenting a confirmation; never use the
  // revision cached when the Eye was originally constructed.
  state();
  const collection=craft.listCards(storage).find(c=>c.id===id&&c.scanContract);
  if(collection){toast('🔯 長按此收納契約卡三秒，可'+(collection.scanContract.form==='card'?'還原原物件':'再次收納原物件'));return true}
  if(!card||card.id!==id||card.truthEyeContract?.form!=='card')return false;
  const entry=floatingCards.get(id);
  if(!entry||entry.summon||entry.recycle){
   toast('真實之眼原卡正在移動，請完成操作後再實體化');
   return true;
  }
  if(card.placement!=='hover'||card.containerId||card.ownerBookId){
   toast('真實之眼原卡仍在圖鑑／GBA 中；請把原卡拖回玩家軌道，再點擊實體化');
   return true;
  }
  if(!entry.group.visible){
   toast('真實之眼原卡尚未回到玩家軌道，請先取出或完成卡片召喚');
   return true;
  }
  // A quick tap is NOT a completed contract selection.
  // Only the genuine 3-second pointer-down gesture opens confirmation.
  if(!pending&&!transfer)toast('🔯 請長按真實之眼契約卡三秒，完成六芒星後再確認實體化');
  return true;
 }
 function getBoundWorldAnchor(){
  if(!card||!['sealed','world'].includes(card.truthEyeContract?.form)||
    !eye.visible||transfer||hold||pending)return null;
  eye.updateWorldMatrix(true,false);
  const p=eye.getWorldPosition(new T.Vector3());
  return {cardId:card.id,revision:card.revision,position:{x:p.x,y:p.y,z:p.z}};
 }
 function refreshAfterUnbind(){
  const before=card?.id;
  state();
  if(before&&!card){selected.clear();stop();cancelPrompt();}
  invalidate();
 }
 // Generic contract release event: refresh this artifact only when its
 // ORIGINAL card was released. Other artifacts can subscribe independently.
 scene.addEventListener('living-contract-unbound',e=>{
  if(e.cardId===card?.id)refreshAfterUnbind();
 });
 function ownsTransferCard(id){return !!transfer&&transfer.cardId===id}
 function active(){return !!bundleTransfer||eye.visible||field.visible||!!hold||!!transfer||!!pending||!!cardCeremony||!!cardHold||performance.now()<selectedUntil}
 function screenPoint(group){
  group.updateWorldMatrix(true,false);const p=group.getWorldPosition(new T.Vector3()).project(camera),r=renderer.domElement.getBoundingClientRect();
  return {x:r.left+(p.x+1)*r.width/2,y:r.top+(1-p.y)*r.height/2};
 }
 apply();state();
 return Object.freeze({tick,active,applyPacked,isPacked,ownsTransferCard,getBoundWorldAnchor,refreshAfterUnbind,pick,cardTap,
  cardPressStart,cardPressMove,cardPressEnd,pointerDown,pointerMove,pointerEnd,inspect:()=>({form:card?.truthEyeContract?.form||'unbound',selected:[...selected],config:{...cfg},objects:entries().map(o=>({id:o.id,kind:o.kind,visible:(o.groups||[o.group]).some(g=>g.visible)})),scanned:targets().map(o=>({id:o.id,kind:o.kind,...screenPoint(o.group)})),cards:[...floatingCards.values()].filter(e=>e.group.visible).map(e=>({id:e.id,...screenPoint(e.group)})),packed:[...packed],bundleBusy:!!bundleTransfer})});
}
root.LivingTruthEyeWorld=Object.freeze({create,model});
})(typeof self!=='undefined'?self:globalThis);
