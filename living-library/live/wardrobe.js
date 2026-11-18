/* 換衣世界（Wardrobe）— 可替換的圖書館人物外觀。
 * 純資料／狀態／載入器組裝；不改契約、不改掃描語義、不新增玩家卡。
 * 人物仍是同一個受保護的 keeper 根節點（scene.keeper），外觀只是掛在它底下的子物件。 */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingWardrobe=api;})(typeof self!=='undefined'?self:this,function(){
'use strict';
const STORAGE_KEY='nw-avatar-wardrobe-v1';
const DEFAULT_ID='keeper.default';
// 換衣咒語：百面相（ひゃくめんそう／hyakumensō）。辨識引擎可能回傳漢字、假名或羅馬拼音。
const SPELL_PATTERNS=Object.freeze(['百面相','ひゃくめんそう','ヒャクメンソウ','hyakumensou','hyakumenso']);
// 日語辨識器常把英語 Book 寫成片假名；原生語音可能回傳英文或中文音譯。
const BOOK_PATTERNS=Object.freeze(['book','ブック','ぶっく','布克']);
const ROSTER=Object.freeze([
 Object.freeze({id:DEFAULT_ID,name:'守書人',subtitle:'原本的圖書館管理員',kind:'builtin'}),
 Object.freeze({id:'anna.ipati.v1',name:'安娜 Anna',subtitle:'走路動畫・102 骨',kind:'pack',
  manifest:'assets/characters/packs/anna.ipati.v1/manifest.json'})
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
/* three.js 的 addon 以裸模組名 'three' 匯入，而且彼此用相對路徑互相引用。圖書館沒有 importmap，
 * 引擎也可能來自 jsdelivr 或 unpkg 備援；為了讓 addon 與已載入的引擎是同一個模組實體，下載同版本
 * addon 原始碼，把 'three' 改成實際引擎網址、相對路徑遞迴改成已改寫模組的 blob 網址。 */
const IMPORT_RE=/(\bfrom\s*|\bimport\s*)(['"])([^'"]+)\2/g;
function rewriteImports(source,replacements){
 return String(source).replace(IMPORT_RE,(all,head,quote,spec)=>
  Object.prototype.hasOwnProperty.call(replacements,spec)?head+quote+replacements[spec]+quote:all);
}
function relativeImports(source){const out=new Set();for(const m of String(source).matchAll(IMPORT_RE))if(/^\.\.?\//.test(m[3]))out.add(m[3]);return [...out]}
function addonBase(engineUrl){
 const m=/^(https:\/\/[^?#]+\/three@[0-9.]+\/)build\/three\.module\.js$/.exec(String(engineUrl));
 if(!m)throw Error('不明的 Three.js 引擎來源，無法載入人物模型');
 return m[1]+'examples/jsm/';
}
function createAddonImporter({engineUrl,fetchText,importModule,makeModuleUrl}){
 const base=addonBase(engineUrl),urls=new Map();
 function moduleUrl(path,trail=[]){
  if(trail.includes(path))throw Error('addon 循環引用：'+trail.concat(path).join(' → '));
  if(!urls.has(path))urls.set(path,(async()=>{
   const source=await fetchText(base+path);if(typeof source!=='string'||!source)throw Error('addon 下載失敗：'+path);
   const map={three:engineUrl};
   for(const spec of relativeImports(source))map[spec]=await moduleUrl(new URL(spec,'https://x/'+path).pathname.slice(1),trail.concat(path));
   // A blob: module has no usable import.meta.url for relative lookups (KTX2Loader finds its WASM that way).
   return makeModuleUrl(rewriteImports(source,map).replace(/\bimport\.meta\.url\b/g,JSON.stringify(base+path)));
  })());
  return urls.get(path);
 }
 return {base,moduleUrl,load:async path=>importModule(await moduleUrl(path))};
}
async function createGLTFLoader({engineUrl,fetchText,importModule,makeModuleUrl,renderer}){
 const addons=createAddonImporter({engineUrl,fetchText,importModule,makeModuleUrl});
 const [gltfModule,ktx2Module,decoderModule]=await Promise.all([addons.load('loaders/GLTFLoader.js'),addons.load('loaders/KTX2Loader.js'),
  importModule(addons.base+'libs/meshopt_decoder.module.js')]);
 const decoder=decoderModule.MeshoptDecoder;if(decoder?.ready)await decoder.ready;
 const loader=new gltfModule.GLTFLoader();loader.setMeshoptDecoder(decoder);
 // GPU-compressed textures (KTX2/Basis) stay compressed in video memory on the phone.
 const ktx2=new ktx2Module.KTX2Loader().setTranscoderPath(addons.base+'libs/basis/');
 if(renderer)ktx2.detectSupport(renderer);
 loader.setKTX2Loader(ktx2);
 return loader;
}
/* 把 glTF 場景整理成可掛在 keeper 底下的外觀：腳底貼地、身高統一、面向 +Z。 */
function fitTransform(box,targetHeight){
 const h=box.max.y-box.min.y;if(!(h>0))throw Error('人物模型沒有有效高度');
 const s=targetHeight/h;
 return {scale:s,offset:{x:-(box.min.x+box.max.x)/2*s,y:-box.min.y*s,z:-(box.min.z+box.max.z)/2*s}};
}
return Object.freeze({STORAGE_KEY,DEFAULT_ID,ROSTER,SPELL_PATTERNS,BOOK_PATTERNS,byId,loadChoice,saveChoice,createState,
 rewriteImports,relativeImports,addonBase,createAddonImporter,createGLTFLoader,fitTransform});
});
