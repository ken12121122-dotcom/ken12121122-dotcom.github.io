import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
const require=createRequire(import.meta.url);
const Craft=require('../living-library/live/object-craft.js');
const Transfer=require('../living-library/live/directed-transfer.js');

function memory(cards){
 let raw=JSON.stringify({version:Craft.VERSION,objects:cards});
 return {getItem:k=>k===Craft.KEY?raw:null,setItem:(k,v)=>{assert.equal(k,Craft.KEY);raw=v},
  raw:()=>raw,objects:()=>JSON.parse(raw).objects};
}
function card(id,title){
 return {id,objectType:'card',templateId:'blank-card-v1',status:'draft',placement:'hover',
  containerId:null,ownerBookId:null,revision:1,cardNumber:'可編輯展示號',
  title,description:'既有卡',image:null};
}
test('contract unbinding permanently consumes spell, leaving same original target independent of origin rune',()=>{
 const target=card('local.card.origin','初代法陣卡');
 const spell=card('local.card.spell','解除契約卡');
 const db=memory([target,spell]);
 let sealed=Craft.sealRuneCard(db,Craft.ORIGIN_RUNE,target.id,target.revision);
 let manifest=Craft.materializeRuneCard(db,Craft.ORIGIN_RUNE,target.id,sealed.revision);
 const result=Craft.unbindAndAbsorbSpell(db,{runeId:Craft.ORIGIN_RUNE,
  targetId:manifest.id,targetRevision:manifest.revision,spellId:spell.id,spellRevision:spell.revision});
 assert.equal(Craft.getRuneBoundCard(db),null);
 assert.equal(result.target.id,target.id);
 assert.equal(result.target.placement,'hover');
 assert.equal(result.target.runeBinding,undefined);
 assert.equal(result.spellId,spell.id);
 assert.equal(result.consumed,true);
 assert.equal(db.objects().length,1,'no spent spell, phantom rune, or duplicate card');
 assert.equal(Craft.floatingCards(db).some(x=>x.id===spell.id),false);
 assert.equal(Craft.floatingCards(db).some(x=>x.id===target.id),true);
});
test('energy path returns from visible target to exact original floor anchor, with easing and no card mutation',()=>{
 const from={x:2.4,y:2.2,z:-1.6},to={x:0,y:.47,z:0},start=1000,duration=1450;
 const plan=Transfer.normalize({sourceId:'local.card.origin',targetId:Craft.ORIGIN_RUNE,
  from,to,startedAt:start,duration,initialScale:1});
 const first=Transfer.sample(plan,start),half=Transfer.sample(plan,start+duration*.5);
 const last=Transfer.sample(plan,start+duration);
 assert.deepEqual(first.position,from);
 assert.equal(first.done,false);
 assert.equal(half.done,false);
 assert.ok(Math.hypot(half.position.x,half.position.z)>0);
 assert.ok(Math.hypot(last.position.x-to.x,last.position.y-to.y,last.position.z-to.z)<1e-9,
  'floating-point interpolation must end within a nanometer-scale world-space tolerance');
 assert.equal(last.done,true);
 assert.equal(plan.targetId,Craft.ORIGIN_RUNE);
});
test('visual return only begins AFTER atomic contract-unbind success; animation never writes another card',async()=>{
 const html=await readFile(new URL('../living-library/live/index.html',import.meta.url),'utf8');
 const update=html.slice(html.indexOf('function updateSpellEffect(now){'),html.indexOf('// RUNE-06:'));
 assert.ok(update.length>2000);
 const cast=html.slice(html.indexOf('function confirmSpellUnbind(){'),html.indexOf("document.getElementById('runeUnbindConfirm')"));
 const commit=cast.indexOf('unbindAndAbsorbSpell(localStorage');
 const refresh=cast.indexOf('refreshFloatingCards();',commit);
 const animate=cast.indexOf("scene.dispatchEvent({type:'living-contract-unbound'",refresh);
 assert.ok(commit>=0&&refresh>commit&&animate>refresh,'unbinding commits on hold before any post-commit effects');
 assert.doesNotMatch(update,/unbindAndAbsorbSpell\(localStorage/,'animation must never write or consume another card');
 assert.match(html,/scene\.addEventListener\('living-contract-unbound',e=>\{/);
 assert.match(html,/startRuneReleaseEffect\(e\.cardId,e\.from,e\.now\)/);
 assert.ok(update.indexOf('runeGroup.visible=true;',commit)<0,'floor rune cannot pop back before energy flight');
 assert.match(html,/function startRuneReleaseEffect\(targetCardId,targetPosition,now\)/);
 assert.match(html,/sourceId:targetCardId,targetId:RuneContract\.RUNE_ID/);
 assert.match(html,/const open=smooth\(\(u-\.70\)\/\.30\)/);
 assert.match(html,/runeGroup\.visible=open>0/);
 assert.match(html,/runeReleaseLanding\.position\.copy\(fx\.to\)/);
 assert.match(html,/function endRuneReleaseEffect\(\)\{\s*if\(!runeReleaseEffect\)return;/);
 assert.match(html,/if\(frame\.done\)\{\s*endRuneReleaseEffect\(\)/);
 assert.match(html,/!!runeReleaseEffect\|\|!!buildState\|\|\s*performance\.now\(\)<runeBindingPulseUntil/);
 assert.match(html,/!runeReleaseEffect&&!buildState&&now>=runeBindingPulseUntil/);
 assert.match(html,/if\(document\.hidden\)\{cancelPermanentBuild\(\);runeTransfer=null;endRuneReleaseEffect\(\)/);
 assert.match(html,/window\.addEventListener\('pagehide',\(\)=>\{cancelPermanentBuild\(\);runeTransfer=null;endRuneReleaseEffect\(\)/);
 assert.doesNotMatch(html,/runeGroup\.add\(runeReleaseOrb\)/,
  'return spark travels as world-space effect, original rune remains single stable model');
});
test('a failed permanent-card unbind does not start effect or consume spell',()=>{
 const target=card('local.card.origin','初代法陣卡');
 const spell=card('local.card.spell','解除契約卡');
 const db=memory([target,spell]);
 const sealed=Craft.sealRuneCard(db,Craft.ORIGIN_RUNE,target.id,target.revision);
 const materialized=Craft.materializeRuneCard(db,Craft.ORIGIN_RUNE,target.id,sealed.revision);
 const objects=db.objects();
 objects[0]={...materialized,permanentRegistration:{state:'registered',number:'P-002'},revision:materialized.revision+1};
 db.setItem(Craft.KEY,JSON.stringify({version:Craft.VERSION,objects}));
 const snapshot=db.raw();
 assert.throws(()=>Craft.unbindAndAbsorbSpell(db,{runeId:Craft.ORIGIN_RUNE,
  targetId:target.id,targetRevision:objects[0].revision,
  spellId:spell.id,spellRevision:spell.revision}),/永久編號卡/);
 assert.equal(db.raw(),snapshot);
 assert.equal(Craft.getRuneBoundCard(db).id,target.id);
});
