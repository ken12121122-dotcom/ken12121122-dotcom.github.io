/* Inner orbit for player-made cards: avatar -> cards -> orbiting books.
 * Geometry-only so 3D placement remains unit-testable and the book orbit is untouched. */
(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LivingCardPlacement=api;
})(typeof self!=='undefined'?self:this,function(){
 'use strict';
 // Vertical tiers are intentional: extra cards must not creep into the book orbit.
 const CAPACITIES=[12,12,12,14,14];
 const RADII=[1.52,1.57,1.62,1.68,1.74];
 const HEIGHTS=[2.22,2.90,3.58,4.26,4.94];
 const MAX_CARDS=CAPACITIES.reduce((n,x)=>n+x,0);
 function ring(index){
  if(!Number.isInteger(index)||index<0||index>=MAX_CARDS)
   throw Error('invalid floating card index');
  let first=0;
  for(let r=0;r<CAPACITIES.length;r++){
   if(index<first+CAPACITIES[r])return {ring:r,slot:index-first,capacity:CAPACITIES[r]};
   first+=CAPACITIES[r];
  }
  throw Error('card ring unavailable');
 }
 // All positions are offsets from the avatar (not camera). Radius < inner book ring.
 // Vary height between tiers and leave a minimum along-track gap for small cards.
 function orbitPosition(index,elapsed=0){
  const {ring:r,slot,capacity}=ring(index);
  const angle=-.34+slot*Math.PI*2/capacity+r*.13-elapsed*(.105/(1+r*.22));
  const radius=RADII[r];
  return {ring:r,angle,radius,
   x:Math.sin(angle)*radius,z:Math.cos(angle)*radius,
   y:HEIGHTS[r]+Math.sin(elapsed*.95+slot*.7+r)*.045};
 }
 return {CAPACITIES,RADII,HEIGHTS,MAX_CARDS,ring,orbitPosition};
});
