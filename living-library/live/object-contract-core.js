/* NW-CORE-CONTRACT-001 / P0 stage 1: pure, read-only, type-neutral ownership index.
 * The persisted contract is authoritative; scene discovery, animation, position,
 * visibility and a prior owner history are deliberately not consulted.
 * Candidate module; not wired to gameplay until an independently reviewed change.
 */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingObjectContractCore=api;})(typeof self!=='undefined'?self:globalThis,function(){
 'use strict';
 const inactive=new Set(['unbound','released','revoked','cancelled','failed','inactive','broken','invalid','error','pending','draft']);
 const own=(obj,key)=>Object.prototype.hasOwnProperty.call(obj,key);
 const identifier=value=>typeof value==='string'&&value.length>0&&value.length<=160&&/^[a-zA-Z0-9_.-]+$/.test(value);
 const keyOf=ref=>{
  if(!ref||typeof ref!=='object'||!identifier(ref.kind)||!identifier(ref.id))throw Error('INVALID_MEMBER_ID');
  return JSON.stringify([ref.kind,ref.id]); // no kind/id delimiter collision
 };
 const snapshotMember=ref=>Object.freeze({kind:ref.kind,id:ref.id});
 function buildIndex(input){
  const s=Array.isArray(input)?{version:1,objects:input}:input;
  if(!s||s.version!==1||!Array.isArray(s.objects))throw Error('INVALID_SAVED_STATE');
  const owners=new Map(),contracts=new Map(),cards=new Map(),diagnostics=[];
  const warn=(code,cardId,detail)=>diagnostics.push(Object.freeze({code,cardId,detail:String(detail||'')}));
  const idSeen=new Set(),contractSeen=new Map();
  for(const card of s.objects){
   if(!card||typeof card!=='object'||!identifier(card.id)||idSeen.has(card.id)){
    throw Error('INVALID_OR_DUPLICATE_SAVED_ID');
   }
   idSeen.add(card.id);
  }
  for(const card of s.objects){
   if(!card.runeBinding)continue;
   const b=card.runeBinding;
   if(typeof b!=='object'||!identifier(b.runeId)||typeof b.state!=='string'){
    warn('INVALID_BINDING',card.id,'Missing binding id or state');continue;
   }
   if(inactive.has(b.state.trim().toLowerCase()))continue;
   // One current owner for an actual contract. A previously released card is not a claimant.
   const contractId=b.runeId;
   if(contractSeen.has(contractId)){
    warn('DUPLICATE_CONTRACT_OWNER',card.id,contractId);
    warn('DUPLICATE_CONTRACT_OWNER',contractSeen.get(contractId),contractId);
    continue;
   }
   contractSeen.set(contractId,card.id);
   const stored=card.scanContract||card.truthEyeContract||card.runeContract||null;
   if(stored&&typeof stored!=='object'){warn('INVALID_CONTRACT',card.id,contractId);continue;}
   if(stored){
    const ids=['artifactId','runeId','contractId'].filter(k=>own(stored,k));
    if(ids.some(k=>stored[k]!==contractId)){
     warn('CONTRACT_ID_MISMATCH',card.id,contractId);continue;
    }
   }
   let members,explicitMembers=false;
   if(card.scanContract){
    if(!Array.isArray(stored.members)||!stored.members.length||stored.members.length>512){
     warn('INVALID_MEMBER_SET',card.id,contractId);continue;
    }
    members=stored.members;explicitMembers=true;
   }else if(stored&&Array.isArray(stored.members)){
    if(!stored.members.length||stored.members.length>512){warn('INVALID_MEMBER_SET',card.id,contractId);continue;}
    members=stored.members;explicitMembers=true;
   }else{
    // Explicitly declared singleton rune/artifact ID, not a guessed 3D identity.
    members=[{kind:card.truthEyeContract?'artifact':'rune',id:contractId}];
   }
   const seen=new Set(),mapped=[];let valid=true;
   for(const ref of members){
    try{
     const key=keyOf(ref);
     if(seen.has(key)){warn('DUPLICATE_MEMBER_IN_CONTRACT',card.id,ref.id);valid=false;continue;}
     seen.add(key);
     if(ref.kind==='scene'&&ref.id.startsWith('scene.auto.'))
      warn('UNVERIFIED_SCENE_ID',card.id,ref.id);
     mapped.push({key,member:snapshotMember(ref)});
    }catch(_){warn('INVALID_MEMBER_ID',card.id,contractId);valid=false;}
   }
   if(!valid)continue;
   const detail=Object.freeze({contractId,ownerCardId:card.id,form:stored?.form||null,
    members:Object.freeze(mapped.map(x=>x.member)),explicitMembers,
    // A stored member ID alone cannot prove resource recovery, even when stable.
    recovery:'UNVERIFIED'});
   contracts.set(contractId,detail);cards.set(card.id,detail);
   for(const item of mapped){
    if(owners.has(item.key)){
     warn('DUPLICATE_MEMBER_OWNER',card.id,item.member.id);
     warn('DUPLICATE_MEMBER_OWNER',owners.get(item.key).ownerCardId,item.member.id);
    }else owners.set(item.key,detail);
   }
  }
  const conflicts=new Set(['DUPLICATE_CONTRACT_OWNER','CONTRACT_ID_MISMATCH','DUPLICATE_MEMBER_OWNER',
   'INVALID_BINDING','INVALID_CONTRACT','INVALID_MEMBER_SET','DUPLICATE_MEMBER_IN_CONTRACT','INVALID_MEMBER_ID']);
  const healthy=!diagnostics.some(d=>conflicts.has(d.code));
  const ownerOf=ref=>{
   const key=keyOf(ref);
   // On contradictory records, never select an arbitrary current owner.
   if(diagnostics.some(d=>d.code==='DUPLICATE_MEMBER_OWNER'&&d.detail===ref.id))return null;
   return owners.get(key)?.ownerCardId||null;
  };
  return Object.freeze({
   healthy,diagnostics:Object.freeze(diagnostics),
   ownerOf,
   contractOf:contractId=>contracts.get(contractId)||null,
   cardContract:cardId=>cards.get(cardId)||null,
   membersOf:cardId=>cards.get(cardId)?.members||Object.freeze([]),
   assertHealthy(){if(!healthy)throw Error('CONTRACT_INDEX_INCONSISTENT');return true;}
  });
 }
 return Object.freeze({buildIndex,keyOf});
});
