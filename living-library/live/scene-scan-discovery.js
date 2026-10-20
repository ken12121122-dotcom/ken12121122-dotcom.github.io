/* Truth Eye v2: discover live Three.js scene geometry, not a scan allowlist.
 * Legacy references annotate identity/grouping only. Newly added scene meshes are discoverable.
 * Unnamed dynamic objects receive best-effort deterministic geometry identities; their
 * creators should supply userData.scanObjectId for persistent contracts across scene changes.
 */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingSceneScanDiscovery=api})(typeof self!=='undefined'?self:globalThis,function(){
 'use strict';
 const validSceneId=id=>typeof id==='string'&&/^scene\.[a-z0-9.-]+$/.test(id)&&id.length<=160&&id!=='scene.keeper';
 function hash(value){let h=2166136261;for(let i=0;i<value.length;i++){h^=value.charCodeAt(i);h=Math.imul(h,16777619)}return(h>>>0).toString(36)}
 function number(v){return Number.isFinite(v)?Math.round(v*1000)/1000:0}
 function signature(node){
  const out=[node.name||'',node.type||'',...[node.position?.x,node.position?.y,node.position?.z,node.scale?.x,node.scale?.y,node.scale?.z].map(number)];
  let collected=0;
  function visit(o){if(collected>=4)return;
   if(o.isMesh){const g=o.geometry;out.push(o.type||'Mesh',g?.type||'',...[o.position?.x,o.position?.y,o.position?.z,o.scale?.x,o.scale?.y,o.scale?.z].map(number));
    if(g?.parameters)out.push(JSON.stringify(g.parameters));collected++}
   for(const child of o.children||[])if(collected<4)visit(child);
  }
  visit(node);return JSON.stringify(out);
 }
 function sceneRoot(node,scene){while(node&&node.parent&&node.parent!==scene)node=node.parent;return node?.parent===scene?node:null}
 function physical(node){
  if(node?.userData?.scanProtected===true)return false;
  if(node?.isMesh&&node.material?.colorWrite!==false)return true;
  for(const child of node?.children||[])if(physical(child))return true;
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
  const used=new Set(),duplicates=new Map(),result=[];
  // The scene graph is the ONLY discovery source: a new mesh is detected even
  // without registerScene(), object kind tags or an author-maintained list.
  for(const node of scene.children){
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
   const sig=signature(node),key=hash(sig),ordinal=duplicates.get(key)||0;
   duplicates.set(key,ordinal+1);
   const id=validSceneId(authored)?authored:'scene.auto.'+key+(ordinal?'.'+ordinal:'');
   if(used.has('scene:'+id))continue;
   used.add('scene:'+id);
   result.push({id,kind:'scene',group:node,groups:[node],identitySource:validSceneId(authored)?'native':'geometry_fingerprint'});
  }
  return result;
 }
 return Object.freeze({discover,signature,validSceneId});
});
