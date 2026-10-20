/* Truth Eye v2: discover live Three.js scene geometry, not a scan allowlist.
 * Legacy references annotate identity/grouping only. Newly added scene meshes are discoverable.
 * Unnamed dynamic objects receive best-effort deterministic geometry identities; their
 * creators should supply userData.scanObjectId for persistent contracts across scene changes.
 */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingSceneScanDiscovery=api})(typeof self!=='undefined'?self:globalThis,function(){
 'use strict';
 const validSceneId=id=>typeof id==='string'&&/^scene\.[a-z0-9.-]+$/.test(id)&&id.length<=160&&id!=='scene.keeper';
 const identityCache=new WeakMap(); // Same runtime node keeps its reference across motion and animation.
 function hash(value){let h=2166136261;for(let i=0;i<value.length;i++){h^=value.charCodeAt(i);h=Math.imul(h,16777619)}return(h>>>0).toString(36)}
 function number(v){return Number.isFinite(v)?Math.round(v*1000)/1000:0}
 function signature(node){
  // Object transforms are mutable during movement, spells and animation.
  // Identity must depend on structural geometry, not the current position.
  const out=[node.name||'',node.type||''];
  let collected=0;
  function visit(o){if(collected>=4)return;
   if(o.isMesh){const g=o.geometry;out.push(o.type||'Mesh',g?.type||'');
    if(g?.parameters)out.push(...Object.entries(g.parameters).filter(([,v])=>['number','string','boolean'].includes(typeof v)).slice(0,16).map(([k,v])=>k+':'+v));
    collected++}
   for(const child of o.children||[])if(collected<4)visit(child);
  }
  visit(node);return JSON.stringify(out);
 }
 function sceneRoot(node,scene){while(node&&node.parent&&node.parent!==scene)node=node.parent;return node?.parent===scene?node:null}
 function physical(node){
  if(!node||node.visible===false||node.userData?.scanProtected===true)return false;
  // Invisible templates and helper geometries are not physical scan targets.
  if(node.isMesh&&node.material?.visible!==false&&node.material?.colorWrite!==false)return true;
  for(const child of node.children||[])if(physical(child))return true;
  return false;
 }
 function discover({scene,keeper,legacyScenes=[],books=[],atlasEntries=[],floatingCards=[],protectedRoots=[]}={}){
  if(!scene?.children||!keeper)throw Error('3D 場景或玩家錨點缺失，不能掃描');
  const protectedSet=new Set([keeper,...protectedRoots,...floatingCards.map(c=>c?.group).filter(Boolean)]);
  // Existing authoring metadata preserves old card IDs, but is NOT an input allowlist.
  const metadata=new Map();
  for(const o of legacyScenes){if(!o?.id||o.id==='scene.keeper')continue;
   for(const g of o.groups||[o.group]){const r=sceneRoot(g,scene);if(r&&!metadata.has(r))metadata.set(r,{kind:'scene',item:o})}
  }
  for(const o of books){const r=sceneRoot(o.group,scene);if(r)metadata.set(r,{kind:'book',item:o})}
  const alternate=new Map(atlasEntries.map(e=>[e.id,e.group]));
  const used=new Set(),result=[];
  // The scene graph is the ONLY discovery source: a new mesh is detected even
  // without registerScene(), object kind tags or an author-maintained list.
  for(const [rootIndex,node] of scene.children.entries()){
   if(protectedSet.has(node)||node.userData?.scanProtected||node.userData?.localCardId||node.userData?.atlasId||
      node.isCamera||node.isLight||node.isPoints||node.isLine||node.isSprite||!physical(node))continue;
   const hint=metadata.get(node);
   if(hint?.item&&used.has(hint.kind+':'+hint.item.id))continue;
   if(hint?.kind==='book'){
    const o=hint.item,alt=alternate.get(o.id);
    result.push({...o,kind:'book',group:alt?.visible?alt:o.group,groups:alt?[o.group,alt]:[o.group]});
    used.add('book:'+o.id);continue;
   }
   if(hint?.kind==='scene'){
    const o=hint.item;
    result.push({...o,kind:'scene',groups:(o.groups||[o.group]).filter(Boolean)});
    used.add('scene:'+o.id);continue;
   }
   const authored=node.userData?.scanObjectId;
   // Deterministic hierarchy slot is the fallback for unregistered geometry.
   // Avoid recalculating from live transforms on every scan tick.
   const id=validSceneId(authored)?authored:(identityCache.get(node)||'scene.auto.'+hash(signature(node))+'.'+rootIndex);
   if(!validSceneId(authored))identityCache.set(node,id);
   if(used.has('scene:'+id))continue;
   used.add('scene:'+id);
   result.push({id,kind:'scene',group:node,groups:[node],identitySource:validSceneId(authored)?'native':'geometry_fingerprint'});
  }
  return result;
 }
 return Object.freeze({discover,signature,validSceneId});
});
