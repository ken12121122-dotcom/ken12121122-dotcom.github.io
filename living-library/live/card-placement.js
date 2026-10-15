/* Independent 3D orbit for local craft cards. Book orbit remains untouched.
 * Pure geometry contract, shared by the scene and regression tests. */
(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LivingCardPlacement=api;
})(typeof self!=='undefined'?self:this,function(){
 'use strict';
 const CAPACITIES=[16,22,26];
 const RADII=[6.35,7.42,8.48];
 function ring(index){
  if(!Number.isInteger(index)||index<0||index>=64)throw Error('invalid floating card index');
  let first=0;
  for(let i=0;i<CAPACITIES.length;i++){
   if(index<first+CAPACITIES[i])return {ring:i,slot:index-first,capacity:CAPACITIES[i]};
   first+=CAPACITIES[i];
  }
  throw Error('card orbit capacity exceeded');
 }
 // x and z follow the player, not the camera. The whole card cluster rotates slowly
 // outside the book rings. Multiple tiers keep 64 cards from touching each other.
 function orbitPosition(index,elapsed=0){
  const {ring:r,slot,capacity}=ring(index);
  const angle=-.42+slot*Math.PI*2/capacity+r*.31-elapsed*(.069/(1+r*.25));
  const radius=RADII[r];
  return {ring:r,angle,radius,
   x:Math.sin(angle)*radius,z:Math.cos(angle)*radius,
   y:2.78+r*.72+Math.sin(elapsed*1.3+slot*.8+r)*.065};
 }
 return {CAPACITIES,RADII,ring,orbitPosition};
});
