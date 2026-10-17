/* Permanent original card: THREE-DIMENSIONAL artifact, never a second card.
 * Based on the previously reviewed spirit-seal prototype:
 * dark teal layered metal skeleton, champagne gold relief,
 * ivory enamel face, glass-like surface, gemstone and etched reverse.
 * All original card picture, title and number come from the SAME card texture.
 * No registration, original-card ID, or contract is issued by this renderer.
 */
(function(root,factory){
 const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LivingPermanentCard3D=api;
})(typeof self!=='undefined'?self:globalThis,function(){
 'use strict';
 function registered(record){
  return record?.permanentRegistry?.state==='registered'&&
   record.permanentRegistry.cardId===record.id;
 }
 function roundedShape(THREE,w,h,r){
  const x=-w/2,y=-h/2,s=new THREE.Shape();
  s.moveTo(x+r,y);s.lineTo(x+w-r,y);s.quadraticCurveTo(x+w,y,x+w,y+r);
  s.lineTo(x+w,y+h-r);s.quadraticCurveTo(x+w,y+h,x+w-r,y+h);
  s.lineTo(x+r,y+h);s.quadraticCurveTo(x,y+h,x,y+h-r);
  s.lineTo(x,y+r);s.quadraticCurveTo(x,y,x+r,y);return s;
 }
 function mount(THREE,record,faceTexture){
  if(!registered(record))return null;
  const group=new THREE.Group(),resources=[],metal=(color,roughness=.25,extra={})=>
   new THREE.MeshPhysicalMaterial({color,metalness:.85,roughness,clearcoat:.87,
    clearcoatRoughness:.16,...extra});
  function add(mesh,visualOnly=true){
   group.add(mesh);resources.push(mesh);
   if(visualOnly)mesh.raycast=()=>{}; // Decorative metal cannot steal clicks.
   return mesh;
  }
  const frameGeo=new THREE.ExtrudeGeometry(roundedShape(THREE,.462,.675,.026),{
   depth:.060,steps:1,bevelEnabled:true,bevelThickness:.004,
   bevelSize:.004,bevelSegments:3,curveSegments:6});
  frameGeo.translate(0,0,-.030);
  add(new THREE.Mesh(frameGeo,metal(0xc7a771,.20)),false);
  const darkBody=add(new THREE.Mesh(new THREE.BoxGeometry(.443,.652,.062),
   metal(0x102628,.41,{metalness:.52})),false);
  darkBody.position.z=.0005;
  const enamel=add(new THREE.Mesh(new THREE.PlaneGeometry(.429,.632),
   new THREE.MeshPhysicalMaterial({color:0xf1ead8,metalness:.1,roughness:.34,clearcoat:.92,
    clearcoatRoughness:.14})),false);
  enamel.position.z=.035;
  const front=new THREE.Mesh(new THREE.PlaneGeometry(.416,.617),
   new THREE.MeshBasicMaterial({map:faceTexture,side:THREE.DoubleSide,transparent:false}));
  front.position.z=.0382;front.userData.localCardId=record.id;
  add(front,false);
  const topGlass=add(new THREE.Mesh(new THREE.PlaneGeometry(.409,.610),
   new THREE.MeshPhysicalMaterial({color:0xfff9e7,transparent:true,opacity:.055,
    metalness:0,roughness:.06,clearcoat:1,clearcoatRoughness:.02,
    depthWrite:false,side:THREE.FrontSide})));
  topGlass.position.z=.039;
  const gold=metal(0xe5c58c,.19),darkGem=metal(0x91b4ae,.16,{metalness:.42,clearcoat:1});
  // Four raised metal rails outline the enamel without covering the player's art.
  for(const side of [-1,1]){
   const rail=add(new THREE.Mesh(new THREE.BoxGeometry(.010,.619,.010),gold));
   rail.position.set(side*.212,0,.046);
   const cap=add(new THREE.Mesh(new THREE.BoxGeometry(.432,.010,.010),gold));
   cap.position.set(0,side*.313,.046);
  }
  // The metallic corners and actual 3D gems are independent physical layers.
  for(const x of [-1,1])for(const y of [-1,1]){
   const angle=add(new THREE.Mesh(new THREE.BoxGeometry(.073,.009,.018),gold));
   angle.position.set(x*.178,y*.290,.057);
   const upright=add(new THREE.Mesh(new THREE.BoxGeometry(.010,.079,.018),gold));
   upright.position.set(x*.205,y*.257,.057);
   const gem=add(new THREE.Mesh(new THREE.OctahedronGeometry(.013,0),darkGem));
   gem.position.set(x*.194,y*.279,.068);gem.rotation.z=Math.PI/4;
  }
  // Seal at the crown and distinct metallic certification stud on reverse.
  const crest=add(new THREE.Mesh(new THREE.OctahedronGeometry(.023,0),darkGem));
  crest.position.set(0,.345,.047);crest.rotation.z=Math.PI/4;
  const reverse=add(new THREE.Mesh(new THREE.PlaneGeometry(.416,.617),
   metal(0x0d2427,.43,{side:THREE.DoubleSide})),false);
  reverse.position.z=-.0352;reverse.rotation.y=Math.PI;
  const reverseOrbits=[];
  for(const radius of [.094,.133]){
   const ring=add(new THREE.Mesh(new THREE.TorusGeometry(radius,.0029,5,48),
    metal(0xcbb27b,.27)));
   ring.position.z=-.042;reverseOrbits.push(ring);
  }
  const reverseCore=add(new THREE.Mesh(new THREE.OctahedronGeometry(.039,0),darkGem));
  reverseCore.position.z=-.052;
  // One subtle THREE-dimensional floating identity halo; not a planar replacement.
  const haloMaterial=new THREE.MeshBasicMaterial({color:0xe7c995,transparent:true,opacity:.19,
   depthWrite:false,side:THREE.DoubleSide,blending:THREE.AdditiveBlending});
  const halo=add(new THREE.Mesh(new THREE.TorusGeometry(.372,.0018,4,92),haloMaterial));
  halo.rotation.x=.15;halo.position.z=-.006;
  function dispose(){
   const geos=new Set(),mats=new Set();
   for(const mesh of resources){
    if(mesh.geometry)geos.add(mesh.geometry);
    const material=mesh.material;
    if(Array.isArray(material))material.forEach(m=>mats.add(m));
    else if(material)mats.add(material);
   }
   for(const geom of geos)geom.dispose();
   for(const mat of mats)mat.dispose();
   // Do not dispose faceTexture; the parent card owns it.
  }
  return {group,halo,dispose,cardId:record.id};
 }
 return Object.freeze({registered,mount});
});
