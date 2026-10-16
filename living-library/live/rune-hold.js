/* NW-RUNE-001 / RUNE-02. Pure 3-second gesture; never binds or writes data. */
(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LivingRuneHold=api;
})(typeof self!=='undefined'?self:globalThis,function(){
 'use strict';
 const DURATION_MS=3000,MAX_MOVE_PX=12,SELECTED_MS=1800;
 function create({now=()=>performance.now(),setTimer=(fn,ms)=>setTimeout(fn,ms),
  clearTimer=id=>clearTimeout(id),onChange=()=>{},onSelected=()=>{},isAllowed=()=>true}={}){
  let press=null,selectedUntil=0,selectedToken=0,fadeTimer=null;
  const captured=new Set();
  function clearFade(){if(fadeTimer!==null){clearTimer(fadeTimer);fadeTimer=null}}
  function resetSelection(){selectedToken++;selectedUntil=0;clearFade()}
  function cancel(reason='cancelled'){
   if(!press)return false;
   clearTimer(press.timer);press=null;onChange(reason);return true;
  }
  function complete(){
   if(!press)return false;
   if(now()-press.started<DURATION_MS)return false;
   if(!isAllowed()){cancel('not_allowed');return false}
   clearTimer(press.timer);const runeId=press.runeId;press=null;
   resetSelection();selectedUntil=now()+SELECTED_MS;
   const token=selectedToken;
   fadeTimer=setTimer(()=>{if(token!==selectedToken)return;selectedUntil=0;fadeTimer=null;onChange('ended')},SELECTED_MS);
   onSelected(runeId);onChange('selected');return true;
  }
  function pointerDown(e,runeId){
   if(press){cancel('second_pointer');return false}
   if(!e||!e.isPrimary||e.button!==0||!Number.isFinite(e.clientX)||!Number.isFinite(e.clientY)||
     !Number.isInteger(e.pointerId)||typeof runeId!=='string'||!runeId||!isAllowed())return false;
   resetSelection();
   captured.add(e.pointerId);
   press={id:e.pointerId,x:e.clientX,y:e.clientY,started:now(),runeId,timer:null};
   press.timer=setTimer(complete,DURATION_MS);
   onChange('started');return true;
  }
  function foreignDown(pointerId){if(press&&press.id!==pointerId)cancel('second_pointer')}
  function pointerMove(e){
   if(!e||!captured.has(e.pointerId))return false;
   if(press&&e.pointerId===press.id&&Math.hypot(e.clientX-press.x,e.clientY-press.y)>MAX_MOVE_PX)cancel('moved');
   return true;
  }
  function pointerUp(e){
   if(!e||!captured.has(e.pointerId))return false;
   if(press&&press.id===e.pointerId){
    if(!Number.isFinite(e.clientX)||!Number.isFinite(e.clientY)||
       Math.hypot(e.clientX-press.x,e.clientY-press.y)>MAX_MOVE_PX)cancel('moved');
    else if(!complete())cancel('released');
   }
   captured.delete(e.pointerId);return true;
  }
  function pointerCancel(e){
   if(!e||!captured.has(e.pointerId))return false;
   if(press?.id===e.pointerId)cancel('pointer_cancelled');
   captured.delete(e.pointerId);return true;
  }
  function cancelAll(){
   cancel('interrupted');captured.clear();resetSelection();onChange('cleared');
  }
  function tick(){if(press)complete()}
  function inspect(t=now()){
   const holding=!!press,selected=!holding&&selectedUntil>t;
   return Object.freeze({phase:holding?'holding':selected?'selected':'idle',
    progress:holding?Math.min(1,Math.max(0,(t-press.started)/DURATION_MS)):selected?1:0,
    runeId:press?.runeId||null,remainingMs:holding?Math.max(0,DURATION_MS-(t-press.started)):0});
  }
  function active(t=now()){return !!press||selectedUntil>t}
  return Object.freeze({pointerDown,foreignDown,pointerMove,pointerUp,pointerCancel,cancelAll,
   inspect,active,tick});
 }
 return Object.freeze({DURATION_MS,MAX_MOVE_PX,SELECTED_MS,create});
});
