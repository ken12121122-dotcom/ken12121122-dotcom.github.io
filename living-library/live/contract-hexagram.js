/* NENDO CONTRACT HEXAGRAM: three-dimensional port of the approved
 * 念動世界_六芒星契約法陣_動畫沙盒.html (2026-10-17).
 * Composition matches original: double gold star, 66 rune ticks, 48 rim marks,
 * double rotating upper rails, six charging light columns, central triple
 * upright orbits, charged outer rim, motes, 2.35s release burst.
 * Native Three.js scene geometry. No separate circle progress UI or fake card.
 */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingContractHexagram=api})(typeof self!=='undefined'?self:globalThis,function(){
 'use strict';
 const TAU=Math.PI*2,EDGE_COUNT=6;
 const clamp=n=>Math.max(0,Math.min(1,n));
 const gold=0xd5ad6e,ivory=0xfff0c6,teal=0x75e6d8;
 function create({THREE:T,parent=null,plane='xy',radius=1}={}){
  if(!T?.Group||!T?.TubeGeometry||!T?.CylinderGeometry)throw Error('Three.js required for true ritual geometry');
  const group=new T.Group();group.name='原始六芒星契約儀式';group.visible=false;
  if(parent)parent.add(group);
  const flat=new T.Group();group.add(flat);
  // Local X/Y is floor art, local Z is height above floor.
  if(plane==='xz')flat.rotation.x=-Math.PI/2;
  const mat=(color,opacity=.7)=>new T.MeshBasicMaterial({color,transparent:true,opacity,
   depthWrite:false,side:T.DoubleSide,blending:T.AdditiveBlending});
  const bronze=mat(gold,.64),cream=mat(ivory,.87),aqua=mat(teal,.71),dim=mat(gold,.29);
  const outlineMat=new T.LineBasicMaterial({color:gold,transparent:true,opacity:.52,depthWrite:false});
  const ringMat=new T.MeshBasicMaterial({color:ivory,transparent:true,opacity:.78,depthWrite:false,blending:T.AdditiveBlending});
  const planePoint=(a,r=1,z=0)=>new T.Vector3(r*Math.cos(a),r*Math.sin(a),z);
  const curve=(pts,width,material,parentGroup=flat)=>{
   const obj=new T.Mesh(new T.TubeGeometry(new T.CatmullRomCurve3(pts,false,'centripetal'),Math.max(4,pts.length*2),width,5,false),material);
   parentGroup.add(obj);return obj;
  };
  const line=(a,b,width,material,parentGroup=flat)=>{
   const p1=a.clone(),p2=b.clone(),d=p2.sub(p1),length=d.length();
   const mesh=new T.Mesh(new T.CylinderGeometry(width,width,1,6),material);
   mesh.position.copy(a).addScaledVector(d,.5);
   mesh.quaternion.setFromUnitVectors(new T.Vector3(0,1,0),d.normalize());
   mesh.scale.y=length;parentGroup.add(mesh);return mesh;
  };
  const ring=(r,z,width,material,parentGroup=flat)=>{
   const mesh=new T.Mesh(new T.TorusGeometry(r,width,5,112),material);
   mesh.position.z=z;parentGroup.add(mesh);return mesh;
  };
  const points=(r,z,num,start=0)=>Array.from({length:num},(_,i)=>planePoint(start+TAU*i/num,r,z));
  function poly(r,z,num,start,material,width){
   const p=points(r,z,num,start);
   for(let i=0;i<num;i++)line(p[i],p[(i+1)%num],width,material);
  }
  function arc(r,z,start,end,material,width,parentGroup=flat){
   const n=Math.max(5,Math.round((end-start)/TAU*100));
   const pts=Array.from({length:n+1},(_,i)=>planePoint(start+(end-start)*i/n,r,z));
   return curve(pts,width,material,parentGroup);
  }
  // Ground perimeter: exact original proportions and separate inner outlines.
  ring(1,-.008,.011,cream);
  ring(.975,-.006,.006,aqua);
  ring(1.044,-.005,.003,bronze);
  ring(.87,.025,.006,bronze);
  ring(.79,.027,.004,dim);
  ring(.747,.028,.006,aqua);
  // Continuous original six-pointed star (two intertwined triangles).
  const theta=-Math.PI/2, seals=[];
  for(let t=0;t<2;t++){
   const a=points(.718,.060,3,theta+t*Math.PI);
   for(let j=0;j<3;j++){
    line(a[j],a[(j+1)%3],.019,t===0?cream:bronze);
    line(a[j].clone().add(new T.Vector3(0,0,.006)),a[(j+1)%3].clone().add(new T.Vector3(0,0,.006)),.033,dim);
   }
  }
  poly(.386,.072,6,theta,cream,.009);
  poly(.31,.08,6,theta+Math.PI/6,aqua,.007);
  ring(.193,.086,.010,cream);ring(.122,.093,.006,aqua);
  poly(.16,.11,6,theta,bronze,.006);
  for(let i=0;i<12;i++)line(planePoint(i*TAU/12,.19,.10),planePoint(i*TAU/12+.048,.31,.084),.004,i%2?bronze:aqua);
  // Fine band of runes like the 66 painted symbols in the original prototype.
  const runes=new T.Group();flat.add(runes);runes.position.z=.03;
  for(let i=0;i<66;i++){
   const a=i*TAU/66,material=i%9===0?aqua:bronze,r=.831;
   line(planePoint(a,r-.012,0),planePoint(a,r+.016,0),i%9===0?.006:.0035,material,runes);
   if(i%6===0){
    const jewel=new T.Mesh(new T.OctahedronGeometry(.011),aqua);
    jewel.position.copy(planePoint(a,r-.031,.008));runes.add(jewel);
   }
  }
  const innerRunes=new T.Group();flat.add(innerRunes);
  for(let i=0;i<54;i++){
   const a=i*TAU/54;
   const mark=new T.Mesh(new T.OctahedronGeometry(i%3===0?.009:.005),i%3===0?aqua:dim);
   mark.position.copy(planePoint(a,.754,.035));innerRunes.add(mark);
  }
  const borderTicks=new T.Group();flat.add(borderTicks);
  for(let i=0;i<48;i++){
   const a=i*TAU/48;
   line(planePoint(a,.976,0),planePoint(a,i%4===0?1.036:1.005,0),
    i%6===0?.007:.0035,i%6===0?aqua:bronze,borderTicks);
  }
  // Six ornamental seal plates and their pillars rising from the exact star tips.
  const columnGeo=new T.CylinderGeometry(.012,.012,1,8);
  const columns=[],nodes=[];
  for(let i=0;i<6;i++){
   const a=theta+i*TAU/6,x=.718*Math.cos(a),y=.718*Math.sin(a);
   const p=new T.Group();p.position.set(x,y,.066);flat.add(p);
   const plate=new T.Mesh(new T.OctahedronGeometry(.049),bronze);plate.scale.z=.28;p.add(plate);
   const glow=new T.Mesh(new T.OctahedronGeometry(.060),aqua.clone());glow.position.z=.026;p.add(glow);
   const upper=new T.Mesh(new T.SphereGeometry(.028,10,8),cream.clone());upper.visible=false;flat.add(upper);
   const column=new T.Mesh(columnGeo,i%2===0?aqua.clone():cream.clone());
   column.position.set(x,y,.08);flat.add(column);column.visible=false;
   const caps=arc(.73,.071,a-.071,a+.071,cream,.008);caps.material.opacity=.64;
   columns.push({column,upper,x,y,i});nodes.push({plate,glow});
  }
  // Upper hovering halo: two segmented solid-light circular rails with opposing rotation.
  const rails=[];
  for(const [rad,high,count,matCol,direction] of [[.91,.21,12,aqua,1],[.947,.37,18,cream,-1]]){
   const parentRing=new T.Group();parentRing.position.z=high;flat.add(parentRing);
   ring(rad,0,.009,matCol,parentRing);
   const segments=new T.Group();parentRing.add(segments);
   for(let i=0;i<count;i++)arc(rad,0,i*TAU/count,(i+.56)*TAU/count,cream,.010,segments);
   rails.push({parentRing,segments,direction});
  }
  // Center, three vertical standing orbits, free from the ground plane.
  const orbits=[];
  for(const [r,rot,speed,opacity] of [[.27,1.1,.00022,.55],[.227,1.32,-.00019,.46],[.186,.82,.00032,.36]]){
   const orbit=new T.Group();orbit.position.z=.29;flat.add(orbit);
   const ringMesh=new T.Mesh(new T.TorusGeometry(r,.009,6,84),aqua.clone());
   ringMesh.rotation.x=rot;orbit.add(ringMesh);
   const gem=new T.Mesh(new T.OctahedronGeometry(.025),cream.clone());
   orbit.add(gem);orbits.push({orbit,gem,r,rot,speed,opacity});
  }
  const innerHalo=new T.Group();innerHalo.position.z=.29;flat.add(innerHalo);
  const innerLoop=new T.LineLoop(new T.BufferGeometry().setFromPoints(points(.235,0,6,0)),
   new T.LineBasicMaterial({color:ivory,transparent:true,opacity:.72,depthWrite:false}));
  innerHalo.add(innerLoop);
  const innerLoopSmall=new T.LineLoop(new T.BufferGeometry().setFromPoints(points(.118,0,6,0)),
   new T.LineBasicMaterial({color:teal,transparent:true,opacity:.54,depthWrite:false}));
  innerHalo.add(innerLoopSmall);
  const centreGem=new T.Mesh(new T.OctahedronGeometry(.044),cream);
  centreGem.position.z=.29;flat.add(centreGem);
  // Stars and dust: restrained on mobile, deterministic positioning.
  const dust=[],count=48;
  for(let i=0;i<count;i++){
   const a=i*2.399963,r=1.08+(i%17)/18*1.24;
   const sparkle=new T.Mesh(new T.OctahedronGeometry(i%8===0?.015:.008),i%6===0?cream:aqua);
   flat.add(sparkle);sparkle.userData.initialAngle=a;
   sparkle.userData.distance=r;sparkle.userData.phase=i*.743;
   dust.push(sparkle);
  }
  const chargeLine=new T.Mesh(new T.TorusGeometry(1.012,.018,5,120,TAU),cream.clone());
  chargeLine.position.z=.105;flat.add(chargeLine);chargeLine.visible=false;
  // Charged burst is distinct from the progress arc, physically expands outward.
  const burstA=ring(.42,.18,.020,aqua.clone());
  const burstB=ring(.35,.19,.012,cream.clone());
  burstA.visible=burstB.visible=false;
  let baseRadius=radius,burstStart=-1;
  function setRadius(r){if(!Number.isFinite(r)||r<=0||r>=1000)return;
   baseRadius=r;group.scale.setScalar(r);
  }
  function update(progress=0,time=0){
   const q=clamp(Number(progress)||0),s=Math.max(0,time);
   group.visible=true;
   group.scale.setScalar(baseRadius);
   // Star itself is ALWAYS complete, as in original; six seals charge in order.
   // Note: ground rim may be round in the art; it is NOT a separate progress UI.
   const tick=s*.001;
   runes.rotation.z=s*.000027;innerRunes.rotation.z=-s*.000019;
   borderTicks.rotation.z=-s*.000018;
   for(let i=0;i<6;i++){
    const activate=clamp(q*6-i),n=nodes[i],c=columns[i];
    n.glow.scale.setScalar(.65+.65*activate+.12*Math.sin(tick*1.4+i));
    n.glow.material.opacity=.22+.76*activate;
    n.plate.scale.setScalar(.78+.35*activate);
    const height=.10+activate*.38;
    c.column.visible=q>i/6;
    c.column.scale.y=Math.max(.001,height);
    c.column.position.z=.08+height*.5;
    c.column.material.opacity=.23+activate*.66;
    c.upper.visible=c.column.visible;
    c.upper.position.set(c.x,c.y,.09+height);
   }
   for(const [i,o] of orbits.entries()){
    o.orbit.rotation.z=i%2?s*.00007:-s*.00008;
    o.orbit.rotation.y=o.rot+o.speed*s;
    o.gem.position.set(o.r*Math.cos(s*.001+i),o.r*Math.sin(s*.001+i),.02);
    o.orbit.scale.setScalar(.97+q*.07);
   }
   rails.forEach(o=>{
    o.parentRing.position.z=o===rails[0]?.20+.017*Math.sin(tick*1.15):.36;
    o.segments.rotation.z=s*.00014*o.direction;
   });
   innerHalo.rotation.z=s*.00017;
   innerLoopSmall.rotation.z=-s*.0005;centreGem.rotation.z=s*.00021;
   for(const mote of dust){
    const a=mote.userData.initialAngle+s*.00009;
    const r=mote.userData.distance;
    mote.position.set(r*Math.cos(a),r*Math.sin(a),.03+.2*(.5+.5*Math.sin(tick*.4+mote.userData.phase)));
    mote.scale.setScalar(.7+q*.5);
   }
   chargeLine.visible=q>.001;
   if(chargeLine.visible){
    chargeLine.geometry.setDrawRange(0,Math.max(0,Math.min(120,Math.floor(120*q)))*5*6);
    chargeLine.rotation.z=-Math.PI/2;
   }
   if(q>=1&&burstStart<0)burstStart=s;
   if(burstStart>=0){
    const u=clamp((s-burstStart)/2350),k=1-u;
    burstA.visible=burstB.visible=k>.015;
    burstA.scale.setScalar(.38+u*.84);burstB.scale.setScalar(.32+u*.72);
    burstA.position.z=.12+u*.10;burstB.position.z=.14+u*.07;
    burstA.material.opacity=.68*k;burstB.material.opacity=.58*k;
   }
  }
  function clear(){group.visible=false;burstStart=-1;chargeLine.visible=false;
   burstA.visible=burstB.visible=false;group.scale.setScalar(baseRadius)}
  setRadius(radius);
  return Object.freeze({group,update,clear,setRadius,edgeCount:EDGE_COUNT,
   source:'2026-10-17-original-contract-animation',progressElements:6});
 }
 return Object.freeze({EDGE_COUNT,create});
});
