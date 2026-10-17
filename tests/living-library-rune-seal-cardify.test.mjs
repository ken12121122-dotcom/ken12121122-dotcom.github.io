import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
const require=createRequire(import.meta.url);
const C=require('../living-library/live/object-craft.js');
const S=require('../living-library/live/atlas-state.js');
const D=require('../living-library/live/directed-transfer.js');
function mkstorage(cards){
 let json=JSON.stringify({version:C.VERSION,objects:cards});
 return {getItem:k=>k===C.KEY?json:null,setItem:(k,v)=>{assert.equal(k,C.KEY);json=v},
 raw:()=>json,objects:()=>JSON.parse(json).objects};
}
function card(id='local.card.origin',other={}){
 return {id,objectType:'card',templateId:'blank-card-v1',
 title:'初代法陣卡',description:'保存圖片與說明',image:{dataUrl:'no-upload'},
 cardNumber:'RUNE-00',createdAt:'before',updatedAt:'before',revision:7,
 placement:'hover',status:'draft',ownerBookId:null,containerId:null,slotIndex:null,...other};
}
const seal=(db,id='local.card.origin',rev=7)=>C.sealRuneCard(db,C.ORIGIN_RUNE,id,rev,
 ()=> '2026-10-17T10:00:00Z');
const materialize=(db,id='local.card.origin',rev=8)=>C.materializeRuneCard(db,C.ORIGIN_RUNE,id,rev,
 ()=> '2026-10-17T10:02:00Z');
test('RUNE-04: card absorbs into FLOOR rune while original identity and photo remain intact',()=>{
 const db=mkstorage([card()]),original=db.objects()[0];
 const sealed=seal(db);
 assert.equal(sealed.id,original.id);
 assert.equal(sealed.placement,'contained');
 assert.equal(sealed.containment.holderId,C.ORIGIN_RUNE);
 assert.equal(sealed.containment.kind,'rune_contract');
 assert.equal(sealed.runeBinding.state,'sealed');
 assert.equal(sealed.runeBinding.runeId,C.ORIGIN_RUNE);
 assert.deepEqual(sealed.image,original.image);
 assert.equal(sealed.createdAt,original.createdAt);
 assert.equal(sealed.cardNumber,original.cardNumber);
 assert.equal(sealed.revision,8);
 assert.equal(db.objects().length,1);
 assert.equal(C.getRuneBoundCard(db).id,original.id);
 assert.equal(C.floatingCards(db).length,0);
 assert.equal(C.listCards(db).length,1,'contained is NOT delete/recycle');
 assert.throws(()=>C.placeCard(db,sealed.id,S.ATLAS,9),/收納/);
 assert.throws(()=>C.recycleCard(db,sealed.id),/收納/);
});
test('RUNE-05: second rune hold may rematerialize the SAME original card, without a duplicate ID',()=>{
 const db=mkstorage([card()]);seal(db);
 const restored=materialize(db);
 assert.equal(restored.placement,'hover');
 assert.equal(restored.runeBinding.state,'cardified');
 assert.equal(Object.hasOwn(restored,'containment'),false);
 assert.equal(restored.id,'local.card.origin');
 assert.equal(C.floatingCards(db).length,1);
 assert.equal(C.listCards(db).length,1);
 assert.equal(restored.image.dataUrl,'no-upload');
 assert.equal(restored.cardNumber,'RUNE-00');
 // GBA right slot remains accessible to original linked card.
 const gba=C.placeCard(db,restored.id,S.ATLAS,9);
 assert.equal(S.readerActive(gba),true);
 assert.equal(gba.runeBinding.state,'cardified');
 const orbit=C.placeCard(db,restored.id,null);
 assert.equal(orbit.runeBinding.state,'cardified');
});
test('legacy RUNE-03 bound card may be sealed without a new binding or card ID',()=>{
 const old=card('local.card.origin',{revision:9,
  runeBinding:{runeId:C.ORIGIN_RUNE,state:'bound',boundAt:'past'}});
 const db=mkstorage([old]),sealed=C.sealRuneCard(db,C.ORIGIN_RUNE,old.id,9);
 assert.equal(sealed.runeBinding.state,'sealed');
 assert.equal(sealed.runeBinding.boundAt,'past');
 assert.equal(db.objects().length,1);
});
test('unbound→sealed→cardified→unbound is a consistent roundtrip with consumable spell',()=>{
 const spell=card('local.card.spell',{title:'解除綁定術',cardNumber:'SPELL-01'});
 const db=mkstorage([card(),spell]);
 seal(db);const visible=materialize(db);
 const effect=C.unbindAndAbsorbSpell(db,{runeId:C.ORIGIN_RUNE,targetId:visible.id,
  targetRevision:visible.revision,spellId:spell.id,spellRevision:spell.revision});
 assert.equal(effect.target.id,'local.card.origin');
 assert.equal(effect.target.runeBinding,undefined);
 assert.equal(effect.target.placement,'hover');
 assert.equal(effect.spell.id,spell.id);
 assert.equal(effect.spell.placement,'contained');
 assert.equal(effect.spell.containment.holderId,effect.target.id);
 assert.equal(db.objects().length,2);
 assert.equal(C.getRuneBoundCard(db),null);
 assert.equal(C.floatingCards(db).length,1);
 // After using the consume spell, the original may be bound again.
 const sealedAgain=C.sealRuneCard(db,C.ORIGIN_RUNE,effect.target.id,effect.target.revision);
 assert.equal(sealedAgain.runeBinding.state,'sealed');
});
test('stale revision, duplicate floor rune holder, or wrong materialization phase never mutate data',()=>{
 for(const records of [
  [card(),card('local.card.other',{runeBinding:{runeId:C.ORIGIN_RUNE,state:'bound'}})],
  [card('local.card.origin',{placement:'book',containerId:S.ATLAS,ownerBookId:S.ATLAS,slotIndex:9})],
  [card('local.card.origin',{runeBinding:{runeId:'other.rune',state:'bound'}})],
  [card('local.card.origin',{title:'空白卡片'})],
  [card('local.card.origin',{upgradeRequest:{state:'requested'}})]
 ]){
  const db=mkstorage(records),before=db.raw();
  assert.throws(()=>seal(db),Error);
  assert.equal(db.raw(),before,'do not partially commit');
 }
 const db=mkstorage([card()]),before=db.raw();
 assert.throws(()=>C.sealRuneCard(db,C.ORIGIN_RUNE,'local.card.origin',6),/變動/);
 assert.throws(()=>materialize(db),/封印/);
 assert.equal(db.raw(),before);
 seal(db);const snap=db.raw();
 assert.throws(()=>seal(db,'local.card.origin',8),/不可吸入/);
 assert.throws(()=>materialize(db,'local.card.origin',5),/版本/);
 assert.equal(db.raw(),snap);
});
test('write exceptions during seal or cardification leave original storage snapshot unchanged',()=>{
 const db=mkstorage([card()]),before=db.raw();
 const blocked={getItem:db.getItem,setItem:()=>{throw Error('quota')}};
 assert.throws(()=>seal(blocked),/quota/);
 assert.equal(db.raw(),before);
 seal(db);const after=db.raw();
 assert.throws(()=>materialize(blocked),/quota/);
 assert.equal(db.raw(),after);
});
test('the scene shows RUNE-04 flight and RUNE-05 reverse flight using one directed-transfer API',async()=>{
 const html=await readFile(new URL('../living-library/live/index.html',import.meta.url),'utf8');
 assert.match(html,/id="runeCardifyDialog"/);
 assert.match(html,/id="runeCardifyConfirm"/);
 assert.match(html,/startRuneTransfer\('seal',record\)/);
 assert.match(html,/startRuneTransfer\('cardify',latest\)/);
 assert.match(html,/DirectedTransfer\.normalize\(/);
 assert.match(html,/DirectedTransfer\.sample\(active\.plan,now\)/);
 assert.match(html,/craft\.sealRuneCard\(localStorage/);
 assert.match(html,/craft\.materializeRuneCard\(localStorage/);
 assert.match(html,/runeGroup\.visible=!cardify/);
 assert.match(html,/runeGroup\.visible=true/);
 assert.match(html,/tickRuneTransfer\(now\)/);
 const from={x:2,y:3,z:0},to={x:0,y:.44,z:0};
 const path=D.normalize({sourceId:'card',targetId:C.ORIGIN_RUNE,from,to,
  startedAt:0,duration:1500});
 assert.deepEqual(D.sample(path,1500).position,{x:0,y:.44,z:0});
});
