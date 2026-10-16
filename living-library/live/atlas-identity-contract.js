/* P1 candidate ID contract — validation-only. Never allocates a permanent ID.
 * "ATLAS-0001" is an unapproved example, NOT a registered atlas identity.
 * Nothing here touches localStorage, ObjectCraft, a network, or an authoritative ledger.
 */
(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LivingAtlasIdentity=api;
})(typeof self!=='undefined'?self:this,function(){
 'use strict';
 const SCHEMA='nw-atlas-identity-check-v1';
 const PROPOSED_PATTERN=/^ATLAS-(?!0+$)[0-9]{4,}$/;
 const STATUS=Object.freeze({
  missing:'missing',invalid:'invalid_proposed_format',matches:'matches_proposed_format',
  unchecked:'not_checked',duplicate:'duplicate_in_supplied_references',sampleClear:'no_conflict_in_supplied_references'
 });
 const EVIDENCE=Object.freeze([
  {path:'living-library/live/public-catalog.js',role:'existing_public_card_id'},
  {path:'living-library/live/atlas-state.js',role:'existing_local_container_id'},
  {path:'living-library/live/atlas-rulebook-review.html',role:'proposed_ATLAS_0001_and_owner_gate'}
 ]);
 function checkCandidate({candidateId,knownIds=null}={}){
  const supplied=typeof candidateId==='string'?candidateId:'';
  const candidate=supplied.trim();
  const validSyntax=candidate.length<=40&&PROPOSED_PATTERN.test(candidate)&&supplied===candidate;
  const formatStatus=!candidate?STATUS.missing:validSyntax?STATUS.matches:STATUS.invalid;
  let conflictStatus=STATUS.unchecked;
  const gap=['official_atlas_id_not_approved','authoritative_registry_not_available','identity_issuance_not_implemented'];
  if(knownIds!==null){
   if(!Array.isArray(knownIds)||knownIds.length>1000||knownIds.some(x=>typeof x!=='string'||x.length>40)){
    gap.push('supplied_reference_set_invalid');
   }else{
    const seen=new Set(knownIds.map(x=>x.toUpperCase()));
    conflictStatus=seen.has(candidate.toUpperCase())?STATUS.duplicate:STATUS.sampleClear;
    gap.push('supplied_references_are_not_authoritative');
   }
  }
  if(!validSyntax)gap.push('candidate_does_not_match_unapproved_pattern');
  if(conflictStatus===STATUS.duplicate)gap.push('candidate_conflict_in_supplied_references');
  return {
   schema:SCHEMA,
   project_id:'NW-ATLAS',
   card_id:'public.gaming.atlas',
   container_id:'local.atlas.compendium',
   candidate_id:candidate||null,
   proposed_pattern:'ATLAS-[0-9]{4,}, non-zero numeric suffix',
   format_status:formatStatus,
   conflict_status:conflictStatus,
   review_status:'generated',
   identity_issued:false,
   may_register:false,
   requires_human_review:true,
   source_records:EVIDENCE.map(x=>({...x})),
   unresolved_gaps:gap
  };
 }
 return Object.freeze({SCHEMA,STATUS,checkCandidate});
});
