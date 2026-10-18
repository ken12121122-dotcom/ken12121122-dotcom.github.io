import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
const require=createRequire(import.meta.url);
const Contract=require('../living-library/live/truth-eye-contract.js');
const Craft=require('../living-library/live/object-craft.js');
const blank=(id)=>({id,objectType:'card',templateId:'blank-card-v1',status:'draft',placement:'hover',
 title:'空白卡片',description:'',image:null,cardNumber:'',cardType:'',linkedCardId:null,ability:null,
 permissions:[],containerId:null,ownerBookId:null,revision:1});
const storage=items=>{const data=new Map([[Craft.KEY,JSON.stringify({version:1,objects:items})]]);
 return {getItem:key=>data.get(key)||null,setItem:(key,value)=>data.set(key,value)};};
const entry=(card,x,z=0)=>({id:card.id,record:card,position:{x,z},visible:true});
test('uses the original blank card ID, does not create a card or mutate other records',()=>{
 const a=blank('local.card.one'),b=blank('local.card.two'),db=storage([a,b]);
 const made=Contract.bind(db,a.id,1);
 assert.equal(made.id,a.id);assert.equal(made.revision,2);
 assert.equal(made.runeBinding.runeId,Contract.ID);
 assert.equal(made.truthEyeContract.form,'sealed');
 assert.equal(Craft.listCards(db).length,2);
 assert.deepEqual(Craft.listCards(db).find(x=>x.id===b.id),b);
 assert.throws(()=>Contract.bind(db,b.id,1),/已經有原卡/);
 assert.throws(()=>Contract.setForm(db,a.id,made.revision,'world'),/轉換順序/);
 const cardified=Contract.setForm(db,a.id,made.revision,'card');
 assert.equal(cardified.truthEyeContract.form,'card');
 const world=Contract.setForm(db,a.id,cardified.revision,'world');
 assert.equal(world.truthEyeContract.form,'world');
 assert.throws(()=>Contract.setForm(db,a.id,cardified.revision,'card'),/已變更/);
 const returned=Contract.setForm(db,a.id,world.revision,'card');
 assert.equal(returned.truthEyeContract.form,'card');
 assert.equal(Craft.canStoreBoundCard(returned,'local.atlas.collection',0),true);
 assert.equal(Craft.canStoreBoundCard({...returned,truthEyeContract:{...returned.truthEyeContract,form:'world'}},'local.atlas.collection',0),false);
});
test('named 真實之眼 card with prepared artwork can bind while preserving custom content',()=>{
 const existing={...blank('local.card.prepared'),title:'真實之眼',description:'自訂神器說明',
  image:{dataUrl:'image/jpg data'},cardNumber:'E-01',cardType:'自訂類型'};
 const db=storage([existing]);
 assert.equal(Contract.isEligible(existing),true);
 const after=Contract.bind(db,existing.id,1);
 assert.equal(after.id,existing.id);
 assert.equal(after.description,existing.description);
 assert.deepEqual(after.image,existing.image);
 assert.equal(after.cardNumber,existing.cardNumber);
 assert.equal(after.cardType,existing.cardType);
 assert.equal(after.truthEyeContract.form,'sealed');
 assert.equal(Craft.listCards(db).length,1);
});
test('existing named spell or unrelated ordinary design cannot be silently claimed by scanner',()=>{
 for(const title of ['解除契約','我的遊戲卡','其他神器']){
  const existing={...blank('local.card.still-mine'),title};
  const db=storage([existing]);
  assert.equal(Contract.isEligible(existing),false);
  assert.throws(()=>Contract.bind(db,existing.id,1));
  assert.deepEqual(Craft.listCards(db),[existing]);
 }
});
test('3D scanner gesture has a wider invisible picking volume, locked target and explicit progress',async()=>{
 const world=await readFile(new URL('../living-library/live/truth-eye-world.js',import.meta.url),'utf8');
 assert.match(world,/truth-eye-gesture-hit-target/);
 assert.match(world,/heldPointers\.add\(e\.pointerId\)/);
 assert.doesNotMatch(world,/showHoldProgress|conic-gradient|id="eyeAction"/);
 assert.match(world,/Hexagram\.create\(/);
 assert.match(world,/dialog\.showModal\(\)/);
 assert.match(world,/Directed\.normalize\(/);
 assert.match(world,/Directed\.sample\(/);
 assert.match(world,/function finishTransfer\(\)/);
 assert.match(world,/if\(sample\.done\)finishTransfer\(\)/);
 assert.match(world,/prompt\('seal'/);
 assert.match(world,/prompt\('cardify'/);
 assert.match(world,/prompt\('return'/);
 assert.match(world,/prompt\('materialize'/);
 assert.doesNotMatch(world,/flip\(\)/);
 assert.match(world,/hex\.update\(p,now\)/);
 assert.match(world,/cardTap\(id\)/);
 assert.match(world,/const target=type==='eye'\?chooseCard\(\):null/);
 assert.match(world,/cardRevision/);
 assert.match(world,/contextmenu/);
});
test('rejects edited or previously bound cards, never destroys custom content',()=>{
 const c=blank('local.card.safe');
 for(const patch of [{title:'我的作品'},{image:{dataUrl:'image'}},{description:'有內容'},{runeBinding:{runeId:'local.rune.origin'}},{containerId:'local.book.x'},{l2Certified:true}]){
  const bad={...c,...patch},db=storage([bad]);
  assert.equal(Contract.isEligible(bad),false);
  assert.throws(()=>Contract.bind(db,c.id,1));
  assert.deepEqual(Craft.listCards(db)[0],bad);
 }
});
test('nearest existing blank card is limited to 3 meters; ties and missing reject',()=>{
 const a=blank('local.card.near'),b=blank('local.card.far');
 assert.deepEqual(Contract.nearest({x:0,z:0},[]).status,'missing');
 assert.equal(Contract.nearest({x:0,z:0},[entry(a,3.01)]).status,'distant');
 assert.equal(Contract.nearest({x:0,z:0},[entry(a,2),entry(b,2.1)]).status,'ambiguous');
 assert.equal(Contract.nearest({x:0,z:0},[entry(a,1),entry(b,6)]).cardId,a.id);
});
test('main world mounts the native artifact and retains original floor rune behavior',async()=>{
 const html=await readFile(new URL('../living-library/live/index.html',import.meta.url),'utf8');
 const world=await readFile(new URL('../living-library/live/truth-eye-world.js',import.meta.url),'utf8');
 assert.match(html,/truth-eye-contract\.js/);
 assert.match(html,/truth-eye-world\.js/);
 assert.match(html,/truthEye\.tick\(now\)/);
 assert.match(html,/truthEye\.pointerDown\(e\)/);
 assert.match(html,/truthEye\.cardTap\(hit\.object\.userData\.localCardId\)/);
 assert.match(html,/focusCard:id=>\{if\(!truthEye\.cardTap\(id\)\)focusCard\(id\)\}/);
 assert.match(html,/runeHexagram=ContractHexagram\.create/);
 assert.match(world,/new T\.ExtrudeGeometry\(outline/);
 assert.match(world,/new T\.SphereGeometry/);
 assert.match(world,/Hexagram\.create/);
 assert.doesNotMatch(world,/data:image|<img|PlaneGeometry/);
});
