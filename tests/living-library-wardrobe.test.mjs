import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
const require=createRequire(import.meta.url);
const W=require('../living-library/live/wardrobe.js');
const Voice=require('../living-library/live/atlas-voice.js');
const live=new URL('../living-library/live/',import.meta.url);
const html=readFileSync(new URL('index.html',live),'utf8');

function memory(){const m=new Map();return {getItem:k=>m.has(k)?m.get(k):null,setItem:(k,v)=>m.set(k,String(v)),m}}

test('roster keeps the original keeper as default and every GLB entry carries CC-BY credit',()=>{
 assert.equal(W.ROSTER[0].id,W.DEFAULT_ID);assert.equal(W.ROSTER[0].kind,'builtin');
 for(const e of W.ROSTER.filter(e=>e.kind==='glb')){
  assert.ok(e.credit?.author&&e.credit?.license&&e.credit?.url,e.id);
  const bytes=readFileSync(new URL(e.url,live));
  assert.equal(bytes.length,e.bytes,e.id);
  assert.equal(createHash('sha256').update(bytes).digest('hex'),e.sha256,e.id);
  const readme=readFileSync(new URL('assets/characters/README.md',live),'utf8');
  assert.ok(readme.includes(e.sha256)&&readme.includes(e.credit.author),'README credit/hash for '+e.id);
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

test('voice: Japanese kigae toggles, Book keeps its boundaries, both share one recognizer',()=>{
 let now=5000,book=0,kigae=0;const sent=[],native={postMessage:x=>sent.push(JSON.parse(x))};
 const v=Voice.create({host:{LivingLibraryNativeVoice:native},now:()=>now,lang:'ja-JP',commands:{
  book:{patterns:W.BOOK_PATTERNS,run:()=>book++},kigae:{patterns:W.KIGAE_PATTERNS,run:()=>kigae++}}});
 v.start();const session=sent.at(-1).session;
 const say=text=>{now+=1000;native.onmessage({data:JSON.stringify({v:1,session,status:'result',text})})};
 for(const t of ['着替え','きがえ','キガエ','お着替えして','Kigae'])say(t);assert.equal(kigae,5);
 say('notebook');say('key guy');assert.equal(book+kigae,5);
 say('Book');say('ブック');assert.equal(book,2);
});

test('web recognizer receives the configured language and counts each new Japanese word once',()=>{
 let instance,count=0;class R{constructor(){instance=this}start(){this.onstart?.()}abort(){}}
 const v=Voice.create({host:{SpeechRecognition:R,setTimeout(){},clearTimeout(){}},lang:'ja-JP',
  commands:{kigae:{patterns:W.KIGAE_PATTERNS,run:()=>count++}}});
 v.start();assert.equal(instance.lang,'ja-JP');
 const ev=(t,f=false)=>({resultIndex:0,results:[Object.assign([{transcript:t}],{isFinal:f})]});
 instance.onresult(ev('きが'));assert.equal(count,0);instance.onresult(ev('きがえ'));assert.equal(count,1);
 instance.onresult(ev('きがえ',true));assert.equal(count,1);
});

test('legacy function commands and default en-US language still work',()=>{
 let instance;class R{constructor(){instance=this}start(){}abort(){}}
 const v=Voice.create({host:{SpeechRecognition:R,setTimeout(){},clearTimeout(){}},commands:{book:()=>{}}});v.start();assert.equal(instance.lang,'en-US');
 assert.throws(()=>Voice.create({host:{},commands:{x:{patterns:['x']}}}));
});

test('addon loader rewrites bare three imports to the exact engine module that loaded',async()=>{
 const engine='https://cdn.jsdelivr.net/npm/three@0.185.0/build/three.module.js';
 assert.equal(W.addonBase(engine),'https://cdn.jsdelivr.net/npm/three@0.185.0/examples/jsm/');
 assert.equal(W.addonBase('https://unpkg.com/three@0.185.0/build/three.module.js'),'https://unpkg.com/three@0.185.0/examples/jsm/');
 assert.throws(()=>W.addonBase('https://evil.example/x.js'));assert.throws(()=>W.addonBase(''));
 const src="import {\n A } from 'three';\nimport { t } from '../utils/BufferGeometryUtils.js';\nimport {c} from \"../utils/SkeletonUtils.js\";\nexport {x} from './keep.js';";
 const out=W.rewriteImports(src,{three:engine,'../utils/BufferGeometryUtils.js':'blob:b','../utils/SkeletonUtils.js':'blob:s'});
 assert.match(out,new RegExp("from '"+engine.replace(/[.]/g,'\\.')+"'"));assert.match(out,/from 'blob:b'/);assert.match(out,/from "blob:s"/);assert.match(out,/from '.\/keep.js'/);
 const fetched=[],imported=[];let n=0;
 class GLTFLoader{setMeshoptDecoder(d){this.d=d}}
 const loader=await W.createGLTFLoader({engineUrl:engine,fetchText:async u=>{fetched.push(u);return "import {X} from 'three';"},
  makeModuleUrl:s=>{assert.ok(!s.includes("'three'"));return 'blob:'+(++n)},
  importModule:async u=>{imported.push(u);return u.endsWith('meshopt_decoder.module.js')?{MeshoptDecoder:{ready:Promise.resolve()}}:{GLTFLoader}}});
 assert.ok(loader instanceof GLTFLoader&&loader.d);assert.equal(fetched.length,3);assert.ok(fetched.every(u=>u.startsWith(W.addonBase(engine))));
 assert.deepEqual(imported,['blob:3',W.addonBase(engine)+'libs/meshopt_decoder.module.js']);
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
 assert.match(html,/kigae:\{patterns:window\.LivingWardrobe\.KIGAE_PATTERNS/);
 assert.match(html,/lang:'ja-JP'/);
 assert.match(html,/const keeperDefaultParts=\[\.\.\.keeper\.children\];/);
 const block=html.slice(html.indexOf('function createWardrobeWorld(){'),html.indexOf('wardrobeWorld=createWardrobeWorld();'));
 assert.ok(block.length>1000);
 assert.ok(!/\bscene\.add\(/.test(block),'wardrobe must not add scannable roots to the library scene');
 assert.match(block,/keeper\.add\(skin\.root\)/);
 assert.ok(!/selectedScanIds|toggleSelected/.test(block+readFileSync(new URL('wardrobe.js',live),'utf8')));
 assert.match(html,/<section id="wardrobeWorld" hidden/);
});
