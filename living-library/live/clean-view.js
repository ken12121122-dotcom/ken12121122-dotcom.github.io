// Presentation preference only. No scene, book, card or player-save mutation.
(()=>{
 'use strict';
 const button=document.getElementById('cleanViewToggle');
 const menu=document.getElementById('libraryQuickMenu');
 const action=document.getElementById('cleanViewAction');
 const build=document.getElementById('permanentBuildToggle');
 const key='nw-library-clean-view-v1';
 let clean=false;
 try{clean=localStorage.getItem(key)==='true'}catch{}
 function setMenu(open){
  menu.hidden=!open;
  button.setAttribute('aria-expanded',String(open));
 }
 function apply(){
  document.body.classList.toggle('clean-view',clean);
  action.setAttribute('aria-pressed',String(clean));
  const label=clean?'顯示操作介面':'開啟乾淨畫面';
  action.setAttribute('aria-label',label);action.title=label;
  action.textContent=clean?'▤':'◌';
 }
 button.addEventListener('click',()=>setMenu(menu.hidden));
 action.addEventListener('click',()=>{
  clean=!clean;apply();setMenu(false);
  try{localStorage.setItem(key,String(clean))}catch{}
 });
 // Building remains an existing world action; this menu only relocates its entry.
 build.addEventListener('click',()=>setMenu(false));
 document.addEventListener('pointerdown',event=>{
  if(!menu.hidden&&!menu.contains(event.target)&&event.target!==button)setMenu(false);
 });
 document.addEventListener('keydown',event=>{
  if(event.key!=='Escape')return;
  if(!menu.hidden){setMenu(false);return}
  if(clean){
   clean=false;apply();
   try{localStorage.setItem(key,'false')}catch{}
  }
 });
 apply();
})();
