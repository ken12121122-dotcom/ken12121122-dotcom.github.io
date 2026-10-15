/* Local object-creation contract: blank cards today, book forging later.
 * Does not grant skills, execute actions, write GitHub or read GEN2.
 * Persisted separately from the library display/reconnect preferences. */
(function(root,factory){
 const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LivingObjectCraft=api;
})(typeof self!=='undefined'?self:this,function(){
 'use strict';
 const KEY='living-library-player-objects-v1',VERSION=1,MAX_CARDS=64;
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
 function listCards(storage){
  return read(storage).objects.filter(x=>x.objectType==='card'&&x.templateId==='blank-card-v1'&&x.id.startsWith('local.card.'))
   .map(x=>({...x}));
 }
 // Cards stay unassigned and floating until the future book-forge placement is implemented.
 function floatingCards(storage){return listCards(storage).filter(x=>x.placement==='hover'&&!x.containerId)}
 function token(random){
  if(random&&typeof random.randomUUID==='function')return random.randomUUID();
  if(random&&typeof random.getRandomValues==='function'){
   const a=new Uint8Array(16);random.getRandomValues(a);
   return Array.from(a,x=>x.toString(16).padStart(2,'0')).join('');
  }
  throw Error('無法安全產生卡片識別碼');
 }
 function createBlankCard(storage,random,clock=()=>new Date().toISOString()){
  const state=read(storage);if(state.objects.filter(x=>x.objectType==='card').length>=MAX_CARDS)
   throw Error('試作上限 64 張；沒有覆蓋既有卡片');
  const used=new Set(state.objects.map(x=>x.id));let id;
  for(let i=0;i<5;i++){id='local.card.'+token(random);if(!used.has(id))break}
  if(used.has(id))throw Error('卡片識別碼重複');
  const at=clock();
  const card={id,objectType:'card',templateId:'blank-card-v1',title:'空白卡片',
   description:'',ownerBookId:null,containerId:null,placement:'hover',status:'draft',ability:null,
   permissions:[],reusePolicy:'not-configured',revision:1,createdAt:at,updatedAt:at};
  write(storage,[...state.objects,card]);return {...card};
 }
 function editCard(storage,id,patch,clock=()=>new Date().toISOString()){
  const state=read(storage),at=state.objects.findIndex(x=>x.id===id&&x.objectType==='card'&&x.templateId==='blank-card-v1');
  if(at<0)throw Error('找不到這張本機卡片');
  if(!patch||Array.isArray(patch)||typeof patch!=='object'||Object.keys(patch).some(x=>!['title','description'].includes(x)))
   throw Error('目前只能設定卡名及說明');
  const card={...state.objects[at]};
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
  card.revision=(Number.isInteger(card.revision)?card.revision:0)+1;card.updatedAt=clock();
  const updated=[...state.objects];updated[at]=card;write(storage,updated);return {...card};
 }
 return {KEY,VERSION,MAX_CARDS,read,listCards,floatingCards,createBlankCard,editCard};
});
