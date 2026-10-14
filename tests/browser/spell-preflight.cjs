// Synthetic module execution only: no scene, voice or device acceptance.
const {chromium}=require('playwright');
const fs=require('node:fs/promises'),path=require('node:path'),assert=require('node:assert/strict');
const proof={schema:'nw-spell-preflight-browser-v1',result:'NOT_TESTED',checks:[],scope:'Unloaded synthetic spell execution; no 3D/device acceptance'};
async function main(){
 const browser=await chromium.launch({headless:true});
 try{
  const page=await browser.newPage();await page.goto('about:blank');
  for(const file of ['action-registry','spell-contract','spell-executor'])await page.addScriptTag({path:path.resolve(__dirname,'../../living-library/live/'+file+'.js')});
  for(const mode of ['valid','invalid_later','disabled','guard_rejected','card_getter','card_hook','non_json','sparse_phrase']){
   const result=await page.evaluate(async mode=>{
    const registry=new LivingActionRegistry.ActionRegistry(),calls=[],guards=[];
    const ids=['book.drag_active','book.synthetic_first','book.synthetic_later'];
    ids.forEach((action_id,i)=>registry.register({action_id,name:action_id,kind:i?'effect':'condition',version:'0.1.0',target_type:'library.book_instance',inputs:i===2?{type:'object',properties:{slot:{type:'integer'}},required:['slot'],additionalProperties:false}:{type:'object',properties:{},required:[],additionalProperties:false},outputs:{type:'boolean'},platform:['web'],policy:{scope:'local'},guard:()=>{guards.push(i);return mode!=='guard_rejected'||i!==1},execute:()=>{calls.push(i);return true}}));
    const card={schema:'nw.spell.v1',spell_id:'spell.synthetic.preflight',version:'0.1.0',name:'Synthetic',description:'No scene',incantations:['synthetic phrase'],enabled:mode!=='disabled',target_type:'library.book_instance',runes:ids.map((action_id,i)=>({schema:'nw.rune.v1',rune_id:'r'+i,action_id,action_version:'0.1.0',label:action_id,parameters:i===2&&mode!=='invalid_later'?{slot:1}:{}})),conditions:{operator:'AND',refs:['r0']},effects:{operator:'SEQUENCE',refs:['r1','r2']},connections:[{from:'r0',to:'r1',operator:'AND'},{from:'r1',to:'r2',operator:'SEQUENCE'}]};
    let hooks=0;
    if(mode==='card_getter')Object.defineProperty(card,'enabled',{enumerable:true,get(){hooks++;return true}});
    if(mode==='card_hook')Object.defineProperty(card,'toJSON',{value(){hooks++;return {...card,enabled:false}}});
    if(mode==='non_json')card.runes[2].parameters.extra=undefined;
    if(mode==='sparse_phrase')card.incantations.length=2;
    try{return {receipt:await new LivingSpellExecutor.SpellExecutor(registry).run(card,{incantation:'synthetic phrase'}),calls,guards,hooks}}
    catch(error){return {error_code:error.code,error_message:error.message,calls,guards,hooks}}
   },mode);
   if(['card_getter','card_hook','non_json','sparse_phrase'].includes(mode)){
    assert.equal(result.error_code,'INVALID_CARD_SCHEMA');assert.equal(result.error_message,'INVALID_CARD_SCHEMA');assert.equal(result.hooks,0);assert.deepEqual(result.calls,[]);assert.deepEqual(result.guards,[]);
    proof.checks.push({case:mode,result:'PASS'});continue;
   }
   assert.equal(result.receipt.status,{valid:'success',invalid_later:'failed',disabled:'disabled',guard_rejected:'failed'}[mode]);assert.equal(result.receipt.runtime_verified,false);
   assert.deepEqual(result.calls,mode==='valid'?[0,1,2]:mode==='guard_rejected'?[0]:[]);
   assert.deepEqual(result.guards,mode==='valid'?[0,1,2]:mode==='guard_rejected'?[0,1]:[]);
   if(mode==='invalid_later'){assert.equal(result.receipt.error_code,'INVALID_INPUT');assert.deepEqual(result.receipt.attempted_effects,[])}
   if(mode==='guard_rejected')assert.equal(result.receipt.error_code,'GUARD_REJECTED');
   proof.checks.push({case:mode,result:'PASS'});
  }
  proof.result='PASS';
 }finally{await browser.close()}
}
main().catch(error=>{proof.result='FAIL';proof.error=error.stack;process.exitCode=1}).finally(async()=>{
 const out=path.resolve(process.env.NW_BROWSER_OUTPUT||'/tmp/nw-browser-proof');await fs.mkdir(out,{recursive:true});await fs.writeFile(path.join(out,'spell-preflight.json'),JSON.stringify(proof,null,2)+'\n');console.log(JSON.stringify(proof,null,2));
});
