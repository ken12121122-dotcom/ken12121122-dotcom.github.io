// Shared synthetic JSON fixtures: structural acceptance is not execution authority.
const {ActionRegistry}=require('../../living-library/live/action-registry.js');
const cases=[];
const add=(name,change,schema=true,semantic=schema,kind='spell')=>cases.push({name,change,schema,semantic,kind});
function fixture(){
 const registry=new ActionRegistry(),calls=[];
 for(const [action_id,kind] of [['book.drag_active','condition'],['book.synthetic_effect','effect']])registry.register({action_id,name:action_id,kind,version:'0.1.0',target_type:'library.book_instance',inputs:{type:'object',properties:{target:{type:'string'}},required:['target'],additionalProperties:false},outputs:{type:'boolean'},platform:['web'],policy:{scope:'local'},guard:()=>{calls.push('guard');return true},execute:()=>{calls.push('execute');return true}});
 const rune={schema:'nw.rune.v1',rune_id:'r0',action_id:'book.drag_active',action_version:'0.1.0',label:'Synthetic condition',parameters:{}};
 const card={schema:'nw.spell.v1',spell_id:'spell.synthetic.parity',version:'0.1.0',name:'Synthetic',description:'No private data',incantations:['synthetic phrase'],enabled:false,target_type:'library.book_instance',runes:[rune,{...rune,rune_id:'r1',action_id:'book.synthetic_effect'}],conditions:{operator:'AND',refs:['r0']},effects:{operator:'SEQUENCE',refs:['r1']},connections:[{from:'r0',to:'r1',operator:'AND'}]};
 return {registry,calls,card,rune};
}
add('valid disabled draft with late-bound parameters',()=>{});
add('valid enabled draft still does not execute',c=>c.enabled=true);
add('valid alternate incantations',c=>c.incantations=['one','two']);
add('valid maximum identity length',c=>c.spell_id='s'.repeat(128));
for(const field of ['schema','spell_id','version','name','description','incantations','enabled','target_type','runes','conditions','effects','connections'])add('missing spell '+field,c=>delete c[field],false);
for(const [name,change] of [
 ['unknown spell key',c=>c.handler='synthetic'],['invalid identity',c=>c.spell_id='INVALID'],['identity too long',c=>c.spell_id='s'.repeat(129)],['version type',c=>c.version=1],['empty name',c=>c.name='  '],['description type',c=>c.description=null],['empty incantations',c=>c.incantations=[]],['blank phrase',c=>c.incantations=[' ']],['duplicate phrase',c=>c.incantations=['one','one']],['enabled type',c=>c.enabled='false'],['empty runes',c=>c.runes=[]],['unknown condition key',c=>c.conditions.extra=true],['empty condition refs',c=>c.conditions.refs=[]],['unsupported effect operator',c=>c.effects.operator='OR'],['unknown connection key',c=>c.connections[0].extra=true],['invalid connection operator',c=>c.connections[0].operator='WHILE'],['duplicate edge',c=>c.connections.push({...c.connections[0]})]
])add(name,change,false);
for(const field of ['schema','rune_id','action_id','action_version','label','parameters'])add('missing rune '+field,r=>delete r[field],false,false,'rune');
for(const [name,change] of [['rune unknown key',r=>r.handler='synthetic'],['rune array parameters',r=>r.parameters=[]],['rune null parameters',r=>r.parameters=null],['rune blank label',r=>r.label=' '],['rune bad version',r=>r.action_version='latest']])add(name,change,false,false,'rune');
add('valid standalone rune with late binding',()=>{},true,true,'rune');
add('trim-equivalent phrases require semantic rejection',c=>c.incantations=['one',' one '],true,false);
add('missing handler requires semantic rejection',c=>c.runes[1].action_id='book.absent',true,false);
add('stale action version requires semantic rejection',c=>c.runes[1].action_version='9.0.0',true,false);
add('target mismatch requires semantic rejection',c=>c.target_type='library.avatar',true,false);
add('wrong action kind requires semantic rejection',c=>c.runes[1].action_id='book.drag_active',true,false);
add('duplicate rune identity requires semantic rejection',c=>c.runes[1].rune_id='r0',true,false);
add('missing graph endpoints require semantic rejection',c=>c.connections[0].to='absent',true,false);
add('cycle requires semantic rejection',c=>c.connections.push({from:'r1',to:'r0',operator:'SEQUENCE'}),true,false);
add('missing edge requires semantic rejection',c=>c.connections=[],true,false);
function chain(c,count){c.runes=Array.from({length:count},(_,i)=>({...c.runes[i?1:0],rune_id:'r'+i}));c.effects.refs=c.runes.slice(1).map(r=>r.rune_id);c.connections=[{from:'r0',to:'r1',operator:'AND'},...c.effects.refs.slice(1).map((to,i)=>({from:c.effects.refs[i],to,operator:'SEQUENCE'}))]}
add('64-rune boundary preserves stable action references',c=>chain(c,64));
add('65-rune overflow rejected by both',c=>chain(c,65),false);
module.exports={cases,fixture};
