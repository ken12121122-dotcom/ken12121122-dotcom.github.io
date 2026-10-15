/* Opt-in, read-only ObjectCraft backup/recovery inspection. No import, migration,
 * network, scene hook or storage writes. Backups contain private player data:
 * keep on the player's device; checksum detects corruption, not authenticity. */
(function(root,factory){
 const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LivingObjectBackup=api;
})(typeof self!=='undefined'?self:this,function(){
 'use strict';
 const KEY='living-library-player-objects-v1',SCHEMA='nw.object-craft-backup.v1',MAX_BYTES=8*1024*1024;
 function fail(code){const error=Error(code);error.code=code;throw error}
 function bounded(text){if(typeof text!=='string'||new TextEncoder().encode(text).length>MAX_BYTES)fail('BACKUP_SIZE_OR_TYPE')}
 function parse(text){try{return JSON.parse(text)}catch(_){fail('BACKUP_INVALID_JSON')}}
 function freeze(value){if(value&&typeof value==='object'){for(const item of Object.values(value))freeze(item);Object.freeze(value)}return value}
 function summarize(raw){
  if(raw===null)return {present:false,total:0,cards:0,books:0,recycled:0,other:0,entries:[]};
  bounded(raw);const state=parse(raw);
  if(!state||state.version!==1||!Array.isArray(state.objects))fail('BACKUP_SOURCE_VERSION');
  const ids=new Set(),summary={present:true,total:state.objects.length,cards:0,books:0,recycled:0,other:0,entries:[]};
  for(const item of state.objects){
   if(!item||typeof item.id!=='string'||!item.id||typeof item.objectType!=='string'||
      !Number.isSafeInteger(item.revision)||item.revision<1)fail('BACKUP_INVALID_OBJECT');
   if(ids.has(item.id))fail('BACKUP_DUPLICATE_ID');ids.add(item.id);
   const card=item.objectType==='card'&&item.templateId==='blank-card-v1'&&item.id.startsWith('local.card.');
   const book=item.objectType==='book'&&item.templateId==='blank-book-v1'&&item.id.startsWith('local.book.');
   if(card){summary.cards++;if(item.status==='recycled')summary.recycled++}
   else if(book)summary.books++;else summary.other++;
   summary.entries.push({id:item.id,objectType:item.objectType,revision:item.revision,
    status:typeof item.status==='string'?item.status:null});
  }
  return summary;
 }
 async function digest(raw){
  if(!globalThis.crypto?.subtle)fail('BACKUP_CRYPTO_UNAVAILABLE');
  const bytes=new TextEncoder().encode(raw===null?'nw.object-craft.absent.v1':raw);
  return Array.from(new Uint8Array(await globalThis.crypto.subtle.digest('SHA-256',bytes)),x=>x.toString(16).padStart(2,'0')).join('');
 }
 async function exportBackup(storage){
  let raw;try{raw=storage.getItem(KEY)}catch(_){fail('BACKUP_STORAGE_UNAVAILABLE')}
  summarize(raw);
  return JSON.stringify({schema:SCHEMA,source_key:KEY,source_text:raw,sha256:await digest(raw)});
 }
 async function inspectBackup(text){
  // JSON escaping can expand the source; bound the serialized envelope separately.
  if(typeof text!=='string'||new TextEncoder().encode(text).length>MAX_BYTES*6+1024)fail('BACKUP_SIZE_OR_TYPE');
  const envelope=parse(text);
  if(!envelope||Array.isArray(envelope)||Object.keys(envelope).sort().join(',')!=='schema,sha256,source_key,source_text'||
     envelope.schema!==SCHEMA||envelope.source_key!==KEY||
     (envelope.source_text!==null&&typeof envelope.source_text!=='string')||
     typeof envelope.sha256!=='string'||!/^[a-f0-9]{64}$/.test(envelope.sha256))fail('BACKUP_ENVELOPE_INVALID');
  const summary=summarize(envelope.source_text);
  if(await digest(envelope.source_text)!==envelope.sha256)fail('BACKUP_CHECKSUM_MISMATCH');
  // Exact original bytes retained; inspection never restores, executes or enqueues.
  return freeze({schema:SCHEMA,source_key:KEY,source_text:envelope.source_text,sha256:envelope.sha256,summary});
 }
 return Object.freeze({KEY,SCHEMA,MAX_BYTES,exportBackup,inspectBackup});
});
