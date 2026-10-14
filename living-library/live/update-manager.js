/* Public Living Library update coordinator. No private sources, credential access or native bridges.
 * Source changes are detected by bypassing HTTP cache on known public assets.
 */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingLibraryUpdates=api;})(typeof self!=='undefined'?self:globalThis,function(){
'use strict';
const BASELINE_KEY='living-library-public-update-baseline-v1';
const PENDING_KEY='living-library-public-reconnect-pending-v1';
const VIEW_KEY='living-library-workbench-v1';
const WATCH_FILES=Object.freeze([
 'index.html','public-catalog.js','book-placement.js','avatar-movement.js',
 'render-capability.js','gen2-editor.js','gpt-skills.html','gpt-library-bridge.html'
]);
function stableCard(c){return {id:c.id,title:c.title||'',desc:c.desc||'',url:c.url||'',kind:c.kind||'',guild:c.guild||'',number:c.number||''};}
function buttonSnapshot(buttons){return Array.from(buttons||[]).filter(b=>b.id&&!b.id.startsWith('llUpdate')).map(b=>({id:b.id,text:(b.textContent||'').trim().replace(/\s+/g,' ').slice(0,100)})).sort((a,b)=>a.id.localeCompare(b.id));}
function makeSnapshot(catalog,buttons,version,fingerprints={}){
 const cards=Object.values(catalog.cards||{}).filter(c=>c.sourceLayer==='public').map(stableCard).sort((a,b)=>a.id.localeCompare(b.id));
 const books=(catalog.books||[]).filter(b=>b.sourceLayer==='public').map(b=>({id:b.id,name:b.name,cardIds:[...(catalog.bookCards[b.id]||[])]})).sort((a,b)=>a.id.localeCompare(b.id));
 return {version:String(version||''),cards,books,buttons:buttonSnapshot(buttons),fingerprints};
}
function checksum(s){let n=2166136261;for(let i=0;i<s.length;i++){n^=s.charCodeAt(i);n=Math.imul(n,16777619);}return (n>>>0).toString(16).padStart(8,'0')+':'+s.length;}
function differences(previous,current){
 if(!previous)return [{type:'info',label:'首次建立更新紀錄',detail:'目前正式書庫已包含 GPT 技能總覽、知識城堡遊戲設計、人物探索與書本顯示功能。先前未留有版本快照，不能判定哪些是首次新增。'}];
 const result=[],previousCards=new Map((previous.cards||[]).map(x=>[x.id,x])),nowCards=new Map((current.cards||[]).map(x=>[x.id,x]));
 for(const [id,c] of nowCards){const before=previousCards.get(id);if(!before)result.push({type:'card',label:'新增卡片',detail:c.title+' · '+id});else if(JSON.stringify(c)!==JSON.stringify(before))result.push({type:'card',label:'更新卡片',detail:c.title+' · '+id});}
 for(const [id,c] of previousCards)if(!nowCards.has(id))result.push({type:'card',label:'移除卡片',detail:c.title+' · '+id});
 const pb=new Map((previous.books||[]).map(b=>[b.id,b]));
 for(const book of current.books||[]){const old=pb.get(book.id);if(!old)result.push({type:'book',label:'新增書籍',detail:book.name});else if(old.name!==book.name||JSON.stringify(old.cardIds)!==JSON.stringify(book.cardIds))result.push({type:'book',label:'書籍卡槽調整',detail:book.name});}
 for(const book of previous.books||[])if(!(current.books||[]).some(b=>b.id===book.id))result.push({type:'book',label:'移除書籍',detail:book.name});
 const oldBtns=new Map((previous.buttons||[]).map(b=>[b.id,b.text]));
 for(const b of current.buttons||[]){if(!oldBtns.has(b.id))result.push({type:'ui',label:'新增按鈕',detail:b.text||b.id});else if(oldBtns.get(b.id)!==b.text)result.push({type:'ui',label:'更新按鈕',detail:b.text||b.id});}
 for(const [id,title] of oldBtns)if(!(current.buttons||[]).some(b=>b.id===id))result.push({type:'ui',label:'調整按鈕',detail:title||id});
 const prevHashes=previous.fingerprints||{},currHashes=current.fingerprints||{};
 if(Object.keys(currHashes).some(path=>prevHashes[path]!==undefined&&prevHashes[path]!==currHashes[path])){
  const changed=Object.keys(currHashes).filter(path=>prevHashes[path]!==undefined&&prevHashes[path]!==currHashes[path]);
  const ui=changed.filter(p=>p==='index.html'||p==='book-placement.js'||p==='avatar-movement.js'||p==='render-capability.js'||p==='gen2-editor.js');
  const cardPages=changed.filter(p=>p.endsWith('.html')&&p!=='index.html');
  if(ui.length)result.push({type:'ui',label:'介面／場景更新',detail:'按鈕配置、排版或 3D 操作程式已重新連結'});
  for(const path of cardPages)result.push({type:'card',label:'卡片網頁更新',detail:path.replace(/^.*\//,'')});
 }
 if(previous.version!==current.version&&!result.some(x=>x.type==='ui'))result.push({type:'ui',label:'網頁版本更新',detail:'介面版本 '+current.version});
 return result.length?result:[{type:'info',label:'已重新連結',detail:'已取得最新書籍、卡片及網頁介面，目前沒有可辨識的新異動。'}];
}
function read(storage,key){try{const value=JSON.parse(storage.getItem(key)||'null');return value&&typeof value==='object'?value:null}catch(_){return null}}
function write(storage,key,value){try{storage.setItem(key,JSON.stringify(value));return true}catch(_){return false}}
function resetView(storage){try{storage.removeItem(VIEW_KEY);return true}catch(_){return false}}
function reconnectUrl(href,millis){
 const url=new URL(href);
 if(url.pathname!=='/living-library/live/index.html')throw Error('不是正式 live 頁面，拒絕變更入口');
 // Android Bridge 107 only permits ?v=<digits>, not additional query parameters.
 url.search='?v='+String(Math.floor(millis));
 url.hash='';
 return url.toString();
}
async function fetchFingerprints(fetcher,baseUrl,extra=[]){
 const result={},files=[...new Set([...WATCH_FILES,...extra])];
 await Promise.all(files.map(async file=>{
  if(!/^(?:[a-z\d][a-z\d_-]*\/)*[a-z\d][a-z\d_.-]*\.(?:html|js)$/i.test(file)||file.includes('..'))return;
  const url=new URL(file,baseUrl);if(url.origin!==new URL(baseUrl).origin)return;
  url.search='?ll_check='+Date.now();
  const res=await fetcher(url.toString(),{cache:'no-store',credentials:'omit',redirect:'error'});
  if(!res.ok)throw Error('更新檢查 HTTP '+res.status+'：'+file);
  result[file]=checksum(await res.text());
 }));
 return result;
}
function changed(a,b){return Object.keys(b||{}).some(k=>a?.[k]!==b[k]);}
return {BASELINE_KEY,PENDING_KEY,VIEW_KEY,WATCH_FILES,makeSnapshot,differences,checksum,read,write,resetView,reconnectUrl,fetchFingerprints,changed};
});
