/* A-button experimental GitHub inbox: single owner card, verified PRIVATE repository,
 * isolated branch, explicit per-upload authorization. No token persistence or fallback
 * to the public website repository. This does NOT run in the GBA emulator.
 */
(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LivingCardGithubInbox=api;
})(typeof self!=='undefined'?self:globalThis,function(){
 'use strict';
 const REPO='ken12121122-dotcom/gen2-knowledge';
 const BRANCH='card-inbox';
 const FOLDER='card-inbox/v1';
 const SCHEMA='nw.github-card-inbox.v1';
 const ROOT='https://api.github.com/repos/'+REPO;
 const ID=/^local\.card\.[A-Za-z0-9-]{1,100}$/;
 const IMAGE=/^data:image\/jpeg;base64,\/9j\/[A-Za-z0-9+/]*={0,2}$/;
 function fail(code){const e=new Error(code);e.code=code;throw e}
 function buildPacket({storage,craft,observer,cardId,clock=()=>new Date().toISOString()}={}){
  if(!ID.test(cardId||''))fail('GITHUB_CARD_INVALID');
  if(!craft||typeof craft.read!=='function'||!observer||typeof observer.snapshot!=='function')fail('GITHUB_SOURCE_MISSING');
  let record,observation;
  try{
   const items=craft.read(storage).objects;
   record=items.find(c=>c.id===cardId&&c.objectType==='card'&&c.templateId==='blank-card-v1'&&
    c.status!=='recycled'&&c.containerId==='local.atlas.compendium'&&c.slotIndex===9);
   if(record)observation=observer.snapshot({storage,craft,cardId,clock});
  }catch(_){fail('GITHUB_SOURCE_INVALID')}
  if(!record||!observation)fail('GITHUB_CARD_NOT_IN_READER');
  const img=record.image;
  if(img!==null&&img!==undefined&&
    (!img||typeof img.dataUrl!=='string'||img.dataUrl.length>70000||
     !IMAGE.test(img.dataUrl)||!Number.isFinite(img.scale)||!Number.isFinite(img.x)||!Number.isFinite(img.y)))
   fail('GITHUB_IMAGE_INVALID');
  // Only explicitly allowed single-card fields are copied. This is private-only.
  const image=img?{present:true,dataUrl:img.dataUrl,scale:img.scale,x:img.x,y:img.y}:{present:false};
  const card={...observation.card,image};
  const packet={schema:SCHEMA,review_status:'generated',transmission:'user_requested',
   exported_at:observation.exported_at,card,source_records:observation.source_records,
   note:'Owner-approved private GitHub inbox, not a formal GEN2 knowledge document.'};
  const json=JSON.stringify(packet,null,2)+'\n';
  if(new TextEncoder().encode(json).length>120000)fail('GITHUB_CARD_TOO_LARGE');
  return Object.freeze({packet,json,path:FOLDER+'/'+cardId+'.json',revision:card.revision});
 }
 function encode(str){
  const bytes=new TextEncoder().encode(str);
  let result='';for(let i=0;i<bytes.length;i+=8192)result+=String.fromCharCode(...bytes.subarray(i,i+8192));
  return btoa(result);
 }
 function errorCode(status,base){
  if(status===401)return 'GITHUB_AUTH_FAILED';
  if(status===403)return 'GITHUB_PERMISSION_DENIED';
  if(status===429)return 'GITHUB_RATE_LIMITED';
  if(status===409)return 'GITHUB_CONFLICT';
  if(status===422)return 'GITHUB_INVALID_REQUEST';
  return base;
 }
 async function upload({storage,craft,observer,cardId,token,fetchImpl=globalThis.fetch,clock=()=>new Date().toISOString()}={}){
  if(typeof token!=='string'||token.trim().length<10||token.length>1024)fail('GITHUB_TOKEN_REQUIRED');
  if(typeof fetchImpl!=='function')fail('GITHUB_NETWORK_UNAVAILABLE');
  const {packet,json,path,revision}=buildPacket({storage,craft,observer,cardId,clock});
  const headers={'Accept':'application/vnd.github+json','Authorization':'Bearer '+token.trim(),
   'X-GitHub-Api-Version':'2022-11-28'};
  async function request(route,options={}){
   let response;
   try{response=await fetchImpl(ROOT+route,{...options,headers:{...headers,...options.headers},
    credentials:'omit',cache:'no-store',redirect:'error',referrerPolicy:'no-referrer'})}
   catch(_){fail('GITHUB_NETWORK_ERROR')}
   return response;
  }
  const repository=await request('');
  if(!repository.ok)fail(errorCode(repository.status,'GITHUB_REPOSITORY_UNAVAILABLE'));
  let repoInfo;try{repoInfo=await repository.json()}catch(_){fail('GITHUB_REPOSITORY_INVALID')}
  if(repoInfo.full_name!==REPO||repoInfo.private!==true)fail('GITHUB_PRIVATE_REQUIRED');
  const ref=await request('/git/ref/heads/'+BRANCH);
  if(!ref.ok)fail(errorCode(ref.status,'GITHUB_INBOX_BRANCH_MISSING'));
  let refInfo;try{refInfo=await ref.json()}catch(_){fail('GITHUB_INBOX_BRANCH_INVALID')}
  if(refInfo.ref!=='refs/heads/'+BRANCH||!/^([0-9a-f]{40})$/.test(refInfo.object?.sha||''))
   fail('GITHUB_INBOX_BRANCH_INVALID');
  const target='/contents/'+path;
  const url=target+'?ref='+encodeURIComponent(BRANCH);
  const previous=await request(url);
  let sha;
  if(previous.status===200){
   try{const old=await previous.json();sha=old.sha;if(!/^[0-9a-f]{40}$/.test(sha))throw Error('invalid')}
   catch(_){fail('GITHUB_EXISTING_FILE_INVALID')}
  }else if(previous.status!==404)fail(errorCode(previous.status,'GITHUB_READ_FAILED'));
  const payload={message:'data(inbox): owner sends card '+cardId+' rev '+revision,
   content:encode(json),branch:BRANCH};
  if(sha)payload.sha=sha;
  const written=await request(target,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
  if(written.status!==200&&written.status!==201)fail(errorCode(written.status,'GITHUB_WRITE_FAILED'));
  let response;
  try{response=await written.json()}catch(_){fail('GITHUB_WRITE_RESPONSE_INVALID')}
  if(response.content?.path!==path||!/^[0-9a-f]{40}$/.test(response.content?.sha||'')||
    !/^[0-9a-f]{40}$/.test(response.commit?.sha||''))fail('GITHUB_WRITE_RESPONSE_INVALID');
  const verified=await request(url);
  if(!verified.ok)fail('GITHUB_VERIFY_FAILED');
  let checked;try{checked=await verified.json()}catch(_){fail('GITHUB_VERIFY_FAILED')}
  if(checked.sha!==response.content.sha||checked.path!==path)fail('GITHUB_VERIFY_FAILED');
  return Object.freeze({ok:true,repo:REPO,branch:BRANCH,path,
    card_id:cardId,revision,commit_sha:response.commit.sha,
    file_sha:response.content.sha,includes_compressed_photo:!!packet.card.image.present});
 }
 return Object.freeze({SCHEMA,REPO,BRANCH,FOLDER,buildPacket,upload});
});
