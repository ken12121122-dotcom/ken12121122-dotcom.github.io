// Browser module contract only: synthetic data, no scene/device/voice claims.
const {chromium}=require('playwright');
const fs=require('node:fs/promises'),path=require('node:path'),assert=require('node:assert/strict');
const result={schema:'nw-action-payload-browser-v1',result:'NOT_TESTED',checks:[],scope:'Candidate ActionRegistry module in Chromium with synthetic payloads; not a real 3D or device interaction'};
async function main(){
 const browser=await chromium.launch({headless:true});
 try{
  const page=await browser.newPage();
  await page.route('https://nw-payload.invalid/',r=>r.fulfill({contentType:'text/html',body:'<!doctype html><title>Synthetic payload contract</title>'}));
  await page.goto('https://nw-payload.invalid/');
  await page.addScriptTag({path:path.resolve(__dirname,'../../living-library/live/action-registry.js')});
  const values=await page.evaluate(async()=>{
   const object=properties=>({type:'object',properties,required:Object.keys(properties),additionalProperties:false});
   const make=(inputs,outputs={type:'boolean'},execute=()=>true)=>{
    const registry=new LivingActionRegistry.ActionRegistry(),calls={guard:0,execute:0};
    registry.register({action_id:'synthetic.payload',name:'Synthetic',kind:'effect',version:'0.1.0',inputs,outputs,platform:['web'],policy:{scope:'local'},guard:()=>{calls.guard++;return true},execute:value=>{calls.execute++;return execute(value)}});
    return {registry,calls,invoke:input=>registry.invoke('synthetic.payload',input)};
   };
   const code=async task=>{try{await task();return null}catch(error){return error.code}};
   const sparse=make({type:'array',items:{type:'boolean'}}),sparseCode=await code(()=>sparse.invoke(new Array(1)));
   let reads=0,hooks=0;
   const getter=Object.defineProperty({},'target',{enumerable:true,get(){reads++;return 'synthetic'}});
   const hook=Object.defineProperty({target:'synthetic'},'toJSON',{value(){hooks++;return {target:'different'}}});
   const input=make(object({target:{type:'string'}})),getterCode=await code(()=>input.invoke(getter)),hookCode=await code(()=>input.invoke(hook));
   let output=new Array(1);const out=make({type:'boolean'},{type:'array',items:{type:'boolean'}},()=>output),outputCode=await code(()=>out.invoke(true));output=[true];const recovered=await out.invoke(true);
   let seen;const valid=make(object({target:object({id:{type:'string'}})}),{type:'boolean'},value=>{seen=value;return true}),source={target:{id:'synthetic.a'}},pending=valid.invoke(source);source.target.id='synthetic.b';const success=await pending;
   return {sparse:{code:sparseCode,calls:sparse.calls},hooks:{getterCode,hookCode,reads,hooks,calls:input.calls},output:{code:outputCode,recovered,frozen:Object.isFrozen(recovered)},valid:{success,target:seen.target.id,frozen:Object.isFrozen(seen.target),detached:seen.target!==source.target}};
  });
  assert.deepEqual(values.sparse,{code:'INVALID_INPUT',calls:{guard:0,execute:0}});result.checks.push({case:'sparse input rejected before handlers',result:'PASS'});
  assert.deepEqual(values.hooks,{getterCode:'INVALID_INPUT',hookCode:'INVALID_INPUT',reads:0,hooks:0,calls:{guard:0,execute:0}});result.checks.push({case:'accessor and serialization hook rejected without execution',result:'PASS'});
  assert.deepEqual(values.output,{code:'INVALID_OUTPUT',recovered:[true],frozen:true});result.checks.push({case:'sparse output rejected and registry lock reusable',result:'PASS'});
  assert.deepEqual(values.valid,{success:true,target:'synthetic.a',frozen:true,detached:true});result.checks.push({case:'valid input detached before async guard',result:'PASS'});
  result.result='PASS';
 }finally{await browser.close()}
}
main().catch(error=>{result.result='FAIL';result.error=error.stack;process.exitCode=1}).finally(async()=>{
 const out=path.resolve(process.env.NW_BROWSER_OUTPUT||'/tmp/nw-browser-proof');await fs.mkdir(out,{recursive:true});await fs.writeFile(path.join(out,'action-payload.json'),JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result,null,2));
});
