/* Living Library walking math. Pure, deterministic; no game engine or saved personal data.
 * The joystick uses x=right, y=forward; movements are camera-relative. */
(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LivingAvatarMovement=api;
})(typeof self!=='undefined'?self:this,function(){
 'use strict';
 const WALK_RADIUS=10.0;
 const WALK_SPEED=3.45;
 const clamp=(n,min,max)=>Math.min(max,Math.max(min,n));
 function normalize(x,y){
  x=Number.isFinite(x)?x:0;y=Number.isFinite(y)?y:0;
  const length=Math.hypot(x,y);
  return length>1?{x:x/length,y:y/length}:{x,y};
 }
 function move(position,input,theta,seconds){
  const vector=normalize(input?.x||0,input?.y||0);
  const dt=clamp(Number.isFinite(seconds)?seconds:0,0,.075);
  const camYaw=Number.isFinite(theta)?theta:0;
  const dx=Math.cos(camYaw)*vector.x-Math.sin(camYaw)*vector.y;
  const dz=-Math.sin(camYaw)*vector.x-Math.cos(camYaw)*vector.y;
  let x=(Number.isFinite(position?.x)?position.x:0)+dx*WALK_SPEED*dt;
  let z=(Number.isFinite(position?.z)?position.z:0)+dz*WALK_SPEED*dt;
  const radius=Math.hypot(x,z);
  if(radius>WALK_RADIUS){x=x/radius*WALK_RADIUS;z=z/radius*WALK_RADIUS;}
  const distance=Math.hypot(x-(position?.x||0),z-(position?.z||0));
  return {x,z,distance,heading:Math.atan2(dx,dz)};
 }
 return {WALK_RADIUS,WALK_SPEED,normalize,move};
});
