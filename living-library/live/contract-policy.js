/* Universal unbinding policy: inspect persisted ORIGINAL player card data.
 * No rune/artifact IDs, visual mesh names or title comparisons in target rules.
 * The world object supplies only an observable world position via boundCardId.
 */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingContractPolicy=api})(typeof self!=='undefined'?self:globalThis,function(){
 'use strict';
 const deniedStates=new Set(['pending','unbound','released','revoked','cancelled','failed','inactive','draft','broken','invalid','error']);
 const isPlayerCard=c=>!!c&&typeof c==='object'&&c.objectType==='card'&&
  c.templateId==='blank-card-v1'&&typeof c.id==='string'&&c.id.startsWith('local.card.');
 function isUpgraded(c){
  if(!c)return true;
  const r=c.permanentRegistry,rg=c.permanentRegistration,cert=c.certification;
  return c.l2Certified===true||c.permanentCard===true||c.upgradeRequest?.l2Certified===true||
   r?.state==='registered'||rg?.state==='registered'||
   ['L2','permanent'].includes(cert?.level)||
   (c.permanentNumber!==null&&c.permanentNumber!==undefined&&String(c.permanentNumber).trim()!=='')||
   (c.upgradeRequest?.state&&!['rejected','cancelled'].includes(c.upgradeRequest.state));
 }
 function binding(c){
  const b=c?.runeBinding;
  if(!b||typeof b.runeId!=='string'||!b.runeId.trim()||
   typeof b.state!=='string'||!b.state.trim()||deniedStates.has(b.state.trim().toLowerCase())||
   b.unbindable===false||b.allowUnbind===false)return null;
  // A record whose contract-owner metadata explicitly contradicts the
  // canonical binding is corrupted/ambiguous: NEVER remove only half of it.
  for(const [key,value] of Object.entries(c)){
   if(!/Contract$/.test(key)||!value||typeof value!=='object'||Array.isArray(value))continue;
   for(const idKey of ['artifactId','runeId','contractId']){
    if(Object.hasOwn(value,idKey)&&value[idKey]!==b.runeId)return null;
   }
  }
  return b;
 }
 function canUnbind(c){
  return isPlayerCard(c)&&c.status==='draft'&&c.placement==='hover'&&
   !c.containerId&&!c.ownerBookId&&!c.containment&&!c.consumedBy&&
   !isUpgraded(c)&&!!binding(c);
 }
 function contractOwnedFields(c){
  const b=binding(c);if(!b)return [];
  // Never strip user-authored descriptions/artwork/ability.
  // Clear only explicit contract metadata with proof of this SAME contract ID.
  const candidates=Object.keys(c).filter(k=>k!=='runeBinding'&&/Contract$/.test(k));
  const id=b.runeId;
  return candidates.filter(k=>{
   const v=c[k];
   return !!v&&typeof v==='object'&&!Array.isArray(v)&&
    (v.artifactId===id||v.runeId===id||v.contractId===id);
  });
 }
 function removeBinding(c){
  if(!canUnbind(c))throw Error('原卡不符合解除綁定資格');
  const next={...c};
  delete next.runeBinding;
  for(const name of contractOwnedFields(c))delete next[name];
  return next;
 }
 return Object.freeze({isPlayerCard,isUpgraded,binding,canUnbind,contractOwnedFields,removeBinding});
});