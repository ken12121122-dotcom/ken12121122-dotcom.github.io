/* 世界傳送門（World Portal）— 念動圖書館之外的「世界」共用入口與統一返回咒語。
 * 每個世界只登記：id、名稱、進入咒語、isOpen/enter/leave。返回咒語全世界只有一句。
 * 不保存自己的開關狀態：「目前在哪個世界」一律問各世界本身，避免兩邊不同步。
 * 不碰掃描、契約、卡片或存檔。 */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingWorldPortal=api;})(typeof self!=='undefined'?self:this,function(){
'use strict';
// 統一返回咒語：転移解除（てんいかいじょ／ten'i kaijo）。辨識引擎可能回傳漢字、假名或羅馬拼音。
const RETURN_SPELL=Object.freeze({name:'転移解除',reading:'てんいかいじょ',patterns:Object.freeze(['転移解除','てんいかいじょ','テンイカイジョ','tenikaijo','teni kaijo'])});
const ID=/^[a-z][a-z0-9-]{1,31}$/;
function createPortal({onCast=()=>{},onTransition=null}={}){
 const worlds=new Map();
 // A short veil hides the scene swap; without one, switch immediately.
 const transition=typeof onTransition==='function'?onTransition:(_kind,swap)=>swap();
 function current(){for(const w of worlds.values())if(w.isOpen())return w;return null}
 function register(world){
  if(!world||!ID.test(String(world.id)))throw Error('世界 id 無效');
  if(worlds.has(world.id))throw Error('世界已登記：'+world.id);
  if(!world.spell?.name||!Array.isArray(world.spell.patterns)||!world.spell.patterns.length)throw Error('世界缺少進入咒語：'+world.id);
  for(const fn of ['isOpen','enter','leave'])if(typeof world[fn]!=='function')throw Error('世界缺少 '+fn+'：'+world.id);
  const taken=new Set([...RETURN_SPELL.patterns,...[...worlds.values()].flatMap(w=>w.spell.patterns)].map(p=>String(p).toLowerCase()));
  for(const p of world.spell.patterns)if(taken.has(String(p).toLowerCase()))throw Error('咒語重複：'+p);
  worlds.set(world.id,world);return world;
 }
 /* Entering a world you are already in is a no-op with feedback, not a toggle:
  * only the one return spell ever leaves. Another open world is closed first. */
 function enter(id){
  const target=worlds.get(id);if(!target)return 'unknown';
  const here=current();
  onCast({kind:'enter',world:target,spell:target.spell.name,already:here===target});
  if(here===target)return 'already';
  transition('enter',()=>{if(here)here.leave();target.enter()},target);
  return 'entered';
 }
 function back(){
  const here=current();
  onCast({kind:'return',world:here,spell:RETURN_SPELL.name,already:!here});
  if(!here)return 'home';
  transition('return',()=>here.leave(),here);
  return 'returned';
 }
 /* One voice table for every world: book stays separate, each world adds its entry spell. */
 function voiceCommands({book}={}){
  const table={};
  if(book)table.book={patterns:book.patterns,run:()=>{if(!current())book.run()}};
  table.return={patterns:RETURN_SPELL.patterns,run:back};
  for(const w of worlds.values())table['world-'+w.id]={patterns:w.spell.patterns,run:()=>enter(w.id)};
  return table;
 }
 return {register,enter,back,voiceCommands,get current(){return current()},list:()=>[...worlds.values()]};
}
return Object.freeze({RETURN_SPELL,createPortal});
});
