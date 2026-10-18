/* Truth Eye 3D world integration: no image planes; original card IDs only.
 * Stage 1: scanner + binding the scanner itself. Book hexagram is a read-only preview. */
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
 const C=root.LivingTruthEyeContract;if(!C)throw Error('真實之眼卡片契約模組不存在');
 const built=model(T),eye=built.group;scene.add(eye);
 const eyeLight=new T.PointLight(0x5cc5eb,4,5);scene.add(eyeLight);
 let cfg={radius:8,aperture:360,direction:0,mode:'both',enabled:true};
 try{const s=JSON.parse(storage.getItem('living-truth-eye-scan-v1')||'null');
  if(s){cfg.radius=clamp(Number(s.radius)||8,2,20);cfg.aperture=clamp(Number(s.aperture)||360,15,360);
   cfg.direction=clamp(Number(s.direction)||0,0,360);cfg.mode=['both','radar','ripple'].includes(s.mode)?s.mode:'both'}
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
 // Single visual hexagram design, adjusted by the chosen object's Box3 footprint.
 const ritual=new T.Group();scene.add(ritual);ritual.visible=false;
 const lines=new T.LineBasicMaterial({color:0xffd891,opacity:.85,transparent:true});
 for(const start of [0,Math.PI]){const points=[];
  for(let i=0;i<3;i++){const a=start+i*Math.PI*2/3;points.push(new T.Vector3(Math.cos(a),0,Math.sin(a)))}
  ritual.add(new T.LineLoop(new T.BufferGeometry().setFromPoints(points),lines));
 }
 const ritualOuter=ring(ritual,amber.clone());ritualOuter.position.y=.01;
 const progGeo=new T.RingGeometry(1.05,1.11,96,1,-Math.PI/2,Math.PI*2);
 const progress=new T.Mesh(progGeo,new T.MeshBasicMaterial({color:0xffedbb,transparent:true,opacity:.96,depthWrite:false,side:T.DoubleSide}));progress.rotation.x=-Math.PI/2;progress.position.y=.025;ritual.add(progress);
 let hold=null,serial=0,lastScan=0;
 const style=document.createElement('style');
 style.textContent='.truthEyeHud{position:fixed;z-index:9;right:250px;top:clamp(105px,19vh,170px);width:195px;max-width:45vw;color:#ffde9c;text-shadow:0 1px 7px #020711;font:11px system-ui;pointer-events:auto}.truthEyeHud button{cursor:pointer;color:#fff0c8;background:#142333b8;border:1px solid #bda1718c;border-radius:50%;width:39px;height:39px;font-size:22px;touch-action:none}.truthEyeHud header{display:flex;justify-content:flex-end;align-items:center;gap:6px}.truthEyeHud small{font-size:10px}.truthEyeHud main{padding-top:7px;display:grid;gap:8px;background:none!important}.truthEyeHud main[hidden]{display:none}.truthEyeHud label{display:grid;grid-template-columns:25px 1fr 33px;gap:5px;align-items:center}.truthEyeHud input{width:100%;accent-color:#e6c381;touch-action:pan-y}.truthEyeHud select{justify-self:end;border:1px solid #a98b5b;color:#fce8bf;background:#1c2b38d9;border-radius:4px}@media(min-width:651px) and (max-width:920px){.truthEyeHud{right:205px}}@media(max-width:650px){.truthEyeHud{right:6px;top:105px;width:165px}.truthEyeHud label{grid-template-columns:25px 1fr 27px;gap:2px}}';
 document.head.append(style);
 const host=document.createElement('section');host.className='truthEyeHud';host.setAttribute('aria-label','真實之眼');
 host.innerHTML='<header><button id="eyeAction" aria-label="締約／具現化／收納" title="真實之眼">👁</button><button id="eyeToggle" aria-label="掃描開關" title="掃描開關">◉</button><small id="eyeStatus"></small></header><main id="eyeControls"><label>半徑<input id="eyeRadius" type="range" min="2" max="20" step=".5"><output id="eyeRadiusVal"></output></label><label>角度<input id="eyeAperture" type="range" min="15" max="360" step="5"><output id="eyeApertureVal"></output></label><label>方向<input id="eyeDirection" type="range" min="0" max="360" step="5"><output id="eyeDirectionVal"></output></label><select id="eyeMode" aria-label="掃描模式"><option value="both">複合掃描</option><option value="radar">雷達</option><option value="ripple">水波</option></select></main>';
 document.body.append(host);
 const el=name=>host.querySelector('#eye'+name),control=el('Controls'),action=el('Action');
 const status=el('Status');
 function state(){try{card=C.boundCard(storage)}catch(e){toast(e.message);card=null}
  const form=card?.truthEyeContract?.form;
  eye.visible=!card||form==='world';eyeLight.visible=eye.visible;
  field.visible=form==='world'&&cfg.enabled;marks.visible=field.visible;control.hidden=form!=='world';
  el('Toggle').hidden=form!=='world';status.textContent=!card?'長按3秒':form==='card'?'點眼具現':'掃描中';
  action.title=!card?'長按三秒與既有空白卡締約':form==='card'?'具現化真實之眼':'將真實之眼收回原卡';
 }
 function apply(){for(const key of ['Radius','Aperture','Direction']){const name=key.toLowerCase(),number=cfg[name];
  el(key).value=String(number);el(key+'Val').textContent=number+(name==='radius'?'m':'°');
 }el('Mode').value=cfg.mode;
  fan.geometry.dispose();fan.geometry=new T.CircleGeometry(cfg.radius,Math.max(48,Math.round(cfg.aperture/3)),0,cfg.aperture*RAD);
  fan.rotation.z=cfg.direction*RAD;border.scale.setScalar(cfg.radius);
  try{storage.setItem('living-truth-eye-scan-v1',JSON.stringify(cfg))}catch(_){}
  invalidate();
 }
 for(const [k,name] of [['radius','Radius'],['aperture','Aperture'],['direction','Direction']]){
  el(name).addEventListener('input',e=>{cfg[k]=Number(e.target.value);apply()});
 }
 el('Mode').addEventListener('change',e=>{cfg.mode=e.target.value;apply()});
 el('Toggle').addEventListener('click',()=>{cfg.enabled=!cfg.enabled;state();invalidate()});
 function hit(e,meshes){const rect=renderer.domElement.getBoundingClientRect();if(!rect.width||!rect.height)return null;
  mouse.set((e.clientX-rect.left)/rect.width*2-1,-(e.clientY-rect.top)/rect.height*2+1);
  caster.setFromCamera(mouse,camera);return caster.intersectObjects(meshes,true)[0]||null;
 }
 function inRange(group){
  group.getWorldPosition(tmp);const dx=tmp.x-keeper.position.x,dz=tmp.z-keeper.position.z;
  const direction=(Math.atan2(dx,-dz)/RAD-cfg.direction+540)%360-180;
  return Math.hypot(dx,dz)<=cfg.radius&&(cfg.aperture===360||Math.abs(direction)<=cfg.aperture/2);
 }
 function targets(){return field.visible?bookEntities.filter(o=>o.group.visible&&inRange(o.group)):[]}
 function at(e){if(!field.visible)return null;const list=[];
  for(const o of targets())o.group.traverse(m=>{if(m.isMesh){m.userData.truthEyeId=o.id;list.push(m)}});
  return hit(e,list)?.object.userData.truthEyeId||null;
 }
 function pick(e){const id=at(e);if(!id)return false;
  if(selected.has(id))selected.delete(id);else selected.add(id);
  toast('👁 已選取 '+selected.size+' 件，長按其中一件可觀看契約動畫');invalidate();return true;
 }
 function updateMarks(){
  const t=targets(),set=new Set(t.map(o=>o.id));
  for(const [id,g] of markMap)g.visible=set.has(id);
  for(const o of t){let m=markMap.get(o.id);
   if(!m){m=new T.Group();const r=ring(m,cyan.clone());r.scale.setScalar(.49);
    const j=new T.Mesh(new T.OctahedronGeometry(.10),amber.clone());j.position.y=.72;m.add(j);marks.add(m);markMap.set(o.id,m);}
   o.group.getWorldPosition(tmp);m.position.set(tmp.x,.15,tmp.z);
   m.scale.setScalar(selected.has(o.id)?1.65:1);
  }
  for(const id of [...selected])if(!set.has(id))selected.delete(id);
 }
 function stop(){if(hold){clearTimeout(hold.timer);hold=null}ritual.visible=false;invalidate()}
 function complete(token){if(!hold||hold.token!==token)return;
  const type=hold.type;stop();
  if(type==='eye'){try{
   const candidates=[...floatingCards.values()].map(e=>{const p=e.group.getWorldPosition(new T.Vector3());return {id:e.id,record:e.record,position:{x:p.x,z:p.z},visible:e.group.visible,isDragging:!!e.drag,isFocused:false,isSummoning:!!e.summon,isRecycling:!!e.recycle}});
   const near=C.nearest({x:keeper.position.x,z:keeper.position.z},candidates);
   if(near.status!=='selected')throw Error(near.reason);
   const original=craft.listCards(storage).find(x=>x.id===near.cardId);
   if(!original)throw Error('原卡不存在');
   C.bind(storage,original.id,original.revision);refreshCards();state();
   toast('✦ 已與原有空白卡締結契約 · 點眼圖示具現化');
  }catch(e){toast('契約未完成：'+e.message)}
  }else{toast('🔯 已顯示 '+selected.size+' 件六芒星契約效果；封裝資料尚未寫入')}
 }
 function start(type,id,x,y){if(hold)return false;
  const box=new T.Box3().makeEmpty();
  if(type==='books')for(const o of bookEntities)if(selected.has(o.id))box.expandByObject(o.group);
  const center=box.isEmpty()?new T.Vector3(keeper.position.x,0,keeper.position.z):box.getCenter(new T.Vector3());
  const size=box.isEmpty()?new T.Vector3(2,0,2):box.getSize(new T.Vector3());
  const radius=type==='eye'?1.65:Math.max(1.2,Math.hypot(size.x,size.z)*.62);
  ritual.position.set(center.x,.29,center.z);ritual.scale.setScalar(radius);ritual.visible=true;progGeo.setDrawRange(0,0);
  const token=++serial;hold={token,type,id,x,y,started:performance.now(),timer:setTimeout(()=>complete(token),3000)};
  invalidate();return true;
 }
 function pointerDown(e){if(e.button!==0||!e.isPrimary||hold)return false;
  if(!card&&eye.visible&&hit(e,[eye]))return start('eye',e.pointerId,e.clientX,e.clientY);
  const id=at(e);if(id&&selected.has(id))return start('books',e.pointerId,e.clientX,e.clientY);
  return false;
 }
 function pointerMove(e){if(!hold||hold.id!==e.pointerId)return false;
  if(Math.hypot(e.clientX-hold.x,e.clientY-hold.y)>12)stop();return true;
 }
 function pointerEnd(e){if(!hold||hold.id!==e.pointerId)return false;stop();return true}
 function flip(){if(!card)return;try{
  const form=card.truthEyeContract.form==='card'?'world':'card';
  C.setForm(storage,card.id,card.revision,form);refreshCards();state();
  toast(form==='world'?'✦ 真實之眼已具現化':'✦ 真實之眼已回到原卡');
 }catch(e){toast(e.message)}invalidate();
 }
 let guiPointer=null;
 action.addEventListener('pointerdown',e=>{if(e.button!==0)return;
  e.preventDefault();e.stopPropagation();if(card){flip();return}
  guiPointer=e.pointerId;action.setPointerCapture?.(e.pointerId);start('eye',e.pointerId,e.clientX,e.clientY);
 });
 for(const name of ['pointerup','pointercancel','lostpointercapture'])action.addEventListener(name,e=>{if(e.pointerId===guiPointer){guiPointer=null;pointerEnd(e)}});
 action.addEventListener('pointermove',e=>{if(e.pointerId===guiPointer)pointerMove(e)});
 action.addEventListener('click',e=>e.preventDefault());
 document.addEventListener('visibilitychange',()=>{if(document.hidden)stop()});
 root.addEventListener('blur',stop);
 function tick(now){const t=now/1000;
  if(eye.visible){
   eye.position.set(keeper.position.x,keeper.position.y+3.50+(reducedMotion?0:.10*Math.sin(t*1.2)),keeper.position.z);
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
  if(hold){const p=clamp((now-hold.started)/3000,0,1);
   progGeo.setDrawRange(0,Math.floor(p*96)*6);ritual.rotation.y=t*.07;
   if(p>=1)complete(hold.token);
  }
 }
 function active(){return eye.visible||field.visible||!!hold}
 apply();state();
 return Object.freeze({tick,active,pick,pointerDown,pointerMove,pointerEnd,inspect:()=>({form:card?.truthEyeContract?.form||'unbound',selected:[...selected],config:{...cfg}})});
}
root.LivingTruthEyeWorld=Object.freeze({create,model});
})(typeof self!=='undefined'?self:globalThis);