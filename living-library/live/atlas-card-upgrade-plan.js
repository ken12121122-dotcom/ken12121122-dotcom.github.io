/* Atlas upgrade request preview — card identity stays unchanged.
 * This module does not modify cards, issue numbers, certify L2, or write data.
 */
(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LivingAtlasUpgradePlan=api;
})(typeof self!=='undefined'?self:this,function(){
 'use strict';
 const SCHEMA='nw-atlas-card-upgrade-plan-v1';
 const CARD_ID=/^local\.card\.[a-zA-Z0-9-]{1,80}$/;
 const ALLOWED_STATUS=new Set(['draft']);
 function inspect({card=null,reason='',worldEntityId='',worldAnchor=null}={}){
  const errors=[];
  if(!card||typeof card!=='object'||Array.isArray(card)||
   card.objectType!=='card'||card.templateId!=='blank-card-v1'||
   typeof card.id!=='string'||!CARD_ID.test(card.id)||
   !ALLOWED_STATUS.has(card.status)||!Number.isSafeInteger(card.revision)||card.revision<1){
   errors.push('invalid_or_non_ordinary_card');
  }
  if(typeof card?.title!=='string'||!card.title.trim()||card.title.trim()==='空白卡片'||card.title.length>40)
   errors.push('card_needs_a_meaningful_title');
  if(typeof reason!=='string'||reason.trim().length<10||reason.length>500)
   errors.push('upgrade_reason_needs_10_to_500_characters');
  if(card?.status==='recycled')errors.push('recycled_card_is_not_eligible');
  // World binding evidence is deliberately read-only and optional at this stage.
  if(worldEntityId&&typeof worldEntityId!=='string')errors.push('invalid_world_reference');
  if(worldAnchor!==null&&(!worldAnchor||Array.isArray(worldAnchor)||typeof worldAnchor!=='object'))errors.push('invalid_world_anchor');
  const ready=errors.length===0;
  return {
   schema:SCHEMA,project_id:'NW-ATLAS',card_id:card?.id||null,
   source_revision:Number.isSafeInteger(card?.revision)?card.revision:null,
   candidate_title:typeof card?.title==='string'?card.title:null,
   reason:typeof reason==='string'?reason.trim():null,
   workflow:['ordinary_card','upgrade_application','ai_review','owner_approval','world_binding','l2_certification','permanent_number'],
   stage:ready?'upgrade_application_draft_ready':'ordinary_card_needs_preparation',
   application_ready:ready,
   upgrade_executed:false,l2_certified:false,identity_issued:false,
   permanent_number:null,permanent_slot:null,may_assign_number:false,
   requires_ai_review:true,requires_owner_approval:true,
   world_binding_status:'not_verified',
   owner_decision:'pending',review_status:'generated',
   errors,
   next_action:ready?'send_to_ai_and_owner_for_review':'complete_application_fields_before_review',
   evidence:['living-library/live/object-craft.js','living-library/live/atlas-rulebook-review.html']
  };
 }
 return Object.freeze({SCHEMA,inspect});
});
