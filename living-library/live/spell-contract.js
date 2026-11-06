/* Candidate card/graph validation only; does not execute effects or enable UI. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingSpellContract=api;})(typeof self!=='undefined'?self:this,function(){
 'use strict';
 const fail=code=>{const e=new Error(code);e.code=code;throw e};
 const text=v=>typeof v==='string'&&v.trim().length>0;
 const id=v=>text(v)&&/^[a-z][a-z0-9_.-]{0,127}$/.test(v);
 const semver=v=>typeof v==='string'&&/^\d+\.\d+\.\d+$/.test(v);
 function object(v,keys){if(!v||typeof v!=='object'||Array.isArray(v)||Object.keys(v).some(k=>!keys.includes(k))||keys.some(k=>!Object.hasOwn(v,k)))fail('INVALID_CARD_SCHEMA')}
 function rune(card,registry){
  object(card,['schema','rune_id','action_id','action_version','label','parameters']);
  if(card.schema!=='nw.rune.v1'||!id(card.rune_id)||!text(card.label)||!semver(card.action_version))fail('INVALID_CARD_SCHEMA');
  const action=registry.describe(card.action_id);if(!action)fail('MISSING_ACTION');
  if(action.version!==card.action_version)fail('ACTION_VERSION_MISMATCH');
  // Parameters are checked by the registry at execution time; a draft may bind
  // runtime context later. Validation here never pretends that a handler ran.
  if(!card.parameters||typeof card.parameters!=='object'||Array.isArray(card.parameters))fail('INVALID_PARAMETERS');
  return action;
 }
 function spell(card,registry){
  object(card,['schema','spell_id','version','name','description','incantations','enabled','target_type','runes','conditions','effects','connections']);
  if(card.schema!=='nw.spell.v1'||!id(card.spell_id)||!semver(card.version)||!text(card.name)||typeof card.description!=='string'||typeof card.enabled!=='boolean'||!id(card.target_type))fail('INVALID_CARD_SCHEMA');
  if(!Array.isArray(card.incantations)||!card.incantations.length||card.incantations.some(v=>!text(v))||new Set(card.incantations.map(v=>v.trim())).size!==card.incantations.length)fail('INVALID_INCANTATIONS');
  if(!Array.isArray(card.runes)||!card.runes.length||card.runes.length>64)fail('INVALID_RUNES');
  const nodes=new Map();
  for(const r of card.runes){
   const action=rune(r,registry);if(nodes.has(r.rune_id))fail('DUPLICATE_RUNE_ID');
   if(action.target_type!==card.target_type)fail('TARGET_TYPE_MISMATCH');
   if(action.policy.scope!=='local')fail('OWNER_GATE_REQUIRED');
   nodes.set(r.rune_id,action);
  }
  object(card.conditions,['operator','refs']);object(card.effects,['operator','refs']);
  if(card.conditions.operator!=='AND'||card.effects.operator!=='SEQUENCE')fail('UNSUPPORTED_OPERATOR');
  const conditionRefs=card.conditions.refs,effectRefs=card.effects.refs;
  for(const refs of [conditionRefs,effectRefs])if(!Array.isArray(refs)||!refs.length||refs.some(v=>!id(v)))fail('INVALID_REFS');
  const used=[...conditionRefs,...effectRefs];
  if(new Set(used).size!==used.length||used.length!==nodes.size)fail('DUPLICATE_OR_UNUSED_RUNE');
  for(const ref of used)if(!nodes.has(ref))fail('MISSING_RUNE');
  if(conditionRefs.some(ref=>!['event','condition'].includes(nodes.get(ref).kind))||effectRefs.some(ref=>nodes.get(ref).kind!=='effect'))fail('WRONG_ACTION_KIND');
  if(conditionRefs.some(ref=>nodes.get(ref).outputs.type!=='boolean'))fail('CONDITION_TYPE_MISMATCH');
  if(!Array.isArray(card.connections)||card.connections.length>128)fail('INVALID_CONNECTIONS');
  const adjacency=new Map([...nodes.keys()].map(k=>[k,[]]));
  for(const c of card.connections){
   object(c,['from','to','operator']);
   if(!nodes.has(c.from)||!nodes.has(c.to))fail('MISSING_RUNE');
   if(!['AND','SEQUENCE'].includes(c.operator))fail('UNSUPPORTED_OPERATOR');
   adjacency.get(c.from).push(c.to);
  }
  const visiting=new Set(),done=new Set();
  function visit(key){if(visiting.has(key))fail('GRAPH_CYCLE');if(done.has(key))return;visiting.add(key);adjacency.get(key).forEach(visit);visiting.delete(key);done.add(key)}
  nodes.forEach((_,key)=>visit(key));
  // MVP has a single explicit conjunction followed by one ordered effect chain.
  const expected=conditionRefs.map(from=>({from,to:effectRefs[0],operator:'AND'}));
  for(let i=1;i<effectRefs.length;i++)expected.push({from:effectRefs[i-1],to:effectRefs[i],operator:'SEQUENCE'});
  const key=c=>JSON.stringify([c.from,c.to,c.operator]);
  const wanted=new Set(expected.map(key)),actual=new Set(card.connections.map(key));
  if(actual.size!==card.connections.length||actual.size!==wanted.size||[...actual].some(k=>!wanted.has(k)))fail('GRAPH_ORDER_MISMATCH');
  return Object.freeze({valid:true,spell_id:card.spell_id,version:card.version,rune_count:nodes.size,runtime_verified:false});
 }
 return Object.freeze({validateRune:rune,validateSpell:spell});
});
