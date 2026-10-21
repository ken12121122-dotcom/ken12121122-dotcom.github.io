/* NW-GATE-WORLD-001: ordinary, initially unbound world object.
 * Asset is the audited 32-mesh GLB, not the preview HTML or a permanent card.
 * This narrowly scoped GLB2 importer supports the exact static PBR mesh subset
 * exported by the Contour3D demo. Reject unknown GLB extensions rather than
 * quietly replacing the user's asset with a different drawing.
 */
(function(root){'use strict';
 const ID='scene.gate.obsidian.001';
 const FILE='./assets/gates/NW_OBSIDIAN_GATE_Contour3D_DEMO.glb';
 function parse(arrayBuffer){
  const d=new DataView(arrayBuffer);
  if(arrayBuffer.byteLength<20||d.getUint32(0,true)!==0x46546c67||d.getUint32(4,true)!==2||d.getUint32(8,true)!==arrayBuffer.byteLength)
   throw Error('不是有效的 GLB 2.0 模型');
  let cursor=12,json=null,binary=null;
  while(cursor+8<=d.byteLength){
   const size=d.getUint32(cursor,true),kind=d.getUint32(cursor+4,true);cursor+=8;
   if(cursor+size>d.byteLength)throw Error('GLB 區塊長度異常');
   if(kind===0x4e4f534a)json=JSON.parse(new TextDecoder().decode(new Uint8Array(arrayBuffer,cursor,size)));
   if(kind===0x004e4942)binary=new Uint8Array(arrayBuffer,cursor,size);
   cursor+=size;
  }
  if(!json||!binary||json.extensionsRequired?.length||json.images?.length||json.textures?.length||json.animations?.length)
   throw Error('GLB 含未支援的擴充、圖片或動畫，停止載入以保護原模型');
  return {gltf:json,bytes:binary};
 }
 function createMeshes(THREE,model){
  const {gltf,bytes}=model;
  const types={5121:Uint8Array,5123:Uint16Array,5125:Uint32Array,5126:Float32Array};
  const counts={SCALAR:1,VEC2:2,VEC3:3,VEC4:4};
  function attribute(id){
   const a=gltf.accessors?.[id],v=gltf.bufferViews?.[a?.bufferView];
   if(!a||!v||v.buffer!==0||a.sparse||a.normalized||v.byteStride)throw Error('模型使用未支援的 GLB accessor');
   const Type=types[a.componentType],components=counts[a.type],offset=(v.byteOffset||0)+(a.byteOffset||0);
   if(!Type||!components||!Number.isInteger(a.count)||a.count<1||offset%Type.BYTES_PER_ELEMENT!==0||
      offset+a.count*components*Type.BYTES_PER_ELEMENT>bytes.byteLength)throw Error('GLB 網格資料越界');
   // Copy out of GLB buffer to keep typed-array alignment unambiguous.
   const typed=new Type(a.count*components);
   typed.set(new Type(bytes.buffer,bytes.byteOffset+offset,a.count*components));
   return {typed,components};
  }
  const materials=(gltf.materials||[]).map(m=>{
   const p=m.pbrMetallicRoughness||{},c=p.baseColorFactor||[1,1,1,1];
   const mat=new THREE.MeshStandardMaterial({color:new THREE.Color().setRGB(c[0],c[1],c[2]),
    opacity:c[3],transparent:c[3]<1,metalness:p.metallicFactor??1,roughness:p.roughnessFactor??1,
    side:m.doubleSided?THREE.DoubleSide:THREE.FrontSide});
   if(m.emissiveFactor)mat.emissive.setRGB(...m.emissiveFactor);
   mat.name=m.name||'';return mat;
  });
  const meshCache=new Map();
  function meshAt(i){
   if(meshCache.has(i))return meshCache.get(i).clone();
   const def=gltf.meshes?.[i];if(!def?.primitives?.length)throw Error('GLB mesh 缺失');
   const group=new THREE.Group();group.name=def.name||'mesh.'+i;
   for(const primitive of def.primitives){
    if(primitive.mode!==undefined&&primitive.mode!==4)throw Error('GLB 不是三角面');
    if(primitive.targets||primitive.extensions||primitive.attributes?.POSITION===undefined)throw Error('GLB 使用未支援的幾何資料');
    const g=new THREE.BufferGeometry();
    for(const [key,label] of [['POSITION','position'],['NORMAL','normal'],['TEXCOORD_0','uv']]){
     if(primitive.attributes[key]!==undefined){const a=attribute(primitive.attributes[key]);g.setAttribute(label,new THREE.BufferAttribute(a.typed,a.components));}
    }
    if(primitive.indices!==undefined){const ind=attribute(primitive.indices);if(ind.components!==1)throw Error('索引資料不是 SCALAR');g.setIndex(new THREE.BufferAttribute(ind.typed,1));}
    if(!g.getAttribute('normal'))g.computeVertexNormals();
    const mesh=new THREE.Mesh(g,materials[primitive.material]||new THREE.MeshStandardMaterial());
    mesh.name=group.name;mesh.userData.sourceAsset=FILE;group.add(mesh);
   }
   meshCache.set(i,group);return group;
  }
  const built=gltf.nodes.map((node,i)=>{
   const group=node.mesh!==undefined?meshAt(node.mesh):new THREE.Group();group.name=node.name||group.name||'node.'+i;
   if(node.matrix){const m=new THREE.Matrix4().fromArray(node.matrix);m.decompose(group.position,group.quaternion,group.scale)}
   else {if(node.translation)group.position.fromArray(node.translation);if(node.rotation)group.quaternion.fromArray(node.rotation);if(node.scale)group.scale.fromArray(node.scale)}
   return group;
  });
  gltf.nodes.forEach((node,i)=>(node.children||[]).forEach(child=>{if(!built[child])throw Error('GLB node 索引錯誤');built[i].add(built[child])}));
  const sceneRoot=new THREE.Group();sceneRoot.name='NW_OBSIDIAN_GATE_Contour3D_DEMO';
  for(const index of gltf.scenes?.[gltf.scene||0]?.nodes||[])sceneRoot.add(built[index]);
  return sceneRoot;
 }
 async function attach({THREE,scene,url=FILE,position={x:0,y:0.006,z:8.4},scale=0.9}={}){
  if(!THREE?.Group||!scene?.add)throw Error('缺少 Three.js 世界');
  const response=await fetch(url,{credentials:'same-origin'});
  if(!response.ok)throw Error('魔法門 GLB HTTP '+response.status);
  const file=await response.arrayBuffer();
  const model=createMeshes(THREE,parse(file));
  const source=new THREE.Group();source.name='Gate_ZUp_To_YUp';source.rotation.x=-Math.PI/2;source.scale.setScalar(scale);source.add(model);
  const outer=new THREE.Group();outer.name='雙界之門';
  outer.userData.scanObjectId=ID;
  outer.userData.scanName='雙界之門';
  outer.userData.displayName='雙界之門';
  outer.userData.sceneObjectId=ID;
  // Generic ordinary-world interaction policy: visible, but inert to bare taps.
  outer.userData.inertUntilScanned=true;
  outer.position.set(position.x,position.y,position.z);outer.rotation.y=Math.PI;outer.add(source);
  const modules=Object.fromEntries(['G01','G02','G03','G04','G05','G06'].map(k=>[k,[]]));
  model.traverse(o=>{if(!o.isMesh)return;const k=o.name.slice(0,3);if(modules[k])modules[k].push(o);
   o.castShadow=false;o.receiveShadow=true;});
  if(Object.values(modules).some(a=>a.length===0))throw Error('缺少原 GLB G01～G06 模組');
  outer.updateWorldMatrix(true,true);
  const bounds=new THREE.Box3().setFromObject(outer);
  if(bounds.isEmpty()||!Number.isFinite(bounds.min.x))throw Error('魔法門立體邊界錯誤');
  scene.add(outer);
  return Object.freeze({id:ID,root:outer,modules,bounds,asset:url});
 }
 root.LivingWorldGate=Object.freeze({ID,FILE,parse,attach});
})(typeof self!=='undefined'?self:globalThis);