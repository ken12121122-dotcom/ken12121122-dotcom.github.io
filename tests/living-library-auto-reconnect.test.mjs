import test from 'node:test';
import vm from 'node:vm';
import assert from 'node:assert/strict';
import {readFile,access} from 'node:fs/promises';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const root=new URL('../',import.meta.url);
const Updates=require('../living-library/live/update-manager.js');
const Catalog=require('../living-library/live/public-catalog.js');
const shell=await readFile(new URL('living-library/live/index.html',root),'utf8');

function store(values={}){const data=new Map(Object.entries(values));return {
 getItem:k=>data.has(k)?data.get(k):null,
 setItem:(k,v)=>data.set(k,v),
 removeItem:k=>data.delete(k),
 keys:()=>[...data.keys()]
}}
function snapshot(){const catalog=Catalog.buildCatalog();return Updates.makeSnapshot(catalog,[{id:'homeBtn',textContent:'⌂ 書庫首頁'},{id:'gameBookShortcut',textContent:'♜ 遊戲之書・新卡'}],'v1',{'index.html':'a','public-catalog.js':'b'});}

test('every existing public card is included without private GEN2 or duplicate card entries',()=>{
 const before=snapshot();
 assert.equal(before.cards.filter(c=>c.id==='public.gpt.skills').length,1);
 assert.equal(before.books.find(b=>b.id==='gpt').cardIds.length,2);
 assert.equal(before.books.find(b=>b.id==='system').cardIds.length,6);
 assert.equal(before.cards.filter(c=>c.id==='public.gba.runtime').length,1);
 assert.equal(before.cards.length,Object.keys(Catalog.buildCatalog().cards).length);
 const changed=Catalog.buildCatalog();
 changed.cards['SK-PRIVATE']={id:'SK-PRIVATE',sourceLayer:'gen2',title:'private token',desc:'secret'};
 assert.equal(Updates.makeSnapshot(changed,[], 'v2').cards.some(c=>c.id==='SK-PRIVATE'),false);
});
test('diff lists added/changed/deleted cards, books, UI buttons and source changes',()=>{
 const old=snapshot(),now=structuredClone(old);
 now.cards.push({id:'test.card',title:'新增測試卡',desc:'',url:'./test.html',guild:'',kind:'public_function',number:''});
 now.cards.find(c=>c.id==='public.gpt.skills').title='GPT 技能總覽新版';
 now.cards=now.cards.filter(c=>c.id!=='public.vault.web');
 now.books.find(b=>b.id==='gpt').cardIds.push('test.card');
 now.buttons.push({id:'newBtn',text:'⌁ 更新功能'});
 now.buttons.find(x=>x.id==='homeBtn').text='⌂ 回到書庫';
 now.version='v2';now.fingerprints['index.html']='changed';
 now.fingerprints['gpt-skills.html']='x';
 const entries=Updates.differences(old,now);
 for(const term of ['新增卡片','更新卡片','移除卡片','書籍卡槽調整','新增按鈕','更新按鈕','介面／場景更新'])assert.ok(entries.some(e=>e.label===term),term);
 assert.ok(entries.some(e=>e.detail.includes('GPT 技能總覽新版')));
 const again=Updates.differences(now,structuredClone(now));
 assert.equal(again[0].label,'已重新連結');
});
test('first setup cannot falsely claim old card history was measured',()=>{
 const entries=Updates.differences(null,snapshot());
 assert.equal(entries[0].label,'首次建立更新紀錄');
 assert.match(entries[0].detail,/不能判定/);
});
test('reset wipes only book view preferences, never GBA, GEN2 or credentials',()=>{
 const saved=store({
 [Updates.VIEW_KEY]:'old book state',
 'gba.save.guard':'KEEP_ME',
 'packagecanvas-gen2-github-v1':'KEEP_ME',
 'google.private.document':'KEEP_ME'
 });
 assert.equal(Updates.resetView(saved),true);
 assert.equal(saved.getItem(Updates.VIEW_KEY),null);
 for(const k of ['gba.save.guard','packagecanvas-gen2-github-v1','google.private.document'])
  assert.equal(saved.getItem(k),'KEEP_ME');
});
test('Android remote-first policy only allows numeric ?v= query on reload',()=>{
 const url=Updates.reconnectUrl('https://ken12121122-dotcom.github.io/living-library/live/index.html?book=gpt',1728924340111);
 assert.equal(url,'https://ken12121122-dotcom.github.io/living-library/live/index.html?v=1728924340111');
 assert.match(new URL(url).search,/^\?v=\d+$/);
 assert.throws(()=>Updates.reconnectUrl('https://other.com/index.html',123),/正式 live/);
});
test('remote signatures bypass caches for all public UI assets and local card pages',async()=>{
 let urls=[];
 const fetcher=async(url,opts)=>{urls.push([url,opts]);return {ok:true,text:async()=>url.includes('public-catalog')?'new contents':'same'}};
 const signatures=await Updates.fetchFingerprints(fetcher,'https://ken12121122-dotcom.github.io/living-library/live/index.html',['gpt-skills.html','../private']);
 assert.ok(urls.length>=8);
 assert.equal(urls.filter(([url])=>url.includes('gpt-skills.html')).length,1);
 for(const [url,opts] of urls){
  assert.equal(opts.cache,'no-store');
  assert.equal(opts.credentials,'omit');
  assert.ok(new URL(url).searchParams.has('ll_check'));
  assert.ok(new URL(url).pathname.startsWith('/living-library/live/'));
 }
 assert.ok(!Object.keys(signatures).some(x=>x.includes('..')));
 assert.equal(Updates.changed(signatures,{...signatures,'index.html':'changed'}),true);
 assert.equal(Updates.changed(signatures,structuredClone(signatures)),false);
});
test('magic-book update dialog and automatic reconnect preserve player layout',async()=>{
 assert.doesNotMatch(shell,/id="llUpdateReconnect"/);
 assert.match(shell,/id="llUpdateDialog"/);
 assert.match(shell,/reconnectNow\(\)/);
 assert.match(shell,/id="llUpdateDialog"/);
 assert.match(shell,/class="ll-update-dialog"/);
 assert.match(shell,/id="llUpdateList"/);
 assert.match(shell,/更新會保留你的書本選取、環繞及書架配置/);
 assert.match(shell,/不會清除 GBA 存檔/);
 assert.match(shell,/window\.location\.replace\(Updates\.reconnectUrl\(location\.href,Date\.now\(\)\)\)/);
 assert.match(shell,/const scripts=\['book-placement\.js','avatar-movement\.js','public-catalog\.js'/);
 assert.match(shell,/document\.write\('<scr'\+'ipt src="'/);
 assert.match(shell,/async function refreshWebVersion\(initial=false\)/);
 assert.match(shell,/setInterval\(\(\)=>\{if\(!document\.hidden\)refreshWebVersion\(\)\},180000\)/);
 assert.doesNotMatch(shell,/Updates\.resetView\(localStorage\)|resetLibraryView/);
 assert.match(shell,/window\.LivingLibrary=\{books:BOOKS,select,openBook/);
 await access(new URL('living-library/live/update-manager.js',root));
 const scripts=[...shell.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)];
 assert.ok(scripts.length>=4);
 for(const [i,m] of scripts.entries())assert.doesNotThrow(()=>new Function(m[1].includes('module')?'return async function(){'+m[2]+'}':m[2]),'inline '+i);
});

// Execute the production watchdog with storage and DOM doubles, including a reload.
test('first visit, changed release and pending reconnect keep saved book state byte-for-byte',async()=>{
 const bootstrap=shell.slice(shell.indexOf('const Updates=window.LivingLibraryUpdates;'),shell.indexOf('const gptSkillsReady='));
 const watchdog=shell.slice(shell.indexOf(' // Release watchdog:'),shell.indexOf('  // Existing direct links'));
 for(const scenario of ['first','changed','pending']){
  const layout=JSON.stringify({selectedId:'gaming',known:['research','gaming'],active:['gaming']});
  const localStorage=store({[Updates.VIEW_KEY]:layout,'living-library-controls-v1':'{"cardSize":0.8}'});
  const sessionStorage=store();
  if(scenario!=='first')Updates.write(localStorage,Updates.BASELINE_KEY,{...snapshot(),version:'old'});
  if(scenario==='pending')Updates.write(sessionStorage,Updates.PENDING_KEY,{previous:snapshot()});
  let reload=null;
  const elements=new Map();
  const $=id=>{if(!elements.has(id))elements.set(id,{open:false,textContent:'',classList:{contains:()=>false},addEventListener(){},replaceChildren(){},append(){},showModal(){this.open=true;}});return elements.get(id)};
  const context=vm.createContext({URL,Date,console,localStorage,sessionStorage,$,
   window:{LivingLibraryUpdates:{...Updates,fetchFingerprints:async()=>({'index.html':'new'})},location:{href:'https://ken12121122-dotcom.github.io/living-library/live/index.html',replace:u=>reload=u}},
   location:{href:'https://ken12121122-dotcom.github.io/living-library/live/index.html'},
   document:{hidden:false,querySelector:()=>({content:'new'}),querySelectorAll:()=>[],createElement:()=>({append(){}}),addEventListener(){}},
   initialCatalog:Catalog.buildCatalog(),syncScene(){},toast(){},setTimeout(){},setInterval(){}});
  context.window.fetch=()=>{};
  vm.runInContext(bootstrap+watchdog,context);
  await vm.runInContext('refreshWebVersion(true)',context);
  assert.equal(localStorage.getItem(Updates.VIEW_KEY),layout,scenario+' notice');
  assert.equal($('llUpdateDialog').open,true);
  assert.equal(sessionStorage.getItem(Updates.PENDING_KEY),null);
  vm.runInContext('reconnectNow()',context);
  assert.equal(localStorage.getItem(Updates.VIEW_KEY),layout,scenario+' reload');
  assert.equal(localStorage.getItem('living-library-controls-v1'),'{"cardSize":0.8}');
  assert.match(reload,/\?v=\d+$/);
 }
});
