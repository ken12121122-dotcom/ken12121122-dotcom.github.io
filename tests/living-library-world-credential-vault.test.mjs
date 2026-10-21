import test from 'node:test';
import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const Vault=require('../living-library/live/world-credential-vault.js');
function storage(){
 const rows=new Map();
 const indexedDBImpl={
  open(){
   const request={result:null,onsuccess:null,onerror:null,onblocked:null,onupgradeneeded:null};
   queueMicrotask(()=>{
    const db={
     objectStoreNames:{contains:name=>name===Vault.STORE},
     createObjectStore(){},
     close(){},
     transaction(){
      const tx={oncomplete:null,onerror:null,onabort:null,
       objectStore(){return {
        get:key=>schedule(()=>rows.get(key),tx),
        put:(value,key)=>schedule(()=>{rows.set(key,structuredClone(value));return key},tx),
        delete:key=>schedule(()=>rows.delete(key),tx)
       }}};
      return tx;
     }};
    request.result=db;request.onsuccess?.();
   });return request;
  }
 };
 function schedule(fn,tx){
  const req={onsuccess:null,onerror:null,result:undefined};
  queueMicrotask(()=>{try{req.result=fn();req.onsuccess?.();
   queueMicrotask(()=>tx.oncomplete?.())}catch(e){req.onerror?.();tx.onerror?.()}});
  return req;
 }
 return {indexedDBImpl,rows};
}
const opts=s=>({indexedDBImpl:s.indexedDBImpl,cryptoImpl:webcrypto,origin:'https://ken12121122-dotcom.github.io'});
const secret='github_pat_test_ONLY_fake_0123456789';
test('the GitHub token is AES-GCM ciphertext only, unlock with 12+ character local password',async()=>{
 const s=storage(),v=Vault.create(opts(s));
 assert.deepEqual(await v.inspect(),{saved:false});
 await assert.rejects(v.remember(secret,'short'),/VAULT_PASSWORD_WEAK/);
 assert.deepEqual(await v.remember(secret,'safe local password 123'),{saved:true});
 assert.deepEqual(await v.inspect(),{saved:true});
 const row=s.rows.values().next().value;
 assert.equal(row.schema,Vault.SCHEMA);
 assert.equal(row.consent,true);
 assert.equal(row.repo,'ken12121122-dotcom/gen2-knowledge');
 assert.equal(row.rounds,350000);
 assert.equal(row.cipher,'AES-256-GCM');
 assert.ok(!JSON.stringify(row).includes(secret));
 assert.ok(!JSON.stringify(row).includes('safe local password 123'));
 await assert.rejects(v.unlock('wrong-password'),/VAULT_UNLOCK_FAILED/);
 assert.equal(await v.unlock('safe local password 123'),secret);
 assert.equal((await v.inspect()).saved,true);
 await v.forget();assert.equal((await v.inspect()).saved,false);
 await assert.rejects(v.unlock('safe local password 123'),/VAULT_NOT_SAVED/);
});
test('encrypted vault is origin-bound and damaged records do not disclose token',async()=>{
 const s=storage(),v=Vault.create(opts(s));await v.remember(secret,'special password 987');
 const foreign=Vault.create({...opts(s),origin:'https://another-domain.example'});
 await assert.rejects(foreign.unlock('special password 987'),/VAULT_UNLOCK_FAILED/);
 const row=s.rows.values().next().value;
 s.rows.set(Vault.STORE==='credentials'?'private-github-card-inbox':'private-github-card-inbox',
  {...row,repo:'an-attacker/repository'});
 await assert.rejects(v.unlock('special password 987'),/VAULT_INVALID_RECORD/);
 assert.equal(s.rows.size,1);
});
test('disabled IndexedDB or WebCrypto never falls back to insecure localStorage',()=>{
 assert.throws(()=>Vault.create({indexedDBImpl:null,cryptoImpl:webcrypto,origin:'https://a'}),/VAULT_UNAVAILABLE/);
 assert.throws(()=>Vault.create({indexedDBImpl:{},cryptoImpl:null,origin:'https://a'}),/VAULT_UNAVAILABLE/);
});
test('the system book requires explicit opt-in, clears passwords, and has revoke',async()=>{
 const fs=await import('node:fs/promises');
 const html=await fs.readFile(new URL('../living-library/live/system-controls.html',import.meta.url),'utf8');
 const main=await fs.readFile(new URL('../living-library/live/index.html',import.meta.url),'utf8');
 assert.match(html,/id="cloudWorldRemember"/);
 assert.match(html,/id="cloudWorldPassword"/);
 assert.match(html,/id="cloudWorldUnlock"/);
 assert.match(html,/id="cloudWorldForget"/);
 assert.match(html,/clearSecrets\(\)/);
 assert.match(html,/cloud-world-unlock/);
 assert.match(html,/cloud-world-forget/);
 assert.match(main,/LivingWorldCredentialVault\.create\(\)/);
 assert.match(main,/credentialVault\.remember/);
 assert.match(main,/credentialVault\.unlock/);
 assert.match(main,/credentialVault\.forget/);
 assert.match(main,/worldSave\.disconnect\(\)/);
});
