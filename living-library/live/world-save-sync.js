/* Candidate cloud-first world save (session-authorized only).
 * GitHub PRIVATE repository, isolated card-inbox branch; never touches main.
 * No token persistence. The local ObjectCraft record is NEVER rewritten here.
 */
(function(root,factory){
 const api=factory(typeof module==='object'&&module.exports?require('./object-backup.js'):root.LivingObjectBackup);
 if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LivingWorldSaveSync=api;
})(typeof self!=='undefined'?self:globalThis,function(Backup){
 'use strict';
 const REPO='ken12121122-dotcom/gen2-knowledge',BRANCH='card-inbox';
 const PATH='world-saves/v1/player-world.json',BASELINE='nw.world-save-baseline.v1';
 const SCENE_PATH='world-saves/v1/world-observation.json',SCENE_BASELINE='nw.world-observation-baseline.v1';
 const API='https://api.github.com/repos/'+REPO;
 // Below GitHub Contents API's reliable 1 MB JSON read ceiling after base64.
 const MAX_BYTES=600000;
 const fail=code=>{const e=new Error(code);e.code=code;throw e};
 const bytes=s=>new TextEncoder().encode(s);
 function encode(s){
  const b=bytes(s);let out='';
  for(let i=0;i<b.length;i+=8192)out+=String.fromCharCode(...b.subarray(i,i+8192));
  return btoa(out);
 }
 function decode(s){
  if(typeof s!=='string'||!s.trim())fail('CLOUD_REMOTE_UNREADABLE');
  let binary;try{binary=atob(s.replace(/\s/g,''))}catch{fail('CLOUD_REMOTE_UNREADABLE')}
  return new TextDecoder('utf-8',{fatal:true}).decode(Uint8Array.from(binary,c=>c.charCodeAt(0)));
 }
 function baseline(storage){
  try{
   const b=JSON.parse(storage.getItem(BASELINE)||'null');
   return b&&b.schema==='nw.world-save-baseline.v1'&&b.repo===REPO&&b.branch===BRANCH&&b.path===PATH&&
    /^[a-f0-9]{40}$/.test(b.blob_sha||'')&&/^[a-f0-9]{64}$/.test(b.source_sha256||'')?b:null;
  }catch{return null}
 }
 function writeBaseline(storage,sha,source){
  const b={schema:'nw.world-save-baseline.v1',repo:REPO,branch:BRANCH,path:PATH,
   blob_sha:sha,source_sha256:source,synced_at:new Date().toISOString()};
  // A baseline is NOT an authorization token. Failure must block further writes.
  try{storage.setItem(BASELINE,JSON.stringify(b))}catch{fail('CLOUD_BASELINE_UNAVAILABLE')}
 }
 function ensureLocal(storage){
  const raw=storage.getItem(Backup.KEY);
  if(raw===null)fail('CLOUD_LOCAL_SAVE_MISSING');
  let state;try{state=JSON.parse(raw)}catch{fail('CLOUD_LOCAL_SAVE_INVALID')}
  if(!state||state.version!==1||!Array.isArray(state.objects))fail('CLOUD_LOCAL_SAVE_INVALID');
  if(Object.hasOwn(state,'pendingCardOutboxPurges')&&
    (!Array.isArray(state.pendingCardOutboxPurges)||state.pendingCardOutboxPurges.length))
   fail('CLOUD_DESTRUCTION_PENDING');
  return raw;
 }
 function create({storage,fetchImpl,notify=()=>{},intervalMs=60000,observeWorld=null}={}){
  if(!storage||typeof storage.getItem!=='function'||typeof storage.setItem!=='function'||typeof fetchImpl!=='function')
   fail('CLOUD_DEPENDENCIES_MISSING');
  let token='',phase='disconnected',error='',lastSynced=null,timer=null,busy=false,epoch=0,abort=null,sceneSynced=null,scenePhase='disabled';
  const status=()=>({phase,error,last_synced:lastSynced,authorized:!!token,
   repo:REPO,branch:BRANCH,path:PATH,token_persisted:false,
   scene_phase:scenePhase,scene_last_synced:sceneSynced,scene_path:SCENE_PATH});
  function publish(){try{notify(status())}catch{}}
  function state(p,e=''){phase=p;error=e;publish()}
  function stop(){if(timer!==null){clearInterval(timer);timer=null}}
  async function request(route,options={}){
   if(!token)fail('CLOUD_NOT_AUTHORIZED');
   let res;
   try{res=await fetchImpl(API+route,{...options,signal:abort?.signal,
    headers:{Accept:'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28',
     Authorization:'Bearer '+token,...(options.headers||{})},
    credentials:'omit',cache:'no-store',redirect:'error',referrerPolicy:'no-referrer'})}
   catch{fail('CLOUD_NETWORK_FAILED')}
   if(res.status===401)fail('CLOUD_TOKEN_INVALID');
   if(res.status===403)fail('CLOUD_PERMISSION_DENIED');
   if(res.status===429)fail('CLOUD_RATE_LIMITED');
   return res;
  }
  async function json(response,code){
   try{return await response.json()}catch{fail(code)}
  }
  async function remote(target=PATH){
   const res=await request('/contents/'+target+'?ref='+encodeURIComponent(BRANCH));
   if(res.status===404)return null;
   if(!res.ok)fail('CLOUD_READ_FAILED');
   const file=await json(res,'CLOUD_REMOTE_UNREADABLE');
   if(file.path!==target||!(/^[a-f0-9]{40}$/).test(file.sha||''))fail('CLOUD_REMOTE_UNREADABLE');
   return file;
  }
  function existingSceneBaseline(){
   try{const x=JSON.parse(storage.getItem(SCENE_BASELINE)||'null');
    return x?.schema==='nw.world-observation-baseline.v1'&&x.repo===REPO&&
      x.branch===BRANCH&&x.path===SCENE_PATH&&
      /^[a-f0-9]{40}$/.test(x.blob_sha||'')&&/^[a-f0-9]{64}$/.test(x.source_sha256||'')?x:null;
   }catch{return null}
  }
  function saveSceneBaseline(blob,hash){
   try{storage.setItem(SCENE_BASELINE,JSON.stringify({
    schema:'nw.world-observation-baseline.v1',repo:REPO,branch:BRANCH,
    path:SCENE_PATH,blob_sha:blob,source_sha256:hash
   }))}catch{fail('CLOUD_SCENE_BASELINE_UNAVAILABLE')}
  }
  async function sha256(value){
   if(!globalThis.crypto?.subtle)fail('CLOUD_CRYPTO_UNAVAILABLE');
   const hash=await globalThis.crypto.subtle.digest('SHA-256',bytes(value));
   return [...new Uint8Array(hash)].map(b=>b.toString(16).padStart(2,'0')).join('');
  }
  async function syncScene(forceVerify,started){
   if(typeof observeWorld!=='function'){scenePhase='disabled';return}
   const scene=observeWorld();
   if(scene?.schema!=='nw.world-observation.v1')fail('CLOUD_SCENE_INVALID');
   const raw=JSON.stringify(scene);
   if(bytes(raw).length>150000)fail('CLOUD_SCENE_TOO_LARGE');
   const hash=await sha256(raw),old=existingSceneBaseline();
   if(!forceVerify&&old?.source_sha256===hash){scenePhase='synced';return}
   scenePhase='syncing';publish();
   const remoteFile=await remote(SCENE_PATH);
   if(remoteFile){
    if(!remoteFile.content)fail('CLOUD_SCENE_REMOTE_UNREADABLE');
    let envelope;
    try{envelope=JSON.parse(decode(remoteFile.content))}
    catch{fail('CLOUD_SCENE_REMOTE_UNREADABLE')}
    if(envelope?.schema!=='nw.world-observation-backup.v1'||
      envelope.source_sha256!==await sha256(envelope.source_text||''))
      fail('CLOUD_SCENE_REMOTE_UNREADABLE');
    if(envelope.source_sha256===hash){
     saveSceneBaseline(remoteFile.sha,hash);scenePhase='synced';
     sceneSynced=new Date().toISOString();publish();return;
    }
    if(!old||old.blob_sha!==remoteFile.sha)fail('CLOUD_SCENE_REMOTE_CONFLICT');
   }else if(old)fail('CLOUD_SCENE_REMOTE_MISSING');
   if(started!==epoch||!token)fail('CLOUD_SESSION_CANCELLED');
   if(JSON.stringify(observeWorld())!==raw)fail('CLOUD_SCENE_CHANGED');
   const envelope=JSON.stringify({schema:'nw.world-observation-backup.v1',
    source_sha256:hash,source_text:raw});
   const payload={message:'data(world-observation): synchronized world geometry and settings',
    content:encode(envelope),branch:BRANCH};
   if(remoteFile)payload.sha=remoteFile.sha;
   const written=await request('/contents/'+SCENE_PATH,{method:'PUT',
    headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
   if(written.status!==200&&written.status!==201)fail('CLOUD_SCENE_WRITE_FAILED');
   const saved=await json(written,'CLOUD_SCENE_WRITE_UNVERIFIED');
   if(saved.content?.path!==SCENE_PATH||!(/^[a-f0-9]{40}$/).test(saved.content?.sha||''))
    fail('CLOUD_SCENE_WRITE_UNVERIFIED');
   const reread=await remote(SCENE_PATH);
   if(!reread||reread.sha!==saved.content.sha)fail('CLOUD_SCENE_WRITE_UNVERIFIED');
   saveSceneBaseline(saved.content.sha,hash);
   sceneSynced=new Date().toISOString();scenePhase='synced';publish();
  }
  async function syncCards(forceVerify,started){
       const original=ensureLocal(storage);
       const backup=await Backup.exportBackup(storage);
       const verified=await Backup.inspectBackup(backup);
       if(bytes(backup).length>MAX_BYTES)fail('CLOUD_SNAPSHOT_TOO_LARGE');
       if(original!==storage.getItem(Backup.KEY))fail('CLOUD_LOCAL_CHANGED');
       const old=baseline(storage);
       // A successful sync can skip both network calls if the persisted source is unchanged.
       if(!forceVerify&&old&&old.source_sha256===verified.sha256)return
       state('syncing');
       const head=await remote();
       if(head){
        // Verify contents before adopting an unfamiliar remote state.
        if(head.content){
         let oldRemote;try{oldRemote=await Backup.inspectBackup(decode(head.content))}
         catch{fail('CLOUD_REMOTE_UNREADABLE')}
         if(oldRemote.sha256===verified.sha256){
          writeBaseline(storage,head.sha,verified.sha256);
          lastSynced=new Date().toISOString();return;
         }
        }
        if(!old||old.blob_sha!==head.sha)fail('CLOUD_REMOTE_CONFLICT');
       }else if(old)fail('CLOUD_REMOTE_MISSING');
       if(started!==epoch||!token)fail('CLOUD_SESSION_CANCELLED');
       if(original!==storage.getItem(Backup.KEY))fail('CLOUD_LOCAL_CHANGED');
       const payload={message:'data(world-save): synchronized owner world snapshot',
        content:encode(backup),branch:BRANCH};
       if(head)payload.sha=head.sha;
       const written=await request('/contents/'+PATH,{method:'PUT',
        headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
       if(written.status!==200&&written.status!==201)fail('CLOUD_WRITE_FAILED');
       const saved=await json(written,'CLOUD_WRITE_UNVERIFIED');
       if(saved.content?.path!==PATH||!(/^[a-f0-9]{40}$/).test(saved.content?.sha||'')||
        !(/^[a-f0-9]{40}$/).test(saved.commit?.sha||''))fail('CLOUD_WRITE_UNVERIFIED');
       const reread=await remote();
       if(!reread||reread.sha!==saved.content.sha)fail('CLOUD_WRITE_UNVERIFIED');
       writeBaseline(storage,saved.content.sha,verified.sha256);
       lastSynced=new Date().toISOString();
       return;
   
  }
  async function sync({forceVerify=false}={}){
   if(!token||busy)return status();
   busy=true;const started=epoch;
   try{
    await syncCards(forceVerify,started);
    await syncScene(forceVerify,started);
    if(started===epoch)state('synced');
    return status();
   }catch(e){
    if(started===epoch){stop();if(scenePhase==='syncing')scenePhase='error';
     state('error',typeof e?.code==='string'?e.code:'CLOUD_UNKNOWN_ERROR')}
    return status();
   }finally{busy=false}
  }
  async function connect(candidate){
   stop();if(abort)abort.abort();abort=new AbortController();token='';epoch++;const mine=epoch;
   if(typeof candidate!=='string'||candidate.trim().length<10||candidate.length>1024)
    return state('error','CLOUD_TOKEN_REQUIRED'),status();
   token=candidate.trim();state('verifying');
   try{
    ensureLocal(storage);
    const infoResponse=await request('');
    if(!infoResponse.ok)fail('CLOUD_REPO_UNAVAILABLE');
    const info=await json(infoResponse,'CLOUD_REPO_UNAVAILABLE');
    if(info.full_name!==REPO||info.private!==true)fail('CLOUD_PRIVATE_REQUIRED');
    const branchResponse=await request('/git/ref/heads/'+BRANCH);
    if(!branchResponse.ok)fail('CLOUD_BRANCH_UNAVAILABLE');
    const ref=await json(branchResponse,'CLOUD_BRANCH_UNAVAILABLE');
    if(ref.ref!=='refs/heads/'+BRANCH)fail('CLOUD_BRANCH_UNAVAILABLE');
    if(mine!==epoch)return status();
    state('ready');
    const result=await sync({forceVerify:true});
    if(result.phase==='synced')
     timer=setInterval(()=>{if(token&&!busy)sync()},Math.max(30000,intervalMs));
    return result;
   }catch(e){token='';state('error',typeof e?.code==='string'?e.code:'CLOUD_UNKNOWN_ERROR');return status()}
  }
  function disconnect(){stop();epoch++;if(abort)abort.abort();abort=null;token='';state('disconnected');return status()}
  return Object.freeze({connect,sync,disconnect,status});
 }
 return Object.freeze({REPO,BRANCH,PATH,BASELINE,SCENE_PATH,SCENE_BASELINE,create});
});
