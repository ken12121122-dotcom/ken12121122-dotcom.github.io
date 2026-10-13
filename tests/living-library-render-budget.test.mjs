import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {runInNewContext} from 'node:vm';

const load=()=>readFile(new URL('../living-library/index.html',import.meta.url),'utf8');

function harness(html,{mobile=true,motion=true,reduced=false}={}){
  const start=html.indexOf("const reduced=preferReduced;");
  const end=html.indexOf('\nsyncRender();',start);
  assert.ok(start>=0&&end>start,'render-budget controller must be present');
  const script=html.slice(start,end+'\nsyncRender();'.length);
  let now=1000,sceneApi,bookOpen=false,searchOpen=false,authOpen=false,hidden=false;
  const renders=[];
  const renderer={
    loop:null,
    setAnimationLoop(callback){this.loop=callback},
    render(){renders.push(now)}
  };
  const events={};
  const listeners=(kind)=>(name,callback)=>{events[kind+':'+name]=callback};
  const window={
    innerWidth:mobile?390:1200,
    matchMedia(query){return {matches:query.includes('prefers-reduced-motion')?reduced:mobile}},
    addEventListener:listeners('window')
  };
  const document={
    get hidden(){return hidden},
    getElementById(id){
      if(id==='drawerWrap')return {classList:{contains:name=>name==='open'&&bookOpen}};
      if(id==='mdSearch')return {get open(){return searchOpen}};
      if(id==='gen2Auth')return {get open(){return authOpen}};
      if(id==='livingReleaseProof')return null; // optional ?verify=1-only badge
      throw Error('unexpected element: '+id);
    },
    addEventListener:listeners('document')
  };
  const context={preferReduced:reduced,window,document,renderer,scene:{},camera:{},
    dust:{rotation:{y:0}},keeper:{rotation:{y:0}},glow:{emissiveIntensity:0},
    performance:{now:()=>now},isMotion:()=>motion,hasFlight:()=>false,hasWalkInput:()=>false,stepAvatar(){},exploring:false,
    walkKeys:new Set(),resetJoystick(){},exploreUI:{hidden:true},updateOrbit(){},
    notifyReady(api){sceneApi=api},rebuild(){},cameraUpdate(){},
    theta:0,phi:0,distance:0};
  runInNewContext(script,context);
  return {
    renderer,renders,events,
    scene:()=>sceneApi,
    advance(ms){now+=ms;renderer.loop?.(now)},
    overlay(flag){bookOpen=flag;sceneApi.syncRender()},
    search(flag){searchOpen=flag;sceneApi.syncRender()},
    auth(flag){authOpen=flag;sceneApi.syncRender()},
    hide(flag){hidden=flag;events['document:visibilitychange']()},
    motion(flag){motion=flag;sceneApi.setMotion()}
  };
}

test('mobile 30 fps budget and book overlay release GPU animation loop',async()=>{
  const a=harness(await load());
  assert.ok(a.renderer.loop,'scene must render when visible');
  a.advance(17);
  assert.equal(a.renders.length,1,'initial visible frame should paint');
  a.advance(20);
  assert.equal(a.renders.length,1,'next frame is throttled on phone');
  a.advance(18);
  assert.equal(a.renders.length,2);
  a.advance(20);
  assert.equal(a.renders.length,2);
  a.overlay(true);
  assert.equal(a.renderer.loop,null,'opening a book must stop GPU loop');
  const frozen=a.renders.length;
  a.advance(1000);
  assert.equal(a.renders.length,frozen);
  a.overlay(false);
  assert.ok(a.renderer.loop,'returning to library must restart rendering');
  a.advance(34);
  assert.ok(a.renders.length>frozen);
});

test('dialogs and hidden pages suspend rendering; close restores it',async()=>{
  const a=harness(await load());
  a.search(true);assert.equal(a.renderer.loop,null);
  a.search(false);assert.ok(a.renderer.loop);
  a.auth(true);assert.equal(a.renderer.loop,null);
  a.auth(false);assert.ok(a.renderer.loop);
  a.hide(true);assert.equal(a.renderer.loop,null);
  a.hide(false);assert.ok(a.renderer.loop);
});

test('paused motion and reduced-motion render only on demand',async()=>{
  const html=await load();
  for(const opts of [{motion:false},{reduced:true}]){
    const a=harness(html,opts);
    assert.equal(a.renderer.loop,null,'static scene does not spin a GPU loop');
    const before=a.renders.length;
    a.scene().invalidate();
    assert.equal(a.renders.length,before+1,'camera/book changes can redraw once');
    a.overlay(true);
    a.scene().invalidate();
    assert.equal(a.renders.length,before+1,'invalidation is ignored beneath a reader');
    a.overlay(false);
    assert.equal(a.renderer.loop,null);
  }
});

test('UI state transitions notify the 3D power controller',async()=>{
 const html=await load();
 assert.match(html,/function openBook\(\)[\s\S]*?classList\.add\('open'\);syncScene\(\)/);
 assert.match(html,/function closeBook\(\)[^\n]+classList\.remove\('open'\);syncScene\(\)/);
 assert.match(html,/showModal\(\);syncScene\(\)/);
 assert.match(html,/\$\('gen2Auth'\)\.addEventListener\('close',syncScene\)/);
 assert.match(html,/\$\('mdSearch'\)\.addEventListener\('close',syncScene\)/);
});
