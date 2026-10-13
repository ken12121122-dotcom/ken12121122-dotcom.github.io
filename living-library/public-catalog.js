/* Public navigation catalog. No private content, no GEN2 capability claims.
 * Restores the 12 book / 7 destination contract from PR #195's earlier version. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingLibraryCatalog=api;})(typeof self!=='undefined'?self:this,function(){
'use strict';
const PUBLIC_BOOKS=[
{id:'research',name:'研究之書',kind:'研究領域',tag:'RESEARCH',icon:'✧',color:'#517f80',description:'研究問題、文獻追蹤、可驗證假設與自主研究歷程。可進入既有研究導航與工作台。',target:'研究導航／文獻工作台',active:true},
{id:'startup',name:'創業之書',kind:'現實計畫',tag:'STARTUP',icon:'◆',color:'#a5764d',description:'公司設立、營業項目、開辦費用、研究成果商業化、授權與退出策略。',target:'創業專案與任務詳情',active:true},
{id:'knowledge',name:'知識世界',kind:'知識管理',tag:'KNOWLEDGE',icon:'✦',color:'#6578a3',description:'將長期累積的知識、流程與技能，以可歸檔、召喚、組合的方式管理。',target:'知識圖譜與書架',active:true},
{id:'missions',name:'任務之書',kind:'執行流程',tag:'MISSIONS',icon:'◎',color:'#9073a5',description:'承載今日工作、本週任務、阻塞因素與任務完成證據。',target:'任務面板／日曆',active:true},
{id:'learning',name:'學習之書',kind:'學習科學',tag:'LEARNING',icon:'❖',color:'#779c80',description:'研究如何學習陌生領域、如何驗證方法有效性，與如何傳遞給其他學習者。',target:'學習研究／教學實驗',active:true},
{id:'finance',name:'資產之書',kind:'財務目標',tag:'ASSETS',icon:'◇',color:'#8a8899',description:'財務自由、被動收入、研究資產授權與未來出售事業的候選規劃。',target:'資產規劃／商業退出',active:true},
{id:'safety',name:'職安典籍',kind:'歷史技能',tag:'SAFETY',icon:'✥',color:'#a46a60',description:'公開工作入口分類；目前連到既有 PackageCanvas，不代表有職安專用 Skill。',target:'歷史技能／工作方法',active:false},
{id:'writing',name:'寫作之書',kind:'工具技能',tag:'WRITING',icon:'✎',color:'#706e99',description:'公開寫作入口分類，目前連到既有 PackageCanvas。',target:'歷史文件／技能模板',active:false},
{id:'design',name:'設計之書',kind:'工具技能',tag:'DESIGN',icon:'✳',color:'#ad9365',description:'公開設計入口分類，目前連到既有 PackageCanvas。',target:'設計技能／成果',active:false},
{id:'teaching',name:'教學之書',kind:'領域技能',tag:'TEACHING',icon:'✺',color:'#578789',description:'公開教學入口分類，可進入研究導航與工作台。',target:'教學方法／課程',active:false}
];
PUBLIC_BOOKS.push(
{id:'gaming',name:'遊戲之書',kind:'原有遊戲功能',tag:'GBA',icon:'♠',color:'#486b80',description:'從圖書館前往現有 GBA 遊戲庫；遊戲存檔與手把操作仍由原有系統負責。',target:'GBA 遊戲庫／存檔',active:true},
{id:'system',name:'系統之書',kind:'裝置與維護',tag:'SYSTEM',icon:'⚙',color:'#77717f',description:'當 3D 功能不足或需要維護時，前往原有網頁控制與系統資訊入口。原生更新及權限仍保留在 Pocket GBA 控制中心。',target:'系統維護／原工作台',active:false},
{id:'gpt',name:'GPT 之書',kind:'AI 知識導航',tag:'GPT ARCHIVE',icon:'✧',color:'#9a7956',description:'通往 ChatGPT Library 的知識檔案、GEN2 正式來源與 AI／Work 協作方式；不公開私有資料。',target:'ChatGPT Library／GEN2 知識路由',active:true}
);
const SITE='https://ken12121122-dotcom.github.io';
// One canonical card = one existing destination. Multiple books may reference it,
// but the collectible mark and displayed name stay unchanged.
const PUBLIC_CARDS=Object.freeze({
research:{id:'research',seal:'✧',number:'01',title:'研究導航',guild:'探索',desc:'研究方法、文獻與心智圖。',url:SITE+'/packagecanvas/research-navigation-v04.html'},
canvas:{id:'canvas',seal:'✦',number:'02',title:'知識工作台',guild:'編纂',desc:'開啟原有 PackageCanvas 知識架構工作台。',url:SITE+'/packagecanvas/'},
startup:{id:'startup',seal:'◆',number:'03',title:'創業計畫',guild:'主線',desc:'公司設立任務、日程與研究事業規劃。',url:SITE+'/packagecanvas/startup.html'},
calendar:{id:'calendar',seal:'◷',number:'04',title:'日曆',guild:'時序',desc:'開啟 Google 日曆；事項需要自行建立與儲存。',url:'https://calendar.google.com/calendar/u/0/r/'},
world:{id:'world',seal:'◎',number:'05',title:'知識世界',guild:'世界',desc:'開啟原有 GBA 風格知識世界與 GEN2 進度畫面。',url:SITE+'/packagecanvas/world.html'},
gba:{id:'gba',seal:'♠',number:'06',title:'GBA 遊戲庫',guild:'遊戲',desc:'開啟現有遊戲庫；存檔及原生操作仍由 Pocket GBA 管理。',url:SITE+'/amin-vault/gba.html'},
vault:{id:'vault',seal:'⚙',number:'07',title:'Pocket Vault',guild:'維護',desc:'回到原有網頁入口；非 Android 原生維護控制中心。',url:SITE+'/amin-vault/'},
gptBridge:{id:'gptBridge',seal:'✧',number:'08',title:'知識庫連結導覽',guild:'GPT／GEN2 連結',desc:'查閱 ChatGPT Library 的路徑、GEN2 正式來源與可複製的 AI 交接方式；不是自動同步功能。',url:'./gpt-library-bridge.html'}
});
const PUBLIC_BOOK_CARDS=Object.freeze({
research:['research','canvas'],
startup:['startup','calendar','canvas'],
knowledge:['world','canvas'],
missions:['startup','world','calendar'],
learning:['research','canvas'],
finance:['startup'],
safety:['canvas'],
writing:['canvas'],
design:['canvas'],
teaching:['research'],
gaming:['gba','vault'],
system:['vault','canvas'],
gpt:['gptBridge']
});

// Canonical public IDs follow public-capabilities.json; legacy IDs are aliases only.
const PUBLIC_IDS={research:'public.research.navigation',canvas:'public.canvas.workbench',startup:'public.startup.tasks',calendar:'public.calendar',world:'public.knowledge.world',gba:'public.gba.runtime',vault:'public.vault.web',gptBridge:'public.gpt.bridge'};
const canonicalPublicId=id=>PUBLIC_IDS[id]||id;
const RESEARCH_ID='KW-STARTUP-PLATFORM-20261013';
function buildCatalog(projection=null){
 const cards=Object.create(null),bookCards=Object.create(null);
 for(const [id,c] of Object.entries(PUBLIC_CARDS))cards[canonicalPublicId(id)]={...c,id:canonicalPublicId(id),legacyId:id,kind:'public_function',sourceLayer:'public',status:'public_entry'};
 cards[RESEARCH_ID]={id:RESEARCH_ID,kind:'external_reference',sourceLayer:'public',title:'創業技術｜平台成長策略',seal:'◆',number:'研01',guild:'外部研究 · 候選',status:'generated',desc:'Book Protocol、專利、開發架構、授權、募資的策略摘要',url:SITE+'/packagecanvas/startup-platform-growth-v01.html'};
 const books=PUBLIC_BOOKS.map(b=>({...b,sourceLayer:'public',kind:'公開功能書',kb_id:null,publicId:b.id}));
 for(const b of books)bookCards[b.id]=PUBLIC_BOOK_CARDS[b.id].map(canonicalPublicId);
 bookCards.startup.push(RESEARCH_ID);
 for(const [id,c] of Object.entries(projection?.cards||{}))cards[id]={...c,sourceLayer:'gen2',seal:c.kind==='skill'?'✦':'◎',number:String(Object.keys(projection.cards).indexOf(id)+1).padStart(2,'0'),guild:c.kind==='skill'?'Skill':'Workflow',desc:c.status,sourceUrl:'https://github.com/ken12121122-dotcom/gen2-knowledge/blob/'+projection.source.commit+'/kb/'+c.sourcePath};
 for(const [i,b] of (projection?.books||[]).entries()){
  // This is a navigation alias, never an alternative kb_id or source registry.
  const publicId=b.id==='KB-STARTUP'?'startup':null;
  const existing=publicId?books.find(x=>x.publicId===publicId):null;
  const value={...b,name:existing?.name||b.name,tag:b.id,sourceLayer:'gen2',kind:existing?'公開入口＋GEN2 正式登記':'GEN2 正式登記',icon:existing?.icon||['✧','◆','✦','◎'][i%4],color:existing?.color||['#517f80','#a5764d','#6578a3','#9073a5'][i%4],target:'公開功能／正式 MD 與 Skill／Workflow',active:existing?.active||false,publicId};
  if(existing){const at=books.indexOf(existing),publicIds=bookCards[existing.id];books[at]=value;delete bookCards[existing.id];bookCards[b.id]=[...new Set([...publicIds,...b.cardIds])];}
  else{books.push(value);bookCards[b.id]=[...b.cardIds];}
 }
 return {books,cards,bookCards};
}
return {buildCatalog,canonicalPublicId};
});
