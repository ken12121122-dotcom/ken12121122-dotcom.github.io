// Synthetic DOM, not a real avatar/raycast/joystick or phone acceptance test.
const {chromium}=require('playwright');
const fs=require('node:fs/promises'),path=require('node:path'),assert=require('node:assert/strict');
const proof={schema:'nw-character-tap-browser-v1',result:'NOT_TESTED',checks:[],scope:'Synthetic DOM click to unloaded character.tap/local-feedback adapter; no 3D or device acceptance'};
async function main(){
 const browser=await chromium.launch({headless:true});
 try{
  const page=await browser.newPage();
  await page.route('https://nw-tap.invalid/',r=>r.fulfill({contentType:'text/html',body:'<!doctype html><button id="avatar">Synthetic avatar tap</button><button id="cancel">Cancel</button><button id="dispose">Dispose</button><button id="resume">Resume guard</button><output id="feedback"></output><output id="phase"></output>'}));
  await page.goto('https://nw-tap.invalid/');
  for(const file of ['action-registry','character-tap-adapter'])await page.addScriptTag({path:path.resolve(__dirname,'../../living-library/live/'+file+'.js')});
  for(const mode of ['success','not_matched','cancel','dispose']){
   await page.evaluate(mode=>{
    const registry=new LivingActionRegistry.ActionRegistry(),controller=new AbortController();let release;
    const inputs={type:'object',properties:{target_id:{type:'string',minLength:1}},required:['target_id'],additionalProperties:false};
    for(const [action_id,kind] of [['character.tap','event'],['ui.toast','effect']])registry.register({action_id,name:action_id,kind,version:'0.1.0',inputs,outputs:{type:'boolean'},platform:['web'],policy:{scope:'local'},guard:()=>((mode==='cancel'&&kind==='effect')||(mode==='dispose'&&kind==='event'))?new Promise(r=>{release=r;document.getElementById('phase').textContent='pending'}):true,execute:(_,context)=>{if(kind==='event')return mode!=='not_matched';document.getElementById('feedback').textContent='synthetic feedback: '+context.target_object_instance_id;return true}});
    const adapter=new LivingCharacterTap.CharacterTapAdapter(registry);window.receipt=null;document.getElementById('feedback').textContent='';document.getElementById('phase').textContent='ready';
    document.getElementById('avatar').onclick=async()=>{window.receipt=await adapter.dispatch('synthetic.avatar',{signal:controller.signal});document.getElementById('phase').textContent='done'};
    document.getElementById('cancel').onclick=()=>controller.abort();document.getElementById('dispose').onclick=()=>adapter.dispose();document.getElementById('resume').onclick=()=>release(true);
   },mode);
   await page.locator('#avatar').click();
   if(['cancel','dispose'].includes(mode)){await page.waitForFunction(()=>document.getElementById('phase').textContent==='pending');await page.locator('#'+mode).click();await page.locator('#resume').click()}
   await page.waitForFunction(()=>document.getElementById('phase').textContent==='done');
   const receipt=await page.evaluate(()=>window.receipt),text=await page.locator('#feedback').innerText();
   assert.equal(receipt.status,{success:'success',not_matched:'not_matched',cancel:'partial_failure',dispose:'failed'}[mode]);assert.equal(receipt.runtime_verified,false);
   assert.equal(text,mode==='success'?'synthetic feedback: synthetic.avatar':'');
   if(mode==='cancel')assert.equal(receipt.error_code,'DISPATCH_CANCELLED');if(mode==='dispose')assert.equal(receipt.error_code,'DISPATCH_DISPOSED');
   proof.checks.push({case:mode,result:'PASS',scope:'Synthetic click and local feedback only'});
  }
  proof.result='PASS';
 }finally{await browser.close()}
}
main().catch(error=>{proof.result='FAIL';proof.error=error.stack;process.exitCode=1}).finally(async()=>{
 const out=path.resolve(process.env.NW_BROWSER_OUTPUT||'/tmp/nw-browser-proof');await fs.mkdir(out,{recursive:true});await fs.writeFile(path.join(out,'character-tap.json'),JSON.stringify(proof,null,2)+'\n');console.log(JSON.stringify(proof,null,2));
});
