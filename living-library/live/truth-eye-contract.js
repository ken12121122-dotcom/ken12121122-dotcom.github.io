/* Truth Eye: player-card binding. Stable artifact ID, original card ID, atomic localStorage changes.
 * Does not alter the existing origin rune contract and does not mint a new card. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingTruthEyeContract=api})(typeof self!=='undefined'?self:globalThis,function(){
 'use strict';
 const ID='artifact.truth-eye',KEY='living-library-player-objects-v1',MAX_DISTANCE=3,TIE=.15;
 const isEligible=c=>!!c&&c.objectType==='card'&&c.templateId==='blank-card-v1'&&
  typeof c.id==='string'&&c.id.startsWith('local.card.')&&c.status==='draft'&&
  c.placement==='hover'&&!c.containerId&&!c.ownerBookId&&!c.containment&&
  !c.runeBinding&&!c.runeContract&&!c.boundRuneId&&!c.truthEyeContract&&
  !c.l2Certified&&!c.permanentCard&&!c.permanentRegistry&&!c.permanentRegistration&&
  (c.title==='空白卡片'||c.title==='真實之眼')&&!c.linkedCardId&&!c.ability&&
  (c.title==='真實之眼'||(!c.description&&!c.image&&!c.cardNumber&&!c.cardType))&&
  (!c.permissions||c.permissions.length===0)&&!c.copyInfo&&
  !c.upgradeRequest?.state&&!c.certification?.level;
 function read(storage){
  const raw=storage.getItem(KEY);if(raw===null)return {version:1,objects:[]};
  let state;try{state=JSON.parse(raw)}catch(_){throw Error('既有卡片資料無法解析，已拒絕修改')}
  if(!state||state.version!==1||!Array.isArray(state.objects)||state.objects.some(o=>!o||typeof o.id!=='string'))
   throw Error('既有卡片資料格式不相容，已拒絕修改');
  return state;
 }
 function boundCard(storage){
  const all=read(storage).objects.filter(o=>o.status!=='recycled'&&o.truthEyeContract?.artifactId===ID);
  if(all.length>1)throw Error('偵測到重複神器契約，為保護卡片資料已停止操作');
  return all[0]||null;
 }
 function nearest(anchor,entries){
  const cards=entries.filter(e=>e?.record&&e.visible===true&&!e.isDragging&&!e.isFocused&&!e.isSummoning&&!e.isRecycling&&
   e.record.id===e.id&&isEligible(e.record)&&Number.isFinite(e.position?.x)&&Number.isFinite(e.position?.z))
   .map(e=>({id:e.id,distance:Math.hypot(e.position.x-anchor.x,e.position.z-anchor.z)}))
   .sort((a,b)=>a.distance-b.distance||a.id.localeCompare(b.id));
  if(!cards.length)return {status:'missing',reason:'附近沒有可締約的原有空白卡'};
  if(cards[0].distance>MAX_DISTANCE)return {status:'distant',reason:'空白卡距離超過三公尺'};
  if(cards[1]&&cards[1].distance<=MAX_DISTANCE&&cards[1].distance-cards[0].distance<TIE)
   return {status:'ambiguous',reason:'兩張空白卡距離太接近，請先移開其中一張'};
  return {status:'selected',cardId:cards[0].id,distance:cards[0].distance};
 }
 function change(storage,cardId,expectedRevision,transform){
  const state=read(storage),at=state.objects.findIndex(x=>x.id===cardId);
  if(at<0)throw Error('契約卡已不存在');
  const old=state.objects[at];
  if(old.revision!==expectedRevision)throw Error('契約卡已變更，請重新操作');
  const next=transform(old);
  const now=new Date().toISOString();
  const objects=[...state.objects];objects[at]={...next,revision:old.revision+1,updatedAt:now};
  storage.setItem(KEY,JSON.stringify({...state,objects}));
  return {...objects[at]};
 }
 function bind(storage,cardId,expectedRevision){
  if(boundCard(storage))throw Error('真實之眼已經有原卡，不可重複締約');
  return change(storage,cardId,expectedRevision,old=>{
   if(!isEligible(old))throw Error('這張卡不是可使用的普通空白卡');
   const now=new Date().toISOString();
   return {...old,title:'真實之眼',cardType:old.cardType||'神器',description:old.description||'獨一物件 · 真實之眼 3D 掃描神器',runeBinding:{runeId:ID,state:'bound',boundAt:now},
    truthEyeContract:{artifactId:ID,form:'sealed',version:2,contractedAt:now}};
  });
 }
 function setForm(storage,cardId,expectedRevision,form){
  if(!['world','card','sealed'].includes(form))throw Error('神器形態錯誤');
  return change(storage,cardId,expectedRevision,old=>{
   if(old.truthEyeContract?.artifactId!==ID||old.runeBinding?.runeId!==ID||
    old.status!=='draft'||old.placement!=='hover'||old.containerId||old.ownerBookId)
    throw Error('請先將真實之眼原卡從書內召回玩家軌道');
   const from=old.truthEyeContract.form;
   const valid=(from==='sealed'&&form==='card')||
    (from==='card'&&form==='world')||(from==='world'&&form==='card');
   if(!valid)throw Error('神器轉換順序錯誤，必須先完成原始封印、再卡片化');
   return {...old,truthEyeContract:{...old.truthEyeContract,form}};
  });
 }
 return Object.freeze({ID,KEY,isEligible,nearest,read,boundCard,bind,setForm});
});