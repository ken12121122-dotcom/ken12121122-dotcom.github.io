/* Safe read-only world observation. Never enumerate localStorage or send credentials.
 * Explicit game-state schema; no restores, no file uploads, no frame data.
 */
(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LivingWorldObservation=api;
})(typeof self!=='undefined'?self:globalThis,function(){
 'use strict';
 const SCHEMA='nw.world-observation.v1';
 const KEYS=Object.freeze({
  books:'living-library-workbench-v1',
  controls:'living-library-controls-v1',
  order:'living-library-book-card-order-v1',
  truthEye:'living-truth-eye-scan-v1'
 });
 const limited=(n,min,max)=>typeof n==='number'&&Number.isFinite(n)&&n>=min&&n<=max?n:null;
 const round=n=>Math.round(n*100)/100;
 const position=(p,range=100)=>p&&typeof p==='object'&&
  ['x','y','z'].every(k=>limited(p[k],-range,range)!==null)?
  {x:round(p.x),y:round(p.y),z:round(p.z)}:null;
 const ident=s=>typeof s==='string'&&s.length<=160&&/^[a-zA-Z0-9._-]+$/.test(s)?s:null;
 function safeRead(storage,key){
  const raw=storage.getItem(key);
  if(raw===null)return {exists:false,value:null};
  if(raw.length>128000)throw Error('WORLD_OBSERVATION_LOCAL_TOO_LARGE');
  let data;try{data=JSON.parse(raw)}catch{throw Error('WORLD_OBSERVATION_LOCAL_INVALID')}
  if(!data||typeof data!=='object'||Array.isArray(data))
   throw Error('WORLD_OBSERVATION_LOCAL_INVALID');
  return {exists:true,value:data};
 }
 function pickControls(source){
  const result={};
  if(!source)return result;
  for(const key of ['rotationEnabled','joystickEnabled','cardReorder','singleFingerRotate'])
   if(typeof source[key]==='boolean')result[key]=source[key];
  for(const [key,allowed] of Object.entries({
    rotationMode:['single','dual'],joystickMode:['follow','fixed']
  }))if(allowed.includes(source[key]))result[key]=source[key];
  for(const [key,min,max] of [
    ['cardSize',.5,2],['cardOrbitDistance',.5,2],['cardOrbitSpeed',0,3],
    ['cardSummonDuration',100,10000],['atlasDistance',0,10],
    ['atlasHeight',0,10],['atlasSize',.1,3],['atlasSeparation',0,5],
    ['atlasFocusDistance',.1,3],['atlasCardSize',.1,3]
  ])if(limited(source[key],min,max)!==null)result[key]=round(source[key]);
  const stick=source.joystickPosition;
  if(stick&&limited(stick.x,0,1)!==null&&limited(stick.y,0,1)!==null)
   result.joystickPosition={x:round(stick.x),y:round(stick.y)};
  return result;
 }
 function pickBookPrefs(source){
  const out={};if(!source)return out;
  if(ident(source.selectedId))out.selectedId=source.selectedId;
  if(Array.isArray(source.active))out.active=[...new Set(source.active.map(ident).filter(Boolean))].slice(0,75);
  if(Array.isArray(source.known))out.known=[...new Set(source.known.map(ident).filter(Boolean))].slice(0,75);
  return out;
 }
 function pickOrder(source){
  const out={};if(!source)return out;
  for(const [key,value] of Object.entries(source).slice(0,90)){
   if(!ident(key)||!Array.isArray(value))continue;
   out[key]=[...new Set(value.map(ident).filter(Boolean))].slice(0,100);
  }
  return out;
 }
 function pickTruthEye(source){
  const out={};if(!source)return out;
  for(const [key,min,max] of [['radius',2,20],['aperture',15,360],['direction',0,360]])
   if(limited(source[key],min,max)!==null)out[key]=round(source[key]);
  if(['both','radar','ripple'].includes(source.mode))out.mode=source.mode;
  if(typeof source.enabled==='boolean')out.enabled=source.enabled;
  return out;
 }
 function sceneProjection(value){
  if(value==null)return {ready:false};
  if(typeof value!=='object'||value.ready!==true)throw Error('WORLD_OBSERVATION_SCENE_INVALID');
  const p=position(value.player),angle=value.camera||{};
  if(!p)throw Error('WORLD_OBSERVATION_SCENE_INVALID');
  const camera={};
  for(const [key,min,max] of [['theta',-100,100],['phi',-10,10],['distance',0,100]])
   if(limited(angle[key],min,max)!==null)camera[key]=round(angle[key]);
  const books=[],sceneObjects=[];
  for(const b of (Array.isArray(value.books)?value.books:[]).slice(0,90)){
   if(!ident(b?.id))continue;
   books.push({id:b.id,active:b.active===true,
    sourceLayer:['public','local'].includes(b.sourceLayer)?b.sourceLayer:'other'});
  }
  for(const o of (Array.isArray(value.sceneObjects)?value.sceneObjects:[]).slice(0,150)){
   if(!ident(o?.id)||!o.id.startsWith('scene.')||o.id==='scene.keeper')continue;
   const at=position(o.position);if(at)sceneObjects.push({id:o.id,position:at});
  }
  return {ready:true,player:p,camera,books,sceneObjects};
 }
 function collect(storage,sceneSnapshot=()=>null){
  if(!storage||typeof storage.getItem!=='function')throw Error('WORLD_OBSERVATION_STORAGE_MISSING');
  const rawBooks=safeRead(storage,KEYS.books);
  const rawControls=safeRead(storage,KEYS.controls);
  const rawOrder=safeRead(storage,KEYS.order);
  const rawEye=safeRead(storage,KEYS.truthEye);
  const observed=sceneProjection(typeof sceneSnapshot==='function'?sceneSnapshot():null);
  // No timestamp: unchanged positions/preferences produce identical payloads.
  return {schema:SCHEMA,source:'living-library/live',session_scene:observed,
   saved:{books:pickBookPrefs(rawBooks.value),controls:pickControls(rawControls.value),
    card_order:pickOrder(rawOrder.value),truth_eye_scan:pickTruthEye(rawEye.value)},
   present_keys:Object.fromEntries(Object.entries(KEYS).map(([name])=>
    [name,{books:rawBooks,controls:rawControls,order:rawOrder,truthEye:rawEye}[name].exists]))};
 }
 return Object.freeze({SCHEMA,KEYS,collect,sceneProjection});
});
