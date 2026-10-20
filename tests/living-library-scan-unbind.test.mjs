import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const Craft=require('../living-library/live/object-craft.js');
const Bundle=require('../living-library/live/scan-bundle.js');
const makeCard=(id,title='空白卡片')=>({id:'local.card.'+id,objectType:'card',templateId:'blank-card-v1',title,
 description:'',cardType:'',image:null,cardNumber:'',status:'draft',placement:'hover',revision:1,permissions:[]});
const store=(cards)=>{
 const x=new Map([['living-library-player-objects-v1',JSON.stringify({version:1,objects:cards})]]);
 return {getItem:k=>x.get(k)??null,setItem(k,v){x.set(k,v)},snapshot:()=>JSON.parse(x.get('living-library-player-objects-v1')),raw:()=>x.get('living-library-player-objects-v1')};
};
const scanDesc=n=>'真實之眼收納 '+n+' 件物件；原始身分保留。';
const scanOrphan=(id,extra={})=>({...makeCard(id),title:'掃描收納卡',description:scanDesc(2),cardType:'收納契約',revision:7,...extra});
const ref={kind:'scene',id:'scene.shelf.0'};
test('generic unbind atomically restores scan-contract original to reusable empty card',()=>{
 const storage=store([makeCard('medium'),makeCard('spell','解除契約')]);
 const bound=Bundle.bind(storage,{cardId:'local.card.medium',revision:1,members:[ref]},[ref]);
 assert.ok(!Bundle.blank(bound));assert.equal(Bundle.claims(storage).get('scene:scene.shelf.0'),'local.card.medium');
 const after=Craft.unbindAndAbsorbSpell(storage,{runeId:'scan.bundle.local.card.medium',targetId:bound.id,
  targetRevision:bound.revision,spellId:'local.card.spell',spellRevision:1},()=> '2026-10-20T11:00:00.000Z');
 assert.equal(after.target.id,bound.id);
 assert.equal(after.target.title,'空白卡片');assert.equal(after.target.description,'');assert.equal(after.target.cardType,'');
 assert.equal(after.target.scanContract,undefined);assert.equal(after.target.runeBinding,undefined);
 assert.ok(Bundle.blank(after.target));assert.equal(storage.snapshot().objects.length,2);
 assert.deepEqual([...Bundle.claims(storage)],[],'no original card ownership memory remains');
 assert.equal(after.spell.containment.kind,'consumed_spell');
 const usedAgain=Bundle.bind(storage,{cardId:bound.id,revision:after.target.revision,members:[ref]},[ref]);
 assert.equal(usedAgain.id,bound.id,'same blank card can bind a brand new contract to the old geometry');
});
test('unbind of card-form scan collection safely returns original identity',()=>{
 const storage=store([makeCard('medium'),makeCard('spell','解除綁定術')]);
 const bound=Bundle.bind(storage,{cardId:'local.card.medium',revision:1,members:[ref]},[ref]);
 const world=Bundle.setForm(storage,bound.id,bound.revision,'world',[ref]);
 const again=Bundle.setForm(storage,world.id,world.revision,'card',[ref]);
 const x=Craft.unbindAndAbsorbSpell(storage,{runeId:'scan.bundle.'+again.id,targetId:again.id,targetRevision:again.revision,spellId:'local.card.spell',spellRevision:1});
 assert.ok(Bundle.blank(x.target));assert.deepEqual([...Bundle.claims(storage,true)],[]);
});
test('startup recovery repairs legacy half-cleared scanner fields and keeps byte-for-byte backup',()=>{
 const storage=store([scanOrphan('old'),makeCard('unrelated','自訂卡片'),makeCard('spell','解除契約')]);
 const original=storage.raw(),restored=Bundle.recoverLegacyUnbound(storage);
 assert.deepEqual(restored,['local.card.old']);assert.equal(storage.getItem(Bundle.BACKUP_KEY),original);
 const records=storage.snapshot().objects;
 assert.ok(Bundle.blank(records[0]));assert.equal(records[0].id,'local.card.old');
 assert.equal(records[0].revision,8);assert.equal(records[1].title,'自訂卡片');
 assert.deepEqual(Bundle.recoverLegacyUnbound(storage),[],'migration idempotent');
 assert.equal(storage.getItem(Bundle.BACKUP_KEY),original,'never overwrite first save backup');
});
test('legacy scanContract without runeBinding is cleaned only with matching owner and generated fields',()=>{
 const old=scanOrphan('old',{scanContract:{version:1,artifactId:'scan.bundle.local.card.old',form:'world',members:[ref]}});
 const storage=store([old]);assert.equal(Bundle.recoverLegacyUnbound(storage).length,1);
 assert.ok(Bundle.blank(storage.snapshot().objects[0]));
 assert.equal(storage.snapshot().objects[0].scanContract,undefined);
});
test('valid active contract is not migrated, and unrelated custom content is preserved',()=>{
 const active=scanOrphan('active',{runeBinding:{runeId:'scan.bundle.local.card.active',state:'bound'}});
 const custom=scanOrphan('custom',{image:{dataUrl:'my-image'},cardNumber:'A-5'});
 const storage=store([active,custom,makeCard('other','我的原創卡')]);
 assert.throws(()=>Bundle.recoverLegacyUnbound(storage),/自訂欄位/);
 assert.equal(storage.snapshot().objects[0].runeBinding.state,'bound');
 assert.equal(storage.snapshot().objects[1].cardNumber,'A-5');
 assert.equal(storage.getItem(Bundle.BACKUP_KEY),null);
});
test('unbinding a customized scan contract refuses mutation rather than erasing artwork or consuming spell',()=>{
 const storage=store([makeCard('target'),makeCard('spell','解除契約')]);
 const bound=Bundle.bind(storage,{cardId:'local.card.target',revision:1,members:[ref]},[ref]);
 const state=storage.snapshot();state.objects[0].image={user:'original art'};storage.setItem(Bundle.KEY,JSON.stringify(state));
 const before=storage.raw();
 assert.throws(()=>Craft.unbindAndAbsorbSpell(storage,{runeId:bound.runeBinding.runeId,targetId:bound.id,targetRevision:bound.revision,spellId:'local.card.spell',spellRevision:1}),/自訂欄位/);
 assert.equal(storage.raw(),before);
});
test('invalid legacy corrupted binding metadata never automatically gets deleted',()=>{
 const storage=store([scanOrphan('odd',{scanContract:{version:1,artifactId:'scan.bundle.other',form:'world',members:[ref]}})]);
 assert.deepEqual(Bundle.recoverLegacyUnbound(storage),[]);
 assert.equal(storage.getItem(Bundle.BACKUP_KEY),null);
});


test('a 15-book collection can release into world and rebind the SAME blank card repeatedly',()=>{
 const storage=store([makeCard('medium')]);
 const books=Array.from({length:15},(_,i)=>({kind:'book',id:'test-book-'+(i+1)}));
 for(let cycle=0;cycle<3;cycle++){
  const blank=storage.snapshot().objects[0];
  assert.ok(Bundle.blank(blank),'card remains reusable before contract cycle '+cycle);
  const bound=Bundle.bind(storage,{cardId:blank.id,revision:blank.revision,members:books},books);
  assert.equal(Bundle.claims(storage).size,15);
  const freed=Bundle.release(storage,{cardId:bound.id,revision:bound.revision,form:'card'},books);
  assert.deepEqual(freed.members,books);
  assert.ok(Bundle.blank(freed.card),'release resets original without extra spell card');
  assert.equal(Bundle.claims(storage).size,0,'all 15 book identities lose the previous owner');
  assert.equal(storage.snapshot().objects.length,1,'original card and book identities preserved');
 }
});
test('legacy world-form 15-book set can detach in place, then bind to another card',()=>{
 const storage=store([makeCard('original'),makeCard('replacement')]);
 const books=Array.from({length:15},(_,i)=>({kind:'book',id:'test-book-'+(i+1)}));
 const bound=Bundle.bind(storage,{cardId:'local.card.original',revision:1,members:books},books);
 const world=Bundle.setForm(storage,bound.id,bound.revision,'world',books);
 assert.equal(Bundle.claims(storage,true).size,0,'world form is physically visible yet STILL under contract');
 assert.equal(Bundle.claims(storage).size,15,'old ownership explains automatic return-to-original prompt');
 assert.throws(()=>Bundle.bind(storage,{cardId:'local.card.replacement',revision:1,members:books},books),/不可用/);
 const freed=Bundle.release(storage,{cardId:world.id,revision:world.revision,form:'world'},books);
 assert.ok(Bundle.blank(freed.card));assert.equal(Bundle.claims(storage).size,0);
 const replacement=Bundle.bind(storage,{cardId:'local.card.replacement',revision:1,members:books},books);
 assert.equal(replacement.scanContract.members.length,15,'the user can choose a DIFFERENT original card after release');
});
test('releasing missing books refuses partial release and preserves complete prior save',()=>{
 const storage=store([makeCard('original')]);
 const all=Array.from({length:15},(_,i)=>({kind:'book',id:'test-book-'+(i+1)}));
 const bound=Bundle.bind(storage,{cardId:'local.card.original',revision:1,members:all},all);
 const before=storage.raw();
 assert.throws(()=>Bundle.release(storage,{cardId:bound.id,revision:bound.revision,form:'card'},all.slice(0,14)),/未全部載入/);
 assert.equal(storage.raw(),before,'no partial mutation when one of 15 is missing');
});


test('three-second owner lookup requires active bound state, not remembered former card id',()=>{
 const storage=store([makeCard('original')]);
 const refs=[{kind:'book',id:'research'}];
 const active=Bundle.bind(storage,{cardId:'local.card.original',revision:1,members:refs},refs);
 assert.equal(Bundle.contract(active).form,'card');
 const dead={...active,runeBinding:{...active.runeBinding,state:'released',releasedAt:'2026-10-20T12:00:00.000Z'}};
 // Historical rune ID, boundAt and complete contract payload alone cannot
 // put a member back into an old card's active ownership map.
 assert.throws(()=>Bundle.contract(dead),/格式錯誤/);
 storage.setItem(Bundle.KEY,JSON.stringify({version:1,objects:[dead]}));
 const before=storage.raw();
 assert.deepEqual(Bundle.recoverLegacyUnbound(storage),[dead.id]);
 assert.equal(storage.getItem(Bundle.BACKUP_KEY),before);
 const cleaned=storage.snapshot().objects[0];
 assert.ok(Bundle.blank(cleaned));
 assert.equal(cleaned.runeBinding,undefined);
 assert.equal(cleaned.scanContract,undefined);
 assert.deepEqual([...Bundle.claims(storage)],[]);
 const newBind=Bundle.bind(storage,{cardId:cleaned.id,revision:cleaned.revision,members:refs},refs);
 assert.deepEqual(newBind.scanContract.members,refs);
});
test('legacy 15-book released-state bindings are recovered without losing original IDs',()=>{
 const storage=store([makeCard('all-books')]);
 const refs=Array.from({length:15},(_,i)=>({kind:'book',id:'book.'+i}));
 const b=Bundle.bind(storage,{cardId:'local.card.all-books',revision:1,members:refs},refs);
 const released={...b,runeBinding:{...b.runeBinding,state:'unbound',unboundAt:'2026-10-20T12:00:00.000Z'}};
 storage.setItem(Bundle.KEY,JSON.stringify({version:1,objects:[released]}));
 assert.deepEqual(Bundle.recoverLegacyUnbound(storage),[released.id]);
 assert.equal(Bundle.claims(storage).size,0);
 const empty=storage.snapshot().objects[0];
 assert.ok(Bundle.blank(empty));
 assert.equal(Bundle.bind(storage,{cardId:empty.id,revision:empty.revision,members:refs},refs).scanContract.members.length,15);
});
test('active scan contracts and unrelated/mismatched historical bindings remain unchanged',()=>{
 const activeStorage=store([makeCard('live')]);
 const refs=[{kind:'book',id:'research'}];
 const active=Bundle.bind(activeStorage,{cardId:'local.card.live',revision:1,members:refs},refs);
 const before=activeStorage.raw();
 assert.deepEqual(Bundle.recoverLegacyUnbound(activeStorage),[]);
 assert.equal(activeStorage.raw(),before);
 const fake=scanOrphan('ambiguous',{runeBinding:{state:'released',runeId:'scan.bundle.someone-elses-card'}});
 const storage=store([fake]);
 assert.deepEqual(Bundle.recoverLegacyUnbound(storage),[]);
 assert.equal(storage.getItem(Bundle.BACKUP_KEY),null);
});
