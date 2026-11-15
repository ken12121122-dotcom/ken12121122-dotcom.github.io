// Builds living-library/live/assets/characters/anna_phone_v1.glb from assets/3d/anna_ipati_animated.glb (run assets/3d/join.sh first).
// Usage: node anna-phone-build.mjs <src.glb> <out.glb> [maxTexturePx=1024]  — needs @gltf-transform/{core,functions,extensions}@4, meshoptimizer, sharp.
import {NodeIO} from '@gltf-transform/core';
import {ALL_EXTENSIONS} from '@gltf-transform/extensions';
import {prune,dedup,resample,textureCompress,quantize,meshopt,weld} from '@gltf-transform/functions';
import {MeshoptEncoder} from 'meshoptimizer';
import sharp from 'sharp';
const [src,out,size='1024']=process.argv.slice(2);
await MeshoptEncoder.ready;
const io=new NodeIO().registerExtensions(ALL_EXTENSIONS).registerDependencies({'meshopt.encoder':MeshoptEncoder});
const doc=await io.read(src);const r=doc.getRoot();
// 1) drop facial morph targets (349 FACS set) + their weight channels: dominant geometry cost
for(const m of r.listMeshes()){for(const p of m.listPrimitives()){for(const t of p.listTargets()){p.removeTarget(t);t.dispose()}p.setAttribute('TANGENT',null)}m.setWeights([])}
for(const a of r.listAnimations())for(const c of a.listChannels())if(c.getTargetPath()==='weights'){const s=c.getSampler();c.dispose();if(s&&!s.listParents().some(p=>p!==a))s.dispose()}
for(const n of r.listNodes())n.setWeights([]);
await doc.transform(prune(),dedup(),resample(),
 textureCompress({encoder:sharp,targetFormat:'webp',resize:[+size,+size],quality:80}),
 quantize(),meshopt({encoder:MeshoptEncoder,level:'medium'}));
await io.write(out,doc);
