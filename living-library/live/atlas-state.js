/* Geometry-independent placement contract. Card identity/data remain in ObjectCraft. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingAtlasState=api;})(typeof self!=='undefined'?self:this,function(){
'use strict';
const ATLAS='local.atlas.compendium',COLLECTION='local.atlas.collection';
function capacity(id){if(id===ATLAS)return 10;if(id===COLLECTION)return 18;throw Error('未知卡冊')}
function validSlot(id,slot){return Number.isInteger(slot)&&slot>=0&&slot<capacity(id)}
function readerActive(card){return !!card&&card.containerId===ATLAS&&card.slotIndex===9}
function slots(cards,id){return Array.from({length:capacity(id)},(_,slot)=>cards.find(c=>c.containerId===id&&c.slotIndex===slot)||null)}
return {ATLAS,COLLECTION,capacity,validSlot,readerActive,slots};
});
