// Synthetic unloaded registry only, not scene/device/authority acceptance.
const {chromium}=require('playwright'),fs=require('node:fs/promises'),path=require('node:path'),assert=require('node:assert/strict');
const proof={schema:'nw-definition-snapshot-browser-v1',result:'NOT_TESTED',checks:[],scope:'Synthetic definition registration and local invocation; no scene/device claim'};
async function main(){
 const browser=await chromium.launch({headless:true});
 try{
  const page=await browser.newPage();await page.goto('about:blank');await page.addScriptTag({path:path.resolve(__dirname,'../../living-library/live/action-registry.js')});
  for(const mode of ['input_hook','policy_getter','platform_hook','valid_and_owner_gate']){
   const r=await page.evaluate(async mode=>{
    const registry=new LivingActionRegistry.ActionRegistry();let hooks=0,calls=0;
    const d={action_id:'synthetic.definition',name:'Synthetic',kind:'effect',version:'0.1.0',inputs:{type:'integer',minimum:2},outputs:{type:'boolean'},platform:['web'],policy:{scope:'local'},guard:()=>{calls++;return true},execute:()=>{calls++;return true}};
    if(mode==='input_hook')Object.defineProperty(d.inputs,'toJSON',{value(){hooks++;return {type:'integer',minimum:0}}});
    if(mode==='policy_getter')Object.defineProperty(d.policy,'scope',{enumerable:true,get(){return ++hooks===1?'private_sync':'local'}});
    if(mode==='platform_hook'){d.platform=['android'];Object.defineProperty(d.platform,'toJSON',{value(){hooks++;return ['web']}})}
    if(mode!=='valid_and_owner_gate'){let code=null;try{registry.register(d)}catch(e){code=e.code}return {code,hooks,calls,count:registry.list().length}}
    const metadata=registry.register(d);d.inputs.minimum=0;d.execute=()=>false;const value=await registry.invoke(d.action_id,2);
    const restricted=new LivingActionRegistry.ActionRegistry();restricted.register({...d,policy:{scope:'private_sync'}});let code;try{await restricted.invoke(d.action_id,2)}catch(e){code=e.code}
    return {value,minimum:metadata.inputs.minimum,frozen:Object.isFrozen(metadata.inputs),code,hooks,calls};
   },mode);
   if(mode==='valid_and_owner_gate'){assert.equal(r.value,true);assert.equal(r.minimum,2);assert.equal(r.frozen,true);assert.equal(r.code,'OWNER_GATE_REQUIRED');assert.equal(r.calls,2)}
   else{assert.equal(r.code,{input_hook:'INVALID_SCHEMA',policy_getter:'INVALID_POLICY',platform_hook:'INVALID_PLATFORM'}[mode]);assert.equal(r.count,0);assert.equal(r.calls,0)}
   assert.equal(r.hooks,0);proof.checks.push({case:mode,result:'PASS'});
  }
  proof.result='PASS';
 }finally{await browser.close()}
}
main().catch(e=>{proof.result='FAIL';proof.error=e.stack;process.exitCode=1}).finally(async()=>{const out=path.resolve(process.env.NW_BROWSER_OUTPUT||'/tmp/nw-browser-proof');await fs.mkdir(out,{recursive:true});await fs.writeFile(path.join(out,'definition-snapshot.json'),JSON.stringify(proof,null,2)+'\n');console.log(JSON.stringify(proof,null,2))});
