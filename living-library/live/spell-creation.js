/* In-book authoring only. No postMessage, microphone, remote request or executor. */
(function(){
 'use strict';
 const $=id=>document.getElementById(id),Draft=window.LivingSpellDraft;
 let store,card,revision=0,dirty=false,busy=false;
 function status(text,error=false){$('status').textContent=text;$('status').dataset.state=error?'error':'ready'}
 function controls(){const ready=!!store&&!busy;$('fields').disabled=!ready;$('newDraft').disabled=!ready;$('loadDraft').disabled=!ready||!$('savedDrafts').value;$('savedDrafts').disabled=!ready}
 function show(next,rev){card=next;revision=rev;dirty=false;$('spellName').value=card.name;$('spellDescription').value=card.description;$('incantations').value=card.incantations.join('\n');$('revision').textContent=rev?'本機版本 '+rev:'新草稿'}
 function begin(){show(Draft.create('spell.local.'+crypto.randomUUID()),0);status('未保存草稿 · 尚不可施法')}
 async function refresh(){
  const rows=await store.list(),select=$('savedDrafts'),selected=card?.spell_id;select.replaceChildren();
  for(const row of rows){try{Draft.validate(row.card);if(row.spell_id!==row.card.spell_id||!Number.isSafeInteger(row.local_revision)||row.local_revision<1)continue}catch{continue}
   const option=document.createElement('option');option.value=row.spell_id;option.textContent=row.card.name;select.append(option);
  }
  if(!select.options.length){const option=document.createElement('option');option.value='';option.textContent='尚無可編輯草稿';select.append(option)}
  if([...select.options].some(o=>o.value===selected))select.value=selected;
 }
 function allowDiscard(){return !dirty||window.confirm('捨棄尚未保存的修改？')}
 $('draftForm').addEventListener('input',()=>{dirty=true;status('有未保存修改 · 尚不可施法')});
 $('newDraft').onclick=()=>{if(!busy&&allowDiscard())begin()};
 $('savedDrafts').onchange=controls;
 $('loadDraft').onclick=async()=>{
  if(busy||!allowDiscard())return;busy=true;controls();
  try{const row=await store.get($('savedDrafts').value);if(!row||row.spell_id!==row.card?.spell_id||!Number.isSafeInteger(row.local_revision)||row.local_revision<1)throw Error('INVALID');Draft.validate(row.card);show(row.card,row.local_revision);status('已讀取本機草稿 · 尚不可施法')}
  catch{status('無法讀取此草稿；目前文字保留。',true)}finally{busy=false;controls()}
 };
 $('draftForm').onsubmit=async event=>{
  event.preventDefault();if(busy||!store)return;
  const next={...card,name:$('spellName').value.trim(),description:$('spellDescription').value,incantations:$('incantations').value.split('\n').map(v=>v.trim()).filter(Boolean)};
  try{Draft.validate(next)}catch{status('請填寫名稱與不重複的咒語，並檢查字數上限。草稿尚未保存。',true);return}
  busy=true;controls();
  try{
   const saved=await store.save(next,{expected_revision:revision});show(saved.card,saved.local_revision);status('本機已保存 · 私人同步待授權 · 尚不可施法');
   try{await refresh()}catch{status('本機已保存；草稿清單暫時無法讀取。私人同步待授權。',true)}
  }catch(error){status(error.code==='LOCAL_REVISION_CONFLICT'?'另一個頁面已保存新版。目前修改保留，請先複製備份，再讀取新版。':'保存失敗；目前文字保留，請重試或先複製備份。',true)}finally{busy=false;controls()}
 };
 window.addEventListener('beforeunload',event=>{if(dirty||busy){event.preventDefault();event.returnValue=''}});
 window.addEventListener('pagehide',()=>store?.close());
 window.addEventListener('pageshow',event=>{if(event.persisted)location.reload()});
 (async()=>{
  try{store=await LivingSpellStore.SpellStore.open(Draft.catalog);begin();await refresh();controls()}
  catch{store?.close();store=null;controls();status('無法開啟本機資料；請保留原頁面並稍後重試。不會清除既有創作。',true)}
 })();
})();
