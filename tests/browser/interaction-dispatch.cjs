// Synthetic DOM adapter only. No real scene, microphone or device acceptance.
const {chromium}=require('playwright');
const fs=require('node:fs/promises'),path=require('node:path'),assert=require('node:assert/strict');
const result={schema:'nw-interaction-dispatch-browser-v1',result:'NOT_TESTED',checks:[],scope:'Synthetic DOM to candidate session/dispatcher/registry/executor; no scene, microphone, deployment or device claim'};
async function main(){
 const browser=await chromium.launch({headless:true});
 try{
  const page=await browser.newPage();
  await page.route('https://nw-dispatch.invalid/',r=>r.fulfill({contentType:'text/html',body:'<!doctype html><button id="dispatch">Dispatch</button><button id="cancel">Cancel</button><button id="replace">Replace</button><button id="resume">Resume guard</button><output id="state"></output>'}));
  await page.goto('https://nw-dispatch.invalid/');
  for(const file of ['action-registry','spell-contract','spell-executor','interaction-session','interaction-dispatch'])await page.addScriptTag({path:path.resolve(__dirname,'../../living-library/live/'+file+'.js')});
  for(const change of ['none','cancel','replace']){
   await page.evaluate(()=>{
    const session=new LivingInteractionSession.TargetInteractionSession(),registry=new LivingActionRegistry.ActionRegistry();
    let release;const calls=[];
    for(const [action_id,kind] of [['book.synthetic_active','condition'],['book.synthetic_effect','effect']])registry.register({action_id,name:action_id,kind,version:'0.1.0',target_type:'library.book_instance',inputs:{type:'object',properties:{},required:[],additionalProperties:false},outputs:{type:'boolean'},platform:['web'],policy:{scope:'local'},guard:()=>kind==='effect'?new Promise(r=>{release=r;document.getElementById('state').textContent='guard_pending'}):true,execute:(_,context)=>{calls.push({kind,target:context.target_object_instance_id});return true}});
    const ids=['book.synthetic_active','book.synthetic_effect'];
    const card={schema:'nw.spell.v1',spell_id:'spell.synthetic.dispatch',version:'0.1.0',name:'Synthetic',description:'DOM only',incantations:['synthetic phrase'],enabled:true,target_type:'library.book_instance',runes:ids.map((action_id,i)=>({schema:'nw.rune.v1',rune_id:'r'+i,action_id,action_version:'0.1.0',label:action_id,parameters:{}})),conditions:{operator:'AND',refs:['r0']},effects:{operator:'SEQUENCE',refs:['r1']},connections:[{from:'r0',to:'r1',operator:'AND'}]};
    session.beginGrab({target_id:'book.synthetic.a',pointer_id:7});
    const context=session.resolveVoiceResult(session.captureVoiceContext(),{transcript:'synthetic phrase',incantations:card.incantations}).context;
    const dispatcher=new LivingInteractionDispatch.InteractionDispatcher({registry,session,allowedActions:ids});
    window.proof={calls,receipt:null,replay:null};document.getElementById('state').textContent='ready';
    document.getElementById('dispatch').onclick=async()=>{try{window.proof.receipt=await dispatcher.run(card,{context,incantation:'synthetic phrase'});document.getElementById('state').textContent='done'}catch(e){window.proof.replay=e.code;document.getElementById('state').textContent='rejected'}};
    document.getElementById('cancel').onclick=()=>session.cancel();
    document.getElementById('replace').onclick=()=>session.beginGrab({target_id:'book.synthetic.b',pointer_id:8});
    document.getElementById('resume').onclick=()=>release(true);
   });
   await page.locator('#dispatch').click();await page.waitForFunction(()=>document.getElementById('state').textContent==='guard_pending');
   if(change!=='none')await page.locator('#'+change).click();
   await page.locator('#resume').click();await page.waitForFunction(()=>document.getElementById('state').textContent==='done');
   const proof=await page.evaluate(()=>window.proof);
   assert.equal(proof.receipt.status,change==='none'?'success':'failed');
   assert.equal(proof.receipt.error_code,change==='none'?null:'STALE_INTERACTION');assert.equal(proof.receipt.runtime_verified,false);
   assert.equal(proof.calls.filter(x=>x.kind==='effect').length,change==='none'?1:0);
   if(change==='none'){
    assert.equal(proof.calls[1].target,'book.synthetic.a');
    await page.locator('#dispatch').click();await page.waitForFunction(()=>document.getElementById('state').textContent==='rejected');
    assert.equal(await page.evaluate(()=>window.proof.replay),'CONTEXT_ALREADY_DISPATCHED');
    result.checks.push({case:'same-context replay rejected',result:'PASS'});
   }
   result.checks.push({case:'awaited guard with '+change,result:'PASS'});
  }
  result.result='PASS';
 }finally{await browser.close()}
}
main().catch(error=>{result.result='FAIL';result.error=error.stack;process.exitCode=1}).finally(async()=>{
 const out=path.resolve(process.env.NW_BROWSER_OUTPUT||'/tmp/nw-browser-proof');await fs.mkdir(out,{recursive:true});await fs.writeFile(path.join(out,'interaction-dispatch.json'),JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result,null,2));
});
