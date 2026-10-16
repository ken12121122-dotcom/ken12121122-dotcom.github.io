import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFileSync} from 'node:fs';
const require=createRequire(import.meta.url);
const Craft=require('../living-library/live/object-craft.js');
const Observer=require('../living-library/live/card-observer.js');
const Inbox=require('../living-library/live/card-github-inbox.js');
const sha=c=>c.repeat(40);
function fixture(){
 const map=new Map(),storage={getItem:k=>map.get(k)||null,setItem:(k,v)=>map.set(k,v)};
 const card=Craft.createBlankCard(storage,{randomUUID:()=> 'github-test-card'});
 const image={dataUrl:'data:image/jpeg;base64,/9j/2Q==',scale:1.2,x:40,y:60};
 Craft.editCard(storage,card.id,{title:'我的唯一圖鑑',cardNumber:'001',description:'只測試私人傳送',image});
 Craft.placeCard(storage,card.id,'local.atlas.compendium',9);
 return {storage,cardId:card.id,image};
}
function server({privateRepo=true,auth=true,branch=true,failPut=0,verifyMismatch=false}={}){
 let written=false,stored=false,count=0,putPayload=null,requests=[];
 async function fetchImpl(url,options={}){
  requests.push({url,method:options.method||'GET',auth:options.headers?.Authorization});
  assert.ok(url.startsWith('https://api.github.com/repos/ken12121122-dotcom/gen2-knowledge'));
  assert.equal(options.credentials,'omit');
  assert.equal(options.cache,'no-store');
  const method=options.method||'GET';
  const respond=(status,data)=>({status,ok:status>=200&&status<300,json:async()=>data});
  if(url.endsWith('/gen2-knowledge'))return respond(auth?200:401,{full_name:Inbox.REPO,private:privateRepo});
  if(url.endsWith('/git/ref/heads/card-inbox'))return respond(branch?200:404,{ref:'refs/heads/card-inbox',object:{sha:sha('a')}});
  if(url.includes('/contents/')&&method==='GET')return stored?respond(200,{path:Inbox.FOLDER+'/local.card.github-test-card.json',sha:verifyMismatch?sha('d'):sha('b')}):respond(404,{message:'Not Found'});
  if(url.includes('/contents/')&&method==='PUT'){
   assert.equal(failPut||200,200,'test expected success');
   putPayload=JSON.parse(options.body);count++;written=true;stored=true;
   assert.equal(putPayload.branch,'card-inbox');
   assert.ok(!url.includes('/main')&&!url.includes('.github.io'));
   return respond(201,{content:{path:Inbox.FOLDER+'/local.card.github-test-card.json',sha:sha('b')},commit:{sha:sha('c')}});
  }
  throw Error('unexpected URL '+url);
 }
 return {fetchImpl,get state(){return {written,stored,count,putPayload,requests}}};
}
test('single placed photo card is allowlisted; token and other objects are excluded',()=>{
 const {storage,cardId,image}=fixture(),raw=storage.getItem(Craft.KEY);
 const item=Inbox.buildPacket({storage,craft:Craft,observer:Observer,cardId,clock:()=> '2026-10-16T00:00:00Z'});
 assert.equal(item.packet.schema,'nw.github-card-inbox.v1');
 assert.equal(item.packet.card.card_number,'001');
 assert.equal(item.packet.card.image.dataUrl,image.dataUrl);
 assert.equal(item.path,Inbox.FOLDER+'/'+cardId+'.json');
 assert.ok(!item.json.includes('github_pat'));
 assert.equal(raw,storage.getItem(Craft.KEY));
});
test('private repository + separate branch + single card write + GET proof returns success',async()=>{
 const {storage,cardId,image}=fixture(),remote=server(),before=storage.getItem(Craft.KEY);
 const result=await Inbox.upload({storage,craft:Craft,observer:Observer,cardId,
  token:'github_pat_TEST_NOT_REAL',fetchImpl:remote.fetchImpl});
 assert.equal(result.ok,true);assert.equal(result.branch,'card-inbox');
 assert.equal(result.commit_sha,sha('c'));assert.equal(result.includes_compressed_photo,true);
 const payload=remote.state.putPayload;
 const decoded=Buffer.from(payload.content,'base64').toString('utf8');
 assert.equal(JSON.parse(decoded).card.image.dataUrl,image.dataUrl);
 assert.equal(JSON.parse(decoded).card.card_id,cardId);
 assert.equal(storage.getItem(Craft.KEY),before);
 assert.equal(remote.state.requests.length,5);
 assert.ok(!JSON.stringify(result).includes('github_pat_TEST_NOT_REAL'));
});
test('hard stop: refuses public repo, without transmitting single private card',async()=>{
 const {storage,cardId}=fixture(),remote=server({privateRepo:false});
 await assert.rejects(Inbox.upload({storage,craft:Craft,observer:Observer,cardId,
  token:'github_pat_TEST_NOT_REAL',fetchImpl:remote.fetchImpl}),{code:'GITHUB_PRIVATE_REQUIRED'});
 assert.equal(remote.state.written,false);assert.equal(remote.state.requests.length,1);
});
test('hard stop: refuses missing separate inbox branch and bad credentials',async()=>{
 const {storage,cardId}=fixture();
 const remote=server({branch:false});
 await assert.rejects(Inbox.upload({storage,craft:Craft,observer:Observer,cardId,
  token:'github_pat_TEST_NOT_REAL',fetchImpl:remote.fetchImpl}),{code:'GITHUB_INBOX_BRANCH_MISSING'});
 assert.equal(remote.state.written,false);
 const unauthorized=server({auth:false});
 await assert.rejects(Inbox.upload({storage,craft:Craft,observer:Observer,cardId,
  token:'github_pat_TEST_NOT_REAL',fetchImpl:unauthorized.fetchImpl}),{code:'GITHUB_AUTH_FAILED'});
 assert.equal(unauthorized.state.written,false);
});
test('verify mismatch fails closed, never display false success',async()=>{
 const {storage,cardId}=fixture(),remote=server({verifyMismatch:true});
 await assert.rejects(Inbox.upload({storage,craft:Craft,observer:Observer,cardId,
  token:'github_pat_TEST_NOT_REAL',fetchImpl:remote.fetchImpl}),{code:'GITHUB_VERIFY_FAILED'});
});
test('A cannot upload unplaced card; no read from unrelated localStorage',async()=>{
 const {storage,cardId}=fixture(),remote=server();
 Craft.placeCard(storage,cardId,null);
 await assert.rejects(Inbox.upload({storage,craft:Craft,observer:Observer,cardId,
  token:'github_pat_TEST_NOT_REAL',fetchImpl:remote.fetchImpl}),{code:'GITHUB_CARD_NOT_IN_READER'});
 assert.equal(remote.state.requests.length,0);
});
test('real scene has GBA SELECT switch and A sender with visible statuses',()=>{
 const html=readFileSync(new URL('../living-library/live/index.html',import.meta.url),'utf8');
 assert.match(html,/card-github-inbox\.js/);
 assert.match(html,/observeCard:id=>window\.LivingLibrary\.sendLocalCardToGithub\(id\)/);
 assert.match(html,/id="atlasGithubDialog"/);
 assert.match(html,/type="password"/);
 const model=readFileSync(new URL('../living-library/live/atlas-model.js',import.meta.url),'utf8');
 assert.match(model,/上傳成功/);
 assert.match(model,/上傳失敗/);
 const runtime=readFileSync(new URL('../living-library/live/atlas-runtime.js',import.meta.url),'utf8');
 assert.match(runtime,/reportGithubUpload/);
 assert.match(runtime,/key==='SELECT'/);
});
