/* P2/P5 isolated preparation. Explicit creator capture only; NOT loaded by 3D scene.
 * ObjectCraft stays authoritative in localStorage. No migration, remote transport,
 * deletion inference, acknowledgement or credential authority is provided here. */
(function(root,factory){
 if(typeof module==='object'&&module.exports)module.exports=factory(require('./object-backup.js'));
 else root.LivingObjectOutbox=factory(root.LivingObjectBackup);
})(typeof self!=='undefined'?self:this,function(Backup){
 'use strict';
 const error=code=>Object.assign(Error(code),{code});
 const fail=code=>{throw error(code)};
 async function hash(text){
  const bytes=new TextEncoder().encode(text);
  return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes)),n=>n.toString(16).padStart(2,'0')).join('');
 }
 async function snapshot(storage){
  const checked=await Backup.inspectBackup(await Backup.exportBackup(storage));
  const objects=checked.source_text===null?[]:JSON.parse(checked.source_text).objects;
  const rows=await Promise.all(objects.map(async object=>{
   const card=object.objectType==='card'&&object.templateId==='blank-card-v1'&&object.id.startsWith('local.card.');
   const book=object.objectType==='book'&&object.templateId==='blank-book-v1'&&object.id.startsWith('local.book.');
   if((!card&&!book)||!/^[a-z][a-z0-9_.-]{0,127}$/.test(object.id))fail('OBJECT_OUTBOX_UNSUPPORTED_OBJECT');
   // Keep the entire object, including recycled/restore fields and future fields.
   // JSON remains inert text; this is not nw.spell.v1 and grants no execution.
   const payload_text=JSON.stringify(object),content_hash=await hash(payload_text);
   return Object.freeze({schema:'nw.object-outbox.v1',object_id:object.id,
    local_revision:object.revision,content_hash,
    idempotency_key:object.id+':'+object.revision+':'+content_hash,
    payload_text,source_hash:checked.sha256,sync_state:'pending_authorization'});
  }));
  let latest;try{latest=storage.getItem(Backup.KEY)}catch{fail('OBJECT_OUTBOX_SOURCE_UNAVAILABLE')}
  if(latest!==checked.source_text)fail('OBJECT_OUTBOX_SOURCE_CHANGED');
  return {rows,source_text:checked.source_text};
 }
 class ObjectOutbox{
  #db;#closed=false;
  constructor(db){this.#db=db;db.onversionchange=()=>this.close()}
  static open({database='nw-object-outbox-v1'}={}){
   if(!globalThis.indexedDB)return Promise.reject(error('OBJECT_OUTBOX_UNAVAILABLE'));
   if(typeof database!=='string'||!/^nw-object-outbox-[a-z0-9-]{1,80}$/.test(database))return Promise.reject(error('OBJECT_OUTBOX_DATABASE'));
   return new Promise((resolve,reject)=>{
    let settled=false;const request=indexedDB.open(database,1);
    const stop=code=>{if(!settled){settled=true;reject(error(code))}};
    request.onupgradeneeded=()=>{request.result.createObjectStore('versions',{keyPath:'object_id'});request.result.createObjectStore('pending',{keyPath:'object_id'})};
    request.onerror=()=>stop('OBJECT_OUTBOX_OPEN_FAILED');request.onblocked=()=>stop('OBJECT_OUTBOX_BLOCKED');
    request.onsuccess=()=>{if(settled){request.result.close();return}settled=true;resolve(new ObjectOutbox(request.result))};
   });
  }
  close(){if(!this.#closed){this.#closed=true;this.#db.close()}}
  pending(){
   if(this.#closed)fail('OBJECT_OUTBOX_CLOSED');
   return new Promise((resolve,reject)=>{
    const tx=this.#db.transaction('pending','readonly'),request=tx.objectStore('pending').getAll();let rows;
    request.onsuccess=()=>{rows=request.result};tx.oncomplete=()=>resolve(Object.freeze(rows.map(Object.freeze)));
    tx.onabort=()=>reject(error('OBJECT_OUTBOX_READ_FAILED'));
   });
  }
  async capture(storage){
   if(this.#closed)fail('OBJECT_OUTBOX_CLOSED');
   const capture=await snapshot(storage);if(this.#closed)fail('OBJECT_OUTBOX_CLOSED');
   return new Promise((resolve,reject)=>{
    const tx=this.#db.transaction(['versions','pending'],'readwrite'),versions=tx.objectStore('versions'),pending=tx.objectStore('pending');
    let code='OBJECT_OUTBOX_WRITE_FAILED',result;
    tx.onabort=()=>reject(error(code));tx.oncomplete=()=>resolve(Object.freeze(result));
    const request=versions.getAll();
    request.onsuccess=()=>{
     try{
      if(storage.getItem(Backup.KEY)!==capture.source_text){code='OBJECT_OUTBOX_SOURCE_CHANGED';tx.abort();return}
      const prior=new Map(request.result.map(row=>[row.object_id,row]));let queued=0,unchanged=0;
      // Validate the whole batch before writing. Read/write transactions serialize
      // competing connections, so an older/equal-revision divergent snapshot loses.
      for(const row of capture.rows){
       const old=prior.get(row.object_id);
       if(old&&old.local_revision>row.local_revision){code='OBJECT_OUTBOX_STALE_REVISION';tx.abort();return}
       if(old?.local_revision===row.local_revision&&old.content_hash!==row.content_hash){code='OBJECT_OUTBOX_REVISION_CONFLICT';tx.abort();return}
      }
      for(const row of capture.rows){
       const old=prior.get(row.object_id);
       if(old?.local_revision===row.local_revision){unchanged++;continue}
       versions.put({object_id:row.object_id,local_revision:row.local_revision,content_hash:row.content_hash});pending.put(row);queued++;
      }
      // Missing IDs are NOT deletion requests; neither absent storage nor an old
      // partial snapshot may clear existing intents. Recycle is an explicit state.
      result={result:'LOCAL_INTENTS_CAPTURED',queued,unchanged};
     }catch{tx.abort()}
    };
   });
  }
 }
 return Object.freeze({ObjectOutbox});
});
