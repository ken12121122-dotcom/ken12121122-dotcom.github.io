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
const host={document:doc,console,performance:{now:()=>clock},addEventListener(){}};
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
assert.equal(world.cardTap(cardId),true);
assert.equal(cardHex.visible,true,'pressing the original card immediately reveals the six-pointed ritual');
assert.ok(cardHex.position.distanceTo(cardGroup.position)<.01,'the star surrounds the card, not the UI');
assert.ok(cardHex.children.length>0);
const dialogs=[...buttons.values()].filter(e=>e.id==='truthEyeContractConfirm');
assert.equal(dialogs.length,1);
assert.equal(dialogs[0].open,true,'fresh contract opens confirmation even after card moved');
ui('#truthEyeApprove').listeners.click();
assert.equal(world.ownsTransferCard(cardId),true,'flight starts from same original card ID');
assert.equal(cardHex.visible,true,'six-pointed ritual persists after confirming');
const before=cardGroup.position.clone(),first=cardGroup.scale.x;
clock=750;world.tick(clock);
assert.equal(world.ownsTransferCard(cardId),true);
assert.ok(cardGroup.visible);
assert.ok(cardGroup.position.distanceTo(before)>.05,'card is physically travelling through real 3D space');
assert.equal(cardHex.visible,true,'ritual remains on while the actual card is flying');
assert.ok(cardHex.position.distanceTo(cardGroup.position)<.01,'the star follows the moving card');
assert.ok(cardGroup.scale.x<first,'the original card shrinks during absorption');
clock=1650;world.tick(clock);
const now=Craft.listCards(storage)[0];
assert.equal(now.id,cardId);
assert.equal(now.revision,7);
assert.equal(now.truthEyeContract.form,'world');
assert.equal(now.description,initial.description);
assert.equal(now.cardNumber,'EYE-7');
assert.equal(Craft.listCards(storage).length,1,'no extra contract card minted');
assert.equal(world.ownsTransferCard(cardId),false);
assert.equal(world.inspect().form,'world');
assert.equal(cardHex.visible,true,'ritual finishes on the restored world Eye');
assert.ok(errors.some(m=>m.includes('掃描控制已啟動')));
clock=4100;world.tick(clock);
assert.equal(cardHex.visible,false,'the completed rite clears after the original 2.35s burst');
// A future return to card form also survives placement revision churn.
const returned=Contract.setForm(storage,cardId,now.revision,'card');refreshCards();
assert.equal(returned.truthEyeContract.form,'card');
assert.equal(world.cardTap(cardId),true);
assert.equal(dialogs[0].open,true);
assert.equal(cardHex.visible,true,'card click should invoke the symbol before approval');
ui('#truthEyeCancel').listeners.click();
assert.equal(dialogs[0].open,false);
assert.equal(cardHex.visible,false,'cancel immediately clears the ritual');
assert.equal(Craft.listCards(storage)[0].truthEyeContract.form,'card',
 'cancel never changes the saved contract');
const index=fs.readFileSync(root+'/living-library/live/index.html','utf8');
const atlas=fs.readFileSync(root+'/living-library/live/atlas-runtime.js','utf8');
assert.match(index,/if\(truthEye\.ownsTransferCard\(entry\.id\)\)continue;/,
 'normal orbit renderer cannot reset a live contract flight each frame');
assert.match(index,/onStoredCardTap:id=>truthEye\.cardTap\(id\)/);
assert.match(atlas,/onStoredCardTap\?\.\(p\.entry\.id\)/);
console.log('PASS: approved hexagram appears around tapped original card, follows Three.js flight, bursts and clears, cancellation safe, original ID unchanged.');
