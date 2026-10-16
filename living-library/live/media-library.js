/* Future shared local picture library. Opt-in API only: no UI hookup, card migration,
 * cloud writes, GEN2 access, permission changes, or automatic deletion.
 * Store immutable, already-compressed raster images by their content hash. */
(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 else root.NWMediaLibrary=api;
})(typeof self!=='undefined'?self:this,function(){
 'use strict';
 const DB_NAME='nw-media-library-v1', DB_VERSION=1;
 const META='assets', BINARY='binary', SCHEMA='nw.media.asset.v1';
 const MAX_BYTES=128*1024, MAX_EDGE=2048;
 const MIME_TYPES=Object.freeze(['image/jpeg','image/png','image/webp']);
 const ID_PATTERN=/^local\.media\.sha256\.[a-f0-9]{64}$/;
 const error=code=>Object.assign(new Error(code),{code});
 const hex=bytes=>Array.from(new Uint8Array(bytes),x=>x.toString(16).padStart(2,'0')).join('');
 function validate(input){
  if(!input||typeof input!=='object'||!input.blob||typeof input.blob.arrayBuffer!=='function'||
   !Number.isInteger(input.width)||!Number.isInteger(input.height)||
   input.width<1||input.height<1||input.width>MAX_EDGE||input.height>MAX_EDGE||
   !MIME_TYPES.includes(input.blob.type))throw error('MEDIA_INVALID_IMAGE');
  if(input.blob.size<1||input.blob.size>MAX_BYTES)throw error('MEDIA_IMAGE_TOO_LARGE');
  return {blob:input.blob,width:input.width,height:input.height};
 }
 function checkId(id){if(typeof id!=='string'||!ID_PATTERN.test(id))throw error('MEDIA_INVALID_ID');return id}
 async function open({indexedDB=globalThis.indexedDB,crypto=globalThis.crypto,clock=()=>new Date().toISOString()}={}){
  if(!indexedDB||typeof indexedDB.open!=='function')throw error('MEDIA_DB_UNAVAILABLE');
  if(!crypto?.subtle||typeof crypto.subtle.digest!=='function')throw error('MEDIA_CRYPTO_UNAVAILABLE');
  if(typeof clock!=='function')throw error('MEDIA_INVALID_CLOCK');
  const db=await new Promise((resolve,reject)=>{
   let settled=false;
   const rejectOnce=()=>{if(!settled){settled=true;reject(error('MEDIA_DB_OPEN_FAILED'))}};
   let request;
   try{request=indexedDB.open(DB_NAME,DB_VERSION)}catch(_){rejectOnce();return}
   request.onupgradeneeded=()=>{
    const database=request.result;
    if(!database.objectStoreNames.contains(META))database.createObjectStore(META,{keyPath:'asset_id'});
    if(!database.objectStoreNames.contains(BINARY))database.createObjectStore(BINARY,{keyPath:'asset_id'});
   };
   request.onsuccess=()=>{
    if(settled){request.result.close();return}
    const database=request.result;
    if(!database.objectStoreNames.contains(META)||!database.objectStoreNames.contains(BINARY)){
     database.close();rejectOnce();return;
    }
    settled=true;resolve(database);
   };
   request.onerror=rejectOnce;
   request.onblocked=rejectOnce;
  });
  let closed=false;
  db.onversionchange=()=>{closed=true;db.close()};
  // Always resolve from transaction completion, not an intermediate request success.
  function transaction(mode,run){
   if(closed)throw error('MEDIA_LIBRARY_CLOSED');
   return new Promise((resolve,reject)=>{
    let tx,output,reason='MEDIA_STORAGE_ERROR';
    try{tx=db.transaction([META,BINARY],mode)}catch(_){reject(error('MEDIA_STORAGE_ERROR'));return}
    tx.oncomplete=()=>resolve(output);
    tx.onerror=()=>reject(error(reason));
    tx.onabort=()=>reject(error(reason));
    try{run(tx.objectStore(META),tx.objectStore(BINARY),value=>{output=value},code=>{
     reason=code;tx.abort();
    })}catch(_){reason='MEDIA_STORAGE_ERROR';try{tx.abort()}catch{reject(error(reason))}}
   });
  }
  async function putProcessed(input){
   const {blob,width,height}=validate(input);
   const digest=hex(await crypto.subtle.digest('SHA-256',await blob.arrayBuffer()));
   const asset_id='local.media.sha256.'+digest;
   return transaction('readwrite',(meta,binary,set,abort)=>{
    const check=meta.get(asset_id);
    check.onsuccess=()=>{
     if(check.result){set({asset_id,reused:true,metadata:check.result});return}
     let created_at;
     try{created_at=clock();if(typeof created_at!=='string'||!created_at.trim())throw Error('bad clock')}
     catch(_){abort('MEDIA_INVALID_CLOCK');return}
     const metadata={schema:SCHEMA,asset_id,sha256:digest,media_type:blob.type,
      byte_length:blob.size,width,height,created_at};
     meta.add(metadata);binary.add({asset_id,blob});
     set({asset_id,reused:false,metadata});
    };
   });
  }
  function getMetadata(asset_id){
   checkId(asset_id);
   return transaction('readonly',(meta,_binary,set)=>{
    const req=meta.get(asset_id);req.onsuccess=()=>set(req.result||null);
   });
  }
  function getBlob(asset_id){
   checkId(asset_id);
   return transaction('readonly',(meta,binary,set,abort)=>{
    const req=meta.get(asset_id);
    req.onsuccess=()=>{
     if(!req.result){set(null);return}
     const data=binary.get(asset_id);
     data.onsuccess=()=>{
      if(!data.result||!data.result.blob){abort('MEDIA_ASSET_INCOMPLETE');return}
      set(data.result.blob);
     };
    };
   });
  }
  function listMetadata(){
   return transaction('readonly',(meta,_binary,set)=>{
    const req=meta.getAll();req.onsuccess=()=>set(req.result||[]);
   });
  }
  function stats(){
   return transaction('readonly',(meta,_binary,set)=>{
    let count=0,total_bytes=0;
    const req=meta.openCursor();
    req.onsuccess=()=>{
     const cursor=req.result;
     if(!cursor){set({count,total_bytes});return}
     count++;total_bytes+=cursor.value.byte_length||0;cursor.continue();
    };
   });
  }
  function close(){if(!closed){closed=true;db.close()}}
  return Object.freeze({putProcessed,getMetadata,getBlob,listMetadata,stats,close});
 }
 return Object.freeze({SCHEMA,DB_NAME,DB_VERSION,MAX_BYTES,MAX_EDGE,MIME_TYPES,validate,checkId,open});
});
