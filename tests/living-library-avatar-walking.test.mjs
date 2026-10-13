import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';

const require=createRequire(import.meta.url);
const M=require('../living-library/avatar-movement.js');
const html=()=>readFile(new URL('../living-library/index.html',import.meta.url),'utf8');

test('touch joystick magnitude normalizes and stays within full-speed range',()=>{
 assert.deepEqual(M.normalize(0,0),{x:0,y:0});
 const r=M.normalize(2,2);
 assert.ok(Math.abs(Math.hypot(r.x,r.y)-1)<1e-9);
 assert.deepEqual(M.normalize(Number.NaN,Infinity),{x:0,y:0});
});

test('camera-relative controls walk forward and strafe according to freely rotated view',()=>{
 const p={x:0,z:0};
 let a=M.move(p,{x:0,y:1},0,1/30);
 assert.ok(a.z<0 && Math.abs(a.x)<1e-8,'forward at yaw zero must go toward -Z');
 a=M.move(p,{x:1,y:0},0,1/30);
 assert.ok(a.x>0 && Math.abs(a.z)<1e-8,'right strafe at yaw zero must go toward +X');
 a=M.move(p,{x:0,y:1},Math.PI/2,1/30);
 assert.ok(a.x<0 && Math.abs(a.z)<1e-8,'forward must follow camera after quarter-turn');
 a=M.move(p,{x:1,y:1},0,1/30);
 assert.ok(Math.hypot(a.x,a.z)<=M.WALK_SPEED/30+1e-7,'diagonals must not run faster');
});

test('walking stays inside the real bookcase perimeter, with frame-time clamp',()=>{
 assert.ok(M.WALK_RADIUS>=9&&M.WALK_RADIUS<=10.5,'near bookcases without walking through them');
 const outside=M.move({x:9.99,z:0},{x:1,y:0},0,9);
 assert.ok(Math.hypot(outside.x,outside.z)<=M.WALK_RADIUS+1e-9);
 const tiny=M.move({x:0,z:0},{x:0,y:1},0,9);
 assert.ok(tiny.distance<=M.WALK_SPEED*.075+1e-8,'resume after phone pause cannot teleport');
 assert.equal(M.move({x:1,z:2},{x:0,y:0},0,.05).distance,0);
});

test('exploration mode is opt-in and leaves the original camera available',async()=>{
 const s=await html();
 assert.ok(s.includes('id="exploreControls" class="walk-ui" hidden'),'hide exploration until WebGL ready');
 assert.ok(s.includes('id="exploreToggle"'),'explicit mode toggle');
 assert.ok(s.includes('id="walkPad"'),'left touch joystick');
 assert.ok(s.includes('src="avatar-movement.js'),'walking helper loaded separately');
 assert.ok(s.includes("showExploreMode(!exploring)"),'switch back to original free camera');
 assert.ok(s.includes('look.set(0,2.9,0)'),'original full-library center target restored in viewing mode');
 assert.ok(s.includes('distance=distanceBeforeExplore'),'old viewing distance restored');
 assert.ok(s.includes('Math.sin(theta)*horizontal'),'camera retains free horizontal rotation');
 assert.ok(!s.includes('shelfViewIndex'),'no locked shelf tour');
});
test('mobile joystick coexists with right-screen free look, WASD and WebGL fallback',async()=>{
 const s=await html();
 assert.ok(s.includes('touch-action:none;user-select:none'),'touch pad should not scroll page');
 assert.ok(s.includes('walkPad.setPointerCapture(e.pointerId)'),'joystick tracking survives small finger drift');
 assert.ok(s.includes("walkPad.addEventListener('pointercancel',joystickRelease)"),'cancel ends movement');
 assert.ok(s.includes('const touchPoints=new Map()'),'stage right-swipe and two-finger camera gestures preserved');
 assert.ok(s.includes("if('wasd'.includes(key)&&key.length===1)"),'keyboard accessible in exploration');
 assert.ok(s.includes('editableTarget(e.target)'),'typing in GEN2 editor must not walk');
 assert.ok(s.includes('exploreUI.hidden=false;'),'only reveal joystick after 3D initialization succeeded');
});
test('walking keeps existing book interactions and pauses behind readers',async()=>{
 const s=await html();
 assert.ok(s.includes('stepAvatar(dt);'),'walk updates in Three.js renderer frame');
 assert.ok(s.includes('look.set(keeper.position.x,2.1,keeper.position.z)'),'view follows figure only in exploration');
 assert.ok(s.includes('keeper.rotation.y+=delta'),'avatar turns toward path');
 assert.ok(s.includes('document.getElementById(\'drawerWrap\').classList.contains(\'open\')'),'overlay blocks WebGL loop');
 assert.ok(s.includes('hasWalkInput()'),'moving requests render even when floating animation is off');
 assert.ok(s.includes('if(overlay&&hasWalkInput()){walkKeys.clear();resetJoystick()}'),'held inputs must stop when book opens');
 assert.ok(s.includes('window.LivingLibrary.summonBook(id)'),'physical books remain selectable');
 assert.ok(s.includes("$('openBtn').onclick=openBook"),'existing book opening control still wired');
});


test('camera WORLD position follows the moving avatar, not only the lookAt target',async()=>{
 const source=await html();
 const start=source.indexOf('function cameraUpdate(){'),end=source.indexOf('\ncameraUpdate();',start);
 assert.ok(start>=0&&end>start,'3D camera function missing');
 const code=source.slice(start,end);
 assert.ok(code.includes('look.x+Math.sin(theta)*horizontal'),'camera position must track avatar X');
 assert.ok(code.includes('look.z+Math.cos(theta)*horizontal'),'camera position must track avatar Z');
 const simulate=new Function('yaw','targetA','targetB',[
  "const THREE={MathUtils:{clamp:(x,a,b)=>Math.max(a,Math.min(b,x))}};",
  "const window={LivingScene:null};",
  "let theta=yaw,phi=.17,distance=6.8;",
  "const look={x:targetA.x,y:2.1,z:targetA.z};",
  "const camera={position:{set(x,y,z){this.x=x;this.y=y;this.z=z}},lookAt(target){this.target={x:target.x,y:target.y,z:target.z}}};",
  code,
  "cameraUpdate();",
  "const first={position:{...camera.position},target:{...camera.target}};",
  "look.x=targetB.x;look.z=targetB.z;cameraUpdate();",
  "return {first,second:{position:{...camera.position},target:{...camera.target}}};"
 ].join('\n'));
 for(const yaw of [0,Math.PI/2,Math.PI,3*Math.PI/2]){
  const t=simulate(yaw,{x:0,z:0},{x:4,z:-3});
  assert.ok(Math.abs((t.second.position.x-t.first.position.x)-4)<1e-8,
   'camera X must follow walking target X at yaw '+yaw);
  assert.ok(Math.abs((t.second.position.z-t.first.position.z)+3)<1e-8,
   'camera Z must follow walking target Z at yaw '+yaw);
  assert.deepEqual(t.second.target,{x:4,y:2.1,z:-3},'view must remain pointed at avatar');
 }
});

test('explore zoom and phone resize keep following, freeview restores former orbit',async()=>{
 const s=await html();
 assert.ok(s.includes('distanceBeforeExplore=distance;distance=6.8;'),'walk camera should be close enough to see figure');
 assert.ok(s.includes('exploring?4.5:7.5,exploring?13:27'),'explore zoom must not use full library zoom range');
 assert.ok(s.includes('if(exploring)distance=THREE.MathUtils.clamp(distance,4.5,13)'),'resize does not teleport back to distant view');
 assert.ok(s.includes('distance=distanceBeforeExplore'),'original free viewing distance restored when exiting');
 assert.ok(s.includes('look.set(0,2.9,0)'),'free view restores original central target');
});
