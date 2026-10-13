/* Living Library 3D placement. Geometry-free so it is independently testable.
 * ORBIT: unlimited logical rings, visually spaced at different heights.
 * SHELF: real interactive book models occupy fixed slots among decorative spines.
 * Public and private books use the same placement; no private data is persisted. */
(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LivingBookPlacement=api;
})(typeof self!=='undefined'?self:this,function(){
 'use strict';
 const SHELF_ORDER=[3,2,4,1,5,0,6],ROWS=[1,2,3,0,4],SLOTS_PER_ROW=5;
 function ring(index){
  if(!Number.isInteger(index)||index<0)throw Error('invalid orbit book index');
  let r=0,start=0;
  for(;;){
   const capacity=7+3*r;
   if(index<start+capacity)return {ring:r,indexInRing:index-start,capacity};
   start+=capacity;r++;
  }
 }
 function ringCount(count){
  if(!Number.isInteger(count)||count<0)throw Error('invalid orbit count');
  return count===0?0:ring(count-1).ring+1;
 }
 function orbitPosition(index,now=0){
  const {ring:r,indexInRing:j,capacity:n}=ring(index);
  // Alternate orbit directions; 3D tiers prevent books from occupying one circle.
  const sign=r%2===0?1:-1;
  const angle=j*Math.PI*2/n + sign*now*(.11/(1+.18*r))-.32 +r*.19;
  const radius=2.78+Math.min(r,4)*1.18;
  return {ring:r,angle,
   x:Math.sin(angle)*radius,
   y:2.55+r*.88+Math.sin(now*1.2+j*1.8+r)*.12,
   z:Math.cos(angle)*radius*.82+.12,
   scale:r>=4?.70:r>=2?.77:r===1?.84:.95};
 }
 const SHELF_CAPACITY=SHELF_ORDER.length*ROWS.length*SLOTS_PER_ROW;
 function shelfSlot(index){
  if(!Number.isInteger(index)||index<0)throw Error('invalid shelf book index');
  if(index>=SHELF_CAPACITY)return null;
  const shelfBlock=ROWS.length*SLOTS_PER_ROW;
  const shelfIndex=SHELF_ORDER[Math.floor(index/shelfBlock)];
  const within=index%shelfBlock;
  const row=ROWS[Math.floor(within/SLOTS_PER_ROW)];
  const position=within%SLOTS_PER_ROW;
  const col=position;
  // Exactly five physical compartments across each of five shelves per cabinet.
  // Front-facing book covers make stored titles distinguishable on phones.
  return {shelfIndex,row,col,x:-2.68+col*1.34,y:.72+row*1.43+.58,z:.64};
 }
 return {ring,ringCount,orbitPosition,shelfSlot,SHELF_CAPACITY};
});
