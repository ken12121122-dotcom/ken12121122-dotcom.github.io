// Presentation preference only. No scene, book, card or player-save mutation.
(()=>{
 'use strict';
 const button=document.getElementById('cleanViewToggle');
 const key='nw-library-clean-view-v1';
 let clean=false;
 try{clean=localStorage.getItem(key)==='true'}catch{}
 function apply(){
  document.body.classList.toggle('clean-view',clean);
  button.setAttribute('aria-pressed',String(clean));
  const label=clean?'顯示操作介面':'開啟乾淨畫面';
  button.setAttribute('aria-label',label);button.title=label;
  button.textContent=clean?'▤':'◌';
 }
 button.addEventListener('click',()=>{
  clean=!clean;apply();
  try{localStorage.setItem(key,String(clean))}catch{}
 });
 // Escape restores navigation without consuming the existing reader/dialog keys.
 document.addEventListener('keydown',event=>{
  if(event.key==='Escape'&&clean){
   clean=false;apply();
   try{localStorage.setItem(key,'false')}catch{}
  }
 });
 apply();
})();
