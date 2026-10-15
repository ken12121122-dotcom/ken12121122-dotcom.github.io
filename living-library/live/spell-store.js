/* Isolated P2/P5 preparation: local IndexedDB only; no scene import or transport. */
(function(root,factory){if(typeof module==='object'&&module.exports)module.exports=factory(require('./action-registry.js'),require('./spell-contract.js'));else root.LivingSpellStore=factory(root.LivingActionRegistry,root.LivingSpellContract);})(typeof self!=='undefined'?self:this,function(Registry,Contract){
 'use strict';
 const fail=code=>{const error=new Error(code);error.code=code;throw error};
 const validID=id=>typeof id==='string'&&/^[a-z][a-z0-9_.-]{0,127}$/.test(id);
 const validTime=now=>Number.isSafeInteger(now)&&now>=0&&now<=8640000000000000-300000;
 function ticket(input){
  let value;try{value=Registry.snapshotJSON(input)}catch{fail('INVALID_OUTBOX_TICKET')}
  if(!value||Object.keys(value).sort().join(',')!=='attempts,content_hash,idempotency_key,local_revision,spell_id'||!validID(value.spell_id)||!Number.isSafeInteger(value.local_revision)||value.local_revision<1||!Number.isSafeInteger(value.attempts)||value.attempts<0||!/^\w{64}$/.test(value.content_hash)||!/^[0-9a-f]{64}$/.test(value.content_hash)||value.idempotency_key!==value.spell_id+':'+value.local_revision+':'+value.content_hash)fail('INVALID_OUTBOX_TICKET');
  return value;
 }
 const same=(a,b)=>!!a&&a.spell_id===b.spell_id&&a.local_revision===b.local_revision&&a.content_hash===b.content_hash&&a.idempotency_key===b.idempotency_key;
 const freezeRecord=row=>row==null?null:Object.freeze({...row,...(row.card?{card:Registry.snapshotJSON(row.card)}:{}),...(row.payload?{payload:Registry.snapshotJSON(row.payload)}:{})});
 async function hash(card){const bytes=new TextEncoder().encode(JSON.stringify(card));const digest=await globalThis.crypto.subtle.digest('SHA-256',bytes);return [...new Uint8Array(digest)].map(x=>x.toString(16).padStart(2,'0')).join('')}
 class SpellStore{
  #db;#registry;#closed=false;
  constructor(db,registry){this.#db=db;this.#registry=registry;db.onversionchange=()=>this.close()}
  static open(registry,{database='nw-rune-creations-v1'}={}){
   if(!globalThis.indexedDB)return Promise.reject(Object.assign(new Error('STORAGE_UNAVAILABLE'),{code:'STORAGE_UNAVAILABLE'}));
   if(typeof database!=='string'||!/^nw-rune-creations-[a-z0-9-]{1,80}$/.test(database))return Promise.reject(Object.assign(new Error('INVALID_DATABASE'),{code:'INVALID_DATABASE'}));
   return new Promise((resolve,reject)=>{
    let settled=false;const request=indexedDB.open(database,1);
    const rejectCode=code=>{if(!settled){settled=true;reject(Object.assign(new Error(code),{code}))}};
    request.onupgradeneeded=()=>{const db=request.result;db.createObjectStore('spells',{keyPath:'spell_id'});db.createObjectStore('outbox',{keyPath:'spell_id'})};
    request.onerror=()=>rejectCode('STORAGE_OPEN_FAILED');request.onblocked=()=>rejectCode('STORAGE_BLOCKED');
    request.onsuccess=()=>{if(settled){request.result.close();return}settled=true;resolve(new SpellStore(request.result,registry))};
   });
  }
  close(){if(!this.#closed){this.#closed=true;this.#db.close()}}
  #read(store,key){
   if(this.#closed)fail('STORAGE_CLOSED');
   return new Promise((resolve,reject)=>{
    const tx=this.#db.transaction(store,'readonly'),request=key===undefined?tx.objectStore(store).getAll():tx.objectStore(store).get(key);let value;
    request.onsuccess=()=>{value=request.result};tx.oncomplete=()=>{try{resolve(Array.isArray(value)?Object.freeze(value.map(freezeRecord)):freezeRecord(value))}catch{reject(Object.assign(new Error('LOCAL_RECORD_INVALID'),{code:'LOCAL_RECORD_INVALID'}))}};tx.onabort=()=>reject(Object.assign(new Error('STORAGE_READ_FAILED'),{code:'STORAGE_READ_FAILED'}));
   });
  }
  get(spell_id){if(!validID(spell_id))fail('INVALID_SPELL_ID');return this.#read('spells',spell_id)}
  pending(){return this.#read('outbox')}
  async due({now=Date.now()}={}){
   if(!validTime(now))fail('INVALID_SYNC_TIME');
   return Object.freeze((await this.pending()).filter(row=>(row.next_attempt_at||0)<=now));
  }
  #settle(input,receipt,now){
   if(this.#closed)fail('STORAGE_CLOSED');
   const attempt=ticket(input);if(!validTime(now))fail('INVALID_SYNC_TIME');
   let ack;
   if(receipt!==null){
    try{ack=Registry.snapshotJSON(receipt)}catch{fail('INVALID_SYNC_RECEIPT')}
    if(!ack||Object.keys(ack).sort().join(',')!=='content_hash,idempotency_key,local_revision,remote_revision,spell_id'||typeof ack.remote_revision!=='string'||!/^[a-zA-Z0-9_.:-]{1,128}$/.test(ack.remote_revision))fail('INVALID_SYNC_RECEIPT');
    if(!same(ack,attempt))return Promise.resolve(Object.freeze({applied:false,reason:'ACK_MISMATCH'}));
   }
   return new Promise((resolve,reject)=>{
    const tx=this.#db.transaction(['spells','outbox'],'readwrite'),spells=tx.objectStore('spells'),outbox=tx.objectStore('outbox');let result,current,queued,ready=0;
    tx.onabort=()=>reject(Object.assign(new Error('OUTBOX_UPDATE_FAILED'),{code:'OUTBOX_UPDATE_FAILED'}));
    tx.oncomplete=()=>resolve(Object.freeze(result));
    const apply=()=>{
     if(++ready!==2)return;
     try{
      if(!same(queued,attempt)||queued.attempts!==attempt.attempts||current?.local_revision!==attempt.local_revision||current?.content_hash!==attempt.content_hash){result={applied:false,reason:'STALE_OUTBOX'};return}
      if(ack){
       spells.put({...current,sync_state:'remote_acknowledged',remote_revision:ack.remote_revision,remote_content_hash:ack.content_hash,acknowledged_at:new Date(now).toISOString()});outbox.delete(attempt.spell_id);
       result={applied:true,reason:'ACKNOWLEDGED'};
      }else{
       if(!Number.isSafeInteger(queued.attempts+1)){tx.abort();return}
       const next_attempt_at=now+Math.min(300000,1000*2**Math.min(queued.attempts,9));
       outbox.put({...queued,attempts:queued.attempts+1,sync_state:'retry_wait',last_error_code:'TRANSPORT_FAILED',next_attempt_at});
       result={applied:true,reason:'RETRY_SCHEDULED',next_attempt_at};
      }
     }catch{tx.abort()}
    };
    const a=spells.get(attempt.spell_id),b=outbox.get(attempt.spell_id);
    a.onsuccess=()=>{current=a.result;apply()};b.onsuccess=()=>{queued=b.result;apply()};
   });
  }
  // Receipts come only from a separately authorized caller. These methods do
  // not authenticate a remote or transmit data, and are not loaded by the scene.
  acknowledge(attempt,receipt,{now=Date.now()}={}){if(receipt==null)fail('INVALID_SYNC_RECEIPT');return this.#settle(attempt,receipt,now)}
  recordFailure(attempt,{now=Date.now()}={}){return this.#settle(attempt,null,now)}
  async save(card,{expected_revision=0}={}){
   if(this.#closed)fail('STORAGE_CLOSED');
   if(!Number.isSafeInteger(expected_revision)||expected_revision<0)fail('INVALID_REVISION');
   let snapshot;try{snapshot=Registry.snapshotJSON(card)}catch{fail('INVALID_CARD_SCHEMA')}
   Contract.validateSpell(snapshot,this.#registry);
   const content_hash=await hash(snapshot);if(this.#closed)fail('STORAGE_CLOSED');
   return new Promise((resolve,reject)=>{
    const tx=this.#db.transaction(['spells','outbox'],'readwrite'),spells=tx.objectStore('spells'),outbox=tx.objectStore('outbox');let result,code='LOCAL_SAVE_FAILED';
    tx.onabort=()=>reject(Object.assign(new Error(code),{code}));tx.oncomplete=()=>resolve(freezeRecord(result));
    const request=spells.get(snapshot.spell_id);
    request.onsuccess=()=>{
     try{
      const prior=request.result;
      // Exact retry acknowledges the durable revision without another queue entry.
      if(prior?.content_hash===content_hash){result=prior;return}
      const revision=prior?.local_revision||0;
      if(revision!==expected_revision){code='LOCAL_REVISION_CONFLICT';tx.abort();return}
      if(!Number.isSafeInteger(revision+1)){code='INVALID_REVISION';tx.abort();return}
      const updated_at=new Date().toISOString();
      result={schema:'nw.local-spell.v1',spell_id:snapshot.spell_id,local_revision:revision+1,content_hash,card:snapshot,updated_at,sync_state:'pending_sync'};
      // Both writes share one transaction: an outbox failure cannot leave an
      // apparently saved card without its synchronization intent. Later edits
      // replace this spell's pending intent; transport is deliberately absent.
      spells.put(result);outbox.put({schema:'nw.outbox.v1',spell_id:snapshot.spell_id,local_revision:revision+1,content_hash,idempotency_key:snapshot.spell_id+':'+(revision+1)+':'+content_hash,payload:snapshot,created_at:updated_at,attempts:0,sync_state:'pending_sync'});
     }catch{tx.abort()}
    };
   });
  }
 }
 return Object.freeze({SpellStore});
});
