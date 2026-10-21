import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const World=require('../living-library/live/world-save-sync.js');
const Observer=require('../living-library/live/world-observation.js');
const storage=()=>{const m=new Map([[ 'living-library-player-objects-v1',JSON.stringify({version:1,objects:[{id:'local.card.synthetic-1',objectType:'card',templateId:'blank-card-v1',revision:1,title:'測試'}]})]]);return {getItem:k=>m.get(k)??null,setItem:(k,v)=>m.set(k,String(v)),values:()=>[...m.values()]};};
function github(){
 const data=new Map(),writes=[];let tick=0;
 const response=(status,body)=>({status,ok:status>=200&&status<300,json:async()=>body});
 const fetchImpl=async(url,opt)=>{
  if(url.endsWith('/gen2-knowledge'))return response(200,{full_name:World.REPO,private:true});
  if(url.endsWith('/git/ref/heads/card-inbox'))return response(200,{ref:'refs/heads/card-inbox'});
  const path=new URL(url).pathname.split('/contents/')[1];
  if(!path)throw Error('unknown URL');
  const before=data.get(path);
  if((opt.method||'GET')==='GET')return before?response(200,{path,sha:before.sha,content:Buffer.from(before.text).toString('base64')}):response(404,{});
  const body=JSON.parse(opt.body);if(before&&body.sha!==before.sha)return response(409,{});
  const sha=(++tick%2?'a':'b').repeat(40);
  data.set(path,{sha,text:Buffer.from(body.content,'base64').toString('utf8')});
  writes.push(path);
  return response(before?200:201,{content:{path,sha},commit:{sha:'c'.repeat(40)}});
 };
 return {data,writes,fetchImpl};
}
test('scene save is private, uses same session and does not rewrite local object data',async()=>{
 const st=storage(),remote=github(),before=st.getItem('living-library-player-objects-v1');
 let x=1;
 const live=()=>({ready:true,player:{x,y:0,z:3},camera:{theta:0,phi:0,distance:15},
  books:[],sceneObjects:[{id:'scene.floor',position:{x:0,y:0,z:0}}]});
 const client=World.create({storage:st,fetchImpl:remote.fetchImpl,intervalMs:999999,
   observeWorld:()=>Observer.collect(st,live)});
 assert.equal((await client.connect('dummy-auth-value')).phase,'synced');
 assert.equal(client.status().scene_phase,'synced');
 assert.deepEqual(remote.writes,[World.PATH,World.SCENE_PATH]);
 assert.equal(st.getItem('living-library-player-objects-v1'),before);
 const scene=JSON.parse(remote.data.get(World.SCENE_PATH).text);
 assert.equal(JSON.parse(scene.source_text).session_scene.player.x,1);
 await client.sync();assert.equal(remote.writes.length,2);
 x=2;assert.equal((await client.sync()).phase,'synced');
 assert.deepEqual(remote.writes,[World.PATH,World.SCENE_PATH,World.SCENE_PATH]);
 client.disconnect();assert.ok(!st.values().some(s=>s.includes('dummy-auth-value')));
});
test('unrecognized remote scene blob change fails closed without overwriting',async()=>{
 const st=storage(),remote=github();let x=1;
 const snapshot=()=>Observer.collect(st,()=>({ready:true,player:{x,y:0,z:0},camera:{},books:[],sceneObjects:[]}));
 const client=World.create({storage:st,fetchImpl:remote.fetchImpl,observeWorld:snapshot,intervalMs:999999});
 assert.equal((await client.connect('dummy-auth-value')).phase,'synced');
 client.disconnect();
 const prior=remote.data.get(World.SCENE_PATH);
 remote.data.set(World.SCENE_PATH,{sha:'f'.repeat(40),text:prior.text});
 x=2;
 const result=await client.connect('dummy-auth-value');
 assert.equal(result.error,'CLOUD_SCENE_REMOTE_CONFLICT');
 assert.equal(remote.writes.length,2);
 client.disconnect();
});
