import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
const require=createRequire(import.meta.url);
const Hold=require('../living-library/live/rune-hold.js');
const press=(pointerId=1,x=30,y=40)=>({pointerId,clientX:x,clientY:y,isPrimary:true,button:0,pointerType:'touch'});
function fixture(allowed=()=>true){
 let now=1000,id=0;const tasks=new Map(),selected=[],changes=[];
 const hold=Hold.create({
  now:()=>now,
  setTimer:(fn,ms)=>{const token=++id;tasks.set(token,{due:now+ms,fn});return token},
  clearTimer:token=>tasks.delete(token),
  onSelected:runeId=>selected.push(runeId),onChange:r=>changes.push(r),isAllowed:allowed
 });
 function advance(ms){now+=ms;while(true){
  const due=[...tasks.entries()].filter(([_,t])=>t.due<=now).sort((a,b)=>a[1].due-b[1].due)[0];
  if(!due)break;
  tasks.delete(due[0]);due[1].fn();
 }}
 return {hold,advance,selected,changes,tasks,get time(){return now}};
}
test('hold needs full 3000 milliseconds and selects exactly once',()=>{
 const f=fixture(),h=f.hold,e=press();
 assert.equal(Hold.DURATION_MS,3000);
 assert.equal(h.pointerDown(e,'local.rune.origin'),true);
 f.advance(1500);assert.equal(h.inspect().progress,.5);
 f.advance(1499);assert.equal(h.inspect().phase,'holding');
 assert.ok(h.inspect().progress>.999);
 assert.equal(f.selected.length,0);
 f.advance(1);
 assert.equal(h.inspect().phase,'selected');assert.deepEqual(f.selected,['local.rune.origin']);
 h.pointerUp(e);f.advance(5000);
 assert.equal(h.inspect().phase,'idle');
 assert.deepEqual(f.selected,['local.rune.origin'],'no duplicate selection after release');
});
test('release before 3 seconds cancels and consumes tap',()=>{
 const f=fixture();const e=press();
 f.hold.pointerDown(e,'local.rune.origin');f.advance(2999);
 assert.equal(f.hold.pointerUp(e),true);
 f.advance(3001);
 assert.equal(f.selected.length,0);assert.equal(f.hold.inspect().phase,'idle');
 assert.equal(f.hold.pointerUp(e),false,'consumed pointer cleaned up');
});
test('12px drift permitted, 13px cancels and does not produce selection',()=>{
 const f=fixture(),e=press();
 f.hold.pointerDown(e,'local.rune.origin');
 assert.equal(f.hold.pointerMove({...e,clientX:42}),true);
 f.advance(500);
 assert.equal(f.hold.inspect().phase,'holding');
 assert.equal(f.hold.pointerMove({...e,clientX:43}),true);
 f.advance(3000);
 assert.equal(f.selected.length,0);assert.equal(f.hold.pointerUp(e),true);
});
test('a second finger cancels first press without stealing the other pointer',()=>{
 const f=fixture(),first=press(1),second=press(2);
 f.hold.pointerDown(first,'local.rune.origin');
 f.hold.foreignDown(second.pointerId);
 assert.equal(f.hold.inspect().phase,'idle');
 assert.equal(f.hold.pointerUp(first),true);
 assert.equal(f.hold.pointerUp(second),false);
 f.advance(5000);assert.equal(f.selected.length,0);
});
test('cancel, lost capture, blur and hidden-page cancellation are fail-closed',()=>{
 for(const ending of ['pointerCancel','cancelAll']){
  const f=fixture(),e=press(20);
  f.hold.pointerDown(e,'local.rune.origin');
  if(ending==='cancelAll')f.hold.cancelAll();else f.hold.pointerCancel(e);
  f.advance(6000);
  assert.equal(f.selected.length,0);
  assert.equal(f.hold.active(),false);
 }
});
test('an overlay can cancel selection at the very end of a 3s timer',()=>{
 let allowed=true;const f=fixture(()=>allowed),e=press();
 assert.equal(f.hold.pointerDown(e,'local.rune.origin'),true);
 f.advance(2999);allowed=false;f.advance(1);
 assert.equal(f.selected.length,0);
 assert.equal(f.hold.inspect().phase,'idle');
});
test('reduced-motion timing works even without an animation-frame tick',()=>{
 const f=fixture(),e=press();
 f.hold.pointerDown(e,'local.rune.origin');
 f.advance(3000);assert.equal(f.selected.length,1);
 f.advance(Hold.SELECTED_MS-1);assert.equal(f.hold.active(),true);
 f.advance(1);assert.equal(f.hold.active(),false);
 assert.equal(f.tasks.size,0);
});
test('new press aborts selected pulse without multiplying timers',()=>{
 const f=fixture(),e=press(1),next=press(3);
 f.hold.pointerDown(e,'local.rune.origin');
 f.advance(3000);f.hold.pointerUp(e);
 f.advance(400);
 f.hold.pointerDown(next,'local.rune.origin');
 assert.equal(f.hold.inspect().phase,'holding');
 f.advance(3000);assert.equal(f.selected.length,2);
 f.hold.pointerUp(next);
 f.advance(2000);assert.equal(f.hold.active(),false);
 assert.equal(f.tasks.size,0);
});
test('invalid pointer and canceled gesture never trigger an application',()=>{
 const f=fixture();
 assert.equal(f.hold.pointerDown({...press(),isPrimary:false},'local.rune.origin'),false);
 assert.equal(f.hold.pointerDown({...press(),button:2},'local.rune.origin'),false);
 assert.equal(f.hold.pointerDown({...press(),pointerId:1.1},'local.rune.origin'),false);
 assert.equal(f.hold.pointerDown(press(),''),false);
 f.advance(10000);assert.equal(f.selected.length,0);
});
test('continuous circular progress bar has no time numeral or segmented ticks',async()=>{
 const html=await readFile(new URL('../living-library/live/index.html',import.meta.url),'utf8');
 assert.match(html,/RUNE_ARC_SEGMENTS=192/);
 assert.match(html,/new THREE\.RingGeometry\(2\.51,2\.64,RUNE_ARC_SEGMENTS,1,-Math\.PI\/2,Math\.PI\*2\)/);
 assert.match(html,/runeArcGeometry\.setDrawRange\(0,Math\.floor\(RUNE_ARC_SEGMENTS\*info\.progress\)\*6\)/);
 assert.match(html,/runeTrack\.visible=show/);
 assert.doesNotMatch(html,/RUNE_TICKS/);
 assert.doesNotMatch(html,/顯示.*3秒|倒數.*3秒|3 秒倒數/);
 assert.match(html,/runeGesture\.foreignDown\(e\.pointerId\)/);
 assert.match(html,/runeGesture\.pointerMove\(e\)/);
 assert.match(html,/runeGesture\.pointerUp\(e\)/);
 assert.match(html,/runeGesture\.pointerCancel\(e\)/);
 assert.match(html,/!runeGesture\.active\(\)/);
 assert.match(html,/\|\|runeGesture\.active\(\)\|\|hasWalkInput\(\)/);
 assert.match(html,/runeNearest=findNearestRuneCard\(\)/);
 assert.match(html,/rune:runeInspection/);
 assert.doesNotMatch(html,/runeBinding\s*=/);
 assert.doesNotMatch(html,/localStorage\.setItem\(['"]rune/);
});
