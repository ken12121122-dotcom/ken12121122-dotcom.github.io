// Browser acceptance: real WebCrypto + real IndexedDB at secure origin,
// reload persistence, wrong password, forgetting authorization.
// Synthetic token only; no calls to GitHub or user data access.
const {chromium}=require('playwright');
const fs=require('node:fs/promises');
const path=require('node:path');
const assert=require('node:assert/strict');
const proof={schema:'nw-world-vault-browser-v1',result:'NOT_TESTED',checks:[]};
async function main(){
 const browser=await chromium.launch({headless:true});
 try{
  const context=await browser.newContext({viewport:{width:400,height:850}}),page=await context.newPage();
  let outgoing=0;
  await context.route('**/*',async route=>{
   const u=new URL(route.request().url());
   if(u.origin!=='https://nw-world-vault.invalid'){outgoing++;return route.abort()}
   if(u.pathname==='/world-credential-vault.js'){
    const body=await fs.readFile(path.resolve(__dirname,'../../living-library/live/world-credential-vault.js'));
    return route.fulfill({status:200,contentType:'application/javascript',body});
   }
   if(u.pathname==='/')return route.fulfill({status:200,contentType:'text/html',
    body:'<!doctype html><meta charset="utf-8"><script src="/world-credential-vault.js"></script>'});
   return route.abort();
  });
  await page.goto('https://nw-world-vault.invalid/');
  const token='github_pat_BROWSER_SYNTHETIC_DO_NOT_USE';
  const first=await page.evaluate(async({token})=>{
   const vault=LivingWorldCredentialVault.create();
   if((await vault.inspect()).saved)throw Error('unexpected stored');
   await vault.remember(token,'long browser password 123');
   const saved=await vault.inspect();
   const keys=await new Promise((resolve,reject)=>{
    const r=indexedDB.open(LivingWorldCredentialVault.DB);
    r.onerror=()=>reject(r.error);
    r.onsuccess=()=>{
     const db=r.result,tx=db.transaction(LivingWorldCredentialVault.STORE,'readonly');
     const get=tx.objectStore(LivingWorldCredentialVault.STORE).get('private-github-card-inbox');
     get.onsuccess=()=>{db.close();resolve(get.result)};
     get.onerror=()=>reject(get.error);
    };
   });
   return {saved,raw:JSON.stringify(keys),localStorageKeys:Object.keys(localStorage)};
  },{token});
  assert.equal(first.saved.saved,true);
  assert.doesNotMatch(first.raw,/github_pat_BROWSER|long browser password/);
  assert.equal(first.localStorageKeys.length,0);
  proof.checks.push({case:'actual Chromium origin stores AES-GCM ciphertext only',result:'PASS'});
  await page.reload();
  const second=await page.evaluate(async({token})=>{
   const vault=LivingWorldCredentialVault.create();
   const exists=(await vault.inspect()).saved;
   let wrong=false;
   try{await vault.unlock('definitely wrong')}catch(e){wrong=e.code==='VAULT_UNLOCK_FAILED'}
   const restored=await vault.unlock('long browser password 123');
   return {exists,wrong,restoredCorrect:restored===token};
  },{token});
  assert.deepEqual(second,{exists:true,wrong:true,restoredCorrect:true});
  proof.checks.push({case:'encrypted token survives page reload, rejects wrong password and decrypts correct password',result:'PASS'});
  await page.evaluate(async()=>LivingWorldCredentialVault.create().forget());
  await page.reload();
  assert.deepEqual(await page.evaluate(async()=>LivingWorldCredentialVault.create().inspect()),{saved:false});
  proof.checks.push({case:'revoke persists through reload, no credential remains',result:'PASS'});
  assert.equal(outgoing,0);
  await context.close();proof.result='PASS';
 }finally{await browser.close()}
}
main().catch(e=>{proof.result='FAIL';proof.error=e.stack;process.exitCode=1})
 .finally(async()=>{const out=path.resolve(process.env.NW_BROWSER_OUTPUT||'/tmp/nw-browser-proof');
  await fs.mkdir(out,{recursive:true});await fs.writeFile(path.join(out,'world-vault.json'),JSON.stringify(proof,null,2)+'\n');
  console.log(JSON.stringify(proof,null,2));});
