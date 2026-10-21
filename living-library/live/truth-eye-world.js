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
 const built=model(T),eye=built.group;eye.userData.scanProtected=true;scene.add(eye);
 const eyeLight=new T.PointLight(0x5cc5eb,4,5);eyeLight.userData.scanProtected=true;scene.add(eyeLight);
 let cfg={radius:8,aperture:360,direction:0,mode:'both',enabled:true};
 try{const s=JSON.parse(storage.getItem('living-truth-eye-scan-v1')||'null');
  if(s){cfg.radius=clamp(Number(s.radius)||8,2,20);cfg.aperture=clamp(Number(s.aperture)||360,15,360);
   cfg.direction=clamp(Number(s.direction)||0,0,360);cfg.mode=['both','radar','ripple'].includes(s.mode)?s.mode:'both'}
 }catch(_){}
 let card=null;try{card=C.boundCard(storage)}catch(e){toast('讀卡失敗：'+e.message)}
 const field=new T.Group();field.userData.scanProtected=true;scene.add(field);
 const cyan=new T.MeshBasicMaterial({color:0x8cf6eb,opacity:.51,transparent:true,depthWrite:false,blending:T.AdditiveBlending,side:T.DoubleSide});
 const amber=new T.MeshBasicMaterial({color:0xfce0a5,opacity:.42,transparent:true,depthWrite:false,blending:T.AdditiveBlending,side:T.DoubleSide});
 const ring=(parent,mat)=>{const m=new T.Mesh(new T.TorusGeometry(1,.013,5,96),mat);m.rotation.x=Math.PI/2;parent.add(m);return m};
 const border=ring(field,cyan),ripples=[ring(field,amber.clone()),ring(field,cyan.clone()),ring(field,amber.clone())];
 let fan=new T.Mesh(new T.CircleGeometry(1,72),new T.MeshBasicMaterial({color:0x5ddce0,opacity:.085,transparent:true,depthWrite:false,side:T.DoubleSide,blending:T.AdditiveBlending}));
 fan.rotation.x=-Math.PI/2;fan.position.y=.018;field.add(fan);
 const marks=new T.Group();marks.userData.scanProtected=true;scene.add(marks);
 const markMap=new Map(),caster=new T.Raycaster(),mouse=new T.Vector2(),tmp=new T.Vector3();
 // The SAME 3D hexagram animation is reused by the original ground rune.
 // For the Eye it faces the camera and floats around the actual 3D artifact.
 const Hexagram=root.LivingContractHexagram;
 if(!Hexagram)throw Error('缺少六芒星動畫模組');
 const hex=Hexagram.create({THREE:T,parent:scene,plane:'xy',radius:1.65});
 const ritual=hex.group;ritual.userData.scanProtected=true;
 // The original ritual geometry is reused around the ORIGINAL orbit card
 // during card -> world manifestation. This is independent of the 3-second
 // hold ritual on the 3D Eye, so both transitions keep the approved artwork.
 const cardHex=Hexagram.create({THREE:T,parent:scene,plane:'xy',radius:.80});
 cardHex.group.userData.scanProtected=true;
 let cardCeremony=null,cardHold=null;
 const cardCaptured=new Set();
 let hold=null,serial=0,lastScan=0,transfer=null,pending=null,selectedUntil=0;
 let bundleTransfer=null,packed=new Map(),bundleError=null,packedRaw,worldCards=[];
 const bundleAnchors=new Map();
 const restoredBundleVersions=new Map();
 const bundleHighlights=[];
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
 // Every runtime-discovered 3D object is directly holdable. Scanned objects are already selected; no separate tap is ever needed.
 // Transparent text readout, not an interactive scan button.
 const readout=document.createElement('aside');
 readout.id='eyeScanReadout';readout.className='truthEyeScanReadout';
 readout.setAttribute('aria-label','掃描到的物件數量與名稱');
 const countLabel=document.createElement('div');countLabel.id='eyeCount';
 countLabel.setAttribute('role','status');countLabel.className='truthEyeScanCount';
 const scanNames=document.createElement('ol');scanNames.id='eyeScanNames';
 scanNames.setAttribute('aria-label','掃描物件名稱');
 readout.append(countLabel,scanNames);host.append(readout);
 const readoutStyle=document.createElement('style');
 readoutStyle.textContent='.truthEyeScanReadout{margin-top:12px;text-align:right;color:#f1e1b9;background:none!important;border:0!important;box-shadow:none!important;text-shadow:0 1px 5px #040710,0 1px 10px #040710;font:12px/1.55 system-ui,sans-serif}.truthEyeScanCount{font-weight:700}.truthEyeScanReadout ol{list-style:none;margin:4px 0 0;padding:0;max-height:min(42vh,420px);overflow-y:auto;overflow-x:hidden;scrollbar-width:thin;touch-action:pan-y}.truthEyeScanReadout li{padding:1px 0;overflow-wrap:anywhere}.truthEyeScanReadout li::before{content:"·  "}.truthEyeScanReadout ol:empty::after{content:"尚未偵測到物件";opacity:.72}@media(max-width:650px){.truthEyeScanReadout{font-size:11px}.truthEyeScanReadout ol{max-height:35vh}}';
 document.head.append(readoutStyle);
 let previousScanDisplay='';
 function busy(){return !!(hold||cardHold||pending||transfer||bundleTransfer||dialog?.open)}
 function entries(){return (opts.getScanEntries?.({excludeRoots:[eye,eyeLight,field,marks,ritual,cardHex.group,...bundleAnchors.values()]})||[])}

 function refreshPacked(){
  try{const raw=storage.getItem(B.KEY);if(raw===packedRaw)return;packed=B.claims(storage,true);worldCards=B.bundles(storage).filter(b=>b.contract.form==='world').map(b=>b.card);bundleError=null;packedRaw=raw}
  catch(e){if(!bundleError)toast(e.message);bundleError=e;packed=new Map()}
 }
 function isPacked(kind,id){return packed.has(kind+':'+id)}
 function applyPacked(){
  refreshPacked();
  if(bundleError)return;
  const mask=bundleTransfer?.releasing?new Map([...packed].filter(([,id])=>id!==bundleTransfer.cardId)):packed;
  for(const o of entries())if(o.kind==='book')for(const g of o.groups||[o.group]){
   const hidden=mask.has('book:'+o.id);
   if(hidden){
    if(!g.userData.scanHidden)g.userData.scanPreviousVisible=g.visible;
    g.userData.scanHidden=true;g.visible=false;
   }else if(g.userData.scanHidden){
    g.visible=g.userData.scanPreviousVisible!==false;
    delete g.userData.scanHidden;delete g.userData.scanPreviousVisible;
   }
  }
  opts.applyPacked?.(mask);
  const liveIds=new Set(worldCards.map(c=>c.id));
  for(const id of restoredBundleVersions.keys())if(!liveIds.has(id))restoredBundleVersions.delete(id);
  for(const [id,anchor] of bundleAnchors)if(!liveIds.has(id)){scene.remove(anchor);bundleAnchors.delete(id)}
  for(const record of worldCards){
   if(restoredBundleVersions.get(record.id)!==record.revision){
     // Only a currently ACTIVE world-form contract may restore its book presentation.
     opts.onBundleRestored?.(record.scanContract.members,record.id);
     restoredBundleVersions.set(record.id,record.revision);
    }
   const entry=floatingCards.get(record.id);
   if(entry)entry.group.visible=false;
   const members=record.scanContract.members.map(ref=>entries().find(o=>o.kind===ref.kind&&o.id===ref.id));
   let anchor=bundleAnchors.get(record.id);
   if(!anchor){anchor=new T.Group();anchor.userData.boundCardId=record.id;anchor.userData.scanProtected=true;scene.add(anchor);bundleAnchors.set(record.id,anchor)}
   anchor.userData.boundCardTargetPoint=point=>{
    let nearest=null,distance=Infinity;
    for(const o of members.filter(Boolean))for(const g of o.groups||[o.group]){
     const box=new T.Box3().setFromObject(g),p=box.isEmpty()?g.getWorldPosition(new T.Vector3()):box.clampPoint(point,new T.Vector3());
     const d=Math.hypot(p.x-point.x,p.z-point.z);if(d<distance){nearest=p;distance=d}
    }
    return nearest;
   };
   anchor.visible=members.every(Boolean);
   if(anchor.visible){anchor.position.set(0,0,0);for(const o of members)anchor.position.add(o.group.getWorldPosition(new T.Vector3()));anchor.position.multiplyScalar(1/members.length)}
  }
 }
 function bundlePrompt(kind,record,members){
  if(busy())return false;
  pending={kind,cardId:record.id,cardRevision:record.revision,members};
  confirmLabel.textContent=kind==='bundle-bind'?'將本次掃描到的 '+members.length+' 件物件全數收進這張空白卡？\n原物件 ID 保留，可再次還原。':kind==='bundle-materialize'?'將這張收納卡的 '+record.scanContract.members.length+' 件物件實體化？\n契約保持有效，以後仍可整組收回。':'將 '+record.scanContract.members.length+' 件世界物件整組收回原卡？\n契約保持有效；只有解除契約卡能真正解除。';
  dialog.showModal();invalidate();return true;
 }
 function startBundleTransfer(request){
  try{
   const record=craft.listCards(storage).find(c=>c.id===request.cardId),entry=floatingCards.get(request.cardId);
   if(bundleError||!record||record.revision!==request.cardRevision||(!entry?.group.visible&&request.kind!=='bundle-cardify')||!entry||entry.summon||entry.recycle||entry.drag||entry.focused||record.placement!=='hover'||record.containerId||record.ownerBookId)throw Error('原卡已改變或正在移動，請重新長按');
   const members=request.members||B.contract(record).members;
   const available=request.kind==='bundle-bind'?targets():entries();
   if(request.kind==='bundle-bind'&&(!B.blank(record)||B.nearest({x:keeper.position.x,z:keeper.position.z},cardEntries())!==record.id))throw Error('最近空白卡已改變');
   if(members.some(r=>!available.some(o=>o.id===r.id&&o.kind===r.kind)))throw Error('選取物件已離開掃描範圍或尚未載入');
   const contract=B.contract(record);
   const expectedForm={'bundle-materialize':'card','bundle-cardify':'world'}[request.kind];
   if(request.kind!=='bundle-bind'&&(!expectedForm||contract?.form!==expectedForm))throw Error('收納形態已變更，請重新長按');
   // A form change never releases ownership. Only the one-shot spell unbinds.
   const releasing=request.kind==='bundle-materialize';
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
   if(job.kind==='bundle-bind')B.bind(storage,{cardId:job.cardId,revision:job.cardRevision,members:job.members},available);
   else if(job.kind==='bundle-materialize')B.setForm(storage,job.cardId,job.cardRevision,'world',available);
   else if(job.kind==='bundle-cardify')B.setForm(storage,job.cardId,job.cardRevision,'card',available);
   else throw Error('未知收納操作，原卡保持不變');
   refreshCards();toast(job.kind==='bundle-materialize'?'✦ 物件已實體化；契約保持有效':job.kind==='bundle-cardify'?'✦ 整組物件已回原卡；契約保持有效':'✦ 已將 '+job.members.length+' 件原物件收進同一張卡');
  }catch(e){toast('收納未寫入，原物件保留：'+e.message)}
  for(const snap of job.snapshots){snap.group.position.copy(snap.position);snap.group.scale.copy(snap.scale);snap.group.visible=job.releasing||snap.visible}
  bundleTransfer=null;refreshPacked();applyPacked();invalidate();
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
 el('Toggle').addEventListener('click',()=>{if(busy())return;cfg.enabled=!cfg.enabled;state();updateMarks();invalidate()});
 function hit(e,meshes){const rect=renderer.domElement.getBoundingClientRect();if(!rect.width||!rect.height)return null;
  mouse.set((e.clientX-rect.left)/rect.width*2-1,-(e.clientY-rect.top)/rect.height*2+1);
  caster.setFromCamera(mouse,camera);return caster.intersectObjects(meshes,true)[0]||null;
 }
 function inRange(o){
  if(o.anchor)tmp.copy(o.anchor);else o.group.getWorldPosition(tmp);const dx=tmp.x-keeper.position.x,dz=tmp.z-keeper.position.z;
  const direction=(Math.atan2(dx,-dz)/RAD-cfg.direction+540)%360-180;
  return Math.hypot(dx,dz)<=cfg.radius&&(cfg.aperture===360||Math.abs(direction)<=cfg.aperture/2);
 }
 function visibleInScene(group){for(let n=group;n;n=n.parent)if(n.visible===false)return false;return true}
 function targets(){
  if(!field.visible||bundleError)return [];
  const seen=new Set();
  return entries().filter(o=>{
   const key=o.kind+':'+o.id;
   if(seen.has(key)||o.protected||isPacked(o.kind,o.id)||!visibleInScene(o.group)||
      !(o.groups||[o.group]).some(visibleInScene)||
      (o.groups||[o.group]).some(g=>g.userData.boundCardId)||!inRange(o))return false;
   seen.add(key);return true;
  });
 }
 function at(e){if(!field.visible)return null;const list=[];
  for(const o of targets()){for(const g of o.groups||[o.group])if(!backgroundSurface(g))g.traverse(m=>{if(m.isMesh){m.userData.truthEyeId=o.id;list.push(m)}});const marker=markMap.get(o.id);if(marker?.visible)marker.traverse(m=>{if(m.isMesh){m.userData.truthEyeId=o.id;list.push(m)}})}
  return hit(e,list)?.object.userData.truthEyeId||null;
 }
 // Scanner does not intercept short taps or require manual selection.
 function pick(){return false}
 function scannedTargets(){return hold?.type==='scan'?hold.scanTargets:targets()}
 // Names are descriptions of live discoveries, never scan allowlist rules.
 function scanName(o){
  // Names come from any live object, independent of its original authoring system.
  const provided=o.group?.userData?.scanName||o.group?.userData?.displayName||o.b?.name||o.b?.title;
  if(typeof provided==='string'&&provided.trim())return provided.trim().slice(0,80);
  const id=o.id||'',m=/^scene\.(shelf|pillar|lamp)\.(\d+)$/.exec(id);
  if(m)return ({shelf:'書架',pillar:'石柱',lamp:'吊燈'}[m[1]])+' '+(Number(m[2])+1);
  const names={'scene.floor':'地板','scene.floor.rings':'地面光環','scene.floor.grid':'地面格線',
   'scene.rune.origin':'初始法陣','scene.oculus.rings':'天頂圓環',
   'scene.oculus.spokes':'天頂光芒','scene.dust':'空氣微粒','scene.lighting':'環境燈光'};
  if(names[id])return names[id];
  const native=o.group?.name;
  if(typeof native==='string'&&native.trim())return native.trim().slice(0,80);
  const type=o.group?.isMesh?(o.group?.geometry?.type||'3D 網格'):'3D 物件';
  return type+' · '+String(id).replace(/^scene\./,'').slice(0,26);
 }
 function renderScanNames(objects){
  const display=objects.map(o=>[o.id,scanName(o)]),key=JSON.stringify(display);
  if(key===previousScanDisplay)return;
  previousScanDisplay=key;
  countLabel.textContent='掃描 '+objects.length+' 件物件';
  const fragment=document.createDocumentFragment();
  for(let i=0;i<objects.length;i++){
   const li=document.createElement('li');
   li.textContent=display[i][1];li.dataset.scanId=objects[i].id;fragment.append(li);
  }
  scanNames.replaceChildren(fragment);
 }
 function updateMarks(){
  const t=scannedTargets(),set=new Set(t.map(o=>o.id));
  for(const [id,g] of markMap)g.visible=set.has(id);
  for(const o of t){let m=markMap.get(o.id);
   if(!m){m=new T.Group();const r=ring(m,cyan.clone());r.scale.setScalar(.49);const volume=new T.Mesh(new T.SphereGeometry(.45,10,8),new T.MeshBasicMaterial({colorWrite:false,depthWrite:false,transparent:true,opacity:0}));m.add(volume);
    const j=new T.Mesh(new T.OctahedronGeometry(.10),amber.clone());j.position.y=.72;m.add(j);marks.add(m);markMap.set(o.id,m);}
   if(o.anchor)tmp.copy(o.anchor);else o.group.getWorldPosition(tmp);m.position.copy(tmp);m.position.y+=.7;
   m.scale.setScalar(1);
  }
  renderScanNames(t);
 }
 function clearBundleHighlights(){
  for(const helper of bundleHighlights){scene.remove(helper);helper.geometry.dispose();helper.material.dispose()}
  bundleHighlights.length=0;
 }
 function showBundleHighlights(record){
  clearBundleHighlights();
  for(const ref of record.scanContract.members){
   const o=entries().find(e=>e.kind===ref.kind&&e.id===ref.id);if(!o)continue;
   const box=new T.Box3().makeEmpty();for(const g of o.groups||[o.group])box.expandByObject(g);
   if(box.isEmpty())continue;
   const helper=new T.Box3Helper(box,0xffdc88);helper.userData.scanHighlight=true;helper.userData.scanProtected=true;
   helper.material.transparent=true;helper.material.opacity=.85;helper.material.depthTest=false;
   helper.userData.scanRef=ref;scene.add(helper);bundleHighlights.push(helper);
  }
 }
 // Wide flat meshes remain scanable but must not capture touches across the world.
 // This is based on physical dimensions, never on a particular object's name or ID.
 function backgroundSurface(group){
  const box=new T.Box3().setFromObject(group);
  if(box.isEmpty())return false;
  const size=box.getSize(new T.Vector3());
  return Math.max(size.x,size.z)>cfg.radius*1.4&&size.y<1;
 }
 function boundMemberAt(e){
  if(bundleError)return null;
  const owners=new Map();
  for(const b of B.bundles(storage))if(b.contract.form==='world')for(const ref of b.contract.members)owners.set(ref.kind+':'+ref.id,b.card);
  if(!owners.size)return null;
  const meshes=[],keys=new Map();
  function add(group,key){group.traverse(m=>{
   if(!m.isMesh||m.material?.colorWrite===false)return;
   for(let p=m;p;p=p.parent)if(!p.visible)return;
   meshes.push(m);if(key)keys.set(m,key);
  })}
  scene.updateMatrixWorld(true);
  for(const o of entries())for(const g of o.groups||[o.group])if(!backgroundSurface(g))add(g,o.kind+':'+o.id);
  // Scanner markers also represent non-mesh objects (lights, lines, particles).
  // Reuse their stable reference so a bound marker acts on its whole contract.
  for(const o of entries()){const key=o.kind+':'+o.id,marker=markMap.get(o.id);if(owners.has(key)&&marker?.visible)add(marker,key)}
  for(const c of floatingCards.values())add(c.group,null);
  if(eye.visible)add(eye,null);
  const found=hit(e,meshes),key=found&&keys.get(found.object);
  const record=owners.get(key);return record?{record,key}:null;
 }
 function stop(){
  clearBundleHighlights();
  if(hold){clearTimeout(hold.timer);hold=null}

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
  if(!(record.scanContract?bundlePrompt('bundle-materialize',record):prompt('materialize',record.id,record.revision,record.title))){
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
   if(!pending||!['materialize','bundle-materialize'].includes(pending.kind)||pending.cardId!==st.cardId){
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
  if(hold.type==='bound-bundle'&&!hold.ceremonyStarted){
   const remaining=3000-(performance.now()-hold.started);
   if(remaining>0){clearTimeout(hold.timer);hold.timer=setTimeout(()=>complete(token),remaining);return}
   const record=craft.listCards(storage).find(c=>c.id===hold.cardId);
   if(!record||record.revision!==hold.revision||B.contract(record)?.form!=='world'){stop();toast('整組契約已改變，請重新長按');return}
   clearTimeout(hold.timer);
   hold.id=null;hold.ceremonyStarted=performance.now();
   showBundleHighlights(record);hex.update(0,hold.ceremonyStarted);
   hold.timer=setTimeout(()=>complete(token),900);invalidate();return;
  }
  if(hold.type==='bound-bundle'&&performance.now()-hold.ceremonyStarted<900)return;
  const gesture=hold,type=gesture.type;stop();
  // Contract/movement state can change when a card enters or leaves the
  // compendium. Never act on a cached original revision.
  state();
  selectedUntil=performance.now()+1800;
  if(type==='bound-bundle'){
   const record=craft.listCards(storage).find(c=>c.id===gesture.cardId);
   if(!record||record.revision!==gesture.revision||B.contract(record)?.form!=='world'){
    toast('整組契約已改變，請重新長按');return;
   }
   selectedUntil=0;bundlePrompt('bundle-cardify',record);return;
  }
  if(type==='scan'){
   try{const scanned=gesture.scanTargets||[],current=targets();
    const expected=new Set(scanned.map(o=>o.kind+':'+o.id)),now=new Set(current.map(o=>o.kind+':'+o.id));
    if(!expected.size||expected.size!==scanned.length||now.size!==expected.size||[...expected].some(k=>!now.has(k)))
     throw Error('掃描物件或範圍已變動，請重新掃描；不會加入其他物件');
    const members=scanned.map(o=>({kind:o.kind,id:o.id}));
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
 function start(type,id,x,y,contractRecord=null,scanObjectId=null){
  if(hold||pending||transfer||bundleTransfer||dialog.open)return false;
  if(cardCeremony?.phase==='release')clearCardCeremony();
  const box=new T.Box3().makeEmpty();
  // Scan targets displayed now are the ENTIRE implicit selection.
  const scanTargets=type==='scan'?targets():null;
  if(type==='scan'){
   if(!scanObjectId||!scanTargets.some(o=>o.id===scanObjectId))return false;
   for(const o of scanTargets)for(const g of o.groups||[o.group])box.expandByObject(g);
   renderScanNames(scanTargets);
  }
  if(contractRecord)for(const ref of contractRecord.scanContract.members){const o=entries().find(o=>o.id===ref.id&&o.kind===ref.kind);if(o)for(const g of o.groups||[o.group])box.expandByObject(g)}
  const center=box.isEmpty()?new T.Vector3(keeper.position.x,0,keeper.position.z):box.getCenter(new T.Vector3());
  const size=box.isEmpty()?new T.Vector3(2,0,2):box.getSize(new T.Vector3());
  const radius=(type==='scan'||type==='bound-bundle')?Math.max(1.2,Math.hypot(size.x,size.z)*.62):1.65;
  hex.setRadius(radius);
  if(type==='scan'||type==='bound-bundle'){
   ritual.position.set(center.x,.43,center.z);
   ritual.rotation.set(-Math.PI/2,0,0);
  }else{
   eye.updateWorldMatrix(true,false);
   eye.getWorldPosition(ritual.position);
   ritual.quaternion.copy(camera.quaternion);
  }
  if(type==='bound-bundle'){selectedUntil=0;hex.clear()}else hex.update(0,performance.now());
  const token=++serial;
  hold={token,type,id,x,y,scanObjectId,scanTargets,started:performance.now(),timer:setTimeout(()=>complete(token),3000)};

  if(contractRecord){hold.cardId=contractRecord.id;hold.revision=contractRecord.revision}
  invalidate();return true;
 }
 function pointerDown(e){
  if(hold?.ceremonyStarted){heldPointers.add(e.pointerId);return true}
  if(hold?.type==='bound-bundle'&&hold.id!==e.pointerId){
   const origin=hold;stop();heldPointers.delete(origin.id);
   opts.onMemberDrag?.({pointerId:origin.id},{x:origin.x,y:origin.y});return false;
  }
  if(e.button!==0||e.isPrimary===false||hold||transfer||bundleTransfer||pending||dialog.open)return false;
  // Real object interactions outrank scanner long-holds.
  if(opts.shouldYieldSceneGesture?.(e))return false;
  const member=boundMemberAt(e);
  if(member){
   heldPointers.add(e.pointerId);
   start('bound-bundle',e.pointerId,e.clientX,e.clientY,member.record);return true;
  }
  if(eye.visible&&hit(e,[eye])){
   heldPointers.add(e.pointerId);
   start(!card?'eye':card.truthEyeContract?.form==='sealed'?'eye-cardify':'eye-return',e.pointerId,e.clientX,e.clientY);
   return true;
  }
  // Floating cards have priority over scanned world geometry behind them.
  // Without this guard a background floor/wall captures the long-press and
  // makes the ORIGINAL bound-card materialization gesture unreachable.
  const liveCardGroups=[...floatingCards.values()].filter(c=>c.group?.visible&&!c.summon&&!c.recycle).map(c=>c.group);
  if(liveCardGroups.length&&hit(e,liveCardGroups))return false;
  // An avatar hit belongs to the movement/joystick workflow, never to a
  // floor behind the character. Keep the same generous joystick hit radius.
  if(hit(e,[keeper])||caster.ray.distanceToPoint(new T.Vector3(keeper.position.x,keeper.position.y+1.3,keeper.position.z))<1.05)return false;
  const id=at(e);
  // Hold any scanned object: all currently scanned objects belong to this one contract.
  if(id&&start('scan',e.pointerId,e.clientX,e.clientY,null,id)){
   heldPointers.add(e.pointerId);
   return true;
  }
  return false;
 }
 function pointerMove(e){
  if(!heldPointers.has(e.pointerId)&&hold?.id!==e.pointerId)return false;
  if(hold?.id===e.pointerId&&Math.hypot(e.clientX-hold.x,e.clientY-hold.y)>
    (e.pointerType==='touch'||e.pointerType==='pen'?26:15)){
   const origin=hold;stop();
   if(origin.type==='bound-bundle'||origin.type==='scan'){
    // Yield camera dragging back to the game's normal pointer workflow on
    // movement. A direct scan hold must never trap scene navigation.
    heldPointers.delete(e.pointerId);opts.onMemberDrag?.(e,{x:origin.x,y:origin.y});return false;
   }
  }
  return true;
 }
 function pointerEnd(e){
  const used=heldPointers.delete(e.pointerId);
  if(hold?.id===e.pointerId){
   if(e.type==='pointerup'&&performance.now()-hold.started>=3000)complete(hold.token);
   else{
    const tapType=e.type==='pointerup'?hold.type:null;
    stop();
    // Short tap never changes scan results.
    if(tapType==='bound-bundle'||tapType==='scan')opts.onMemberTap?.(e);
   }
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
   const p=hold.ceremonyStarted?clamp((now-hold.ceremonyStarted)/900,0,1):clamp((now-hold.started)/3000,0,1);
   if(hold.type!=='scan'&&hold.type!=='bound-bundle'){
    eye.getWorldPosition(ritual.position);
    ritual.quaternion.copy(camera.quaternion);
   }
   if(hold.type!=='bound-bundle'||hold.ceremonyStarted)hex.update(p,now);else hex.clear();
   for(const helper of bundleHighlights){
    const ref=helper.userData.scanRef,o=entries().find(o=>o.id===ref.id&&o.kind===ref.kind);
    if(o){helper.box.makeEmpty();for(const g of o.groups||[o.group])helper.box.expandByObject(g);helper.updateMatrixWorld(true)}
    helper.material.opacity=.55+.4*Math.sin(p*Math.PI*3)**2;
   }
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
  if(collection){toast('🔯 '+(collection.scanContract.form==='card'?'長按三秒可將整組物件實體化；契約仍有效':'長按世界中的群組物件三秒可收回原卡；契約仍有效'));return true}
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
  if(before&&!card){stop();cancelPrompt();}
  invalidate();
 }
 // Generic contract release event: refresh this artifact only when its
 // ORIGINAL card was released. Other artifacts can subscribe independently.
 scene.addEventListener('living-contract-unbound',e=>{
  if(e.bindingId==='scan.bundle.'+e.cardId&&Array.isArray(e.scanMembers)){
   // The old owner no longer exists after the atomic unbind. Remove cached
   // ownership AND world anchors immediately, not on a future scanner tick.
   stop();cancelPrompt();applyPacked();
   // A released contract has NO group ownership: never replay the old group's
   // shelf-return callback after unbinding. Recompute scan targets instead.
   updateMarks();invalidate();
  }
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
  cardPressStart,cardPressMove,cardPressEnd,pointerDown,pointerMove,pointerEnd,inspect:(options={})=>({form:card?.truthEyeContract?.form||'unbound',selected:scannedTargets().map(o=>o.id),config:{...cfg},ritualVisible:ritual.visible,hold:hold?{type:hold.type,phase:hold.ceremonyStarted?'animation':'holding',cardId:hold.cardId,targetId:hold.scanObjectId,scanIds:hold.scanTargets?.map(o=>o.id)||[],progress:clamp((performance.now()-hold.started)/3000,0,1),highlightCount:bundleHighlights.length}:null,objects:entries().map(o=>{const point=screenPoint(o.group);const member=options.pickPoints?boundMemberAt({clientX:point.x,clientY:point.y}):null;return {id:o.id,kind:o.kind,visibleGroups:(o.groups||[o.group]).filter(g=>g.visible).length,visible:(o.groups||[o.group]).some(g=>g.visible),...point,memberPickable:member?.key===o.kind+':'+o.id}}),scanned:scannedTargets().map(o=>({id:o.id,kind:o.kind,...screenPoint(o.group)})),cards:[...floatingCards.values()].filter(e=>e.group.visible).map(e=>({id:e.id,...screenPoint(e.group)})),packed:[...packed],bundleBusy:!!bundleTransfer})});
}
root.LivingTruthEyeWorld=Object.freeze({create,model});
})(typeof self!=='undefined'?self:globalThis);
