/* NW-SUMMON-001: read-only reverse-containment discovery for ACTIVE rune cards.
 * Spent one-shot spells no longer exist and have no summoning history. */
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
   card.containment?.kind==='rune_contract';
 }
 function storedCards(cards,holderId){
  if(!Array.isArray(cards)||typeof holderId!=='string'||!holderId)return [];
  return cards.filter(c=>containedIn(c,holderId)).map(c=>Object.freeze({
   cardId:c.id,title:c.title||'未命名卡片',holderId,
   kind:c.containment.kind,usable:true,requiredAction:'rune-cardification'
  }));
 }
 function preview(cards,{holderId,cardId,action='inspect'}={}){
  if(!['inspect','rune-cardification'].includes(action))
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
