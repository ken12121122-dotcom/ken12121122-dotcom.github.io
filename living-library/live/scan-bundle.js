/* Truth Eye collection contracts. Same ObjectCraft transaction, original IDs only.
 * Stores allowlisted public/local identities, never meshes, private text or executable data. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingScanBundle=api})(typeof self!=='undefined'?self:globalThis,function(){
 'use strict';
 const KEY='living-library-player-objects-v1';
 const position=p=>!!p&&['x','z'].every(k=>Number.isFinite(p[k]));
 function inRange(p,anchor,cfg){
  if(!position(p)||!position(anchor))return false;
  const dx=p.x-anchor.x,dz=p.z-anchor.z;
  const delta=(Math.atan2(dx,-dz)*180/Math.PI-cfg.direction+540)%360-180;
  return Math.hypot(dx,dz)<=cfg.radius&&(cfg.aperture===360||Math.abs(delta)<=cfg.aperture/2);
 }
 function read(storage){
  const raw=storage.getItem(KEY);if(raw===null)return {version:1,objects:[]};
  let s;try{s=JSON.parse(raw)}catch(_){throw Error('卡片存檔無法解析，未修改')}
  if(s?.version!==1||!Array.isArray(s.objects)||s.objects.some(o=>!o||typeof o.id!=='string')||new Set(s.objects.map(o=>o.id)).size!==s.objects.length)throw Error('卡片存檔格式或 ID 衝突，未修改');
  return s;
 }
 function blank(c){return !!c&&c.objectType==='card'&&c.templateId==='blank-card-v1'&&c.id?.startsWith('local.card.')&&
  c.status==='draft'&&Number.isInteger(c.revision)&&c.revision>=1&&c.placement==='hover'&&!c.containerId&&!c.ownerBookId&&!c.containment&&c.title==='空白卡片'&&
  !c.description&&!c.image&&!c.cardNumber&&!c.cardType&&!c.ability&&!c.linkedCardId&&!c.copyInfo&&
  !c.runeBinding&&!c.runeContract&&!c.boundRuneId&&!c.truthEyeContract&&!c.scanContract&&
  !c.permanentCard&&!c.permanentRegistry&&!c.permanentRegistration&&!c.permanentNumber&&!c.l2Certified&&
  !c.upgradeRequest?.state&&!c.certification?.level&&(!c.permissions||c.permissions.length===0)}
 function nearest(anchor,entries){
  const list=entries.filter(e=>e.visible&&!e.isDragging&&!e.isFocused&&!e.isSummoning&&!e.isRecycling&&e.id===e.record?.id&&blank(e.record)&&position(e.position))
   .map(e=>({id:e.id,distance:Math.hypot(e.position.x-anchor.x,e.position.z-anchor.z)})).sort((a,b)=>a.distance-b.distance);
  if(!list.length||list[0].distance>3)throw Error('請將一張未使用的空白卡放在人物三公尺內的漂浮軌道');
  if(list[1]&&list[1].distance<=3&&list[1].distance-list[0].distance<.15)throw Error('兩張空白卡距離太接近，請移開其中一張');
  return list[0].id;
 }
 const validRef=r=>!!r&&['book','scene'].includes(r.kind)&&typeof r.id==='string'&&r.id.length<=160&&
  (r.kind==='book'?/^[a-zA-Z0-9._-]+$/.test(r.id)&&!r.id.startsWith('KB-'):/^scene\.[a-z0-9.-]+$/.test(r.id));
 function contract(c){
  const b=c?.scanContract;
  if(!b)return null;
  if(b.version!==1||b.artifactId!=='scan.bundle.'+c.id||c.runeBinding?.runeId!==b.artifactId||
   !['card','world'].includes(b.form)||!Array.isArray(b.members)||!b.members.length||b.members.length>512||
   !b.members.every(validRef)||new Set(b.members.map(r=>r.kind+':'+r.id)).size!==b.members.length)
   throw Error('收納契約格式錯誤，停止收納以保護原物件');
  return b;
 }
 function bundles(storage){return read(storage).objects.filter(c=>c.scanContract).map(c=>({card:c,contract:contract(c)}))}
 function claims(storage,packedOnly=false){
  const found=new Map();
  for(const b of bundles(storage)){
   if(packedOnly&&b.contract.form!=='card')continue;
   for(const ref of b.contract.members){const key=ref.kind+':'+ref.id;if(found.has(key))throw Error('物件有重複收納契約，停止操作');found.set(key,b.card.id)}
  }
  return found;
 }
 function bind(storage,{cardId,revision,members},allowed){
  const s=read(storage),at=s.objects.findIndex(c=>c.id===cardId),c=s.objects[at];
  if(!blank(c)||c.revision!==revision)throw Error('原空白卡已變更，請重新掃描');
  if(!Array.isArray(members)||!members.length||members.length>512)throw Error('沒有可收納的掃描物件');
  const refs=members.map(r=>({kind:r.kind,id:r.id}));
  if(!refs.every(validRef)||new Set(refs.map(r=>r.kind+':'+r.id)).size!==refs.length)throw Error('掃描物件身分無效或重複');
  const available=new Set(allowed.map(r=>r.kind+':'+r.id)),owned=claims(storage);
  if(refs.some(r=>!available.has(r.kind+':'+r.id)||owned.has(r.kind+':'+r.id)))throw Error('掃描物件已離開範圍、被收納或不可用');
  const id='scan.bundle.'+c.id,now=new Date().toISOString();
  const next={...c,title:'掃描收納卡',description:'真實之眼收納 '+refs.length+' 件物件；原始身分保留。',cardType:'收納契約',
   runeBinding:{runeId:id,state:'bound',boundAt:now},scanContract:{version:1,artifactId:id,form:'card',members:refs},revision:c.revision+1,updatedAt:now};
  s.objects[at]=next;storage.setItem(KEY,JSON.stringify(s));return next;
 }
 function setForm(storage,id,revision,form,available){
  const s=read(storage),at=s.objects.findIndex(c=>c.id===id),c=s.objects[at],b=contract(c);
  if(!b||c.revision!==revision||c.status!=='draft'||c.placement!=='hover'||c.containerId||c.ownerBookId||c.containment)throw Error('請將原收納卡取回軌道後重新長按');
  if(!['card','world'].includes(form)||form===b.form)throw Error('收納形態已改變');
  const ids=new Set(available.map(r=>r.kind+':'+r.id));
  if(b.members.some(r=>!ids.has(r.kind+':'+r.id)))throw Error('部分原物件尚未載入，未改變契約');
  claims(storage);
  const next={...c,scanContract:{...b,form},revision:c.revision+1,updatedAt:new Date().toISOString()};
  s.objects[at]=next;storage.setItem(KEY,JSON.stringify(s));return next;
 }
 return Object.freeze({KEY,inRange,read,blank,nearest,contract,bundles,claims,bind,setForm});
});
