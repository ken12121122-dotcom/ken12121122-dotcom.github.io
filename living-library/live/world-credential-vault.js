/* Private GitHub save credential vault v1.
 * Explicitly opt-in only. PAT is AES-256-GCM encrypted in IndexedDB, with a
 * password-derived PBKDF2 key; plaintext and derived key are never persisted.
 * Opening/unlocking does not grant more permissions than the original consent.
 * Password is required again after page reload. Not an Android biometric API.
 */
(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LivingWorldCredentialVault=api;
})(typeof self!=='undefined'?self:globalThis,function(){
 'use strict';
 const DB='nw.living-library.world-save-credential-v1',STORE='credentials',KEY='private-github-card-inbox';
 const SCHEMA='nw.world-save-credential-v1',REPO='ken12121122-dotcom/gen2-knowledge',BRANCH='card-inbox';
 const ROUNDS=350000,MIN_PASSWORD=12;
 const fail=code=>{const e=Error(code);e.code=code;throw e};
 const enc=new TextEncoder(),dec=new TextDecoder('utf-8',{fatal:true});
 const base64=a=>{let b='';for(const x of a)b+=String.fromCharCode(x);return btoa(b)};
 const unbase64=s=>{try{return Uint8Array.from(atob(s),x=>x.charCodeAt(0))}catch{fail('VAULT_INVALID_RECORD')}};
 function create({indexedDBImpl=globalThis.indexedDB,cryptoImpl=globalThis.crypto,origin=globalThis.location?.origin||''}={}){
  if(!indexedDBImpl||!cryptoImpl?.subtle||typeof cryptoImpl.getRandomValues!=='function')
   fail('VAULT_UNAVAILABLE');
  // Serialize remember/forget so a late PBKDF2 completion cannot re-enable
  // an authorization explicitly revoked while encryption was running.
  let revocation=0,mutation=Promise.resolve();
  function serialize(fn){const p=mutation.then(fn,fn);mutation=p.catch(()=>{});return p}
  // Authenticated additional data prevents reuse under another origin/repository.
  const bound=enc.encode([SCHEMA,origin,REPO,BRANCH].join('|'));
  async function open(){
   return new Promise((resolve,reject)=>{
    let request;
    try{request=indexedDBImpl.open(DB,1)}catch{reject(Error('VAULT_STORAGE_UNAVAILABLE'));return}
    request.onupgradeneeded=()=>{
     if(!request.result.objectStoreNames.contains(STORE))request.result.createObjectStore(STORE);
    };
    request.onerror=()=>reject(Error('VAULT_STORAGE_UNAVAILABLE'));
    request.onblocked=()=>reject(Error('VAULT_STORAGE_UNAVAILABLE'));
    request.onsuccess=()=>resolve(request.result);
   });
  }
  async function operation(kind,writeValue){
   const db=await open();
   try{
    return await new Promise((resolve,reject)=>{
     let tx,req;
     try{
      tx=db.transaction(STORE,kind==='get'?'readonly':'readwrite');
      const store=tx.objectStore(STORE);
      req=kind==='get'?store.get(KEY):kind==='put'?store.put(writeValue,KEY):store.delete(KEY);
     }catch{reject(Error('VAULT_STORAGE_UNAVAILABLE'));return}
     let result;
     req.onsuccess=()=>{result=req.result};
     req.onerror=()=>reject(Error('VAULT_STORAGE_UNAVAILABLE'));
     tx.onerror=()=>reject(Error('VAULT_STORAGE_UNAVAILABLE'));
     tx.onabort=()=>reject(Error('VAULT_STORAGE_UNAVAILABLE'));
     tx.oncomplete=()=>resolve(result);
    });
   }finally{db.close()}
  }
  function valid(record){
   return record?.schema===SCHEMA&&record.repo===REPO&&record.branch===BRANCH&&
    record.consent===true&&record.kdf==='PBKDF2-SHA256'&&record.cipher==='AES-256-GCM'&&
    record.rounds===ROUNDS&&typeof record.salt==='string'&&
    typeof record.iv==='string'&&typeof record.ciphertext==='string'&&
    /^[A-Za-z0-9+/]+={0,2}$/.test(record.ciphertext)&&record.ciphertext.length<8192;
  }
  async function inspect(){
   let entry;try{entry=await operation('get')}catch{fail('VAULT_STORAGE_UNAVAILABLE')}
   if(!entry)return {saved:false};
   if(!valid(entry))fail('VAULT_INVALID_RECORD');
   return {saved:true}; // Never expose ciphertext, private token or password.
  }
  async function derive(password,salt){
   const raw=await cryptoImpl.subtle.importKey('raw',enc.encode(password),
    'PBKDF2',false,['deriveKey']);
   return cryptoImpl.subtle.deriveKey({name:'PBKDF2',salt,iterations:ROUNDS,hash:'SHA-256'},
    raw,{name:'AES-GCM',length:256},false,['encrypt','decrypt']);
  }
  async function remember(token,password,isAuthorized=()=>true){
   const lease=revocation;
   if(typeof token!=='string'||token.length<10||token.length>1024)
    fail('VAULT_TOKEN_INVALID');
   if(typeof password!=='string'||password.length<MIN_PASSWORD||password.length>256)
    fail('VAULT_PASSWORD_WEAK');
   const salt=cryptoImpl.getRandomValues(new Uint8Array(16));
   const iv=cryptoImpl.getRandomValues(new Uint8Array(12));
   let ciphertext;
   try{
    const key=await derive(password,salt);
    ciphertext=new Uint8Array(await cryptoImpl.subtle.encrypt(
     {name:'AES-GCM',iv,additionalData:bound},key,
     enc.encode(JSON.stringify({token,repo:REPO,branch:BRANCH,consent:true}))));
   }catch{fail('VAULT_ENCRYPT_FAILED')}
   const entry={schema:SCHEMA,repo:REPO,branch:BRANCH,consent:true,
    kdf:'PBKDF2-SHA256',cipher:'AES-256-GCM',rounds:ROUNDS,
    salt:base64(salt),iv:base64(iv),ciphertext:base64(ciphertext)};
   return serialize(async()=>{
    if(lease!==revocation||!isAuthorized())fail('VAULT_AUTH_REVOKED');
    try{await operation('put',entry)}catch{fail('VAULT_STORAGE_UNAVAILABLE')}
    return {saved:true};
   });
  }
  async function unlock(password){
   if(typeof password!=='string'||!password||password.length>256)
    fail('VAULT_PASSWORD_REQUIRED');
   let record;try{record=await operation('get')}catch{fail('VAULT_STORAGE_UNAVAILABLE')}
   if(!record)fail('VAULT_NOT_SAVED');
   if(!valid(record))fail('VAULT_INVALID_RECORD');
   let salt,iv,ct;
   try{salt=unbase64(record.salt);iv=unbase64(record.iv);ct=unbase64(record.ciphertext)}
   catch{fail('VAULT_INVALID_RECORD')}
   if(salt.length!==16||iv.length!==12||ct.length<16||ct.length>4096)
    fail('VAULT_INVALID_RECORD');
   let contents;
   try{
    const key=await derive(password,salt);
    const plaintext=await cryptoImpl.subtle.decrypt(
     {name:'AES-GCM',iv,additionalData:bound},key,ct);
    contents=JSON.parse(dec.decode(plaintext));
   }catch{fail('VAULT_UNLOCK_FAILED')}
   if(contents?.consent!==true||contents.repo!==REPO||contents.branch!==BRANCH||
      typeof contents.token!=='string'||contents.token.length<10||contents.token.length>1024)
    fail('VAULT_INVALID_RECORD');
   return contents.token; // Caller must forward directly into session-only sync module.
  }
  async function forget(){
   revocation++;
   return serialize(async()=>{
    try{await operation('delete')}catch{fail('VAULT_STORAGE_UNAVAILABLE')}
    return {saved:false};
   });
  }
  return Object.freeze({inspect,remember,unlock,forget});
 }
 return Object.freeze({DB,STORE,SCHEMA,MIN_PASSWORD,create});
});
