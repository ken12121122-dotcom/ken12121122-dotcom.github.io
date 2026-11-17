import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
const require=createRequire(import.meta.url);
const W=require('../living-library/live/wardrobe.js');
const Voice=require('../living-library/live/atlas-voice.js');
const P=require('../living-library/live/character-packs.js');
const live=new URL('../living-library/live/',import.meta.url);
const html=readFileSync(new URL('index.html',live),'utf8');

function memory(){const m=new Map();return {getItem:k=>m.has(k)?m.get(k):null,setItem:(k,v)=>m.set(k,String(v)),m}}

test('roster keeps the original keeper as default; every pack manifest is valid, measured and credited',()=>{
 assert.equal(W.ROSTER[0].id,W.DEFAULT_ID);assert.equal(W.ROSTER[0].kind,'builtin');
 for(const e of W.ROSTER.filter(e=>e.kind==='pack')){
  const url=new URL(e.manifest,live),m=P.validateManifest(JSON.parse(readFileSync(url,'utf8')));
  assert.equal(m.id,e.id);assert.ok(m.credit.author&&m.credit.license&&m.credit.modified===true);
  for(const f of m.files){const bytes=readFileSync(new URL(f.path,url));
   assert.equal(bytes.length,f.bytes,f.path);assert.equal(createHash('sha256').update(bytes).digest('hex'),f.sha256,f.path)}
  const glb=readFileSync(new URL(P.fileOf(m,'model').path,url)),json=JSON.parse(glb.subarray(20,20+glb.readUInt32LE(12)).toString('utf8'));
  for(const ext of m.requires)assert.ok(json.extensionsUsed.includes(ext),ext);
  assert.ok(json.images.every(i=>i.mimeType==='image/ktx2'||json.textures.every(t=>t.extensions?.KHR_texture_basisu)),'all textures GPU-compressed');
  assert.equal(json.skins[0].joints.length,102);assert.ok(json.animations[0].channels.length>=300);
  assert.ok(json.asset.extras?.author?.includes('Jungle Jim'),'embedded credit kept');
 }
});

test('choice persistence rejects unknown ids and survives broken storage',()=>{
 const s=memory();assert.equal(W.loadChoice(s),W.DEFAULT_ID);
 assert.equal(W.saveChoice(s,'anna.ipati.v1'),true);assert.equal(W.loadChoice(s),'anna.ipati.v1');
 assert.equal(W.saveChoice(s,'evil'),false);s.setItem(W.STORAGE_KEY,'evil');assert.equal(W.loadChoice(s),W.DEFAULT_ID);
 const broken={getItem(){throw Error('denied')},setItem(){throw Error('denied')}};
 assert.equal(W.loadChoice(broken),W.DEFAULT_ID);assert.equal(W.saveChoice(broken,W.DEFAULT_ID),false);
});

test('browsing never changes the worn outfit; only confirm does',()=>{
 const st=W.createState(W.DEFAULT_ID);assert.equal(st.next(),null,'closed state ignores browsing');
 st.enter();st.next();assert.equal(st.current.id,'anna.ipati.v1');assert.equal(st.applied,W.DEFAULT_ID);
 st.leave();assert.equal(st.applied,W.DEFAULT_ID);assert.equal(st.open,false);
 assert.equal(st.toggle(),true);assert.equal(st.current.id,W.DEFAULT_ID,'re-entering starts at worn outfit');
 st.prev();assert.equal(st.confirm(),'anna.ipati.v1');assert.equal(st.open,false);
 st.enter();assert.equal(st.current.id,'anna.ipati.v1');st.leave();st.revert('bad');assert.equal(st.applied,'anna.ipati.v1');
});

test('voice: spell 百面相 toggles, Book keeps its boundaries, both share one recognizer',()=>{
 let now=5000,book=0,kigae=0;const sent=[],native={postMessage:x=>sent.push(JSON.parse(x))};
 const v=Voice.create({host:{LivingLibraryNativeVoice:native},now:()=>now,lang:'ja-JP',commands:{
  book:{patterns:W.BOOK_PATTERNS,run:()=>book++},kigae:{patterns:W.SPELL_PATTERNS,run:()=>kigae++}}});
 v.start();const session=sent.at(-1).session;
 const say=text=>{now+=1000;native.onmessage({data:JSON.stringify({v:1,session,status:'result',text})})};
 for(const t of ['百面相','ひゃくめんそう','ヒャクメンソウ','百面相！','Hyakumensou','hyakumenso'])say(t);assert.equal(kigae,6);
 say('notebook');say('百面');say('面相');say('hyakumensouu');say('換衣服');assert.equal(book+kigae,6);
 say('Book');say('ブック');say('布克');assert.equal(book,3);
});

test('web recognizer receives the configured language and counts each new Japanese word once',()=>{
 let instance,count=0;class R{constructor(){instance=this}start(){this.onstart?.()}abort(){}}
 const v=Voice.create({host:{SpeechRecognition:R,setTimeout(){},clearTimeout(){}},lang:'ja-JP',
  commands:{kigae:{patterns:W.SPELL_PATTERNS,run:()=>count++}}});
 v.start();assert.equal(instance.lang,'ja-JP');
 const ev=(t,f=false)=>({resultIndex:0,results:[Object.assign([{transcript:t}],{isFinal:f})]});
 instance.onresult(ev('百面'));assert.equal(count,0);instance.onresult(ev('百面相'));assert.equal(count,1);
 instance.onresult(ev('百面相',true));assert.equal(count,1);
});

test('legacy function commands and default en-US language still work',()=>{
 let instance;class R{constructor(){instance=this}start(){}abort(){}}
 const v=Voice.create({host:{SpeechRecognition:R,setTimeout(){},clearTimeout(){}},commands:{book:()=>{}}});v.start();assert.equal(instance.lang,'en-US');
 assert.throws(()=>Voice.create({host:{},commands:{x:{patterns:['x']}}}));
});

test('addon importer rewrites bare three and resolves relative addon imports to the same engine',async()=>{
 const engine='https://cdn.jsdelivr.net/npm/three@0.185.0/build/three.module.js';
 assert.equal(W.addonBase(engine),'https://cdn.jsdelivr.net/npm/three@0.185.0/examples/jsm/');
 assert.equal(W.addonBase('https://unpkg.com/three@0.185.0/build/three.module.js'),'https://unpkg.com/three@0.185.0/examples/jsm/');
 assert.throws(()=>W.addonBase('https://evil.example/x.js'));assert.throws(()=>W.addonBase(''));
 const files={'loaders/GLTFLoader.js':"import {A} from 'three';\nimport { t } from '../utils/BufferGeometryUtils.js';\nimport {c} from \"../utils/SkeletonUtils.js\";",
  'loaders/KTX2Loader.js':"import {B} from 'three';import {W} from '../utils/WorkerPool.js';import {Z} from '../libs/zstddec.module.js';import {S} from '../math/ColorSpaces.js';",
  'utils/BufferGeometryUtils.js':"import {X} from 'three';",'utils/SkeletonUtils.js':"import {Y} from 'three';",'utils/WorkerPool.js':'export class WorkerPool{}',
  'libs/zstddec.module.js':'export class ZSTDDecoder{}','math/ColorSpaces.js':"import {C} from 'three';"};
 const fetched=[],modules=new Map();let n=0;
 class GLTFLoader{setMeshoptDecoder(d){this.d=d}setKTX2Loader(k){this.k=k}}
 class KTX2Loader{setTranscoderPath(p){this.p=p;return this}detectSupport(r){this.r=r;return this}}
 const loader=await W.createGLTFLoader({engineUrl:engine,renderer:'R',
  fetchText:async u=>{fetched.push(u);const k=u.slice(W.addonBase(engine).length);assert.ok(k in files,'unexpected fetch '+k);return files[k]},
  makeModuleUrl:src=>{const id='blob:'+(++n);modules.set(id,src);return id},
  importModule:async u=>u.endsWith('meshopt_decoder.module.js')?{MeshoptDecoder:{ready:Promise.resolve()}}:modules.get(u).includes('{A}')?{GLTFLoader}:{KTX2Loader}});
 assert.ok(loader instanceof GLTFLoader&&loader.d&&loader.k instanceof KTX2Loader);
 assert.equal(loader.k.p,W.addonBase(engine)+'libs/basis/');assert.equal(loader.k.r,'R');
 assert.equal(new Set(fetched).size,fetched.length,'each addon fetched once');assert.equal(fetched.length,7);
 for(const src of modules.values()){assert.ok(!/from\s*['"](three|\.\.?\/)/.test(src.replace(engine,'')),src)}
 const cyc={'a.js':"import {b} from './b.js';",'b.js':"import {a} from './a.js';"};
 const imp=W.createAddonImporter({engineUrl:engine,fetchText:async u=>cyc[u.split('/').pop()],importModule:async()=>({}),makeModuleUrl:()=>'blob:x'});
 await assert.rejects(imp.moduleUrl('a.js'),/循環/);
});

test('fitTransform puts feet on the floor, centres X/Z and normalises height',()=>{
 const f=W.fitTransform({min:{x:-1,y:-.5,z:1},max:{x:3,y:3,z:3}},2.45);
 assert.ok(Math.abs(f.scale*3.5-2.45)<1e-9);assert.ok(Math.abs(f.offset.y-.5*f.scale)<1e-9);
 assert.ok(Math.abs(f.offset.x+f.scale)<1e-9);assert.ok(Math.abs(f.offset.z+2*f.scale)<1e-9);
 assert.throws(()=>W.fitTransform({min:{x:0,y:1,z:0},max:{x:0,y:1,z:0}},2));
});

test('index wiring: wardrobe loads before voice, skins stay under protected keeper, P0 scan semantics untouched',()=>{
 const list=html.match(/const scripts=\[([^\]]+)\]/)[1];
 assert.ok(list.indexOf("'wardrobe.js'")>=0&&list.indexOf("'wardrobe.js'")<list.indexOf("'atlas-voice.js'"));
 assert.match(html,/dress:\{patterns:window\.LivingWardrobe\.SPELL_PATTERNS/);
 assert.match(html,/lang:'ja-JP'/);
 assert.match(html,/const keeperDefaultParts=\[\.\.\.keeper\.children\];/);
 const block=html.slice(html.indexOf('function createWardrobeWorld(){'),html.indexOf('wardrobeWorld=createWardrobeWorld();'));
 assert.ok(block.length>1000);
 assert.ok(!/\bscene\.add\(/.test(block),'wardrobe must not add scannable roots to the library scene');
 assert.match(block,/keeper\.add\(skin\.root\)/);
 assert.ok(!/selectedScanIds|toggleSelected/.test(block+readFileSync(new URL('wardrobe.js',live),'utf8')));
 assert.match(html,/<section id="wardrobeWorld" hidden/);
});
