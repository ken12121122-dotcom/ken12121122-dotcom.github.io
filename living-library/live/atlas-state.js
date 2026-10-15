/* Geometry-independent placement contract. Card identity/data remain in ObjectCraft. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingAtlasState=api;})(typeof self!=='undefined'?self:this,function(){
'use strict';
const ATLAS='local.atlas.compendium',COLLECTION='local.atlas.collection';
const DEFAULTS=Object.freeze({atlasDistance:1.35,atlasHeight:2.35,atlasSize:1,atlasSeparation:.95,atlasFocusDistance:1,atlasCardSize:1});
const LIMITS=Object.freeze({atlasDistance:[.8,2.8],atlasHeight:[1.2,3.6],atlasSize:[.65,1.4],atlasSeparation:[.65,1.8],atlasFocusDistance:[.75,1.5],atlasCardSize:[.65,1]});
function normalizeSettings(raw){const out={};for(const [key,value] of Object.entries(DEFAULTS)){const n=raw?.[key],[lo,hi]=LIMITS[key];out[key]=typeof n==='number'&&Number.isFinite(n)?Math.min(hi,Math.max(lo,n)):value}return out}
// Upgrade only the new book; retain intentional shelf choices and existing atlas layout.
function introduceCollection(bookPrefs,atlasView){return !atlasView&&!bookPrefs?.known?.includes('cardCollection')}
function capacity(id){if(id===ATLAS)return 10;if(id===COLLECTION)return 18;throw Error('未知卡冊')}
function validSlot(id,slot){return Number.isInteger(slot)&&slot>=0&&slot<capacity(id)}
function readerActive(card){return !!card&&card.containerId===ATLAS&&card.slotIndex===9}
function slots(cards,id){return Array.from({length:capacity(id)},(_,slot)=>cards.find(c=>c.containerId===id&&c.slotIndex===slot)||null)}
return {ATLAS,COLLECTION,DEFAULTS,LIMITS,normalizeSettings,introduceCollection,capacity,validSlot,readerActive,slots};
});
