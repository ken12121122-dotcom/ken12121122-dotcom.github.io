/* Local object-creation contract: blank cards today, book forging later.
 * Does not grant skills, execute actions, write GitHub or read GEN2.
 * Persisted separately from the library display/reconnect preferences. */
(function(root,factory){
 const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LivingObjectCraft=api;
})(typeof self!=='undefined'?self:this,function(){
 'use strict';
 const KEY='living-library-player-objects-v1',VERSION=1,MAX_CARDS=64,MAX_BOOKS=12,MAX_RECYCLED=128;
 const ORIGIN_RUNE='local.rune.origin';
 function read(storage){
  const raw=storage.getItem(KEY);
  if(raw===null)return {version:VERSION,objects:[]};
  let state;try{state=JSON.parse(raw)}catch(_){throw Error('創作資料格式錯誤，為保護原檔已停止修改')}
  if(!state||state.version!==VERSION||!Array.isArray(state.objects)||
     state.objects.some(x=>!x||typeof x.id!=='string'||typeof x.objectType!=='string'))
   throw Error('創作資料版本不相容，已停止修改');
  return state;
 }
 function write(storage,objects){storage.setItem(KEY,JSON.stringify({version:VERSION,objects}))}
 function isPlayerCard(x){return x.objectType==='card'&&x.templateId==='blank-card-v1'&&x.id.startsWith('local.card.')}
 // Return the same original card; a rune bond never creates a second card ID.
 function getRuneBoundCard(storage,runeId=ORIGIN_RUNE){
  return listCards(storage).find(x=>x.runeBinding?.runeId===runeId)||null;
 }
 function bindRuneCard(storage,runeId,cardId,expectedRevision,clock=()=>new Date().toISOString()){
  if(runeId!==ORIGIN_RUNE)throw Error('目前只支援初始法陣');
  if(typeof cardId!=='string'||!cardId.startsWith('local.card.'))throw Error('無效卡片識別碼');
  const state=read(storage);
  const at=state.objects.findIndex(x=>x.id===cardId&&isPlayerCard(x)&&x.status!=='recycled');
  if(at<0)throw Error('找不到可連結的普通卡');
  const old=state.objects[at];
  if(old.runeBinding?.runeId===runeId)return {...old}; // idempotent, no extra revision.
  if(old.runeBinding||old.runeContract||old.boundRuneId)throw Error('這張卡片已與其他法陣建立契約');
  if(state.objects.some(x=>x.id!==cardId&&isPlayerCard(x)&&x.status!=='recycled'&&x.runeBinding?.runeId===runeId))
   throw Error('此法陣已經連結其他卡片');
  if(!Number.isInteger(expectedRevision)||expectedRevision!==old.revision)throw Error('卡片已變動，請重新選擇');
  if(old.status!=='draft'||old.placement!=='hover'||old.containerId||old.ownerBookId||
     typeof old.title!=='string'||!old.title.trim()||old.title.trim()==='空白卡片'||
     old.l2Certified===true||old.upgradeRequest?.l2Certified===true||old.certification?.level==='L2')
   throw Error('此卡片不符合目前的法陣連結條件');
  const atTime=clock();
  const next={...old,runeBinding:{runeId,state:'bound',boundAt:atTime},
   revision:old.revision+1,updatedAt:atTime};
  const objects=[...state.objects];objects[at]=next;
  write(storage,objects);return {...next};
 }
 // RUNE-04: finish the directed card → floor-rune absorption in ONE atomic write.
 // Accepts both a fresh unbound card and a legacy temporarily bound card.
 function sealRuneCard(storage,runeId,cardId,expectedRevision,clock=()=>new Date().toISOString()){
  if(runeId!==ORIGIN_RUNE)throw Error('目前只支援初始法陣');
  const state=read(storage),index=state.objects.findIndex(x=>
   x.id===cardId&&isPlayerCard(x)&&x.status!=='recycled');
  if(index<0)throw Error('法陣契約原卡不存在');
  const old=state.objects[index];
  if(!Number.isInteger(expectedRevision)||old.revision!==expectedRevision)
   throw Error('卡片資料已變動，請重新選取');
  if(state.objects.some(x=>x.id!==cardId&&isPlayerCard(x)&&x.status!=='recycled'&&
     x.runeBinding?.runeId===runeId))throw Error('法陣已與其他卡片締結契約');
  if(old.status!=='draft'||old.placement!=='hover'||old.containerId||old.ownerBookId||
     old.containment||old.runeBinding?.state==='cardified'||old.runeBinding?.state==='sealed'||
     old.l2Certified===true||old.upgradeRequest?.state==='requested'||
     !old.title||old.title.trim()==='空白卡片')throw Error('此卡片不可吸入法陣');
  if(old.runeBinding&&(old.runeBinding.runeId!==runeId||old.runeBinding.state!=='bound'))
   throw Error('卡片已有不相容的法陣契約');
  const now=clock(),binding=old.runeBinding||{runeId,boundAt:now};
  const next={...old,runeBinding:{...binding,runeId,state:'sealed',sealedAt:now},
   placement:'contained',containerId:null,ownerBookId:null,slotIndex:null,
   containment:{kind:'rune_contract',holderId:runeId,holderType:'rune',
    state:'stored',absorbedAt:now},
   revision:old.revision+1,updatedAt:now};
  const objects=[...state.objects];objects[index]=next;write(storage,objects);return {...next};
 }
 // RUNE-05: the ONE original card is materialized from the floor rune;
 // cardification is a change of state, NOT creation of a second card ID.
 function materializeRuneCard(storage,runeId,cardId,expectedRevision,clock=()=>new Date().toISOString()){
  if(runeId!==ORIGIN_RUNE)throw Error('未知法陣');
  const state=read(storage),index=state.objects.findIndex(x=>
   x.id===cardId&&isPlayerCard(x)&&x.status!=='recycled');
  if(index<0)throw Error('封印卡片不存在');
  const old=state.objects[index];
  if(old.revision!==expectedRevision)throw Error('卡片版本已變更');
  if(old.status!=='draft'||old.runeBinding?.runeId!==runeId||
     old.runeBinding.state!=='sealed'||old.placement!=='contained'||
     old.containment?.kind!=='rune_contract'||old.containment.holderId!==runeId)
   throw Error('此法陣並未保存可轉換的原卡');
  const now=clock(),{containment,...withoutContainer}=old;
  const next={...withoutContainer,placement:'hover',containerId:null,
   ownerBookId:null,slotIndex:null,runeBinding:{...old.runeBinding,state:'cardified',cardifiedAt:now},
   revision:old.revision+1,updatedAt:now};
  const objects=[...state.objects];objects[index]=next;write(storage,objects);return {...next};
 }
 function restoreUncommittedRuneCard(storage,runeId=ORIGIN_RUNE,clock=()=>new Date().toISOString(),expectedCardId=null,expectedRevision=null){
  if(runeId!==ORIGIN_RUNE)throw Error('目前僅支援初始法陣');
  const state=read(storage);
  const matches=state.objects.map((item,index)=>({item,index})).filter(x=>
   isPlayerCard(x.item)&&x.item.runeBinding?.runeId===runeId&&x.item.status!=='recycled');
  if(matches.length!==1)throw Error('找不到唯一的待還原契約卡');
  const {item:old,index}=matches[0];
  if((expectedCardId!==null&&old.id!==expectedCardId)||(expectedRevision!==null&&old.revision!==expectedRevision))
   throw Error('契約卡在施法期間已有變動，為保護資料已停止解除');
  if(old.runeBinding.state!=='bound')throw Error('封印後的契約不可直接還原');
  if(old.status!=='draft'||!['hover','book'].includes(old.placement)||
     (old.containerId&&(old.containerId!=='local.atlas.compendium'||old.slotIndex!==9)))
   throw Error('卡片狀態不符，未修改');
  if(old.upgradeRequest?.state==='requested')throw Error('卡片升級申請尚未結束');
  const {runeBinding,...unchanged}=old;
  const result={...unchanged,containerId:null,ownerBookId:null,slotIndex:null,placement:'hover',
   revision:(old.revision||0)+1,updatedAt:clock()};
  const objects=[...state.objects];objects[index]=result;
  write(storage,objects);return {...result};
 }

 // Directed card absorption is containment, never deletion. One write commits
 // both the restored target and the consumed spell, preserving both stable IDs.
 function unbindAndAbsorbSpell(storage,{runeId=ORIGIN_RUNE,targetId,targetRevision,spellId,spellRevision},
   clock=()=>new Date().toISOString()){
  if(runeId!==ORIGIN_RUNE)throw Error('未知法陣');
  if(!targetId||!spellId||targetId===spellId)throw Error('施法卡與目標不得相同');
  const state=read(storage),cards=state.objects;
  const targetIndex=cards.findIndex(x=>x.id===targetId&&isPlayerCard(x)&&x.status!=='recycled');
  const spellIndex=cards.findIndex(x=>x.id===spellId&&isPlayerCard(x)&&x.status!=='recycled');
  if(targetIndex<0||spellIndex<0)throw Error('施法卡或目標卡已不存在');
  const target=cards[targetIndex],spell=cards[spellIndex];
  const targets=cards.filter(x=>isPlayerCard(x)&&x.status!=='recycled'&&x.runeBinding?.runeId===runeId);
  if(targets.length!==1||targets[0].id!==targetId)throw Error('法陣契約目標已改變');
  if(target.revision!==targetRevision||spell.revision!==spellRevision)
   throw Error('施法期間卡片資料已變動');
  if(!['bound','cardified'].includes(target.runeBinding?.state)||target.status!=='draft'||target.placement!=='hover'||
     target.containerId||target.ownerBookId||target.upgradeRequest?.state==='requested')
   throw Error('此契約尚不可解除');
  if(spell.status!=='draft'||spell.placement!=='hover'||spell.containerId||spell.ownerBookId||
     spell.runeBinding||spell.consumedBy||spell.containment)
   throw Error('施法卡目前不可消耗');
  const title=typeof spell.title==='string'?spell.title.trim().replace(/\s+/g,''):'';
  if(!/^(解除綁定|解除契約|契約解除)(術|卡|魔法|卡片)?$/.test(title))
   throw Error('此卡不是解除綁定術卡');
  // Never create a second ID, overwrite another object, or use the recycle bin.
  const when=clock(),{runeBinding,...targetData}=target;
  const updatedTarget={...targetData,revision:target.revision+1,updatedAt:when};
  const updatedSpell={...spell,placement:'contained',containerId:null,ownerBookId:null,slotIndex:null,
   containment:{kind:'consumed_spell',holderId:targetId,holderType:'card',
    state:'stored',absorbedAt:when},consumedBy:targetId,
   revision:spell.revision+1,updatedAt:when};
  const objects=[...cards];objects[targetIndex]=updatedTarget;objects[spellIndex]=updatedSpell;
  write(storage,objects);
  return {target:{...updatedTarget},spell:{...updatedSpell}};
 }
 function listCards(storage){
  return read(storage).objects.filter(x=>isPlayerCard(x)&&x.status!=='recycled').map(x=>({...x}));
 }
 function listRecycledCards(storage){
  return read(storage).objects.filter(x=>isPlayerCard(x)&&x.status==='recycled').map(x=>({...x}));
 }
 function getStats(storage){
  const cards=listCards(storage),recycled=listRecycledCards(storage),books=listBooks(storage);
  return {totalCards:cards.length,floatingCards:cards.filter(x=>x.placement==='hover'&&!x.containerId&&!x.containment).length,
   inBooks:cards.filter(x=>!!x.containerId).length,recycledCards:recycled.length,createdBooks:books.length};
 }
 // Cards stay unassigned and floating until the future book-forge placement is implemented.
 function floatingCards(storage){return listCards(storage).filter(x=>x.placement==='hover'&&!x.containerId&&!x.containment)}
 function listBooks(storage){return read(storage).objects.filter(x=>x.objectType==='book'&&x.templateId==='blank-book-v1'&&x.id.startsWith('local.book.')).map(x=>({...x}))}
 function token(random){
  if(random&&typeof random.randomUUID==='function')return random.randomUUID();
  if(random&&typeof random.getRandomValues==='function'){
   const a=new Uint8Array(16);random.getRandomValues(a);
   return Array.from(a,x=>x.toString(16).padStart(2,'0')).join('');
  }
  throw Error('無法安全產生卡片識別碼');
 }
 function createBlankCard(storage,random,clock=()=>new Date().toISOString()){
  const state=read(storage);if(state.objects.filter(x=>isPlayerCard(x)&&x.status!=='recycled').length>=MAX_CARDS)
   throw Error('試作上限 64 張；沒有覆蓋既有卡片');
  const used=new Set(state.objects.map(x=>x.id));let id;
  for(let i=0;i<5;i++){id='local.card.'+token(random);if(!used.has(id))break}
  if(used.has(id))throw Error('卡片識別碼重複');
  const at=clock();
  const card={id,objectType:'card',templateId:'blank-card-v1',title:'空白卡片',
   description:'',cardLayout:'compendium-v2',cardNumber:'',cardType:'',image:null,linkedCardId:null,ownerBookId:null,containerId:null,placement:'hover',status:'draft',ability:null,
   permissions:[],reusePolicy:'not-configured',revision:1,createdAt:at,updatedAt:at};
  write(storage,[...state.objects,card]);return {...card};
 }
 function createBook(storage,random,name='空白魔法書',clock=()=>new Date().toISOString(),openingMode='legacy'){
  const state=read(storage);
  if(state.objects.filter(x=>x.objectType==='book').length>=MAX_BOOKS)throw Error('本機試製上限 12 本書，未修改既有書籍');
  if(typeof name!=='string'||!name.trim()||name.trim().length>30)throw Error('書名須介於 1～30 字');
  const used=new Set(state.objects.map(x=>x.id));let id;
  for(let i=0;i<5;i++){id='local.book.'+token(random);if(!used.has(id))break}
  if(used.has(id))throw Error('書本識別碼重複');
  const at=clock();
  const book={id,objectType:'book',templateId:'blank-book-v1',title:name.trim(),
   description:openingMode==='atlas'?'左右各九格，可拖曳收藏與交換卡片':'可放入相容卡片的空白書，尚未賦予特殊能力',
   contentIds:[],status:'draft',openingMode,viewMode:'outer',viewOpen:true,active:true,reusePolicy:'local-only',
   createdAt:at,updatedAt:at,revision:1};
  write(storage,[...state.objects,book]);return {...book};
 }
 function createBlankBook(storage,random,name='空白魔法書',clock){return createBook(storage,random,name,clock,'legacy')}
 function createCardBook(storage,random,name='卡片之書',clock){return createBook(storage,random,name,clock,'atlas')}
 function setBookView(storage,id,mode,open){
  if(!['outer','inner','shelf'].includes(mode)||typeof open!=='boolean')throw Error('卡冊位置不正確');
  const state=read(storage),at=state.objects.findIndex(x=>x.id===id&&x.objectType==='book'&&x.templateId==='blank-book-v1'&&x.openingMode==='atlas');
  if(at<0)throw Error('找不到這本卡片之書');
  const objects=[...state.objects];objects[at]={...objects[at],viewMode:mode,viewOpen:open,active:mode!=='shelf',revision:(objects[at].revision||1)+1};
  write(storage,objects);return {...objects[at]};
 }
 function setBookActive(storage,id,active){
  if(typeof active!=='boolean')throw Error('書本位置設定不正確');
  const state=read(storage),at=state.objects.findIndex(x=>x.id===id&&x.objectType==='book'&&x.templateId==='blank-book-v1');
  if(at<0)throw Error('找不到這本本機書');
  const objects=[...state.objects];objects[at]={...objects[at],active,revision:(objects[at].revision||1)+1};
  write(storage,objects);return {...objects[at]};
 }
 // Atomic card transfers: a full destination rejects; no duplicate card registry.
 // A rune-linked card remains a valid upgrade candidate in the RIGHT GBA reader.
 // The 9 left display wells and all other book pages are intentionally locked.
 // Returning a card to the floating orbit never clears its rune contract.
 function canStoreBoundCard(card,containerId,slotIndex){
  if(!card?.runeBinding?.runeId)return true;
  return containerId===null||(containerId==='local.atlas.compendium'&&slotIndex===9);
 }
 function placeCard(storage,id,containerId,slotIndex=null,clock=()=>new Date().toISOString()){
  const state=read(storage),sizes={'local.atlas.compendium':10,'local.atlas.collection':18};
  for(const book of state.objects)if(book.objectType==='book'&&book.templateId==='blank-book-v1'&&book.openingMode==='atlas'&&book.id.startsWith('local.book.'))sizes[book.id]=18;
  if(containerId!==null&&(!Object.hasOwn(sizes,containerId)||!Number.isInteger(slotIndex)||slotIndex<0||slotIndex>=sizes[containerId]))throw Error('無效卡槽');
  const at=state.objects.findIndex(x=>x.id===id&&isPlayerCard(x)&&x.status!=='recycled');
  if(at<0)throw Error('找不到卡片');
  if(containerId&&state.objects.some(x=>x.id!==id&&isPlayerCard(x)&&x.status!=='recycled'&&x.containerId===containerId&&x.slotIndex===slotIndex))throw Error('這個卡槽已有卡片');
  const old=state.objects[at];
  if(old.placement==='contained'||old.containment)throw Error('卡片已收納，請使用專用召喚術取回');
  if(!canStoreBoundCard(old,containerId,slotIndex))
   throw Error('已連結法陣的卡片只能放入萬物圖鑑右側 GBA 讀卡格；左側展示格仍鎖定');
  const next={...old,containerId,ownerBookId:containerId,slotIndex:containerId?slotIndex:null,
   placement:containerId?'book':'hover',revision:(old.revision||0)+1,updatedAt:clock()};
  const objects=[...state.objects];objects[at]=next;write(storage,objects);return {...next};
 }
 // GBA-COPY-001: a physical blank medium in the right GBA well receives
 // only appearance/content. It never duplicates identity, authority or a rune.
 const ATLAS_ID='local.atlas.compendium';
 function isBlankCopyMedium(c){
  return isPlayerCard(c)&&c.status==='draft'&&c.placement==='book'&&
   c.containerId===ATLAS_ID&&c.slotIndex===9&&c.ownerBookId===ATLAS_ID&&
   c.title==='空白卡片'&&!c.description&&!c.cardNumber&&!c.cardType&&!c.image&&
   !c.linkedCardId&&!c.ability&&(!c.permissions||c.permissions.length===0)&&
   !c.runeBinding&&!c.runeContract&&!c.boundRuneId&&!c.containment&&!c.copyInfo&&
   !c.l2Certified&&!c.upgradeRequest&&!c.certification;
 }
 function canCopyAppearance(c){
  if(!isPlayerCard(c)||c.status!=='draft'||c.placement!=='book'||
    c.containerId!==ATLAS_ID||!Number.isInteger(c.slotIndex)||
    c.slotIndex<0||c.slotIndex>8)return false;
  if(typeof c.title!=='string'||!c.title.trim()||c.title==='空白卡片')return false;
  if(c.ability||c.runeBinding||c.runeContract||c.boundRuneId||c.containment||
    c.l2Certified||c.upgradeRequest||c.certification||c.isUnique===true||
    c.unique===true||c.uniqueness||c.copyPolicy==='forbidden'||
    c.reusePolicy==='unique'||c.cardType==='唯一'||c.cardType==='獨一無二')return false;
  // Never represent a copy of a protected ID, binding or actual capability.
  return true;
 }
 function copySources(storage){
  return listCards(storage).filter(canCopyAppearance)
   .sort((a,b)=>a.slotIndex-b.slotIndex||a.id.localeCompare(b.id));
 }
 function copyIntoBlankCard(storage,{blankId,blankRevision,sourceId,sourceRevision},
   clock=()=>new Date().toISOString()){
  if(typeof blankId!=='string'||typeof sourceId!=='string'||blankId===sourceId)
   throw Error('空白卡與來源卡必須為不同的原卡');
  const state=read(storage),objects=state.objects;
  const blankIndex=objects.findIndex(x=>x.id===blankId&&isPlayerCard(x)&&x.status!=='recycled');
  const sourceIndex=objects.findIndex(x=>x.id===sourceId&&isPlayerCard(x)&&x.status!=='recycled');
  if(blankIndex<0||sourceIndex<0)throw Error('讀卡來源或空白卡已不存在');
  const blank=objects[blankIndex],source=objects[sourceIndex];
  if(!Number.isInteger(blankRevision)||blankRevision!==blank.revision||
    !Number.isInteger(sourceRevision)||sourceRevision!==source.revision)
   throw Error('卡片已被修改，請重新選擇複製');
  if(!isBlankCopyMedium(blank))throw Error('GBA 右側讀卡格必須放入未使用的空白卡');
  if(!canCopyAppearance(source))throw Error('來源卡不在左頁展示格或不允許複製');
  const time=clock();
  // Copy only user-editable presentation fields, not public action links, rune
  // contracts, skill certification, authorizations, uniqueness or card number.
  const next={...blank,title:source.title,description:source.description||'',
   cardType:source.cardType||'',image:source.image?{...source.image}:null,
   cardNumber:'',linkedCardId:null,ability:null,permissions:[],
   copyInfo:{kind:'appearance',sourceId:source.id,sourceRevision:source.revision,
    copiedAt:time},revision:blank.revision+1,updatedAt:time};
  const updated=[...objects];updated[blankIndex]=next;
  write(storage,updated);return {...next};
 }
 function editCard(storage,id,patch,clock=()=>new Date().toISOString()){
  const state=read(storage),at=state.objects.findIndex(x=>x.id===id&&isPlayerCard(x)&&x.status!=='recycled');
  if(at<0)throw Error('找不到可編輯的本機卡片');
  if(!patch||Array.isArray(patch)||typeof patch!=='object'||Object.keys(patch).some(x=>!['title','description','cardNumber','cardType','image','linkedCardId'].includes(x)))
   throw Error('目前只能設定卡面文字、圖片及公開內容連結');
  const card={...state.objects[at]};
  if(card.placement==='contained'||card.containment)throw Error('已吸收的卡片需先召喚才能編輯');
  if(Object.hasOwn(patch,'title')){
   if(typeof patch.title!=='string'||!patch.title.trim()||patch.title.trim().length>40)
    throw Error('卡片名稱必須是 1～40 字');
   card.title=patch.title.trim();
  }
  if(Object.hasOwn(patch,'description')){
   if(typeof patch.description!=='string'||patch.description.length>500)
    throw Error('卡片說明不可超過 500 字');
   card.description=patch.description;
  }
  if(Object.hasOwn(patch,'cardNumber')){
   if(typeof patch.cardNumber!=='string'||patch.cardNumber.length>20)throw Error('卡片編號不可超過 20 字');
   card.cardNumber=patch.cardNumber.trim();
  }
  if(Object.hasOwn(patch,'cardType')){
   if(typeof patch.cardType!=='string'||patch.cardType.length>20)throw Error('內容類型不可超過 20 字');
   card.cardType=patch.cardType.trim();
  }
  if(Object.hasOwn(patch,'linkedCardId')){
   if(patch.linkedCardId!==null&&(typeof patch.linkedCardId!=='string'||!/^public\.[a-z0-9.]+$/.test(patch.linkedCardId)||patch.linkedCardId.length>100))throw Error('只能連結既有公開卡片');
   card.linkedCardId=patch.linkedCardId;
  }
  if(Object.hasOwn(patch,'image')){
   const v=patch.image;
   if(v!==null&&(!v||Array.isArray(v)||typeof v!=='object'||Object.keys(v).sort().join(',')!=='dataUrl,scale,x,y'||
     typeof v.dataUrl!=='string'||v.dataUrl.length>70000||!/^data:image\/jpeg;base64,\/9j\/[A-Za-z0-9+/]*={0,2}$/.test(v.dataUrl)||
     !Number.isFinite(v.scale)||v.scale<1||v.scale>3||!Number.isFinite(v.x)||v.x<0||v.x>100||!Number.isFinite(v.y)||v.y<0||v.y>100))
    throw Error('圖片格式或裁切參數無效，未修改卡片');
   card.image=v===null?null:{...v};
  }
  card.cardLayout='compendium-v2';
  card.revision=(Number.isInteger(card.revision)?card.revision:0)+1;card.updatedAt=clock();
  const updated=[...state.objects];updated[at]=card;write(storage,updated);return {...card};
 }
 function recycleCard(storage,id,clock=()=>new Date().toISOString()){
  const state=read(storage),at=state.objects.findIndex(x=>x.id===id&&isPlayerCard(x)&&x.status!=='recycled');
  if(at<0)throw Error('找不到可回收的本機卡片');
  const old=state.objects[at];
  if(old.placement==='contained'||old.containment)throw Error('已收納的法術卡不可直接回收');
  if(old.runeBinding?.runeId)throw Error('已連結法陣的卡片不可直接回收');
  if(old.containerId||old.ownerBookId)throw Error('書內卡片需先移出書本才可回收');
  if(state.objects.filter(x=>isPlayerCard(x)&&x.status==='recycled').length>=MAX_RECYCLED)
   throw Error('回收站已滿，未修改卡片');
  const when=clock(),next={...old,status:'recycled',placement:'recycled',recycledAt:when,
   restorePlacement:old.placement,revision:old.revision+1,updatedAt:when};
  const objects=[...state.objects];objects[at]=next;write(storage,objects);return {...next};
 }
 function restoreCard(storage,id,clock=()=>new Date().toISOString()){
  const state=read(storage),at=state.objects.findIndex(x=>x.id===id&&isPlayerCard(x)&&x.status==='recycled');
  if(at<0)throw Error('找不到可還原的卡片');
  if(state.objects.filter(x=>isPlayerCard(x)&&x.status!=='recycled').length>=MAX_CARDS)
   throw Error('目前有 64 張有效卡片，無法還原；原卡仍留在回收站');
  const old=state.objects[at],when=clock();
  const next={...old,status:'draft',placement:old.restorePlacement==='hover'?'hover':'hover',
   recycledAt:null,restorePlacement:null,revision:old.revision+1,updatedAt:when};
  const objects=[...state.objects];objects[at]=next;write(storage,objects);return {...next};
 }
 return {KEY,VERSION,MAX_CARDS,MAX_BOOKS,MAX_RECYCLED,read,listCards,listRecycledCards,getStats,floatingCards,listBooks,
  createBlankCard,createBlankBook,createCardBook,setBookView,setBookActive,editCard,recycleCard,restoreCard,placeCard,isBlankCopyMedium,canCopyAppearance,copySources,copyIntoBlankCard,
  ORIGIN_RUNE,getRuneBoundCard,bindRuneCard,canStoreBoundCard,restoreUncommittedRuneCard,unbindAndAbsorbSpell,sealRuneCard,materializeRuneCard};
});
