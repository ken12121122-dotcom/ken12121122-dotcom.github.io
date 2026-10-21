/* NW-RUNE-001 / RUNE-01: pure rune identity + nearest ordinary card search.
 * Read-only foundation. No binding, hold handler, mutation, ID issuance or network. */
(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LivingRuneContract=api;
})(typeof self!=='undefined'?self:globalThis,function(){
 'use strict';
 const RUNE_ID='local.rune.origin',DISPLAY_NAME='初始法陣';
 const DEFAULTS=Object.freeze({maxDistance:3,tieEpsilon:.15});
 const isPosition=p=>!!p&&typeof p==='object'&&Number.isFinite(p.x)&&Number.isFinite(p.z);
 const planarDistance=(a,b)=>Math.hypot(a.x-b.x,a.z-b.z);
 const isOrdinaryCard=(record)=>{
  if(!record||typeof record!=='object'||Array.isArray(record))return false;
  if(record.objectType!=='card'||record.templateId!=='blank-card-v1'||typeof record.id!=='string'||!record.id.startsWith('local.card.'))return false;
  if(record.status!=='draft'||record.containerId||record.ownerBookId||record.placement!=='hover')return false;
  if(typeof record.title!=='string'||!record.title.trim()||record.title.trim()==='空白卡片')return false;
  if(record.runeBinding?.runeId||record.runeContract?.runeId||record.boundRuneId)return false;
  if(record.l2Certified===true||record.upgradeRequest?.l2Certified===true||record.certification?.level==='L2')return false;
  return true;
 };
 function isEligibleEntry(entry){
  if(!entry||typeof entry!=='object'||!entry.record||entry.visible!==true)return false;
  if(entry.isDragging||entry.isSummoning||entry.isRecycling||entry.isFocused)return false;
  if(entry.id!==entry.record.id||!isPosition(entry.position))return false;
  return isOrdinaryCard(entry.record);
 }
 function normalizeOptions(raw){
  const v=raw&&typeof raw==='object'&&!Array.isArray(raw)?raw:{};
  return {maxDistance:Number.isFinite(v.maxDistance)&&v.maxDistance>0&&v.maxDistance<=10?v.maxDistance:DEFAULTS.maxDistance,
   tieEpsilon:Number.isFinite(v.tieEpsilon)&&v.tieEpsilon>=0&&v.tieEpsilon<=1?v.tieEpsilon:DEFAULTS.tieEpsilon};
 }
 function findNearestEligibleCard(runeAnchor,entries,config={}){
  const options=normalizeOptions(config);
  const result=(status,reason,extras={})=>Object.freeze({status,reason,cardId:null,distance:null,eligibleCount:0,withinRangeCount:0,...extras});
  if(!isPosition(runeAnchor))return result('unavailable','invalid_rune_anchor');
  if(!Array.isArray(entries))return result('unavailable','invalid_card_collection');
  const candidates=[];const ids=new Set();
  for(const entry of entries){
   if(!isEligibleEntry(entry))continue;
   if(ids.has(entry.id))return result('unavailable','duplicate_candidate_id');
   ids.add(entry.id);
   candidates.push({id:entry.id,distance:planarDistance(runeAnchor,entry.position)});
  }
  if(!candidates.length)return result('unavailable','no_eligible_cards');
  candidates.sort((a,b)=>a.distance-b.distance||(a.id<b.id?-1:a.id>b.id?1:0));
  const eligibleCount=candidates.length;
  const within=candidates.filter(c=>c.distance<=options.maxDistance);
  if(!within.length)return result('unavailable','out_of_range',{eligibleCount,nearestDistance:candidates[0].distance});
  // Two cards almost equally near require explicit human selection in RUNE-03.
  if(within.length>1&&within[1].distance-within[0].distance<options.tieEpsilon)
   return result('unavailable','ambiguous_nearest',{eligibleCount,withinRangeCount:within.length,nearestDistance:within[0].distance});
  return result('selected',null,{cardId:within[0].id,distance:within[0].distance,
   eligibleCount,withinRangeCount:within.length});
 }
 function describeRune(worldPosition){
  if(!isPosition(worldPosition))return null;
  return Object.freeze({runeId:RUNE_ID,displayName:DISPLAY_NAME,worldAnchor:Object.freeze({
   x:worldPosition.x,y:Number.isFinite(worldPosition.y)?worldPosition.y:0,z:worldPosition.z
  }),contractState:'idle',verifiedWorldBinding:false,l2Certified:false,permanentNumber:null});
 }
 return Object.freeze({RUNE_ID,DISPLAY_NAME,DEFAULTS,isPosition,planarDistance,isOrdinaryCard,
  isEligibleEntry,normalizeOptions,findNearestEligibleCard,describeRune});
});
