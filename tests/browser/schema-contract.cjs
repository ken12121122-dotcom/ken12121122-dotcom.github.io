const Ajv=require('ajv/dist/2020');
const assert=require('node:assert/strict');
const rune=require('../../living-library/live/rune-card.schema.json');
const spell=require('../../living-library/live/spell-card.schema.json');
const ajv=new Ajv({strict:true,allErrors:true});
ajv.addSchema(rune);
const validateRune=ajv.getSchema(rune.$id),validateSpell=ajv.compile(spell);
const r={schema:'nw.rune.v1',rune_id:'r0',action_id:'book.drag_active',action_version:'0.1.0',label:'Synthetic condition',parameters:{}};
const s={schema:'nw.spell.v1',spell_id:'spell.synthetic',version:'0.1.0',name:'Synthetic spell',description:'Fixture only',incantations:['synthetic spell'],enabled:false,target_type:'library.book_instance',runes:[r,{...r,rune_id:'r1',action_id:'book.return_to_assigned_shelf'}],conditions:{operator:'AND',refs:['r0']},effects:{operator:'SEQUENCE',refs:['r1']},connections:[{from:'r0',to:'r1',operator:'AND'}]};
assert.ok(validateRune(r),JSON.stringify(validateRune.errors));
assert.ok(validateSpell(s),JSON.stringify(validateSpell.errors));
let rejected=0;
for(const change of [c=>delete c.spell_id,c=>c.incantations=[' '],c=>c.enabled='yes',c=>c.runes[0].action_version='latest',c=>c.conditions.operator='OR',c=>c.runes[0].handler='text',c=>c.connections[0].operator='WHILE',c=>c.javascript='text']){
 const c=structuredClone(s);change(c);assert.equal(validateSpell(c),false);rejected++;
}
console.log(JSON.stringify({schema:'nw-card-json-schema-proof-v1',result:'PASS',positive_cases:2,negative_cases:rejected,meta_schema:'Draft 2020-12',limits:'Structural validation only; registry/graph semantic validation is a separate gate'}));
