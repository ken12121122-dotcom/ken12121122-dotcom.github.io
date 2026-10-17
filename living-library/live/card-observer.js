/* Explicit local-only, read-only AI observation for ONE saved player card.
 * No raw photo bytes, unknown object properties, network transport, or storage writes.
 * The user must manually share the snapshot with an assistant.
 */
(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LivingCardObserver=api;
})(typeof self!=='undefined'?self:this,function(){
 'use strict';
 const SCHEMA='nw.ai-card-observation.v1';
 const SOURCE_KEY='living-library-player-objects-v1';
 function fail(code){const error=new Error(code);error.code=code;throw error}
 function snapshot({storage,craft,cardId,clock=()=>new Date().toISOString()}={}){
  if(!storage||typeof storage.getItem!=='function'||!craft||typeof craft.read!=='function'||
    craft.KEY!==SOURCE_KEY)fail('OBSERVATION_SOURCE_UNAVAILABLE');
  if(typeof cardId!=='string'||!/^local\.card\.[a-zA-Z0-9-]{1,100}$/.test(cardId))
   fail('OBSERVATION_CARD_ID_INVALID');
  if(typeof clock!=='function')fail('OBSERVATION_CLOCK_INVALID');
  let state;
  try{state=craft.read(storage)}catch(_){fail('OBSERVATION_SOURCE_INVALID')}
  const item=state.objects.find(card=>card.id===cardId&&card.objectType==='card'&&
    card.templateId==='blank-card-v1'&&card.status!=='recycled');
  if(!item)fail('OBSERVATION_CARD_NOT_FOUND');
  let exportedAt;
  try{exportedAt=clock()}catch(_){fail('OBSERVATION_CLOCK_INVALID')}
  if(typeof exportedAt!=='string'||!exportedAt)fail('OBSERVATION_CLOCK_INVALID');
  const img=item.image;
  // Strict allowlist: never expose image.dataUrl, arbitrary future fields, tokens, or full storage.
  const photo=img&&typeof img==='object'&&typeof img.dataUrl==='string' ? {
   present:true,
   mime_type:img.dataUrl.startsWith('data:image/jpeg;base64,')?'image/jpeg':'unknown',
   encoded_characters:img.dataUrl.length,
   scale:Number.isFinite(img.scale)?img.scale:null,
   x:Number.isFinite(img.x)?img.x:null,
   y:Number.isFinite(img.y)?img.y:null
  }:{present:false};
  // Non-executable, explicitly allowlisted contract/upgrade evidence.
  // These fields let a receiving assistant distinguish a bound original rune
  // from a manually typed card number without leaking arbitrary card metadata.
  const binding=item.runeBinding&&typeof item.runeBinding==='object'?{
   rune_id:typeof item.runeBinding.runeId==='string'?item.runeBinding.runeId:null,
   state:['bound','sealed','cardified'].includes(item.runeBinding.state)?item.runeBinding.state:'unknown',
   bound_at:typeof item.runeBinding.boundAt==='string'?item.runeBinding.boundAt:null,
   sealed_at:typeof item.runeBinding.sealedAt==='string'?item.runeBinding.sealedAt:null,
   cardified_at:typeof item.runeBinding.cardifiedAt==='string'?item.runeBinding.cardifiedAt:null
  }:null;
  const registry=item.permanentRegistry&&typeof item.permanentRegistry==='object'?{
   state:item.permanentRegistry.state==='registered'?'registered':'unverified',
   scope:item.permanentRegistry.scope==='local-only'?'local-only':'unverified',
   number:Number.isSafeInteger(item.permanentRegistry.number)?item.permanentRegistry.number:null,
   book_id:typeof item.permanentRegistry.bookId==='string'?item.permanentRegistry.bookId:null,
   slot_index:Number.isInteger(item.permanentRegistry.slotIndex)?item.permanentRegistry.slotIndex:null,
   card_id:typeof item.permanentRegistry.cardId==='string'?item.permanentRegistry.cardId:null,
   registered_at:typeof item.permanentRegistry.registeredAt==='string'?item.permanentRegistry.registeredAt:null
  }:null;
  const origin=item.permanentRegistry?.anchor?.position;
  const worldAnchor=registry?.state==='registered'&&origin&&
   ['x','y','z'].every(k=>Number.isFinite(origin[k]))?{
    scene_id:item.permanentRegistry.anchor.sceneId==='living-library'?'living-library':null,
    position:{x:origin.x,y:origin.y,z:origin.z},
    rotation_y:Number.isFinite(item.permanentRegistry.anchor.rotationY)?
     item.permanentRegistry.anchor.rotationY:null
  }:null;
  return Object.freeze({
   schema:SCHEMA,
   review_status:'generated',
   observation_mode:'manual_export_only',
   read_only:true,
   image_payload_included:false,
   exported_at:exportedAt,
   card:{
    card_id:item.id,
    revision:Number.isSafeInteger(item.revision)?item.revision:null,
    card_number:typeof item.cardNumber==='string'?item.cardNumber:'',
    title:typeof item.title==='string'?item.title:'',
    description:typeof item.description==='string'?item.description:'',
    card_type:typeof item.cardType==='string'?item.cardType:'',
    linked_public_card_id:typeof item.linkedCardId==='string'?item.linkedCardId:null,
    owner_book_id:typeof item.ownerBookId==='string'?item.ownerBookId:null,
    container_id:typeof item.containerId==='string'?item.containerId:null,
    slot_index:Number.isInteger(item.slotIndex)?item.slotIndex:null,
    placement:typeof item.placement==='string'?item.placement:null,
    status:typeof item.status==='string'?item.status:null,
    updated_at:typeof item.updatedAt==='string'?item.updatedAt:null,
    rune_binding:binding,
    permanent_registration:registry,
    permanent_card:registry?.state==='registered'&&registry.card_id===item.id,
    world_anchor:worldAnchor,
    image:photo
   },
   source_records:[{source_type:'device_local_storage',source_key:SOURCE_KEY,
     object_id:item.id,revision:Number.isSafeInteger(item.revision)?item.revision:null}],
   unresolved_gaps:['photo_pixels_require_separate_user_screenshot',
     'no_live_remote_device_access']
  });
 }
 return Object.freeze({SCHEMA,SOURCE_KEY,snapshot});
});
