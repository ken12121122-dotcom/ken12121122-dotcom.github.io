/* RUNE-UNBIND-001: read-only target selection for an explicit local spell card.
 * Exact spell title semantics only; never consumes an arbitrary ordinary card. */
(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LivingRuneUnbindSpell=api;
})(typeof self!=='undefined'?self:globalThis,function(){
 'use strict';
 const RADIUS=3.6,AMBIGUITY=.15,ORIGIN='local.rune.origin',EYE='artifact.truth-eye';
 function isSpellCard(record){
  if(!record||typeof record!=='object'||record.objectType!=='card'||
     record.templateId!=='blank-card-v1'||typeof record.id!=='string'||
     !record.id.startsWith('local.card.')||record.status!=='draft'||
     record.containerId||record.ownerBookId||record.placement!=='hover'||record.runeBinding)return false;
  // Owner-supplied card, not an automatically generated second card.
  const title=typeof record.title==='string'?record.title.trim().replace(/\s+/g,''):'';
  return /^(解除綁定|解除契約|契約解除)(術|卡|魔法|卡片)?$/.test(title);
 }
 function validPoint(p){return p&&Number.isFinite(p.x)&&Number.isFinite(p.z)}
 function findNearestBoundCard(spellId,source,entries,options={}){
  const unavailable=(reason,rest={})=>Object.freeze({status:'unavailable',reason,cardId:null,distance:null,...rest});
  if(!isSpellCard(source?.record)||source.id!==spellId||!validPoint(source.position)||source.visible!==true)
   return unavailable('invalid_spell');
  if(!Array.isArray(entries))return unavailable('invalid_entries');
  const radius=Number.isFinite(options.radius)&&options.radius>0&&options.radius<=10?options.radius:RADIUS;
  const eligible=[];
  for(const candidate of entries){
   if(!candidate||candidate.id===spellId||candidate.id!==candidate.record?.id||
     !candidate.id?.startsWith('local.card.')||candidate.visible!==true||
     !validPoint(candidate.position)||candidate.isDragging||candidate.isSummoning||
     candidate.isRecycling||candidate.isFocused)continue;
   const c=candidate.record;
   if(c.objectType!=='card'||c.templateId!=='blank-card-v1'||c.status!=='draft'||
      c.placement!=='hover'||c.containerId||c.ownerBookId||
      !((c.runeBinding?.runeId===ORIGIN&&['bound','cardified'].includes(c.runeBinding.state))||(c.runeBinding?.runeId===EYE&&c.runeBinding.state==='bound'&&c.truthEyeContract?.artifactId===EYE&&['card','sealed','world'].includes(c.truthEyeContract.form)&&(c.truthEyeContract.form==='card'?!candidate.artifactWorldAnchor:candidate.artifactWorldAnchor===true)))||
      c.l2Certified===true||c.permanentCard===true||
      c.permanentRegistration?.state==='registered'||c.permanentRegistry?.state==='registered'||
      ['L2','permanent'].includes(c.certification?.level)||
      (c.permanentNumber!==null&&c.permanentNumber!==undefined&&String(c.permanentNumber).trim()!==''))continue;
   const d=Math.hypot(candidate.position.x-source.position.x,candidate.position.z-source.position.z);
   if(d<=radius)eligible.push({id:candidate.id,distance:d,runeId:c.runeBinding.runeId});
  }
  if(eligible.length===0)return unavailable('no_linked_card');
  eligible.sort((a,b)=>a.distance-b.distance||a.id.localeCompare(b.id));
  if(eligible.length>1&&eligible[1].distance-eligible[0].distance<AMBIGUITY)
   return unavailable('ambiguous_nearest',{candidateCount:eligible.length});
  return Object.freeze({status:'selected',reason:null,cardId:eligible[0].id,
   distance:eligible[0].distance,runeId:eligible[0].runeId,candidateCount:eligible.length});
 }
 return Object.freeze({RADIUS,AMBIGUITY,ORIGIN,EYE,isSpellCard,findNearestBoundCard});
});
