/* NW-3D-INV-001: authored 3D scene inventory, read-only; no card creation or scene mutation.
 * Counts reflect semantic scene elements in living-library/live/index.html, not every Three.js mesh.
 * New environment elements must be registered here alongside the scene change. */
(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LivingWorldObjectInventory=api;
})(typeof self!=='undefined'?self:globalThis,function(){
 'use strict';
 const GROUPS=Object.freeze([
  {id:'local.rune.origin',name:'初始法陣',type:'法陣',count:1,capability:'conditional',note:'唯一已實作「法陣 ⇄ 原卡」轉換；須先締結契約及封印。'},
  {id:'scene.floor',name:'圖書館地台',type:'建築',count:1},
  {id:'scene.floor.rings',name:'地面同心環紋',type:'場景裝飾',count:13},
  {id:'scene.floor.grid',name:'地面格線',type:'場景裝飾',count:26},
  {id:'scene.shelves',name:'五層書櫃',type:'家具／建築',count:7},
  {id:'scene.pillars',name:'石柱',type:'建築',count:7},
  {id:'scene.lamps',name:'懸掛魔法燈',type:'燈具',count:5},
  {id:'scene.oculus.rings',name:'天穹環飾',type:'建築裝飾',count:2},
  {id:'scene.oculus.spokes',name:'天穹放射飾條',type:'建築裝飾',count:18},
  {id:'scene.keeper',name:'圖書館管理員',type:'角色',count:1}
 ].map(g=>Object.freeze({...g})));
 function runeState(bound,readable=true){
  if(!readable)return '本機卡片資料無法讀取';
  const state=bound?.runeBinding?.state;
  if(state==='sealed')return '已封印，可具現化為原卡';
  if(state==='cardified')return '已具現化為原卡';
  if(state==='bound')return '已締結契約，待封印';
  return '未締結契約';
 }
 function build({catalog,craft,storage}={}){
  const rows=GROUPS.map(g=>({id:g.id,name:g.name,kind:g.type,count:g.count,
   capability:g.capability==='conditional'?'已實作（限定法陣）':'尚未支援',
   state:g.capability==='conditional'?'待讀取契約':'世界中',note:g.note||''}));
  let readable=true,bound=null,localBooks=[],floatingCards=[];
  if(craft&&storage)try{
   bound=craft.getRuneBoundCard(storage);
   localBooks=craft.listBooks(storage);
   floatingCards=craft.floatingCards(storage);
  }catch(_){readable=false}
  rows[0].state=runeState(bound,readable);
  if(catalog&&typeof catalog.buildCatalog==='function'){
   for(const b of catalog.buildCatalog().books){
    rows.push({id:'book:'+b.id,name:b.name,kind:'3D 書本',count:1,
     capability:'不適用（已是書本）',state:b.active?'環繞／可收回書架':'書架中',note:'既有書本不具通用 3D 物件卡片化功能。'});
   }
  }
  for(const b of localBooks){
   rows.push({id:b.id,name:b.title||'未命名書本',kind:'玩家 3D 書本',count:1,
    capability:'不適用（已是書本）',state:b.active===false?'書架中':'世界／書架',note:'本機玩家書本。'});
  }
  for(const c of floatingCards){
   rows.push({id:c.id,name:c.title||'未命名卡片',kind:'玩家 3D 卡片',count:1,
    capability:'不適用（已是卡片）',state:'漂浮中',note:'已是卡片，不應重複卡片化。'});
  }
  return {rows,readable,supported:GROUPS.filter(g=>g.capability==='conditional').length,
   sceneGroups:GROUPS.length,sceneInstances:GROUPS.reduce((n,g)=>n+g.count,0)};
 }
 return Object.freeze({GROUPS,runeState,build});
});
