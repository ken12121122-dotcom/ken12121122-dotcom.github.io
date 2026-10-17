/* Geometry-independent placement preview: two entry points share one resolver.
 * Building does not clone registered objects or execute any GBA-world action. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingPermanentBuild=api;})(typeof self!=='undefined'?self:globalThis,function(){
'use strict';
const BOUND=8,GRID=.5;
function position(input){
 if(!input||!Number.isFinite(input.x)||!Number.isFinite(input.z))
  throw Error('無效的放置座標');
 return {x:input.x,y:0,z:input.z};
}
function valid(raw){
 try{const p=position(raw);return Math.abs(p.x)<=BOUND&&Math.abs(p.z)<=BOUND
   &&Math.hypot(p.x,p.z)<=9.5}catch{return false}
}
function place(input,{grid=false}={}){
 let p=position(input);
 if(grid)p={...p,x:Math.round(p.x/GRID)*GRID,z:Math.round(p.z/GRID)*GRID};
 return {position:p,valid:valid(p),reason:valid(p)?'可放置':'超出目前開放建造區'};
}
function nudge(point,axis,units=1,{grid=false}={}){
 if(!['x','z'].includes(axis)||!Number.isFinite(units)||Math.abs(units)>10)
  throw Error('不合法的移動指令');
 const p=position(point);p[axis]+=units*(grid?GRID:.10);return place(p,{grid});
}
function rotate(yaw,delta=Math.PI/12){
 if(!Number.isFinite(yaw)||!Number.isFinite(delta))throw Error('角度必須為有限數字');
 let next=(yaw+delta)%(Math.PI*2);if(next>Math.PI)next-=Math.PI*2;
 if(next<-Math.PI)next+=Math.PI*2;return next;
}
return Object.freeze({BOUND,GRID,valid,place,nudge,rotate});
});
