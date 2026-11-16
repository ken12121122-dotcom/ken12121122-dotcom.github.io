// 角色包建置：原始 GLB → 手機用角色包（meshopt 網格 + KTX2/Basis GPU 壓縮貼圖）。
// Usage: node build-character-pack.mjs <src.glb> <out.glb>
// Needs: @gltf-transform/{core,functions,extensions}@4, meshoptimizer, sharp, ktx2-encoder@0.6.0
// The pack manifest (bytes, SHA-256) is written by make-pack-manifest.mjs afterwards.
import {NodeIO} from '@gltf-transform/core';
import {ALL_EXTENSIONS} from '@gltf-transform/extensions';
import {prune,dedup,resample,textureCompress,quantize,meshopt} from '@gltf-transform/functions';
import {MeshoptEncoder} from 'meshoptimizer';
import {ktx2} from 'ktx2-encoder/gltf-transform';
import sharp from 'sharp';
const [src,out]=process.argv.slice(2);
if(!src||!out)throw Error('usage: build-character-pack.mjs <src.glb> <out.glb>');
await MeshoptEncoder.ready;
const io=new NodeIO().registerExtensions(ALL_EXTENSIONS).registerDependencies({'meshopt.encoder':MeshoptEncoder});
const doc=await io.read(src),root=doc.getRoot();
// Facial FACS morphs (349 targets) and their weight channels dominate geometry size; tangents are rebuilt by the shader.
for(const m of root.listMeshes()){for(const p of m.listPrimitives()){for(const t of p.listTargets()){p.removeTarget(t);t.dispose()}p.setAttribute('TANGENT',null)}m.setWeights([])}
for(const a of root.listAnimations())for(const c of a.listChannels())if(c.getTargetPath()==='weights')c.dispose();
for(const n of root.listNodes())n.setWeights([]);
const imageDecoder=async buffer=>{const {data,info}=await sharp(buffer).ensureAlpha().raw().toBuffer({resolveWithObject:true});return {data:new Uint8Array(data),width:info.width,height:info.height}};
const COLOR=/baseColorTexture|emissiveTexture/,NORMAL=/normalTexture/;
await doc.transform(prune(),dedup(),resample(),
 // Colour and normal detail at 1024 px; roughness/occlusion/metal at 512 px. PNG is a lossless intermediate.
 textureCompress({encoder:sharp,targetFormat:'png',resize:[1024,1024],slots:/baseColorTexture|emissiveTexture|normalTexture/}),
 textureCompress({encoder:sharp,targetFormat:'png',resize:[512,512],slots:/^(?!.*(baseColorTexture|emissiveTexture|normalTexture)).*$/}),
 ktx2({slots:NORMAL,isUASTC:true,isNormalMap:true,isPerceptual:false,needSupercompression:true,generateMipmap:true,imageDecoder}),
 ktx2({slots:COLOR,isUASTC:false,isPerceptual:true,isSetKTX2SRGBTransferFunc:true,qualityLevel:200,generateMipmap:true,imageDecoder}),
 ktx2({isUASTC:false,isPerceptual:false,qualityLevel:160,generateMipmap:true,imageDecoder}),
 quantize(),meshopt({encoder:MeshoptEncoder,level:'medium'}));
for(const t of root.listTextures())if(t.getMimeType()!=='image/ktx2')throw Error('texture not converted to KTX2: '+(t.getName()||t.getURI()));
await io.write(out,doc);
