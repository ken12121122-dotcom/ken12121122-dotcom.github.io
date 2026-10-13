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
 assert.ok(s.includes('look.set(0,2.1,0)'),'old central camera target restored in viewing mode');
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
 assert.ok(s.includes('window.LivingLibrary.summonBook(id)'),'physical books remain selectable');
 assert.ok(s.includes("get('")===false||s.includes('const BOOKS='),'do not replace book controls');
});
