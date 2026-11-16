/* 角色包（Character Pack）— 下載一次、存在手機、離線可用、可更新、可刪除。
 * 格式 nw-character-pack-v1：manifest.json + model(.glb) + thumbnail(.webp)。
 * 這一層只負責「檔案」：驗證清單、串流下載、SHA-256 校驗、存取 Cache Storage。
 * 不碰契約、掃描、卡片或玩家存檔；以後 App 原生下載也沿用同一份 manifest。 */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingCharacterPacks=api;})(typeof self!=='undefined'?self:this,function(){
'use strict';
const SCHEMA='nw-character-pack-v1';
const CACHE_NAME='nw-character-packs-v1';
const INDEX_KEY='nw-character-packs-installed-v1';
const MAX_MODEL_BYTES=80*1024*1024;
const HEX64=/^[0-9a-f]{64}$/;
const ID=/^[a-z0-9][a-z0-9.-]{1,63}$/;
const FILE=/^[a-z0-9][a-z0-9._-]{0,63}$/i;
const ROLES={model:'model/gltf-binary',thumbnail:'image/webp'};
function fail(msg){throw Error('角色包清單無效：'+msg)}
/* Strict on purpose: a manifest is fetched from the network and drives what gets cached. */
function validateManifest(m){
 if(!m||typeof m!=='object')fail('不是物件');
 if(m.schema!==SCHEMA)fail('schema 必須是 '+SCHEMA);
 if(!ID.test(String(m.id)))fail('id');
 if(!Number.isInteger(m.version)||m.version<1)fail('version');
 if(typeof m.name!=='string'||!m.name.trim())fail('name');
 const c=m.credit;if(!c||!c.title||!c.author||!c.license||!/^https:\/\//.test(String(c.url)))fail('credit（作者標示必填）');
 const d=m.display;if(!d||!(d.height>0&&d.height<10)||!(d.idleTime>=0))fail('display');
 if(!Array.isArray(m.files))fail('files');
 const seen=new Set();
 for(const f of m.files){
  if(!f||!Object.prototype.hasOwnProperty.call(ROLES,f.role))fail('file role');
  if(seen.has(f.role))fail('重複的 '+f.role);seen.add(f.role);
  if(!FILE.test(String(f.path))||f.path.includes('..'))fail('file path '+f.path);
  if(f.type!==ROLES[f.role])fail('file type '+f.path);
  if(!Number.isInteger(f.bytes)||f.bytes<=0||(f.role==='model'&&f.bytes>MAX_MODEL_BYTES))fail('file bytes '+f.path);
  if(!HEX64.test(String(f.sha256)))fail('file sha256 '+f.path);
 }
 if(!seen.has('model'))fail('缺少 model');
 return m;
}
function fileOf(m,role){return m.files.find(f=>f.role===role)||null}
function hex(buffer){return Array.from(new Uint8Array(buffer),b=>b.toString(16).padStart(2,'0')).join('')}
function formatMB(bytes){return (bytes/1048576).toFixed(bytes<10485760?1:0)+' MB'}
/* Cache key is the file URL plus its hash: a new version never collides with the old one. */
function keyFor(baseUrl,file){const u=new URL(file.path,baseUrl);u.search='sha256='+file.sha256;return u.href}
function readIndex(storage){try{const v=JSON.parse(storage?.getItem(INDEX_KEY)||'{}');return v&&typeof v==='object'&&!Array.isArray(v)?v:{}}catch{return {}}}
function writeIndex(storage,index){try{storage?.setItem(INDEX_KEY,JSON.stringify(index));return true}catch{return false}}

function createStore({fetch:fetchFn,caches:cacheStorage,crypto:cryptoApi,storage,persist}={}){
 if(typeof fetchFn!=='function')throw Error('需要 fetch');
 if(!cryptoApi?.subtle)throw Error('需要 crypto.subtle 才能校驗角色包');
 const memory=new Map();// fallback when Cache Storage is unavailable: session only
 const durable=!!cacheStorage?.open;
 const jobs=new Map();
 async function cache(){return durable?cacheStorage.open(CACHE_NAME):null}
 async function get(key){if(!durable)return memory.get(key)||null;const r=await (await cache()).match(key);return r?r.blob():null}
 async function put(key,blob){if(!durable){memory.set(key,blob);return}await (await cache()).put(key,new Response(blob,{headers:{'Content-Type':blob.type||'application/octet-stream','Content-Length':String(blob.size)}}))}
 async function drop(key){if(!durable){memory.delete(key);return}await (await cache()).delete(key)}
 async function fetchManifest(url){
  const r=await fetchFn(url,{cache:'no-cache'});if(!r.ok)throw Error('清單下載失敗 HTTP '+r.status);
  const m=validateManifest(await r.json());return {manifest:m,baseUrl:new URL('.',r.url||url).href};
 }
 async function status(manifest,baseUrl){
  const entry=readIndex(storage)[manifest.id];
  const model=fileOf(manifest,'model');
  if(entry&&entry.version===manifest.version&&entry.sha256===model.sha256&&await get(keyFor(baseUrl,model)))return 'installed';
  if(entry&&await get(entry.key||''))return 'update';
  return 'missing';
 }
 async function download(file,baseUrl,onProgress,signal){
  const r=await fetchFn(new URL(file.path,baseUrl).href,{cache:'no-store',signal});
  if(!r.ok)throw Error(file.path+' 下載失敗 HTTP '+r.status);
  const chunks=[];let received=0;
  if(r.body?.getReader){const reader=r.body.getReader();
   for(;;){const {done,value}=await reader.read();if(done)break;chunks.push(value);received+=value.byteLength;
    if(received>file.bytes)throw Error(file.path+' 大小超過清單宣告');onProgress?.(received,file.bytes)}
  }else{const b=new Uint8Array(await r.arrayBuffer());chunks.push(b);received=b.byteLength;onProgress?.(received,file.bytes)}
  if(received!==file.bytes)throw Error(file.path+' 大小不符：'+received+'／'+file.bytes);
  const bytes=new Uint8Array(received);let at=0;for(const c of chunks){bytes.set(c,at);at+=c.byteLength}
  const digest=hex(await cryptoApi.subtle.digest('SHA-256',bytes));
  if(digest!==file.sha256)throw Error(file.path+' SHA-256 不符，已拒絕');
  return new Blob([bytes],{type:file.type});
 }
 /* All files are verified before anything is written; a failed download leaves the old version intact. */
 function install(manifest,baseUrl,{onProgress,signal}={}){
  validateManifest(manifest);
  if(jobs.has(manifest.id))return jobs.get(manifest.id);
  const job=(async()=>{
   const total=manifest.files.reduce((n,f)=>n+f.bytes,0);let done=0;const blobs=[];
   for(const f of manifest.files){blobs.push([f,await download(f,baseUrl,(got)=>onProgress?.(done+got,total),signal)]);done+=f.bytes}
   for(const [f,b] of blobs)await put(keyFor(baseUrl,f),b);
   const index=readIndex(storage),old=index[manifest.id],model=fileOf(manifest,'model');
   index[manifest.id]={version:manifest.version,sha256:model.sha256,key:keyFor(baseUrl,model),
    files:manifest.files.map(f=>keyFor(baseUrl,f)),installedAt:new Date().toISOString(),durable};
   writeIndex(storage,index);
   if(old)for(const k of old.files||[])if(!index[manifest.id].files.includes(k))await drop(k).catch(()=>{});
   try{await persist?.()}catch{}
   return {durable};
  })();
  jobs.set(manifest.id,job);job.finally(()=>jobs.delete(manifest.id)).catch(()=>{});
  return job;
 }
 /* Opening never touches the network: an installed pack works offline. */
 async function open(manifest,baseUrl,role='model'){
  const file=fileOf(manifest,role),blob=await get(keyFor(baseUrl,file));
  if(!blob)throw Error('角色包尚未下載：'+manifest.id);
  return blob.type===file.type?blob:new Blob([blob],{type:file.type});
 }
 async function remove(id){
  const index=readIndex(storage),entry=index[id];if(!entry)return false;
  for(const k of entry.files||[])await drop(k).catch(()=>{});
  delete index[id];writeIndex(storage,index);return true;
 }
 return {durable,fetchManifest,status,install,open,remove,installed:()=>readIndex(storage),busy:id=>jobs.has(id)};
}
return Object.freeze({SCHEMA,CACHE_NAME,INDEX_KEY,MAX_MODEL_BYTES,validateManifest,fileOf,keyFor,formatMB,createStore});
});
