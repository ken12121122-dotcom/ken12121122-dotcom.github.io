// Synthetic DOM fixture only. Does not claim a real 3D character/voice event.
const {chromium}=require('playwright');
const fs=require('node:fs/promises');
const path=require('node:path');
const assert=require('node:assert/strict');
const out=path.resolve(process.env.NW_BROWSER_OUTPUT||'/tmp/nw-browser-proof');
const result={schema:'nw-interaction-session-browser-v1',result:'NOT_TESTED',checks:[],scope:'Synthetic DOM clicks and candidate context module; no 3D, microphone, network or device acceptance'};
async function main(){
 const browser=await chromium.launch({headless:true});
 try{
  const page=await browser.newPage();
  await page.route('https://nw-session.invalid/',route=>route.fulfill({contentType:'text/html',body:'<!doctype html><button id="a">Grab A</button><button id="b">Grab B</button><button id="capture">Capture context</button><button id="release">Release</button><button id="resolve">Resolve result</button><output id="result"></output>'}));
  await page.goto('https://nw-session.invalid/');
  await page.addScriptTag({path:path.resolve(__dirname,'../../living-library/live/interaction-session.js')});
  await page.evaluate(()=>{
   const session=new LivingInteractionSession.TargetInteractionSession();let token=null;
   for(const id of ['a','b'])document.getElementById(id).onclick=()=>session.beginGrab({target_id:'book.synthetic.'+id,pointer_id:7});
   document.getElementById('capture').onclick=()=>{token=session.captureVoiceContext()};
   document.getElementById('release').onclick=()=>session.endGrab(7);
   document.getElementById('resolve').onclick=()=>{document.getElementById('result').textContent=JSON.stringify(session.resolveVoiceResult(token,{transcript:'synthetic phrase',incantations:['synthetic phrase']}))};
  });
  for(const [actions,reason,target] of [
   [['a','capture','resolve'],'MATCHED','book.synthetic.a'],
   [['a','capture','release','resolve'],'STALE_INTERACTION',null],
   [['a','capture','b','resolve'],'STALE_INTERACTION',null],
   [['b','capture','resolve','resolve'],'ALREADY_CONSUMED',null]
  ]){
   for(const id of actions)await page.locator('#'+id).click();
   const value=JSON.parse(await page.locator('#result').innerText());assert.equal(value.reason,reason);assert.equal(value.context?.target_object_instance_id||null,target);assert.equal(value.runtime_verified,false);
   result.checks.push({actions,reason,result:'PASS'});
  }
  result.result='PASS';
 }finally{await browser.close()}
}
main().catch(error=>{result.result='FAIL';result.error=error.stack;process.exitCode=1})
 .finally(async()=>{await fs.mkdir(out,{recursive:true});await fs.writeFile(path.join(out,'interaction-session.json'),JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result,null,2))});
