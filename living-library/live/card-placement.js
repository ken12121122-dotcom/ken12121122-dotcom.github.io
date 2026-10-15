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
 const DEFAULTS=Object.freeze({cardSize:1,cardOrbitDistance:1,cardOrbitSpeed:1,cardSummonDuration:1700});
 const LIMITS=Object.freeze({cardSize:[.65,1.2],cardOrbitDistance:[.88,1.06],cardOrbitSpeed:[0,1.8],cardSummonDuration:[900,2600]});
 function normalizeOptions(raw){
  const obj=raw&&typeof raw==='object'&&!Array.isArray(raw)?raw:{};
  const result={};
  for(const [key,def] of Object.entries(DEFAULTS)){
   const number=Number.isFinite(obj[key])?obj[key]:def;
   const [low,high]=LIMITS[key];
   result[key]=Math.min(high,Math.max(low,number));
  }
  return result;
 }
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
 function orbitPosition(index,elapsed=0,options=null){
  const {ring:r,slot,capacity}=ring(index),prefs=normalizeOptions(options);
  const angle=-.34+slot*Math.PI*2/capacity+r*.13-elapsed*prefs.cardOrbitSpeed*(.105/(1+r*.22));
  const radius=RADII[r]*prefs.cardOrbitDistance;
  return {ring:r,angle,radius,
   x:Math.sin(angle)*radius,z:Math.cos(angle)*radius,
   y:HEIGHTS[r]+Math.sin(elapsed*.95+slot*.7+r)*.045};
 }
 return {CAPACITIES,RADII,HEIGHTS,MAX_CARDS,DEFAULTS,LIMITS,normalizeOptions,ring,orbitPosition};
});
