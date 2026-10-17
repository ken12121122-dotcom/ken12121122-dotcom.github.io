/* NW-SUMMON-001: read-only, deterministic reverse-containment discovery.
 * This is architectural scaffolding ONLY: never auto-releases, recharges,
 * resets contracts or grants another copy of a consumed card. */
(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LivingSummonPlan=api;
})(typeof self!=='undefined'?self:globalThis,function(){
 'use strict';
 function validCard(c){return !!c&&typeof c.id==='string'&&c.id.startsWith('local.card.')&&
  c.objectType==='card'&&c.templateId==='blank-card-v1'&&c.status!=='recycled'}
 function containedIn(card,holderId){
  return validCard(card)&&card.placement==='contained'&&
   card.containment?.state==='stored'&&card.containment?.holderId===holderId&&
   ['card','rune'].includes(card.containment?.holderType)&&
   ['consumed_spell','rune_contract'].includes(card.containment?.kind);
 }
 function storedCards(cards,holderId){
  if(!Array.isArray(cards)||typeof holderId!=='string'||!holderId)return [];
  return cards.filter(c=>containedIn(c,holderId)).map(c=>Object.freeze({
   cardId:c.id,title:c.title||'未命名卡片',holderId,
   kind:c.containment.kind,usable:c.containment.kind!=='consumed_spell',
   requiredAction:c.containment.kind==='consumed_spell'?'summon-spell':'rune-cardification'
  }));
 }
 function preview(cards,{holderId,cardId,action='inspect'}={}){
  if(!['inspect','summon-spell','rune-cardification'].includes(action))
   return Object.freeze({allowed:false,reason:'unsupported_action'});
  const matches=storedCards(cards,holderId).filter(c=>c.cardId===cardId);
  if(matches.length!==1)return Object.freeze({allowed:false,reason:'not_found_or_ambiguous'});
  const selected=matches[0];
  if(action==='inspect')return Object.freeze({allowed:true,action,card:selected});
  if(selected.requiredAction!==action)return Object.freeze({allowed:false,reason:'wrong_method',card:selected});
  // Actual release needs a separate authenticated spell/confirmation transaction.
  return Object.freeze({allowed:false,reason:'release_not_implemented',card:selected});
 }
 return Object.freeze({validCard,containedIn,storedCards,preview});
});
