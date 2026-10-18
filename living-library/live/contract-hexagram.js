/* Contract progress is a six-pointed star, never a circular loading UI.
 * Pure Three.js presentation; caller owns the 3000ms gesture and persistence.
 * Coordinates: XY plane by default. Set plane:'xz' for a ground ritual.
 */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingContractHexagram=api})(typeof self!=='undefined'?self:globalThis,function(){
 'use strict';
 const EDGE_COUNT=6;
 function create({THREE:T,parent=null,plane='xy',color=0xffd999,radius=1}={}){
  if(!T||!T.Group||!T.CylinderGeometry)throw Error('Three.js is required');
  const root=new T.Group();
  root.name='六芒星契約進度';root.visible=false;
  root.scale.setScalar(radius);
  if(parent)parent.add(root);
  const flat=new T.Group();root.add(flat);
  if(plane==='xz')flat.rotation.x=-Math.PI/2;
  const trackMat=new T.MeshBasicMaterial({color:0x8f7956,transparent:true,opacity:.35,depthWrite:false,side:T.DoubleSide});
  const litMat=new T.MeshBasicMaterial({color,transparent:true,opacity:.96,depthWrite:false,blending:T.AdditiveBlending});
  const detailMat=new T.MeshBasicMaterial({color:0xd1b478,transparent:true,opacity:.62,depthWrite:false,blending:T.AdditiveBlending});
  const auraMat=new T.MeshBasicMaterial({color:0x70cfd3,transparent:true,opacity:.35,depthWrite:false,blending:T.AdditiveBlending});
  const upright=new T.Vector3(0,1,0);
  const edgeGeom=new T.CylinderGeometry(.025,.025,1,6);
  const glowGeom=new T.CylinderGeometry(.050,.050,1,8);
  const p=(angle,r=1)=>new T.Vector3(Math.cos(angle)*r,Math.sin(angle)*r,0);
  const triangles=[
   [p(-Math.PI/2),p(-Math.PI/2+Math.PI*2/3),p(-Math.PI/2+Math.PI*4/3)],
   [p(Math.PI/2),p(Math.PI/2+Math.PI*2/3),p(Math.PI/2+Math.PI*4/3)]
  ];
  function bar(from,to,geo,mat,z){
   const delta=to.clone().sub(from),length=delta.length();
   const m=new T.Mesh(geo,mat);m.position.copy(from).addScaledVector(delta,.5);m.position.z=z;
   m.quaternion.setFromUnitVectors(upright,delta.clone().normalize());m.scale.y=length;
   flat.add(m);return m;
  }
  const edges=[],glyphs=[];
  for(const tri of triangles)for(let i=0;i<3;i++){
   const a=tri[i],b=tri[(i+1)%3];
   bar(a,b,edgeGeom,trackMat,.004);
   const fill=bar(a,b,glowGeom,litMat,.021);fill.visible=false;
   edges.push({a,b,delta:b.clone().sub(a),length:a.distanceTo(b),fill});
  }
  // Delicate hexagonal filigree and a second nested star make the symbol
  // readable on a mobile screen without a circular progress track.
  const hex=new T.BufferGeometry().setFromPoints(Array.from({length:6},(_,i)=>p(-Math.PI/2+i*Math.PI/3,1.125)));
  const hexLine=new T.LineLoop(hex,new T.LineBasicMaterial({color:0xc3a16f,transparent:true,opacity:.41,depthWrite:false}));
  hexLine.position.z=.012;flat.add(hexLine);
  for(const tri of triangles)for(let i=0;i<3;i++){
   const a=tri[i].clone().multiplyScalar(.37),b=tri[(i+1)%3].clone().multiplyScalar(.37);
   bar(a,b,edgeGeom,detailMat,.022);
  }
  const tipGeom=new T.OctahedronGeometry(.075,0),coronaGeom=new T.OctahedronGeometry(.115,0);
  for(const tri of triangles)for(const a of tri){
   const halo=new T.Mesh(coronaGeom,auraMat.clone());halo.position.copy(a);halo.position.z=.038;flat.add(halo);
   const node=new T.Mesh(tipGeom,litMat.clone());node.position.copy(a);node.position.z=.075;flat.add(node);
   glyphs.push({node,halo});
  }
  const core=new T.Mesh(new T.OctahedronGeometry(.135,0),detailMat);
  core.scale.z=.28;core.position.z=.11;flat.add(core);
  const accent=new T.Group();flat.add(accent);
  for(let i=0;i<12;i++){
   const a=i*Math.PI/6,x=Math.cos(a)*1.24,y=Math.sin(a)*1.24;
   const block=new T.Mesh(new T.BoxGeometry(i%3===0?.12:.060,.015,.018),detailMat);
   block.position.set(x,y,.012);block.rotation.z=a;accent.add(block);
  }
  function update(progress=0,time=0){
   const q=Math.max(0,Math.min(1,Number(progress)||0));
   root.visible=true;
   for(let i=0;i<EDGE_COUNT;i++){
    const edge=edges[i],amount=Math.max(0,Math.min(1,q*EDGE_COUNT-i)),f=edge.fill;
    f.visible=amount>.001;
    if(f.visible){
     f.scale.y=edge.length*amount;
     f.position.copy(edge.a).addScaledVector(edge.delta,amount*.5);
     f.position.z=.042;
    }
   }
   glyphs.forEach(({node,halo},i)=>{
    const activated=q*EDGE_COUNT>=i;
    const pulse=activated?1.05+.14*Math.sin(time*.005+i):.64;
    node.scale.setScalar(pulse);halo.scale.setScalar(activated?1.2: .58);
    node.material.opacity=activated?.99:.25;halo.material.opacity=activated?.67:.18;
   });
   if(q>=1){litMat.opacity=.92+.08*Math.sin(time*.018);
    root.scale.setScalar(radius*(1+.025*Math.sin(time*.016)));}
   else{litMat.opacity=.92;root.scale.setScalar(radius)}
   core.rotation.z=time*.00018;
  }
  function clear(){root.visible=false;root.scale.setScalar(radius)}
  return Object.freeze({group:root,update,clear,edgeCount:EDGE_COUNT,progressElements:edges.length,
   setRadius(r){if(Number.isFinite(r)&&r>0&&r<1000)radius=r;root.scale.setScalar(radius)}});
 }
 return Object.freeze({EDGE_COUNT,create});
});
