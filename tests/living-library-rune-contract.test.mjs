import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const require=createRequire(import.meta.url);
const Rune=require('../living-library/live/rune-contract.js');
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const origin={x:0,y:0,z:0};
const card=(id='local.card.a',title='初代法陣',patch={})=>({id,objectType:'card',templateId:'blank-card-v1',status:'draft',title,containerId:null,ownerBookId:null,placement:'hover',...patch});
const entry=(id='local.card.a',x=1,z=0,record=card(id),patch={})=>({id,position:{x,y:2.22,z},record,visible:true,isDragging:false,isSummoning:false,isRecycling:false,isFocused:false,...patch});
const pick=(entries,anchor=origin,options)=>Rune.findNearestEligibleCard(anchor,entries,options);
test('RUNE-01 uses a stable internal rune ID but issues no permanent identity',()=>{
 const a=Rune.describeRune(origin);
 assert.equal(a.runeId,'local.rune.origin');
 assert.equal(a.displayName,'初始法陣');
 assert.deepEqual(a.worldAnchor,origin);
 assert.equal(a.contractState,'idle');
 assert.equal(a.verifiedWorldBinding,false);
 assert.equal(a.l2Certified,false);
 assert.equal(a.permanentNumber,null);
 assert.equal(Rune.describeRune(null),null);
 assert.throws(()=>{a.worldAnchor.x=999},TypeError);
});
test('finds uniquely closest floating card by X/Z (not height, screen location or hand-entered number)',()=>{
 const near=entry('local.card.near',1,0,card('local.card.near','法陣卡',{cardNumber:'001'}));
 const far=entry('local.card.far',2.5,0,card('local.card.far','遠卡',{cardNumber:'001'}));
 const before=structuredClone([near,far]);
 const result=pick([far,near]);
 assert.equal(result.status,'selected');
 assert.equal(result.cardId,near.id);
 assert.equal(result.distance,1);
 assert.equal(result.eligibleCount,2);
 assert.equal(result.withinRangeCount,2);
 assert.deepEqual([near,far],[before[0],before[1]],'pure function must not modify records');
 assert.equal(near.record.cardNumber,'001','hand-entered display number must remain untouched');
 const high=entry('local.card.high',.8,0,card('local.card.high','高卡'));
 high.position.y=10_000;
 assert.equal(pick([near,high]).cardId,high.id,'planar distance ignores orbit height');
});
test('nearest distance is world-relative, never relative to the librarian',()=>{
 const base={x:5,y:.2,z:-7};
 assert.equal(pick([entry('local.card.1',5,-6)],base).distance,1);
 assert.equal(pick([entry('local.card.1',5,-6)],origin).reason,'out_of_range');
});
test('empty, invalid origin, invalid collection and range rejections are fail-closed',()=>{
 assert.equal(pick([]).reason,'no_eligible_cards');
 assert.equal(pick(null).reason,'invalid_card_collection');
 assert.equal(pick([entry()],{x:NaN,z:0}).reason,'invalid_rune_anchor');
 assert.equal(pick([entry()],{x:0,z:Infinity}).reason,'invalid_rune_anchor');
 assert.equal(pick([entry('local.card.out',3.01)]).reason,'out_of_range');
 assert.equal(pick([entry('local.card.on',3)]).cardId,'local.card.on');
 assert.equal(pick([entry()],origin,{maxDistance:.5}).reason,'out_of_range');
 assert.equal(pick([entry()],origin,{maxDistance:-1}).cardId,'local.card.a','invalid override falls back to safe default');
});
test('almost equal nearest cards must be selected by user later, not silently chosen',()=>{
 const a=entry('local.card.a',1),b=entry('local.card.b',1.1);
 const t=pick([b,a]);assert.equal(t.status,'unavailable');assert.equal(t.reason,'ambiguous_nearest');
 assert.equal(t.cardId,null);
 assert.equal(pick([a,entry('local.card.c',1.151)]).reason,null);
 assert.equal(pick([a,b],origin,{tieEpsilon:0}).cardId,a.id);
 assert.equal(pick([a,entry('local.card.b',1)]).reason,'ambiguous_nearest');
});
test('duplicate candidate identities reject the whole selection, never duplicate a binding',()=>{
 assert.equal(pick([entry(),entry()]).reason,'duplicate_candidate_id');
 assert.equal(pick([entry(),entry('local.card.b',2)]).status,'selected');
});
test('rejects invalid, recycled, book-held, blank and L2 card records',()=>{
 const bad=[
  card('local.card.a','空白卡片'),card('local.card.a','  '),
  card('local.card.a','有名',{status:'recycled'}),
  card('local.card.a','有名',{status:'sealed'}),
  card('local.card.a','有名',{placement:'book'}),
  card('local.card.a','有名',{containerId:'local.atlas.compendium'}),
  card('local.card.a','有名',{ownerBookId:'local.book.test'}),
  card('local.card.a','有名',{objectType:'book'}),
  card('local.card.a','有名',{templateId:'anything-else'}),
  card('local.card.a','有名',{id:'public.gaming.atlas'}),
  card('local.card.a','有名',{l2Certified:true}),
  card('local.card.a','有名',{upgradeRequest:{l2Certified:true}}),
  card('local.card.a','有名',{certification:{level:'L2'}}),
  card('local.card.a','有名',{runeBinding:{runeId:'local.rune.other'}}),
  card('local.card.a','有名',{runeContract:{runeId:'local.rune.other'}}),
  card('local.card.a','有名',{boundRuneId:'local.rune.other'})
 ];
 for(const record of bad){assert.equal(pick([entry('local.card.a',1,0,record)]).reason,'no_eligible_cards',JSON.stringify(record));}
});
test('rejects invisible or temporarily busy cards, invalid positions and mismatched ids',()=>{
 const original=entry();
 const patches=[{visible:false},{isDragging:true},{isSummoning:true},{isRecycling:true},{isFocused:true},{position:{x:NaN,z:1}},{position:null},{id:'local.card.not-the-record'}];
 for(const patch of patches)assert.equal(pick([{...original,...patch}]).reason,'no_eligible_cards',JSON.stringify(patch));
 assert.equal(pick([{...original,visible:undefined}]).reason,'no_eligible_cards');
});
test('descriptor and selector remain immutable local-only utilities with no side effects',()=>{
 const arr=[entry('local.card.a',1)];
 const before=structuredClone(arr);
 const r=pick(arr);assert.deepEqual(arr,before);assert.equal(r.permanentNumber,undefined);
 assert.throws(()=>{r.cardId='replaced'},TypeError);
 assert.deepEqual(Rune.DEFAULTS,{maxDistance:3,tieEpsilon:.15});
});
test('Three.js wiring groups the EXISTING world-origin floor meshes without intercepting pointer events',async()=>{
 const html=await readFile(path.join(root,'living-library/live/index.html'),'utf8');
 assert.match(html,/rune-contract\.js/);
 assert.match(html,/const runeGroup=new THREE\.Group\(\)/);
 assert.match(html,/runeGroup\.userData\.runeId=RuneContract\.RUNE_ID/);
 assert.match(html,/scene\.add\(runeGroup\)/);
 assert.match(html,/cyl\(3\.02,3\.35,\.36,0,-\.13,0,stone2,runeGroup\)/);
 assert.match(html,/for\(let i=0;i<22;i\+\+\).*runeGroup/);
 assert.match(html,/runeHitRay\.intersectObject\(runeGroup,true\)/);
 assert.match(html,/runeGroup\.getWorldPosition\(new THREE\.Vector3\(\)\)/);
 assert.match(html,/return RuneContract\.findNearestEligibleCard\(runeWorldAnchor\(\),entries\)/);
 assert.match(html,/rune:runeInspection/);
 assert.match(html,/const bookOrbitAnchor=new THREE\.Vector3\(0,0,0\)/);
 assert.match(html,/const floatingCardMeshes=new Map\(\)/);
 assert.doesNotMatch(html,/runeGroup\.position\.copy\(keeper/);
 assert.doesNotMatch(html,/runeContract.*\.setItem\(/i);
});
