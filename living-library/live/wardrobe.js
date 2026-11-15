/* 換衣世界（Wardrobe）— 可替換的圖書館人物外觀。
 * 純資料／狀態／載入器組裝；不改契約、不改掃描語義、不新增玩家卡。
 * 人物仍是同一個受保護的 keeper 根節點（scene.keeper），外觀只是掛在它底下的子物件。 */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingWardrobe=api;})(typeof self!=='undefined'?self:this,function(){
'use strict';
const STORAGE_KEY='nw-avatar-wardrobe-v1';
const DEFAULT_ID='keeper.default';
// 「換衣服」的日語：着替え（きがえ／kigae）。各辨識引擎可能回傳漢字、平假名、片假名或羅馬拼音。
const KIGAE_PATTERNS=Object.freeze(['着替え','着替','きがえ','キガエ','kigae']);
// 日語辨識器會把英語 Book 轉成片假名；保留原本的英文 Book。
const BOOK_PATTERNS=Object.freeze(['book','ブック','ぶっく']);
const ROSTER=Object.freeze([
 Object.freeze({id:DEFAULT_ID,name:'守書人',subtitle:'原本的圖書館管理員',kind:'builtin'}),
 Object.freeze({id:'anna.ipati.v1',name:'安娜 Anna',subtitle:'走路動畫・102 骨',kind:'glb',
  url:'assets/characters/anna_phone_v1.glb',bytes:10796376,
  sha256:'8c63642059acba37dc00f000ba44c585f60a31e1c2891c763032f6f5e55e0041',
  height:2.45,idleTime:0.2,
  credit:Object.freeze({title:'Anna Ipati (animated)',author:'Jungle Jim',
   url:'https://sketchfab.com/3d-models/anna-ipati-animated-9ef7dbea47854f49b496e8c0a8332157',
   license:'CC-BY-4.0'})})
]);
function byId(id){return ROSTER.find(e=>e.id===id)||null}
function loadChoice(storage){
 try{const id=storage?.getItem(STORAGE_KEY);return byId(id)?id:DEFAULT_ID}catch{return DEFAULT_ID}
}
function saveChoice(storage,id){
 if(!byId(id))return false;
 try{storage?.setItem(STORAGE_KEY,id);return true}catch{return false}
}
/* 換衣世界的瀏覽狀態。只有 confirm() 會改變「目前穿著」，返回不會改變任何東西。 */
function createState(appliedId=DEFAULT_ID){
 let open=false,index=Math.max(0,ROSTER.findIndex(e=>e.id===appliedId)),applied=byId(appliedId)?appliedId:DEFAULT_ID;
 const api={
  get open(){return open},get applied(){return applied},get current(){return ROSTER[index]},get index(){return index},
  enter(){open=true;index=Math.max(0,ROSTER.findIndex(e=>e.id===applied));return ROSTER[index]},
  leave(){open=false;return applied},
  toggle(){return open?(api.leave(),false):(api.enter(),true)},
  next(){if(!open)return null;index=(index+1)%ROSTER.length;return ROSTER[index]},
  prev(){if(!open)return null;index=(index-1+ROSTER.length)%ROSTER.length;return ROSTER[index]},
  confirm(){if(!open)return null;applied=ROSTER[index].id;open=false;return applied},
  revert(id){if(byId(id))applied=id;return applied}
 };
 return api;
}
/* three.js 的 GLTFLoader 以裸模組名 'three' 匯入。圖書館沒有 importmap，而且引擎可能來自
 * jsdelivr 或 unpkg 備援；為了讓 loader 與已載入的引擎是同一個模組實體，下載同版本 addon
 * 原始碼並把 'three' 與相對路徑改寫成實際網址。 */
function rewriteImports(source,replacements){
 return String(source).replace(/(\bfrom\s*)(['"])([^'"]+)\2/g,(all,head,quote,spec)=>
  Object.prototype.hasOwnProperty.call(replacements,spec)?head+quote+replacements[spec]+quote:all);
}
function addonBase(engineUrl){
 const m=/^(https:\/\/[^?#]+\/three@[0-9.]+\/)build\/three\.module\.js$/.exec(String(engineUrl));
 if(!m)throw Error('不明的 Three.js 引擎來源，無法載入人物模型');
 return m[1]+'examples/jsm/';
}
async function createGLTFLoader({engineUrl,fetchText,importModule,makeModuleUrl}){
 const base=addonBase(engineUrl);
 const text=async path=>{const t=await fetchText(base+path);if(typeof t!=='string'||!t)throw Error('addon 下載失敗：'+path);return t};
 const [bufferUtils,skeletonUtils,gltf]=await Promise.all([text('utils/BufferGeometryUtils.js'),text('utils/SkeletonUtils.js'),text('loaders/GLTFLoader.js')]);
 const three={three:engineUrl};
 const bufferUrl=makeModuleUrl(rewriteImports(bufferUtils,three));
 const skeletonUrl=makeModuleUrl(rewriteImports(skeletonUtils,three));
 const gltfUrl=makeModuleUrl(rewriteImports(gltf,{...three,'../utils/BufferGeometryUtils.js':bufferUrl,'../utils/SkeletonUtils.js':skeletonUrl}));
 const [loaderModule,decoderModule]=await Promise.all([importModule(gltfUrl),importModule(base+'libs/meshopt_decoder.module.js')]);
 const decoder=decoderModule.MeshoptDecoder;if(decoder?.ready)await decoder.ready;
 const loader=new loaderModule.GLTFLoader();loader.setMeshoptDecoder(decoder);
 return loader;
}
/* 把 glTF 場景整理成可掛在 keeper 底下的外觀：腳底貼地、身高統一、面向 +Z。 */
function fitTransform(box,targetHeight){
 const h=box.max.y-box.min.y;if(!(h>0))throw Error('人物模型沒有有效高度');
 const s=targetHeight/h;
 return {scale:s,offset:{x:-(box.min.x+box.max.x)/2*s,y:-box.min.y*s,z:-(box.min.z+box.max.z)/2*s}};
}
function creditText(entry){
 const c=entry?.credit;if(!c)return '';
 return '模型：'+c.title+'｜作者 '+c.author+'｜授權 '+c.license;
}
return Object.freeze({STORAGE_KEY,DEFAULT_ID,ROSTER,KIGAE_PATTERNS,BOOK_PATTERNS,byId,loadChoice,saveChoice,createState,
 rewriteImports,addonBase,createGLTFLoader,fitTransform,creditText});
});
