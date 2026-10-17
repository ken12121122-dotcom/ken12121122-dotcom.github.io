/* PERM-001: canonical BOOK registration and card-only GBA workflow.
 * Local-only prototype, NOT a global authoritative number allocator/AI certificate.
 * No direct GBA control over physical world entities.
 */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingPermanentCards=api;})(typeof self!=='undefined'?self:globalThis,function(){
'use strict';
const BOOK='local.atlas.compendium',READER_SLOT=9,MAX_LOCAL_WELLS=9,RUNE='local.rune.origin';
const KEY='living-library-player-objects-v1',VERSION=1;
function read(storage){
 let value=storage.getItem(KEY);
 if(value===null)return {version:VERSION,objects:[]};
 let state;try{state=JSON.parse(value)}catch{throw Error('永久卡資料不可讀；原資料已保留')}
 if(!state||state.version!==VERSION||!Array.isArray(state.objects))throw Error('永久卡版本不符，已保留原檔');
 return state;
}
function save(storage,state){storage.setItem(KEY,JSON.stringify({version:VERSION,objects:state.objects}))}
function candidateObject(x){return x?.objectType==='card'&&x.templateId==='blank-card-v1'&&x.status!=='recycled'}
function isRegistered(x){return x?.permanentRegistry?.state==='registered'}
function list(storage){return read(storage).objects.filter(x=>candidateObject(x)&&isRegistered(x))
 .map(x=>({...x})).sort((a,b)=>a.permanentRegistry.number-b.permanentRegistry.number)}
function inspect(storage,id){
 const state=read(storage),card=state.objects.find(x=>x?.id===id&&candidateObject(x));
 const registered=state.objects.filter(isRegistered);
 const max=registered.reduce((acc,x)=>Math.max(acc,Number(x.permanentRegistry.number)||0),0);
 const number=max+1;
 const checks={
  originalCard:!!card&&card.status==='draft',
  gbaZero:!!card&&card.containerId===BOOK&&card.slotIndex===READER_SLOT&&card.placement==='book',
  uniqueCardId:!!card&&state.objects.filter(x=>x.id===id).length===1,
  contracted:!!card&&card.runeBinding?.runeId===RUNE&&card.runeBinding.state==='cardified',
  noDuplicateRune:!!card&&state.objects.filter(x=>x.runeBinding?.runeId===RUNE&&x.status!=='recycled').length===1,
  noContainment:!!card&&!card.containment,
  notPermanent:!!card&&!card.permanentCard&&!card.l2Certified&&!card.permanentNumber&&
   !card.permanentRegistry&&!card.upgradeRequest,
  localSlotAvailable:number>=1&&number<=MAX_LOCAL_WELLS&&!registered.some(x=>x.permanentRegistry?.number===number),
  slotEmpty:!state.objects.some(x=>x.id!==id&&candidateObject(x)&&x.containerId===BOOK&&x.slotIndex===number-1)
 };
 return {card:card?{...card}:null,checks,eligible:Object.values(checks).every(Boolean),
  proposedNumber:number,scope:'local-only',verifiedByAI:false};
}
function transaction(storage,id,expectedRevision,check,mutate,clock){
 const state=read(storage),i=state.objects.findIndex(x=>x.id===id&&candidateObject(x));
 if(i<0)throw Error('找不到登錄原卡');
 const old=state.objects[i];
 if(!Number.isInteger(expectedRevision)||old.revision!==expectedRevision)throw Error('卡片版本已變更，未作修改');
 check(old,state.objects);
 const now=clock();
 state.objects[i]={...mutate(old,now),revision:old.revision+1,updatedAt:now};
 save(storage,state);return {...state.objects[i]};
}
function register(storage,{cardId,expectedRevision,number,ownerConfirmed},clock=()=>new Date().toISOString()){
 if(ownerConfirmed!==true)throw Error('需要玩家在 GBA 完成不可逆最終確認');
 return transaction(storage,cardId,expectedRevision,()=>{
  const e=inspect(storage,cardId);
  if(!e.eligible||e.proposedNumber!==number)throw Error('卡片、契約或永久格不符，請重新進行 GBA 核驗');
 },(old,now)=>({...old,permanentCard:true,l2Certified:true,permanentNumber:String(number).padStart(3,'0'),
  permanentRegistry:{state:'registered',scope:'local-only',number,bookId:BOOK,
   slotIndex:number-1,cardId:old.id,registeredAt:now,
   anchor:{sceneId:'living-library',position:{x:0,y:0,z:0},rotationY:0,scale:1}},
  placement:'book',containerId:BOOK,ownerBookId:BOOK,slotIndex:number-1}),clock);
}
function toReader(storage,id,revision,clock=()=>new Date().toISOString()){
 return transaction(storage,id,revision,(card,records)=>{
  if(!isRegistered(card)||card.containerId!==BOOK||card.slotIndex!==card.permanentRegistry.number-1)
   throw Error('只有 BOOK 自己編號格內的永久原卡可調入 GBA');
  if(records.some(x=>x.id!==id&&candidateObject(x)&&x.containerId===BOOK&&x.slotIndex===READER_SLOT))
   throw Error('0 號讀卡格已被佔用');
 },old=>({...old,placement:'book',containerId:BOOK,ownerBookId:BOOK,slotIndex:READER_SLOT}),clock);
}
function archive(storage,id,revision,clock=()=>new Date().toISOString()){
 return transaction(storage,id,revision,card=>{
  if(!isRegistered(card)||card.containerId!==BOOK||card.slotIndex!==READER_SLOT)
   throw Error('只有放入 GBA 0 號格的原卡能歸檔');
 },old=>({...old,placement:'book',containerId:BOOK,ownerBookId:BOOK,
  slotIndex:old.permanentRegistry.number-1}),clock);
}
function canStore(card,containerId,slotIndex){
 if(!isRegistered(card))return true;
 const n=card.permanentRegistry.number;
 return containerId===null||(containerId===BOOK&&
  (slotIndex===READER_SLOT||(Number.isInteger(n)&&n>=1&&n<=MAX_LOCAL_WELLS&&slotIndex===n-1)));
}
function movableAnchor(storage,id,revision,position,rotationY=0,clock=()=>new Date().toISOString()){
 return transaction(storage,id,revision,old=>{
  if(!isRegistered(old)||old.runeBinding?.state!=='sealed'||old.placement!=='contained'||
   old.containment?.holderId!==RUNE)throw Error('建造模式只能移動已具現化的永久法陣物件');
  if(!position||!['x','y','z'].every(k=>Number.isFinite(position[k]))||
   Math.abs(position.x)>40||Math.abs(position.z)>40||Math.abs(position.y)>1||
   !Number.isFinite(rotationY)||Math.abs(rotationY)>Math.PI*2)
   throw Error('不合法的世界座標，未移動法陣');
 },old=>({...old,permanentRegistry:{...old.permanentRegistry,anchor:{
  sceneId:'living-library',position:{x:position.x,y:position.y,z:position.z},rotationY,scale:1}}}),clock);
}
return Object.freeze({BOOK,READER_SLOT,MAX_LOCAL_WELLS,KEY,isRegistered,list,inspect,register,
 toReader,archive,canStore,movableAnchor});
});
