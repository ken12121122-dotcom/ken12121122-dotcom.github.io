import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url),B=require('../living-library/live/scan-bundle.js');
const blank=id=>({id:'local.card.'+id,objectType:'card',templateId:'blank-card-v1',status:'draft',placement:'hover',title:'空白卡片',revision:1});
function db(objects){let raw=JSON.stringify({version:1,objects});return {getItem:()=>raw,setItem(k,v){raw=v}}}
const refs=[{kind:'book',id:'research'},{kind:'book',id:'local.book.one'}];
test('one original card owns every scanned identity, survives reload, restores and repacks',()=>{
 const c=blank('one'),other=blank('other'),s=db([c,other]);
 const next=B.bind(s,{cardId:c.id,revision:1,members:refs},refs);
 assert.equal(next.id,c.id);assert.equal(B.read(s).objects.length,2);
 assert.deepEqual(B.read(s).objects[1],other);assert.equal(B.claims(s,true).size,2);
 assert.deepEqual(B.bundles(s)[0].contract.members,refs);
 const released=B.setForm(s,c.id,2,'world',refs);assert.equal(B.claims(s,true).size,0);assert.equal(B.claims(s).size,2);
 assert.equal(B.setForm(s,c.id,released.revision,'card',refs).scanContract.form,'card');
 assert.equal(B.claims(s,true).size,2);
});
test('range uses adjustable radius, centered aperture and world direction including wraparound',()=>{
 const a={x:0,z:0};
 assert.equal(B.inRange({x:0,z:-4},a,{radius:4,aperture:15,direction:0}),true);
 assert.equal(B.inRange({x:0,z:-4.01},a,{radius:4,aperture:360,direction:0}),false);
 assert.equal(B.inRange({x:4,z:0},a,{radius:4,aperture:15,direction:90}),true);
 assert.equal(B.inRange({x:4,z:0},a,{radius:4,aperture:15,direction:0}),false);
 assert.equal(B.inRange({x:0,z:-4},a,{radius:4,aperture:15,direction:360}),true);
});
test('unknown, private, duplicate, owned or changed selections do not mutate source',()=>{
 const s=db([blank('one'),blank('two')]);
 for(const members of [[...refs,refs[0]],[{kind:'book',id:'KB-PRIVATE'}],[{kind:'scene',id:'javascript:bad'}],[{kind:'book',id:'absent'}]]){
  const raw=s.getItem();assert.throws(()=>B.bind(s,{cardId:'local.card.one',revision:1,members},refs));assert.equal(s.getItem(),raw);
 }
 B.bind(s,{cardId:'local.card.one',revision:1,members:refs},refs);
 const raw=s.getItem();
 assert.throws(()=>B.bind(s,{cardId:'local.card.two',revision:1,members:refs},refs));
 assert.throws(()=>B.setForm(s,'local.card.one',1,'world',refs));
 assert.throws(()=>B.setForm(s,'local.card.one',2,'world',refs.slice(0,1)));
 assert.equal(s.getItem(),raw);
});
test('storage failure leaves original card untouched; malformed save fails closed',()=>{
 const s=db([blank('one')]),raw=s.getItem();s.setItem=()=>{throw Error('quota')};
 assert.throws(()=>B.bind(s,{cardId:'local.card.one',revision:1,members:refs},refs),/quota/);assert.equal(s.getItem(),raw);
 assert.throws(()=>B.claims({getItem:()=>'{bad'}));
 assert.throws(()=>B.read(db([blank('one'),blank('one')])));
});
test('only unused blank media within three meters; ties and already-bound media reject',()=>{
 const a=blank('a'),b=blank('b'),entry=(c,x)=>({id:c.id,record:c,visible:true,position:{x,z:0}});
 assert.equal(B.nearest({x:0,z:0},[entry(a,1),entry(b,2)]),a.id);
 assert.throws(()=>B.nearest({x:0,z:0},[entry(a,2),entry(b,2.1)]));
 assert.throws(()=>B.nearest({x:0,z:0},[entry(a,3.01)]));
 for(const patch of [{image:{}},{runeBinding:{}},{title:'作品'},{scanContract:{}},{permanentRegistry:{}}])assert.equal(B.blank({...a,...patch}),false);
});
test('existing解除契約 spell atomically removes collection ownership and consumes only its original spell',()=>{
 const Craft=require('../living-library/live/object-craft.js'),Spell=require('../living-library/live/rune-unbind-spell.js');
 const medium=blank('medium'),spell={...blank('spell'),title:'解除契約'},s=db([medium,spell]);
 const bound=B.bind(s,{cardId:medium.id,revision:1,members:refs},refs);
 const result=Spell.findNearestBoundCard(spell.id,{id:spell.id,record:spell,position:{x:0,z:0},visible:true},[{id:bound.id,record:bound,position:{x:1,z:0},visible:true}]);
 assert.equal(result.cardId,medium.id);
 const released=Craft.unbindAndAbsorbSpell(s,{runeId:bound.runeBinding.runeId,targetId:bound.id,targetRevision:2,spellId:spell.id,spellRevision:1});
 assert.equal(released.target.id,medium.id);assert.equal(released.target.scanContract,undefined);assert.equal(released.target.runeBinding,undefined);
 assert.equal(released.spell.id,spell.id);assert.equal(released.spell.containment.kind,'consumed_spell');
 assert.equal(B.claims(s).size,0);assert.equal(B.read(s).objects.length,2);
});
