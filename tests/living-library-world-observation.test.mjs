import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const Observer=require('../living-library/live/world-observation.js');
const Mem=()=>{const m=new Map();return {
 getItem:k=>m.has(k)?m.get(k):null,setItem:(k,v)=>m.set(k,String(v)),
 keys:()=>[...m.keys()]};};
function live(){return {ready:true,player:{x:3.123,y:.46,z:-2.238},camera:{theta:1.234,phi:.17,distance:15.8},
 books:[{id:'public.system',active:true,sourceLayer:'public'}],
 sceneObjects:[{id:'scene.rune.origin',position:{x:0,y:0,z:0}},
   {id:'scene.keeper',position:{x:100,y:100,z:100}}]};}
test('world snapshot extracts only allowlisted prefs and stable scene IDs, never tokens',()=>{
 const st=Mem();
 st.setItem(Observer.KEYS.controls,JSON.stringify({rotationMode:'dual',cardSize:1,token:'TOP_SECRET',junk:{nested:'PRIVATE'}}));
 st.setItem(Observer.KEYS.books,JSON.stringify({selectedId:'book1',active:['book1'],accessToken:'TOP_SECRET'}));
 st.setItem('packagecanvas-gen2-github-v1',JSON.stringify({token:'TOP_SECRET'}));
 st.setItem(Observer.KEYS.truthEye,JSON.stringify({radius:8,mode:'both',githubKey:'TOP_SECRET'}));
 const a=Observer.collect(st,live),b=Observer.collect(st,live);
 assert.equal(JSON.stringify(a),JSON.stringify(b));
 assert.equal(a.schema,'nw.world-observation.v1');
 assert.equal(a.session_scene.ready,true);
 assert.deepEqual(a.session_scene.player,{x:3.12,y:.46,z:-2.24});
 assert.equal(a.session_scene.sceneObjects.length,1);
 assert.equal(a.saved.controls.cardSize,1);
 assert.doesNotMatch(JSON.stringify(a),/TOP_SECRET|accessToken|token|junk/);
 assert.equal(st.keys().length,4);
});
test('render failure leaves honest no-scene snapshot but retains safe preferences',()=>{
 const st=Mem();st.setItem(Observer.KEYS.controls,JSON.stringify({joystickEnabled:true}));
 const a=Observer.collect(st,()=>null);
 assert.equal(a.session_scene.ready,false);
 assert.equal(a.saved.controls.joystickEnabled,true);
});
test('tampered or huge local prefs fail closed instead of being silently synchronized',()=>{
 const st=Mem();st.setItem(Observer.KEYS.controls,'{garbled');
 assert.throws(()=>Observer.collect(st),/WORLD_OBSERVATION_LOCAL_INVALID/);
 st.setItem(Observer.KEYS.controls,JSON.stringify({unexpected:'x'.repeat(130000)}));
 assert.throws(()=>Observer.collect(st),/WORLD_OBSERVATION_LOCAL_TOO_LARGE/);
});
