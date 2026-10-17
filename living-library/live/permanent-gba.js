/* GBA menu adapter: operates only on physical cards in BOOK wells, never
 * summons or stores a ground/world entity directly. */
(function(root){'use strict';
root.LivingPermanentGba={create({storage,toast,refresh,invalidate,allowed,reduced=false}){
 const Registry=root.LivingPermanentCards;
 const dialog=document.getElementById('atlasPermanentDialog'),
  name=document.getElementById('atlasPermanentName'),
  checks=document.getElementById('atlasPermanentChecks'),
  button=document.getElementById('atlasPermanentHold'),
  label=document.getElementById('atlasPermanentHoldStatus');
 let index=0,holdTimer=null,request=null,effect=null;
 function selected(){return Registry.list(storage)[index]||null}
 function clearHold(){
  if(holdTimer!==null){clearTimeout(holdTimer);holdTimer=null}
  if(label)label.textContent='長按 ✦ 三秒完成確認，放開即取消。';
 }
 function cancel(){
  clearHold();request=null;effect=null;
  if(dialog?.open)dialog.close();invalidate();
 }
 function choose(delta){
  const n=Registry.list(storage).length;
  index=n?(index+delta+n)%n:0;invalidate();
 }
 function activate(reader){
  if(effect||dialog?.open)return 'upgrading';
  try{
   if(reader){
    if(Registry.isRegistered(reader.record)){
     const archived=Registry.archive(storage,reader.id,reader.record.revision);
     toast('✦ 永久原卡已歸回 BOOK Nº '+archived.permanentNumber);refresh();
    }else{
     const scan=Registry.inspect(storage,reader.id);
     if(!scan.eligible){
      const fails=Object.entries(scan.checks).filter(([,ok])=>!ok).map(([k])=>k);
      toast('不符合永久升級條件：'+fails.join('、'));return 'permanent';
     }
     request={cardId:reader.id,expectedRevision:reader.record.revision,
      number:scan.proposedNumber};
     name.textContent=reader.record.title;
     checks.textContent='✓ 原卡唯一　✓ 已締結契約　✓ 無重複登錄　✓ 原格空白　｜預定 Nº '+
      String(scan.proposedNumber).padStart(3,'0');
     dialog.showModal();
    }
   }else{
    const card=selected();if(!card){toast('請先把已締約的原卡放入 GBA 0 號格');return 'permanent'}
    Registry.toReader(storage,card.id,card.revision);
    toast('✦ 永久 Nº '+card.permanentNumber+' 已調入 GBA 0 號格；請將卡片拖出');refresh();
   }
  }catch(err){toast('永久卡操作未完成：'+err.message)}
  return 'permanent';
 }
 function startHold(e){
  if(!request||effect||!allowed()||holdTimer!==null)return;
  e.preventDefault();
  button.setPointerCapture?.(e.pointerId);
  label.textContent='正在確認不可逆永久認證，請保持三秒…';
  holdTimer=setTimeout(()=>{
   holdTimer=null;
   if(!request||!allowed()){cancel();return}
   const choice=request;request=null;
   if(dialog.open)dialog.close();
   effect={...choice,started:performance.now(),duration:reduced?350:3500};
   invalidate();
  },3000);
 }
 button?.addEventListener('pointerdown',startHold);
 for(const event of ['pointerup','pointercancel','lostpointercapture'])
  button?.addEventListener(event,clearHold);
 dialog?.addEventListener('cancel',cancel);
 document.getElementById('atlasPermanentCancel')?.addEventListener('click',cancel);
 function tick(now){
  if(!effect)return false;
  if(!allowed()){cancel();return false}
  if(now-effect.started<effect.duration)return true;
  const choice=effect;effect=null;
  try{
   const item=Registry.register(storage,{...choice,ownerConfirmed:true});
   index=Math.max(0,Registry.list(storage).findIndex(x=>x.id===item.id));
   toast('✦ 永久卡進化完成：Nº '+item.permanentNumber+'｜本機認證（非跨裝置登錄）');
   refresh();
  }catch(err){toast('永久認證未完成，原卡保持不變：'+err.message)}
  invalidate();return false;
 }
 function progress(now){return effect?Math.min(100,100*(now-effect.started)/effect.duration):null}
 return {choose,activate,tick,progress,cancel,
  data:()=>({permanents:Registry.list(storage),permanentIndex:index}),
  get active(){return !!effect},get pending(){return !!request}};
}};
})(typeof self!=='undefined'?self:globalThis);
