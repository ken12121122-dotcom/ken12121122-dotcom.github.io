import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {webcrypto,createHash} from 'node:crypto';
const require=createRequire(import.meta.url);
const P=require('../living-library/live/character-packs.js');
const sha=b=>createHash('sha256').update(b).digest('hex');
const BASE='https://example.test/packs/x.v1/';
function pack(model=Buffer.from('MODEL-v1-'.repeat(50)),version=1){
 const thumb=Buffer.from('THUMB');
 return {bytes:{'m.glb':model,'t.webp':thumb},manifest:{schema:P.SCHEMA,id:'x.v1',version,name:'X',display:{height:2.4,idleTime:.2},
  credit:{title:'T',author:'A',license:'CC-BY-4.0',url:'https://a.example/'},
  files:[{role:'model',path:'m.glb',type:'model/gltf-binary',bytes:model.length,sha256:sha(model)},{role:'thumbnail',path:'t.webp',type:'image/webp',bytes:thumb.length,sha256:sha(thumb)}]}};
}
function env(served){
 const store=new Map(),cache={match:async k=>store.has(k)?new Response(store.get(k)):undefined,put:async(k,r)=>{store.set(k,new Uint8Array(await r.arrayBuffer()))},delete:async k=>store.delete(k)};
 const mem=new Map(),storage={getItem:k=>mem.has(k)?mem.get(k):null,setItem:(k,v)=>mem.set(k,v)};
 let fetches=0,persisted=0;
 const fetch=async(url)=>{fetches++;const name=new URL(url).pathname.split('/').pop();const b=served.bytes[name];
  if(name==='manifest.json'){const r=new Response(JSON.stringify(served.manifest));Object.defineProperty(r,'url',{value:url});return r}
  if(!b)return new Response('',{status:404});
  // stream in two chunks so progress reports are exercised
  const half=Math.floor(b.length/2);return new Response(new ReadableStream({start(c){c.enqueue(b.subarray(0,half));c.enqueue(b.subarray(half));c.close()}}))};
 const s=P.createStore({fetch,caches:{open:async()=>cache},crypto:webcrypto,storage,persist:async()=>persisted++});
 return {s,store,storage,get fetches(){return fetches},get persisted(){return persisted}};
}

test('manifest validation is strict: schema, ids, paths, types, sizes, hashes and credit',()=>{
 const ok=pack().manifest;assert.equal(P.validateManifest(ok),ok);
 const bad=[m=>m.schema='x',m=>m.id='../x',m=>m.version=0,m=>m.credit.author='',m=>m.credit.url='http://x',m=>m.display.height=0,
  m=>m.files[0].path='../m.glb',m=>m.files[0].path='a/b.glb',m=>m.files[0].type='text/html',m=>m.files[0].sha256='abc',
  m=>m.files[0].bytes=P.MAX_MODEL_BYTES+1,m=>m.files.push({...m.files[1]}),m=>m.files.shift(),m=>m.files[1].role='script'];
 for(const f of bad){const m=structuredClone(ok);f(m);assert.throws(()=>P.validateManifest(m),/角色包清單無效/,f.toString())}
});

test('install streams with progress, verifies, stores, then opens offline without network',async()=>{
 const p=pack(),e=env(p),progress=[];
 assert.equal(await e.s.status(p.manifest,BASE),'missing');
 await assert.rejects(e.s.open(p.manifest,BASE),/尚未下載/);
 const r=await e.s.install(p.manifest,BASE,{onProgress:(got,total)=>progress.push([got,total])});
 assert.equal(r.durable,true);assert.equal(e.persisted,1);
 const total=p.manifest.files.reduce((n,f)=>n+f.bytes,0);
 assert.ok(progress.length>=3&&progress.every(([g,t],i)=>t===total&&(i===0||g>=progress[i-1][0])));assert.deepEqual(progress.at(-1),[total,total]);
 assert.equal(await e.s.status(p.manifest,BASE),'installed');
 const before=e.fetches,blob=await e.s.open(p.manifest,BASE);
 assert.equal(e.fetches,before,'open never fetches');assert.equal(Buffer.from(await blob.arrayBuffer()).toString(),p.bytes['m.glb'].toString());
 assert.equal(blob.type,'model/gltf-binary');
});

test('corrupt or wrong-size download is rejected and nothing is stored',async()=>{
 const p=pack(),tampered={...p,bytes:{...p.bytes,'m.glb':Buffer.from(p.bytes['m.glb']).fill(65,0,4)}};
 const e=env(tampered);await assert.rejects(e.s.install(p.manifest,BASE),/SHA-256 不符/);
 assert.equal(e.store.size,0);assert.equal(await e.s.status(p.manifest,BASE),'missing');assert.deepEqual(e.s.installed(),{});
 const short={...p,bytes:{...p.bytes,'m.glb':p.bytes['m.glb'].subarray(1)}};
 await assert.rejects(env(short).s.install(p.manifest,BASE),/大小不符/);
 const long={...p,bytes:{...p.bytes,'m.glb':Buffer.concat([p.bytes['m.glb'],Buffer.from('xx')])}};
 await assert.rejects(env(long).s.install(p.manifest,BASE),/超過/);
});

test('new version shows as update, keeps old until the new one verifies, then replaces it; remove clears all',async()=>{
 const v1=pack(),served={...v1},e=env(served);await e.s.install(v1.manifest,BASE);
 const v2=pack(Buffer.from('MODEL-v2-'.repeat(60)),2);
 assert.equal(await e.s.status(v2.manifest,BASE),'update');
 Object.assign(served,{bytes:{...v2.bytes,'m.glb':Buffer.from('broken')},manifest:v2.manifest});
 await assert.rejects(e.s.install(v2.manifest,BASE));
 assert.equal(await e.s.status(v1.manifest,BASE),'installed','failed update leaves v1 usable');
 Object.assign(served,v2);await e.s.install(v2.manifest,BASE);
 assert.equal(await e.s.status(v2.manifest,BASE),'installed');assert.equal(e.store.size,2,'old version files dropped');
 assert.equal(e.s.installed()['x.v1'].version,2);
 assert.equal(await e.s.remove('x.v1'),true);assert.equal(e.store.size,0);assert.equal(await e.s.status(v2.manifest,BASE),'missing');
 assert.equal(await e.s.remove('x.v1'),false);
});

test('concurrent installs share one job; fetchManifest validates and returns the base URL',async()=>{
 const p=pack(),e=env(p);const a=e.s.install(p.manifest,BASE),b=e.s.install(p.manifest,BASE);
 assert.equal(a,b);assert.equal(e.s.busy('x.v1'),true);await a;assert.equal(e.s.busy('x.v1'),false);
 const got=await e.s.fetchManifest(BASE+'manifest.json');assert.equal(got.baseUrl,BASE);assert.equal(got.manifest.id,'x.v1');
});

test('without Cache Storage packs still work for the session and say so',async()=>{
 const p=pack(),mem=new Map();
 const s=P.createStore({fetch:env(p).s&&(async u=>new Response(p.bytes[new URL(u).pathname.split('/').pop()])),crypto:webcrypto,storage:{getItem:k=>mem.get(k)??null,setItem:(k,v)=>mem.set(k,v)}});
 assert.equal(s.durable,false);const r=await s.install(p.manifest,BASE);assert.equal(r.durable,false);
 assert.equal(await s.status(p.manifest,BASE),'installed');
 assert.throws(()=>P.createStore({fetch:()=>{},crypto:{}}),/crypto/);
});
