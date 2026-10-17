/* NW-TRANSFER-001 | reusable directed 3D absorption math.
 * The same motion works for card -> card, card -> rune and future object receivers.
 * No persistence, card naming, WebGL or special-case destination knowledge here.
 * A caller must commit the corresponding atomic transition after sampling 'done'.
 */
(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LivingDirectedTransfer=api;
})(typeof self!=='undefined'?self:globalThis,function(){
 'use strict';
 const clamp=(n)=>Math.min(1,Math.max(0,n));
 const smooth=n=>{const t=clamp(n);return t*t*(3-2*t)};
 function point(p){
  if(!p||!['x','y','z'].every(k=>Number.isFinite(p[k])))
   throw Error('定向轉移的世界座標無效');
  return {x:p.x,y:p.y,z:p.z};
 }
 function normalize(raw){
  if(!raw||typeof raw.sourceId!=='string'||!raw.sourceId||
    typeof raw.targetId!=='string'||!raw.targetId||
    raw.sourceId===raw.targetId)throw Error('轉移來源與目標不得相同');
  const duration=Number.isFinite(raw.duration)&&raw.duration>=1&&raw.duration<=30000?raw.duration:1400;
  return Object.freeze({sourceId:raw.sourceId,targetId:raw.targetId,
   from:point(raw.from),to:point(raw.to),
   duration,startedAt:Number.isFinite(raw.startedAt)?raw.startedAt:0,
   initialScale:Number.isFinite(raw.initialScale)&&raw.initialScale>0?raw.initialScale:1});
 }
 function sample(plan,now,toOverride=null){
  if(!plan||!Number.isFinite(now))throw Error('轉移進度無效');
  const to=toOverride===null?plan.to:point(toOverride);
  const t=clamp((now-plan.startedAt)/plan.duration),u=smooth(t);
  // Start with a small lift, converge accurately on the TARGET SURFACE,
  // then shrink into its center. Works without a locked camera direction.
  const arc=Math.sin(Math.PI*t)*Math.min(.55,
   .12+Math.hypot(to.x-plan.from.x,to.z-plan.from.z)*.08);
  return Object.freeze({progress:t,done:t>=1,position:Object.freeze({
   x:plan.from.x+(to.x-plan.from.x)*u,
   y:plan.from.y+(to.y-plan.from.y)*u+arc,
   z:plan.from.z+(to.z-plan.from.z)*u}),
   scale:plan.initialScale*Math.max(.001,1-.999*Math.pow(t,2.3)),
   targetFlash:Math.sin(Math.PI*t),fade:1-Math.pow(t,3)});
 }
 return Object.freeze({normalize,sample,point});
});
