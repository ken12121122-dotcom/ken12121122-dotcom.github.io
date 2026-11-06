/* Authoring metadata only. No ActionRegistry registration, executor or scene access. */
(function(root,factory){if(typeof module==='object'&&module.exports)module.exports=factory(require('./spell-contract.js'));else root.LivingSpellDraft=factory(root.LivingSpellContract);})(typeof self!=='undefined'?self:this,function(Contract){
 'use strict';
 const actions=Object.freeze([
  ['book.drag_active','condition','抓著書本'],
  ['voice.incantation_matched','condition','咒語命中'],
  ['book.release_grab','effect','解除抓取'],
  ['book.return_to_assigned_shelf','effect','返回原書架'],
  ['ui.toast','effect','結果提示']
 ].map(([action_id,kind,label])=>Object.freeze({action_id,kind,label,version:'0.1.0',target_type:'library.book_instance',outputs:Object.freeze({type:'boolean'}),policy:Object.freeze({scope:'local'}),capability_state:'planned'})));
 // Structural validation catalog, deliberately not an executable registry.
 const catalog=Object.freeze({describe:id=>actions.find(a=>a.action_id===id)});
 const fail=()=>{throw Object.assign(new Error('UNSUPPORTED_DRAFT'),{code:'UNSUPPORTED_DRAFT'})};
 function create(spell_id){
  return {schema:'nw.spell.v1',spell_id,version:'0.1.0',name:'歸書術',description:'抓著書本並念中咒語後，讓同一本書回到原書架。',incantations:['萬書歸位'],enabled:false,target_type:'library.book_instance',
   runes:actions.map((a,i)=>({schema:'nw.rune.v1',rune_id:'r'+i,action_id:a.action_id,action_version:a.version,label:a.label,parameters:{}})),
   conditions:{operator:'AND',refs:['r0','r1']},effects:{operator:'SEQUENCE',refs:['r2','r3','r4']},
   connections:[{from:'r0',to:'r2',operator:'AND'},{from:'r1',to:'r2',operator:'AND'},{from:'r2',to:'r3',operator:'SEQUENCE'},{from:'r3',to:'r4',operator:'SEQUENCE'}]};
 }
 function validate(card){
  Contract.validateSpell(card,catalog);
  if(!/^spell\.local\.[0-9a-f-]{36}$/.test(card.spell_id)||card.enabled!==false||card.name.length>128||card.description.length>4000||card.incantations.length>16||card.incantations.some(v=>v.length>128))fail();
  const template=create(card.spell_id);
  // Other saved spell graphs stay untouched; this slice edits disabled recipes.
  for(const key of ['version','target_type','runes','conditions','effects','connections'])if(JSON.stringify(card[key])!==JSON.stringify(template[key]))fail();
  return card;
 }
 return Object.freeze({catalog,create,validate});
});
