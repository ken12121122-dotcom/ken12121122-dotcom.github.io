// Real Chromium startup UI test: exercise the exact production login prompt
// JavaScript + markup from index.html, real encrypted IndexedDB and WebCrypto.
// No network access or private data, only a synthetic mock PAT.
const {chromium}=require('playwright');
const fs=require('node:fs/promises');
const path=require('node:path');
const assert=require('node:assert/strict');
const proof={schema:'nw-world-vault-startup-browser-v1',result:'NOT_TESTED',checks:[]};
const origin='https://nw-world-startup.invalid';
async function main(){
 const html=await fs.readFile(path.resolve(__dirname,'../../living-library/live/index.html'),'utf8');
 const dialog=html.match(/<dialog id="worldVaultUnlockDialog"[\s\S]*?<\/dialog>/)?.[0];
 const begin=html.indexOf('// At game launch, invite unlocking an ALREADY stored');
 const end=html.indexOf('// Only the open creator iframe',begin);
 assert.ok(dialog&&begin!==-1&&end>begin,'actual startup unlock markup/script missing');
 const snippet=html.slice(begin,end);
 const bootstrap=`
 const $=id=>document.getElementById(id);
 const credentialVault=LivingWorldCredentialVault.create();
 let credentialVaultEpoch=0;
 window.__connectedTokens=[];
 window.__notices=[];
 const worldSave={
   status:()=>({authorized:window.__connectedTokens.length>0}),
   connect:async token=>{
     window.__connectedTokens.push(token);
     return {phase:'synced',credential_verified:true,error:''};
   }
 };
 function syncScene(){}
 function toast(message){window.__notices.push(message)}
 `;
 const browser=await chromium.launch({headless:true});
 try{
  const context=await browser.newContext({viewport:{width:400,height:850}}),page=await context.newPage();
  let outgoing=0;
  await context.route('**/*',async route=>{
   const u=new URL(route.request().url());
   if(u.origin!==origin){outgoing++;return route.abort()}
   if(u.pathname==='/vault.js'){
    return route.fulfill({status:200,contentType:'application/javascript',
     body:await fs.readFile(path.resolve(__dirname,'../../living-library/live/world-credential-vault.js'))});
   }
   if(u.pathname==='/startup.js'){
    return route.fulfill({status:200,contentType:'application/javascript',body:bootstrap+snippet});
   }
   if(u.pathname==='/')return route.fulfill({status:200,contentType:'text/html',
    body:`<!doctype html><meta charset="utf-8"><dialog id="llUpdateDialog"></dialog>
    <dialog id="gen2Auth"></dialog><dialog id="mdSearch"></dialog>
    <div id="drawerWrap"></div>${dialog}
    <script src="/vault.js"></script><script src="/startup.js"></script>`});
   return route.abort();
  });
  await page.goto(origin);
  await page.waitForTimeout(520);
  assert.equal(await page.locator('#worldVaultUnlockDialog').evaluate(el=>el.open),false);
  proof.checks.push({case:'first visit without stored consent never asks for a password',result:'PASS'});
  await page.evaluate(async()=>{
   await LivingWorldCredentialVault.create().remember('github_pat_SYNTHETIC_BROWSER_ONLY',
     'valid local password 123');
  });
  await page.reload();
  await page.waitForFunction(()=>document.querySelector('#worldVaultUnlockDialog').open);
  assert.deepEqual(await page.evaluate(()=>window.__connectedTokens),[]);
  proof.checks.push({case:'reopening the game prompts for saved encrypted credentials but never autoconnects',result:'PASS'});
  await page.locator('#worldVaultUnlockSkip').click();
  assert.equal(await page.locator('#worldVaultUnlockDialog').evaluate(el=>el.open),false);
  assert.deepEqual(await page.evaluate(()=>window.__connectedTokens),[]);
  proof.checks.push({case:'skip returns to game without connecting GitHub or deleting saved credentials',result:'PASS'});
  await page.reload();
  await page.waitForFunction(()=>document.querySelector('#worldVaultUnlockDialog').open);
  await page.locator('#worldVaultUnlockPassword').fill('wrong local password');
  await page.locator('#worldVaultUnlockSubmit').click();
  await page.waitForFunction(()=>document.querySelector('#worldVaultUnlockStatus').textContent.includes('密碼錯誤'));
  assert.equal(await page.locator('#worldVaultUnlockDialog').evaluate(el=>el.open),true);
  assert.deepEqual(await page.evaluate(()=>window.__connectedTokens),[]);
  await page.locator('#worldVaultUnlockPassword').fill('valid local password 123');
  await page.locator('#worldVaultUnlockSubmit').click();
  await page.waitForFunction(()=>!document.querySelector('#worldVaultUnlockDialog').open);
  assert.deepEqual(await page.evaluate(()=>window.__connectedTokens),['github_pat_SYNTHETIC_BROWSER_ONLY']);
  assert.equal(await page.locator('#worldVaultUnlockPassword').inputValue(),'');
  proof.checks.push({case:'wrong password is rejected, correct password connects and clears input',result:'PASS'});
  await page.evaluate(async()=>LivingWorldCredentialVault.create().forget());
  await page.reload();
  await page.waitForTimeout(530);
  assert.equal(await page.locator('#worldVaultUnlockDialog').evaluate(el=>el.open),false);
  assert.equal(outgoing,0);
  proof.checks.push({case:'forgetting device prevents startup prompt and uses zero external calls',result:'PASS'});
  await context.close();proof.result='PASS';
 }finally{await browser.close()}
}
main().catch(e=>{proof.result='FAIL';proof.error=e.stack;process.exitCode=1})
 .finally(async()=>{const out=path.resolve(process.env.NW_BROWSER_OUTPUT||'/tmp/nw-browser-proof');
  await fs.mkdir(out,{recursive:true});
  await fs.writeFile(path.join(out,'world-vault-startup.json'),JSON.stringify(proof,null,2)+'\n');
  console.log(JSON.stringify(proof,null,2));});
