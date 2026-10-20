/* Local object-creation contract: blank cards today, book forging later.
 * Does not grant skills, execute actions, write GitHub or read GEN2.
 * Persisted separately from the library display/reconnect preferences. */
(function(root,factory){
 const api=factory(root);if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LivingObjectCraft=api;
})(typeof self!=='undefined'?self:globalThis,function(root){
 'use strict';
 const KEY='living-library-player-objects-v1',VERSION=1,MAX_CARDS=64,MAX_BOOKS=12,MAX_RECYCLED=128;
 const ORIGIN_RUNE='local.rune.origin';
 const ContractPolicy=typeof module==='object'&&module.exports?
   require('./contract-policy.js'):root.LivingContractPolicy;
 // Standalone card/book editors load this module without the world contract runtime.
 // Only the unbind transaction requires the shared policy.
 function read(storage){
  const raw=storage.getItem(KEY);
  if(raw===null)return {version:VERSION,objects:[]};
  let state;try{state=JSON.parse(raw)}catch(_){throw Error('創作資料格式錯誤，為保護原檔已停止修改')}
  if(!state||state.version!==VERSION||!Array.isArray(state.objects)||
     state.objects.some(x=>!x||typeof x.id!=='string'||typeof x.objectType!=='string'))
   throw Error('創作資料版本不相容，已停止修改');
  return state;
 }
 // Recipe artwork is a reusable VISUAL ASSET, not a card or spent-card record.
 // Keep it inside the SAME atomic save as all card transactions.
 function write(storage,objects,options={}){
  const prior=read(storage);
  const recipeArtwork=Object.hasOwn(options,'recipeArtwork')?options.recipeArtwork:prior.recipeArtwork;
  const next={version:VERSION,objects};
  if(recipeArtwork)next.recipeArtwork={...recipeArtwork};
  storage.setItem(KEY,JSON.stringify(next));
 }
 function isUnbindSpellTitle(title){
  return /^(解除綁定|解除契約|契約解除)(術|卡|魔法|卡片)?$/.test(
   typeof title==='string'?title.trim().replace(/\s+/g,''):'');
 }
 function isPlayerCard(x){return x.objectType==='card'&&x.templateId==='blank-card-v1'&&x.id.startsWith('local.card.')}
 // Return the same original card; a rune bond never creates a second card ID.
 function getRuneBoundCard(storage,runeId=ORIGIN_RUNE){
  return listCards(storage).find(x=>x.runeBinding?.runeId===runeId)||null;
 }
 // A future authorized permanent registry is independent of editable cardNumber.
 // An upgraded permanent rune bond is irreversible by normal/unbinding spells.
 function isPermanentRuneCard(card){
  if(!card||typeof card!=='object')return false;
  return card.l2Certified===true||card.permanentCard===true||
   card.permanentRegistration?.state==='registered'||
   card.permanentRegistry?.state==='registered'||
   card.certification?.level==='L2'||card.certification?.level==='permanent'||
   (card.permanentNumber!==null&&card.permanentNumber!==undefined&&String(card.permanentNumber).trim()!=='');
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
 // Restore the SAME materialized card to the floor rune, with its original
 // contract and ID intact; counterpart of RUNE-05 and safe for future permanent
 // cards (permanence locks unbinding, not changing form).
 function returnRuneCard(storage,runeId,cardId,expectedRevision,clock=()=>new Date().toISOString(),placement=null){
  if(runeId!==ORIGIN_RUNE)throw Error('未知初始法陣');
  const state=read(storage);
  const index=state.objects.findIndex(x=>x.id===cardId&&isPlayerCard(x)&&x.status!=='recycled');
  if(index<0)throw Error('找不到可歸還法陣的卡');
  const old=state.objects[index];
  if(!Number.isInteger(expectedRevision)||old.revision!==expectedRevision)
   throw Error('卡片資料已變動，請重新選取');
  const matches=state.objects.filter(x=>isPlayerCard(x)&&x.status!=='recycled'&&
   x.runeBinding?.runeId===runeId);
  if(matches.length!==1||matches[0].id!==cardId)throw Error('法陣已有其他契約');
  if(old.status!=='draft'||old.placement!=='hover'||old.ownerBookId||old.containerId||
   old.containment||old.runeBinding?.state!=='cardified')
   throw Error('卡片尚未具現化或目前不在世界中');
  // One atomic transaction: a permanent card's last approved hologram
  // position becomes the current world object anchor at card→rune conversion.
  if(placement){
   if(old.permanentRegistry?.state!=='registered'||!placement.position||
    !['x','y','z'].every(k=>Number.isFinite(placement.position[k]))||
    Math.abs(placement.position.x)>8||Math.abs(placement.position.z)>8||
    Math.abs(placement.position.y)>1||Math.hypot(placement.position.x,placement.position.z)>9.5||
    !Number.isFinite(placement.rotationY)||Math.abs(placement.rotationY)>Math.PI*2)
    throw Error('投影位置不合法；永久原卡不變');
  }
  const when=clock(),permanentRegistry=placement?{...old.permanentRegistry,
   anchor:{...old.permanentRegistry.anchor,position:{...placement.position},
    rotationY:placement.rotationY,scale:1}}:old.permanentRegistry;
  const now=when,next={...old,permanentRegistry,placement:'contained',ownerBookId:null,
   containerId:null,slotIndex:null,runeBinding:{...old.runeBinding,state:'sealed',resealedAt:now},
   containment:{kind:'rune_contract',holderId:runeId,holderType:'rune',state:'stored',absorbedAt:now},
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
  if(isPermanentRuneCard(old))throw Error('已升級為永久編號卡，禁止解除契約');
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

 // A one-use unbinding spell is permanently consumed, never archived.
 // Its deletion and the target's contract release share ONE atomic write.
 function unbindAndAbsorbSpell(storage,{runeId=ORIGIN_RUNE,targetId,targetRevision,spellId,spellRevision},
   clock=()=>new Date().toISOString()){
  if(!ContractPolicy)throw Error('解除契約所需的共用規則尚未載入');
  if(typeof runeId!=='string'||!runeId.trim())throw Error('無效契約身分');
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
  if(isPermanentRuneCard(target))throw Error('此法陣已升級為永久編號卡，不可解除契約');
  if(!ContractPolicy.canUnbind(target)||target.runeBinding.runeId!==runeId)
   throw Error('此原卡的契約狀態或升級資格不允許解除');

  if(spell.status!=='draft'||spell.placement!=='hover'||spell.containerId||spell.ownerBookId||
     spell.runeBinding||spell.consumedBy||spell.containment)
   throw Error('施法卡目前不可消耗');
  const title=typeof spell.title==='string'?spell.title.trim().replace(/\s+/g,''):'';
  if(!/^(解除綁定|解除契約|契約解除)(術|卡|魔法|卡片)?$/.test(title))
   throw Error('此卡不是解除綁定術卡');
  // Never create a second ID, overwrite another object, or use the recycle bin.
  // A single atomic transaction releases any contract's canonical binding;
  // only matching contract-owned metadata is cleared, never card artwork.
  const when=clock();
  let targetData=ContractPolicy.removeBinding(target);
  if(target.scanContract){
   // Resolve at call time: browser loads scan-bundle.js after object-craft.js.
   const ScanBundle=typeof module==='object'&&module.exports?
    require('./scan-bundle.js'):root.LivingScanBundle;
   if(!ScanBundle?.resetAfterUnbind)throw Error('掃描契約解除規則尚未載入，原卡保持不變');
   targetData=ScanBundle.resetAfterUnbind(target,targetData);
  }
  const updatedTarget={...targetData,revision:target.revision+1,updatedAt:when};
  // Only the original target survives. No hidden spent-spell card remains.
  const objects=cards.map((item,index)=>index===targetIndex?updatedTarget:item)
   .filter(item=>item.id!==spellId);
  // If the original spell had a player's photo, keep only its artwork format,
  // without retaining or restoring the consumed physical card.
  write(storage,objects,{recipeArtwork:spell.image||state.recipeArtwork||null});
  return {target:{...updatedTarget},spellId,consumed:true};
 }
 function listCards(storage){
  // Old spent spells from earlier builds are not live inventory.
  return read(storage).objects.filter(x=>isPlayerCard(x)&&x.status!=='recycled'&&
   x.containment?.kind!=='consumed_spell').map(x=>({...x}));
 }
 function purgeLegacyConsumedSpells(storage){
  const state=read(storage);
  // Strict legacy cleanup: never remove arbitrary contained or permanent cards.
  const spent=state.objects.filter(c=>isPlayerCard(c)&&c.status==='draft'&&
   c.placement==='contained'&&c.containment?.kind==='consumed_spell'&&
   c.containment.holderType==='card'&&c.containment.state==='stored'&&
   c.consumedBy===c.containment.holderId&&c.consumedBy!==c.id&&
   !c.runeBinding&&!c.runeContract&&!c.scanContract&&!c.truthEyeContract&&
   !c.containerId&&!c.ownerBookId&&!isPermanentRuneCard(c));
  if(!spent.length)return [];
  const ids=new Set(spent.map(c=>c.id));
  write(storage,state.objects.filter(c=>!ids.has(c.id)));
  return [...ids];
 }
 // Read-only card audit: persisted links versus known conditional game behaviors.
 function inspectCard(storage,id){
  const s=read(storage),c=s.objects.find(x=>x.id===id&&isPlayerCard(x)&&x.status!=='recycled'&&x.containment?.kind!=='consumed_spell');
  if(!c)throw Error('這張卡已不存在或已消耗');
  const links=[],spells=[],animations=[],actions=[];
  const add=(list,name,detail)=>list.push({name,detail});
  const link=(kind,id,detail,status='有效')=>links.push({kind,id:String(id),detail,status});
  if(c.runeBinding?.runeId)link('法陣／物件契約',c.runeBinding.runeId,'狀態 '+(c.runeBinding.state||'未知'));
  if(c.scanContract){
   for(const m of c.scanContract.members||[])link('群組成員',m.id,m.kind);
   add(actions,c.scanContract.form==='world'?'整組收回卡片':'整組物件實體化','長按三秒，切換形態不解除契約');
   add(animations,'掃描群組形態轉換','依卡／世界形態觸發');
  }
  if(c.truthEyeContract){
   link('真實之眼契約',c.truthEyeContract.artifactId||c.runeBinding?.runeId||'—','神器形態 '+(c.truthEyeContract.form||'未知'));
   add(actions,'真實之眼形態控制','依神器狀態觸發');add(animations,'神器形態動畫','依場景條件觸發');
  }
  if(c.runeBinding?.runeId===ORIGIN_RUNE){
   add(actions,c.runeBinding.state==='sealed'?'法陣具現化':c.runeBinding.state==='cardified'?'歸還法陣':'封印到初始法陣','依目前契約狀態操作');
   add(animations,'初始法陣與原卡轉換','具現化／收回動畫（條件式）');
  }
  if(isUnbindSpellTitle(c.title)&&!c.runeBinding&&!c.containment){
   const ready=c.placement==='hover'&&!c.containerId&&!c.ownerBookId;
   add(spells,'解除契約術',ready?'已在玩家軌道；需確認目標才可施法':
    '已配置法術；需先將卡片取回玩家軌道才可施法');
   add(actions,'確認式解除契約','長按三秒選目標 → 檢查契約／群組 → 點 ✓ 後永久消耗施法卡');
   add(animations,'六芒星長按特效','長按三秒期間（出卡後可觸發）');
   add(animations,'指向性吸收特效','確認並成功解除後才播放');
  }
  if(c.linkedCardId)link('公開內容',c.linkedCardId,'卡片內容連結，非施法');
  if(c.copyInfo?.sourceId){
   const live=s.objects.some(x=>x.id===c.copyInfo.sourceId&&x.status!=='recycled'&&x.containment?.kind!=='consumed_spell');
   link('卡面下載來源',c.copyInfo.sourceId,'來源參考，非綁定',live||c.copyInfo.sourceId===UNBIND_BLUEPRINT_ID?'來源可用':'來源已不存在');
  }
  if(c.containment?.holderId)link('收納持有者',c.containment.holderId,c.containment.kind);
  if(c.containerId)link('目前存放位置',c.containerId,'格位 '+(c.slotIndex??'—'));
  if(c.ability)link('能力資料','ability','此欄位存在，不代表動作已啟用','僅資料');
  const json=JSON.stringify(c),data=JSON.parse(json);
  if(data.image?.dataUrl)data.image.dataUrl='[圖片 Base64 省略；原檔仍完整保存]';
  const valid=v=>v!==null&&v!==undefined&&v!==''&&!(Array.isArray(v)&&!v.length)&&!(v&&typeof v==='object'&&!Array.isArray(v)&&!Object.keys(v).length);
  return {id:c.id,title:c.title||'未命名卡',status:c.status,placement:c.placement,revision:c.revision,
   stats:{fields:Object.keys(c).length,filled:Object.values(c).filter(valid).length,
    bytes:typeof TextEncoder==='function'?new TextEncoder().encode(json).length:json.length,
    photo:!!c.image?.dataUrl,groupMembers:c.scanContract?.members?.length||0,
    links:links.length,spells:spells.length,animations:animations.length,actions:actions.length},
   links,spells,animations,actions,data,
   note:'只讀取現有卡片；數量為已辨識的契約資料與條件式行為，非目前正在執行的動畫或未發現的程式連結。'};
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
  if(!card||typeof card!=='object')return false;
  const uniqueBook='local.atlas.compendium';
  const reg=card.permanentRegistry;
  const registered=reg?.state==='registered';
  // Universal UNIQUE BOOK rule: the nine display wells require the
  // irreversible registered original card at its own permanent-number slot.
  // The GBA reader (well 9) is intentionally NOT a permanent display well:
  // it must still accept ordinary/blank media for inspection and upgrading.
  // No tests against specific rune, spell or artifact IDs.
  if(containerId===uniqueBook&&Number.isInteger(slotIndex)&&slotIndex>=0&&slotIndex<9){
   const number=reg?.number;
   return registered&&Number.isInteger(number)&&number>=1&&number<=9&&
    slotIndex===number-1&&reg.bookId===uniqueBook&&
    (!reg.cardId||reg.cardId===card.id);
  }
  // Registered originals may leave their own well for the GBA (or orbit),
  // but cannot be permanently displayed in other ordinary card books.
  if(registered){
   return containerId===null||(containerId===uniqueBook&&slotIndex===9);
  }
  // A card that represents a physically manifested or sealed world object
  // is not a draggable card until its normal card form is restored.
  const metadata=Object.entries(card).filter(([key,value])=>
   /Contract$/.test(key)&&value&&typeof value==='object'&&!Array.isArray(value)&&
   (value.artifactId===card.runeBinding?.runeId||value.runeId===card.runeBinding?.runeId));
  if(metadata.some(([,value])=>['world','sealed'].includes(value.form)))return false;
  if(!card.runeBinding?.runeId)return true;
  // Unupgraded bound ordinary cards remain allowed in the GBA reader.
  // An artifact already cardified may additionally use normal user-made books,
  // without getting access to unique/permanent showcase wells.
  if(metadata.some(([,value])=>value.form==='card'))return true;
  return containerId===null||(containerId===uniqueBook&&slotIndex===9);
 }
 // One-time non-destructive correction of previously permitted illegal unique
 // BOOK placements. The SAME original card returns to the player's orbit.
 // Never migrate or modify an already registered permanent original.
 function restoreUnregisteredUniqueWells(storage,clock=()=>new Date().toISOString()){
  const state=read(storage),book='local.atlas.compendium';
  const offenders=state.objects.filter(c=>isPlayerCard(c)&&c.status==='draft'&&
   c.placement==='book'&&c.containerId===book&&c.ownerBookId===book&&
   Number.isInteger(c.slotIndex)&&c.slotIndex>=0&&c.slotIndex<9&&
   c.permanentRegistry?.state!=='registered'&&!c.containment);
  if(!offenders.length)return [];
  const ids=new Set(offenders.map(c=>c.id));
  const when=clock();
  const updated=state.objects.map(c=>ids.has(c.id)?{
   ...c,placement:'hover',containerId:null,ownerBookId:null,slotIndex:null,
   revision:(Number.isInteger(c.revision)?c.revision:0)+1,updatedAt:when
  }:c);
  write(storage,updated);return [...ids];
 }
 function placeCard(storage,id,containerId,slotIndex=null,clock=()=>new Date().toISOString()){
  const state=read(storage),sizes={'local.atlas.compendium':10,'local.atlas.collection':18};
  for(const book of state.objects)if(book.objectType==='book'&&book.templateId==='blank-book-v1'&&book.openingMode==='atlas'&&book.id.startsWith('local.book.'))sizes[book.id]=18;
  if(containerId!==null&&(!Object.hasOwn(sizes,containerId)||!Number.isInteger(slotIndex)||slotIndex<0||slotIndex>=sizes[containerId]))throw Error('無效卡槽');
  const at=state.objects.findIndex(x=>x.id===id&&isPlayerCard(x)&&x.status!=='recycled');
  if(at<0)throw Error('找不到卡片');
  if(containerId&&state.objects.some(x=>x.id!==id&&isPlayerCard(x)&&x.status!=='recycled'&&x.containerId===containerId&&x.slotIndex===slotIndex))throw Error('這個卡槽已有卡片');
  // Permanent BOOK addresses remain occupied by their registered ID even
  // while the sole physical original is outside the book or in GBA reader 0.
  if(containerId==='local.atlas.compendium'&&slotIndex!==9&&
   state.objects.some(x=>x.id!==id&&isPlayerCard(x)&&x.status!=='recycled'&&
    x.permanentRegistry?.state==='registered'&&x.permanentRegistry.bookId===containerId&&
    x.permanentRegistry.slotIndex===slotIndex))
   throw Error('此 BOOK 位置已由其他永久編號卡永久保留');
  const old=state.objects[at];
  if(old.placement==='contained'||old.containment)throw Error('卡片已收納，請使用專用召喚術取回');
  if(!canStoreBoundCard(old,containerId,slotIndex)){
   if(containerId==='local.atlas.compendium'&&slotIndex>=0&&slotIndex<9)
    throw Error('唯一圖鑑左側九格僅接受已永久登錄、且編號對應該格的原卡；請先在右側 GBA 完成永久登錄');
   throw Error(isPermanentRuneCard(old)?
    '永久卡只能放在唯一 BOOK 的自身永久格或 GBA 0 號格':
    '目前卡片狀態不允許放入此格；未升級契約卡只能使用右側 GBA 讀卡格');
  }
  const next={...old,containerId,ownerBookId:containerId,slotIndex:containerId?slotIndex:null,
   placement:containerId?'book':'hover',revision:(old.revision||0)+1,updatedAt:clock()};
  const objects=[...state.objects];objects[at]=next;write(storage,objects);return {...next};
 }
 // EXPERIMENTAL CARD-COPY (ON HOLD): a physical blank medium in the right GBA well receives
 // only appearance/content. It never duplicates identity, authority or a rune.
 const ATLAS_ID='local.atlas.compendium';
 function isBlankCopyMedium(c){
  return isPlayerCard(c)&&c.status==='draft'&&c.placement==='book'&&
   c.containerId===ATLAS_ID&&c.slotIndex===9&&c.ownerBookId===ATLAS_ID&&
   c.title==='空白卡片'&&!c.description&&!c.cardNumber&&!c.cardType&&!c.image&&
   !c.linkedCardId&&!c.ability&&(!c.permissions||c.permissions.length===0)&&
   !c.runeBinding&&!c.runeContract&&!c.boundRuneId&&!c.scanContract&&!c.truthEyeContract&&
   !c.containment&&!c.consumedBy&&!c.copyInfo&&!c.downloadTemplate&&
   !c.l2Certified&&!c.upgradeRequest&&!c.certification;
 }
 function canCopyAppearance(c){
  if(!isPlayerCard(c)||c.status!=='draft'||c.placement!=='book'||
    c.containerId!==ATLAS_ID||!Number.isInteger(c.slotIndex)||
    c.slotIndex<0||c.slotIndex>8)return false;
  if(typeof c.title!=='string'||!c.title.trim()||c.title==='空白卡片')return false;
  if(c.ability||(Array.isArray(c.permissions)?c.permissions.length>0:!!c.permissions)||
    c.runeBinding||c.runeContract||c.boundRuneId||c.containment||
    isPermanentRuneCard(c)||c.l2Certified||c.upgradeRequest||c.certification||c.isUnique===true||
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
 // GBA-FORGE-001: downloadable FORMAT definitions are not historical cards.
 // Keep a verified system recipe for the one-use unbinding spell: consuming the
 // physical spell deletes its card record, NEVER the recipe needed to make more.
 // No extra local.card ID, inventory, save field, or spent-spell record is minted.
 const UNBIND_BLUEPRINT_ID='system.template.unbind-contract-v1';
 const UNBIND_BLUEPRINT=Object.freeze({
  id:UNBIND_BLUEPRINT_ID,revision:1,title:'解除契約卡',
  description:'解除一項有效契約；使用後卡片永久消耗。',
  cardType:'法術',image:null,downloadTemplate:true,sourceKind:'system-copyable-recipe'
 });
 function isSystemDownloadTemplate(source){
  return source?.id===UNBIND_BLUEPRINT_ID&&
   source.revision===UNBIND_BLUEPRINT.revision&&
   source.sourceKind===UNBIND_BLUEPRINT.sourceKind;
 }
 function unbindRecipe(storage){
  const state=read(storage);
  // A surviving player-designed spell with artwork may supply the original
  // appearance when no format artwork was previously saved.
  const living=state.objects.find(c=>isPlayerCard(c)&&c.status==='draft'&&
   ['hover','book'].includes(c.placement)&&!c.containment&&
   isUnbindSpellTitle(c.title)&&c.image);
  const art=state.recipeArtwork||living?.image||null;
  return {...UNBIND_BLUEPRINT,image:art?{...art}:null};
 }
 function canDownloadTemplate(c){
  // Opt-in only: being a draft card, an old book, or a stored spell is not
  // permission to publish this card's appearance in the GBA download list.
  return isPlayerCard(c)&&c.status==='draft'&&c.downloadTemplate===true&&
   ['hover','book'].includes(c.placement)&&!c.containment&&!c.consumedBy&&
   typeof c.title==='string'&&!!c.title.trim()&&c.title.trim()!=='空白卡片'&&
   !c.ability&&!(Array.isArray(c.permissions)?c.permissions.length>0:!!c.permissions)&&
   !c.runeBinding&&!c.runeContract&&!c.boundRuneId&&!c.scanContract&&!c.truthEyeContract&&
   !isPermanentRuneCard(c)&&!c.l2Certified&&!c.upgradeRequest&&!c.certification&&
   c.isUnique!==true&&c.unique!==true&&!c.uniqueness&&
   c.copyPolicy!=='forbidden'&&c.reusePolicy!=='unique'&&
   c.cardType!=='唯一'&&c.cardType!=='獨一無二'&&
   !c.scanContract&&!c.truthEyeContract;
 }
 function listDownloadableTemplates(storage){
  // Always present the reusable recipe FIRST, even if the last physical copy
  // was consumed. Only other LIVE player cards explicitly opted in may follow.
  return [unbindRecipe(storage),...listCards(storage).filter(canDownloadTemplate)
   .sort((a,b)=>String(a.title).localeCompare(String(b.title),'zh-Hant')||
    a.id.localeCompare(b.id))];
 }
 function forgeBlankFromTemplate(storage,{blankId,blankRevision,sourceId,sourceRevision},
  clock=()=>new Date().toISOString()){
  if(typeof blankId!=='string'||typeof sourceId!=='string'||blankId===sourceId)
   throw Error('空白卡與卡片範本不得為同一物件');
  const state=read(storage),objects=state.objects;
  const blankIndex=objects.findIndex(x=>x.id===blankId&&isPlayerCard(x)&&x.status!=='recycled');
  const systemRecipe=sourceId===UNBIND_BLUEPRINT_ID;
  const sourceIndex=systemRecipe?-1:objects.findIndex(x=>
   x.id===sourceId&&isPlayerCard(x)&&x.status!=='recycled');
  if(blankIndex<0||(!systemRecipe&&sourceIndex<0))throw Error('空白卡或目前可複製的卡片不存在');
  const blank=objects[blankIndex],source=systemRecipe?unbindRecipe(storage):objects[sourceIndex];
  if(!Number.isInteger(blankRevision)||blankRevision!==blank.revision||
    !Number.isInteger(sourceRevision)||sourceRevision!==source.revision)
   throw Error('卡片已變動，請重新選擇');
  if(!isBlankCopyMedium(blank))throw Error('右側 GBA 讀卡格需要一張真正的空白卡');
  if(systemRecipe?!isSystemDownloadTemplate(source):!canDownloadTemplate(source))
   throw Error('此範本不允許下載');
  const when=clock();
  const next={...blank,title:source.title,description:source.description||'',
   cardType:source.cardType||'',image:source.image?{...source.image}:null,
   cardNumber:'',linkedCardId:null,ability:null,permissions:[],downloadTemplate:false,
   copyInfo:{kind:'gba-template-download',sourceId:source.id,
    sourceRevision:source.revision,copiedAt:when},
   revision:blank.revision+1,updatedAt:when};
  const updated=[...objects];updated[blankIndex]=next;write(storage,updated);
  return {...next};
 }
 function editCard(storage,id,patch,clock=()=>new Date().toISOString()){
  const state=read(storage),at=state.objects.findIndex(x=>x.id===id&&isPlayerCard(x)&&x.status!=='recycled');
  if(at<0)throw Error('找不到可編輯的本機卡片');
  if(!patch||Array.isArray(patch)||typeof patch!=='object'||Object.keys(patch).some(x=>!['title','description','cardNumber','cardType','image','linkedCardId','downloadTemplate'].includes(x)))
   throw Error('目前只能設定卡面文字、圖片及公開內容連結');
  const card={...state.objects[at]};
  if(isPermanentRuneCard(card))throw Error('永久卡已鎖定，修改需經 GBA 正式變更程序');
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
  if(Object.hasOwn(patch,'downloadTemplate')){
   if(typeof patch.downloadTemplate!=='boolean')throw Error('可複製卡分類只能啟用或停用');
   card.downloadTemplate=patch.downloadTemplate;
   if(card.downloadTemplate&&!canDownloadTemplate(card))
    throw Error('這張卡片具有契約、唯一身分、能力或其他保護狀態，不可列為可複製卡');
  }
  card.cardLayout='compendium-v2';
  card.revision=(Number.isInteger(card.revision)?card.revision:0)+1;card.updatedAt=clock();
  const updated=[...state.objects];updated[at]=card;
  // Save edited spell photography as a reusable visual recipe, not a historical
  // spell card. A later one-use cast may destroy the source card safely.
  const recipeEdit=isUnbindSpellTitle(card.title)&&Object.hasOwn(patch,'image');
  write(storage,updated,recipeEdit?{recipeArtwork:card.image||null}:{});
  return {...card};
 }
 function recycleCard(storage,id,clock=()=>new Date().toISOString()){
  const state=read(storage),at=state.objects.findIndex(x=>x.id===id&&isPlayerCard(x)&&x.status!=='recycled');
  if(at<0)throw Error('找不到可回收的本機卡片');
  const old=state.objects[at];
  if(isPermanentRuneCard(old))throw Error('永久編號卡禁止回收');
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
  createBlankCard,createBlankBook,createCardBook,setBookView,setBookActive,editCard,recycleCard,restoreCard,placeCard,isBlankCopyMedium,canCopyAppearance,copySources,copyIntoBlankCard,canDownloadTemplate,listDownloadableTemplates,forgeBlankFromTemplate,purgeLegacyConsumedSpells,UNBIND_BLUEPRINT_ID,inspectCard,
  ORIGIN_RUNE,getRuneBoundCard,bindRuneCard,canStoreBoundCard,restoreUnregisteredUniqueWells,restoreUncommittedRuneCard,unbindAndAbsorbSpell,sealRuneCard,materializeRuneCard,returnRuneCard,isPermanentRuneCard};
});
