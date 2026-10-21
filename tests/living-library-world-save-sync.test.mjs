import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const Backup=require('../living-library/live/object-backup.js');
const World=require('../living-library/live/world-save-sync.js');
function storage(initial){
 const data=new Map(initial?[[Backup.KEY,JSON.stringify(initial)]]:[]);
 return {getItem:k=>data.has(k)?data.get(k):null,setItem:(k,v)=>data.set(k,String(v)),
  dump:()=>[...data.entries()]};
}
const one=(revision=1)=>({version:1,objects:[{id:'local.card.test-1',
 objectType:'card',templateId:'blank-card-v1',revision,status:'draft',
 title:'測試圖片',image:{dataUrl:'data:image/jpeg;base64,/9j/4A=='}}]});
const result=(status,data)=>({status,ok:status>=200&&status<300,json:async()=>data});
function github({isPrivate=true,remoteEnvelope=null}={}){
 const requests=[],data={remote:remoteEnvelope,blob:'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'};
 let putCount=0;
 const fetchImpl=async (url,options)=>{
  requests.push({url,method:options.method||'GET',headers:options.headers});
  if(url.endsWith('/gen2-knowledge'))return result(200,{full_name:World.REPO,private:isPrivate});
  if(url.endsWith('/git/ref/heads/card-inbox'))return result(200,{ref:'refs/heads/card-inbox',
   object:{sha:'c'.repeat(40)}});
  if(url.includes('/contents/'+World.PATH)){
   if((options.method||'GET')==='GET')return data.remote===null?result(404,{}):
    result(200,{path:World.PATH,sha:data.blob,content:Buffer.from(data.remote,'utf8').toString('base64')});
   const body=JSON.parse(options.body);
   if(data.remote!==null&&body.sha!==data.blob)return result(409,{});
   data.remote=Buffer.from(body.content,'base64').toString('utf8');
   data.blob=(++putCount%2?'b':'d').repeat(40);
   return result(putCount===1?201:200,{content:{path:World.PATH,sha:data.blob},
    commit:{sha:'e'.repeat(40)}});
  }
  throw Error('unexpected '+url);
 };
 return {fetchImpl,requests,data,get putCount(){return putCount}};
}
test('private session-only GitHub world save, idempotent and preserves device source',async()=>{
 const source=storage(one()),githubMock=github();
 const before=source.getItem(Backup.KEY);
 const client=World.create({storage:source,fetchImpl:githubMock.fetchImpl,intervalMs:100000});
 assert.equal((await client.connect('github_pat_example')).phase,'synced');
 assert.equal(githubMock.putCount,1);
 assert.equal(source.getItem(Backup.KEY),before);
 assert.ok(source.getItem(World.BASELINE));
 assert.ok(!source.dump().some(([k,v])=>v.includes('github_pat_example')));
 assert.equal((await client.sync()).phase,'synced');
 assert.equal(githubMock.putCount,1);
 source.setItem(Backup.KEY,JSON.stringify(one(2)));
 assert.equal((await client.sync()).phase,'synced');
 assert.equal(githubMock.putCount,2);
 assert.equal(source.getItem(Backup.KEY),JSON.stringify(one(2)));
 client.disconnect();
 assert.equal(client.status().authorized,false);
});
test('unfamiliar conflicting cloud data blocks overwrite',async()=>{
 const cloud=storage(one(7)),remote=await Backup.exportBackup(cloud);
 const local=storage(one(2)),mock=github({remoteEnvelope:remote});
 const client=World.create({storage:local,fetchImpl:mock.fetchImpl});
 const state=await client.connect('github_pat_example');
 assert.equal(state.error,'CLOUD_REMOTE_CONFLICT');
 assert.equal(mock.putCount,0);
 assert.equal(local.getItem(Backup.KEY),JSON.stringify(one(2)));
 client.disconnect();
});
test('public repository is rejected and no private data uploaded',async()=>{
 const local=storage(one()),mock=github({isPrivate:false});
 const client=World.create({storage:local,fetchImpl:mock.fetchImpl});
 assert.equal((await client.connect('github_pat_example')).error,'CLOUD_PRIVATE_REQUIRED');
 assert.equal(mock.putCount,0);
 client.disconnect();
});
test('pending destruction cleanup blocks all synchronization',async()=>{
 const local=storage({...one(),pendingCardOutboxPurges:[{id:'local.card.test-1'}]}),mock=github();
 const client=World.create({storage:local,fetchImpl:mock.fetchImpl});
 assert.equal((await client.connect('github_pat_example')).error,'CLOUD_DESTRUCTION_PENDING');
 assert.equal(mock.putCount,0);
 client.disconnect();
});

test('verified private permission may be encrypted after a non-destructive cloud conflict',async()=>{
 const remote=await Backup.exportBackup(storage(one(9)));
 const local=storage(one(2)),mock=github({remoteEnvelope:remote});
 const client=World.create({storage:local,fetchImpl:mock.fetchImpl});
 const connected=await client.connect('github_pat_example');
 assert.equal(connected.phase,'error');
 assert.equal(connected.error,'CLOUD_REMOTE_CONFLICT');
 assert.equal(connected.credential_verified,true);
 assert.equal(mock.putCount,0);
 const observed=[];
 const vault={remember:async(token,password,check)=>{
  assert.equal(check(),true);
  observed.push({token,password});
 }};
 const proof=await client.rememberCredential(vault,'safe password longer');
 assert.deepEqual(proof,{saved:true});
 assert.deepEqual(observed,[{token:'github_pat_example',password:'safe password longer'}]);
 assert.equal(local.getItem(Backup.KEY),JSON.stringify(one(2)));
 client.disconnect();
 assert.equal(client.status().credential_verified,false);
 await assert.rejects(client.rememberCredential(vault,'safe password longer'),/VAULT_SESSION_NOT_VERIFIED/);
});
test('invalid PAT/repo is never permitted into encrypted credential vault',async()=>{
 const local=storage(one()),mock=github({isPrivate:false});
 const client=World.create({storage:local,fetchImpl:mock.fetchImpl});
 assert.equal((await client.connect('github_pat_bad')).credential_verified,false);
 let called=false;
 await assert.rejects(client.rememberCredential({remember:async()=>{called=true}},'valid password length'),/VAULT_SESSION_NOT_VERIFIED/);
 assert.equal(called,false);
 client.disconnect();
});
