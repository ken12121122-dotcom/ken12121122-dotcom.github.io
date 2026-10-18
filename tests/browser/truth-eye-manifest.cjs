/* Reproduce the reported player bug with REAL Three.js geometry and the
 * original saved-card schema. No WebGL GPU required for trajectory checks. */
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const T=require('three');
const Craft=require('../../living-library/live/object-craft.js');
const Contract=require('../../living-library/live/truth-eye-contract.js');
const Directed=require('../../living-library/live/directed-transfer.js');
const Hex=require('../../living-library/live/contract-hexagram.js');
const root=path.resolve(__dirname,'../..'),cardId='local.card.truth-eye-original';
const initial={id:cardId,objectType:'card',templateId:'blank-card-v1',title:'真實之眼',
 description:'玩家自訂神器說明',cardNumber:'EYE-7',image:null,cardType:'神器',revision:4,
 status:'draft',placement:'hover',containerId:null,ownerBookId:null,slotIndex:null,
 runeBinding:{runeId:'artifact.truth-eye',state:'bound'},
 truthEyeContract:{artifactId:'artifact.truth-eye',form:'card',version:2}};
let raw=JSON.stringify({version:1,objects:[initial]});
const storage={getItem:k=>k===Craft.KEY?raw:null,setItem(k,v){if(k===Craft.KEY)raw=v}};
const buttons=new Map();
function ui(id){
 if(!buttons.has(id))buttons.set(id,{id,hidden:false,style:{},listeners:{},addEventListener(type,cb){this.listeners[type]=cb;},
  setAttribute(){},setPointerCapture(){},value:'',textContent:'',open:false,showModal(){this.open=true},
  close(){this.open=false;this.listeners.close?.({type:'close'})}});
 return buttons.get(id);
}
function el(type){
 const e=ui('created-'+type+'-'+buttons.size);
 e.innerHTML='';e.querySelector=sel=>ui(sel);
 return e;
}
const doc={head:{append(){}},body:{append(){}},createElement:el,addEventListener(){}};
let clock=0;
const host={document:doc,console,performance:{now:()=>clock},setTimeout,clearTimeout,addEventListener(){}};
host.self=host;host.LivingTruthEyeContract=Contract;host.LivingDirectedTransfer=Directed;
host.LivingContractHexagram=Hex;
vm.runInNewContext(fs.readFileSync(root+'/living-library/live/truth-eye-world.js','utf8'),host);
const scene=new T.Scene(),keeper=new T.Group();scene.add(keeper);
keeper.position.set(2,.46,0);
const camera=new T.PerspectiveCamera(50,1.5,.1,200);camera.position.set(2,4,16);camera.lookAt(keeper.position);camera.updateMatrixWorld();
const cardGroup=new T.Group(),mesh=new T.Mesh(new T.BoxGeometry(.5,.8,.025),new T.MeshBasicMaterial());
cardGroup.add(mesh);scene.add(cardGroup);cardGroup.position.set(3,2,0);
const cards=new Map([[cardId,{id:cardId,group:cardGroup,record:initial}]]);
const errors=[];
function refreshCards(){const c=Craft.listCards(storage)[0];cards.get(cardId).record=c;}
const world=host.LivingTruthEyeWorld.create({
 THREE:T,scene,keeper,camera,renderer:{domElement:{addEventListener(){},getBoundingClientRect(){
  return {left:0,top:0,width:900,height:600}}}},
 bookEntities:[],floatingCards:cards,storage,craft:Craft,refreshCards,
 invalidate(){},toast:s=>errors.push(String(s)),reducedMotion:false
});
assert.equal(world.inspect().form,'card');
assert.equal(world.ownsTransferCard(cardId),false);
const originalHexes=scene.children.filter(x=>x.name==='原始六芒星契約儀式');
assert.equal(originalHexes.length,2,'Eye and orbit card share approved original artwork');
const cardHex=originalHexes[1];
assert.equal(cardHex.visible,false);
// Reproduce the exact regression: the player's original card was moved through
// the UNIQUE compendium reader and back, increasing its revision twice.
Craft.placeCard(storage,cardId,'local.atlas.compendium',9);refreshCards();
assert.equal(world.cardTap(cardId),true);
assert.ok(errors.at(-1).includes('GBA'));
assert.equal(world.ownsTransferCard(cardId),false);
assert.equal(cardHex.visible,false,'the reader does not open the star');
Craft.placeCard(storage,cardId,null);refreshCards();
const moved=Craft.listCards(storage)[0];
assert.equal(moved.revision,6);
// While cardified, the hidden 3D Eye no longer follows the moving player.
keeper.position.set(11,.46,-8);clock=50;world.tick(clock);
assert.equal(world.cardTap(cardId),true,'a quick card tap is allowed to show guidance');
const dialogs=[...buttons.values()].filter(e=>e.id==='truthEyeContractConfirm');
assert.equal(dialogs.length,1);
assert.equal(dialogs[0].open,false,'a quick click MUST NOT bypass the original three-second selection');
assert.equal(cardHex.visible,false,'quick click has not begun ritual');
const press=(id=7)=>({pointerId:id,clientX:440,clientY:230,button:0,isPrimary:true,pointerType:'touch'});
assert.equal(world.cardPressStart(press(),cardId),true);
assert.equal(cardHex.visible,true,'original 3D six-pointed star appears at card POINTER DOWN');
assert.ok(cardHex.position.distanceTo(cardGroup.position)<.01,'star surrounds the card rather than the UI');
assert.ok(cardHex.children.length>0);
clock=1400;world.tick(clock);
assert.equal(dialogs[0].open,false,'the question cannot appear mid-hold');
assert.equal(cardHex.visible,true,'six-star charge is shown during the actual hold');
clock=3300;world.tick(clock);
assert.equal(dialogs[0].open,true,'only a completed 3-second hold may open the materialize question');
assert.equal(cardHex.visible,true,'completed six-star stays behind the confirmation question');
assert.equal(world.cardPressEnd(press(),cardId,false,false),true);
ui('#truthEyeApprove').listeners.click();
assert.equal(world.ownsTransferCard(cardId),true,'flight starts from same original card ID');
assert.equal(cardHex.visible,false,'ritual belongs to card SELECTION, not the materialize flight');
const before=cardGroup.position.clone(),first=cardGroup.scale.x;
clock=3900;world.tick(clock);
assert.equal(world.ownsTransferCard(cardId),true);
assert.ok(cardGroup.visible);
assert.ok(cardGroup.position.distanceTo(before)>.05,'original card flies after separate confirmation');
assert.equal(cardHex.visible,false,'no duplicate six-point animation during the transfer');
assert.ok(cardGroup.scale.x<first,'original card shrinks during transfer');
clock=4900;world.tick(clock);
const now=Craft.listCards(storage)[0];
assert.equal(now.id,cardId);
assert.equal(now.revision,7);
assert.equal(now.truthEyeContract.form,'world');
assert.equal(now.description,initial.description);
assert.equal(now.cardNumber,'EYE-7');
assert.equal(Craft.listCards(storage).length,1,'no extra contract card minted');
assert.equal(world.ownsTransferCard(cardId),false);
assert.equal(world.inspect().form,'world');
assert.equal(cardHex.visible,false);
assert.ok(errors.some(m=>m.includes('掃描控制已啟動')));
// Card is returned using the existing reversible contract and can be selected
// again, including cancelling confirmation without touching stored data.
const returned=Contract.setForm(storage,cardId,now.revision,'card');refreshCards();
assert.equal(returned.truthEyeContract.form,'card');
clock=5100;world.tick(clock);
assert.equal(world.cardPressStart(press(8),cardId),true);
clock=8200;world.tick(clock);
assert.equal(dialogs[0].open,true);
assert.equal(cardHex.visible,true);
assert.equal(world.cardPressEnd(press(8),cardId,false,false),true);
ui('#truthEyeCancel').listeners.click();
assert.equal(dialogs[0].open,false);
assert.equal(cardHex.visible,false,'cancel clears all six-point geometry');
assert.equal(Craft.listCards(storage)[0].truthEyeContract.form,'card',
 'cancel never changes saved binding');
// A short press must cancel both gesture and star without prompting.
clock=8300;
assert.equal(world.cardPressStart(press(9),cardId),true);
assert.equal(cardHex.visible,true);
clock=8500;world.tick(clock);
assert.equal(world.cardPressEnd(press(9),cardId,false,false),true);
assert.equal(cardHex.visible,false,'early release clears six-star');
assert.equal(dialogs[0].open,false,'early release never prompts');
const index=fs.readFileSync(root+'/living-library/live/index.html','utf8');
const atlas=fs.readFileSync(root+'/living-library/live/atlas-runtime.js','utf8');
assert.match(index,/if\(truthEye\.ownsTransferCard\(entry\.id\)\)continue;/,
 'normal orbit renderer cannot reset a live contract flight each frame');
assert.match(index,/onStoredCardTap:id=>truthEye\.cardTap\(id\)/);
assert.match(atlas,/onStoredCardTap\?\.\(p\.entry\.id\)/);
assert.match(atlas,/onCardPressStart\?\.\(e,entry\.id\)/);
assert.match(atlas,/onCardPressMove\?\.\(e,p\.entry\.id,p\.moved\)/);
assert.match(atlas,/onCardPressEnd\?\.\(e,p\.entry\.id,cancel,p\.moved\)/);
console.log('PASS: approved hexagram charges during 3-second card hold BEFORE confirmation; separate flight remains intact; cancel and early release preserve original ID.');
