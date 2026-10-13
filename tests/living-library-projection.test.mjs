import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
const require=createRequire(import.meta.url), G=require('../packagecanvas/gen2-source.js');
const source=()=>({key:'github:ken12121122-dotcom/gen2-knowledge@main',ref:'main',commit:'a'.repeat(40),files:[]});
const doc=(path,type,fields,body='')=>({path,text:`---\ndocument_type: ${type}\n${fields}\n---\n# Synthetic fixture\n${body}`});
function fixture(){const s=source();s.files.push({path:'00_ENTRY/KB_REGISTRY.md',text:'---\ntype: registry\nstatus: active\n---\n| kb_id | 名稱 | 一句用途 | 狀態 | 資料夾 |\n|---|---|---|---|---|\n| KB-TEST | 測試 | 隔離測試 | active | KB-TEST_測試 |'});s.files.push(doc('02_KNOWLEDGE_BASES/KB-TEST_測試/KB_CONTRACT.md','kb_contract','kb_id: KB-TEST\nstatus: draft'));return s;}
const addSkill=(s,id='SK-TEST-001',extra='')=>s.files.push(doc('02_KNOWLEDGE_BASES/KB-TEST_測試/05_SKILLS/'+id+'.md','skill','skill_id: '+id+'\nkb_id: KB-TEST\nstatus: draft\n'+extra));
test('registered main KB can be an empty book; main draft status is preserved',()=>{const p=G.buildLibraryProjection(fixture());assert.equal(p.books.length,1);assert.equal(p.books[0].status,'draft');assert.deepEqual(p.books[0].cardIds,[])});
test('new skill and new registered KB appear on next projection without UI definitions',()=>{const s=fixture();addSkill(s);assert.deepEqual(G.buildLibraryProjection(s).books[0].cardIds,['SK-TEST-001']);s.files[0].text+='\n| KB-SECOND | 第二 | 測試 | active | KB-SECOND_第二 |';s.files.push(doc('02_KNOWLEDGE_BASES/KB-SECOND_第二/KB_CONTRACT.md','kb_contract','kb_id: KB-SECOND\nstatus: draft'));assert.equal(G.buildLibraryProjection(s).books.length,2)});
test('PR, incomplete sources and missing registry fail closed',()=>{for(const patch of [{ref:'candidate'},{key:'github:x/y@main'},{truncated:true},{commit:'invalid'},{files:[]}])assert.throws(()=>G.buildLibraryProjection({...fixture(),...patch}))});
test('unregistered, candidate and generated-review files never masquerade as formal',()=>{const s=fixture();addSkill(s,'SK-TEST-001','review_status: generated');addSkill(s,'SK-TEST-002','status: draft_candidate');s.files.push(doc('02_KNOWLEDGE_BASES/KB-OTHER_其他/KB_CONTRACT.md','kb_contract','kb_id: KB-OTHER'));const p=G.buildLibraryProjection(s);assert.equal(Object.keys(p.cards).length,0);assert.equal(p.books.length,1);assert.ok(p.diagnostics.some(d=>d.id==='KB-OTHER'))});
test('identity collision is excluded instead of silently overriding a card',()=>{const s=fixture();addSkill(s);s.files.push({...s.files.at(-1),path:'02_KNOWLEDGE_BASES/KB-TEST_測試/05_SKILLS/duplicate.md'});assert.equal(Object.keys(G.buildLibraryProjection(s).cards).length,0)});
test('cross-book references share canonical IDs and unresolved BOM references are gaps',()=>{const s=fixture();addSkill(s);s.files[0].text+='\n| KB-SECOND | 第二 | 測試 | active | KB-SECOND_第二 |';s.files.push(doc('02_KNOWLEDGE_BASES/KB-SECOND_第二/KB_CONTRACT.md','kb_contract','kb_id: KB-SECOND\nstatus: draft'));s.files.push(doc('02_KNOWLEDGE_BASES/KB-SECOND_第二/03_WORKFLOW_BOM/BOM.md','workflow_bom','kb_id: KB-SECOND','[[SK-TEST-001]] [[SK-MISSING-001]]'));const p=G.buildLibraryProjection(s);assert.equal(Object.keys(p.cards).length,1);assert.equal(p.books[1].cardIds[0],p.books[0].cardIds[0]);assert.ok(p.diagnostics.some(d=>d.id==='SK-MISSING-001'))});
test('library preserves public catalog without persistent private token writes or native bridge',async()=>{const html=await readFile(new URL('../living-library/index.html',import.meta.url),'utf8');assert.ok(html.includes('public-catalog.js'));assert.ok(!html.includes('AminPackageCanvasFiles'));assert.ok(!/localStorage\.setItem\([^\n]*token/.test(html));assert.ok(html.includes('content.textContent=card.body'));assert.ok(html.includes('GEN2.buildLibraryProjection(source)'));assert.ok(html.includes('setInterval'));assert.ok(html.includes('Math.max(6,ids.length)'))});

test('UI authorization → book → card → scrollable source → workbench → back → disconnect',async()=>{
 const {runInNewContext}=await import('node:vm');
 const html=await readFile(new URL('../living-library/index.html',import.meta.url),'utf8');
 class Element{
 constructor(){this.children=[];this.style={};this.dataset={};this.hidden=false;this.className='';this.listeners={};this.attributes={};this.value='';this.textContent='';this.classList={add:x=>{this.className+=' '+x},remove:x=>{this.className=this.className.split(' ').filter(c=>c!==x).join(' ')},contains:x=>this.className.split(' ').includes(x),toggle:(x,force)=>{const yes=force??!this.classList.contains(x);yes?this.classList.add(x):this.classList.remove(x);return yes}};}
 append(...x){this.children.push(...x)}replaceChildren(...x){this.children=x}
 setAttribute(k,v){this.attributes[k]=v}removeAttribute(k){delete this.attributes[k];if(k==='src')delete this.src}
 addEventListener(k,fn){this.listeners[k]=fn}focus(){}showModal(){this.open=true}close(){this.open=false}
 querySelector(){return this.children[0]||null}querySelectorAll(){return this.children}
 click(){this.onclick?.({target:this});this.listeners.click?.({target:this})}
 }
 const elements=new Map();const get=id=>{if(!elements.has(id))elements.set(id,new Element());return elements.get(id)};
 get('readerShell').hidden=true;get('editorPane').hidden=true;const drawer=new Element();get('drawerWrap').append(drawer);
 const doc={getElementById:get,createElement:()=>new Element(),addEventListener(){},hidden:false,body:new Element()};
 let current=fixture();addSkill(current);current.files.at(-1).text+='\nPrivate synthetic text <script>unsafe()</script>';
 const window={LivingLibraryCatalog:require('../living-library/public-catalog.js'),location:{href:'https://preview.example/living-library/index.html'},LivingLibraryDrafts:{...require('../living-library/gen2-editor.js'),submitDraft:async(draft,token)=>{assert.equal(token,'write-fixture');assert.equal(draft.review_status,'generated');return {url:'https://github.com/ken12121122-dotcom/gen2-knowledge/pull/999'}}},PackageCanvasGen2:{...G,sources:{github:{...G.sources.github,collect:async()=>current}}},addEventListener(){}};
 const store=new Map(),ctx={window,document:doc,localStorage:{getItem:k=>store.get(k),setItem:(k,v)=>store.set(k,v)},setInterval(){},setTimeout(){},clearTimeout(){},URL,console,AbortController};
 const script=[...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)].find(m=>m[1].includes('const BOOKS='))[1];runInNewContext(script,ctx);
 assert.equal(window.LivingLibrary.books.length,12);
 const selectBook=id=>{window.LivingLibrary.select(id);window.LivingLibrary.openBook()};
 const clickCard=id=>{const slot=get('drawerFunctions').children.find(b=>b.dataset.cardId===window.LivingLibraryCatalog.canonicalPublicId(id));assert.ok(slot,id+' is reachable');slot.click()};
 for(const [b,id,url] of [['research','research','/research-navigation-v04.html'],['startup','startup','/startup.html'],['knowledge','world','/world.html'],['gaming','gba','/gba.html'],['system','vault','/amin-vault/'],['research','canvas','/packagecanvas/'],['startup','KW-STARTUP-PLATFORM-20261013','/startup-platform-growth-v01.html']]){selectBook(b);clickCard(id);assert.ok(get('readerFrame').src.endsWith(url));get('readerBack').click()}
 selectBook('startup');clickCard('calendar');assert.equal(get('readerFrame').hidden,true);assert.equal(get('sourcePage').children.at(-1).href,'https://calendar.google.com/calendar/u/0/r/');get('readerClose').click();
 get('connectGen2').click();assert.ok(get('gen2Auth').open);
 get('gen2Token').value='synthetic-token';get('readGen2').click();await new Promise(resolve=>setImmediate(resolve));
 assert.equal(window.LivingLibrary.books.length,13);assert.equal(get('gen2Token').value,'');
 selectBook('KB-TEST');assert.ok(get('drawerWrap').classList.contains('open'));
 get('drawerFunctions').children[0].click();assert.equal(get('readerShell').hidden,false);assert.equal(get('readerFrame').hidden,true);assert.ok(get('sourcePage').children.at(-1).textContent.includes('<script>'));
 get('sourcePage').children.find(x=>x.textContent==='編輯候選 MD').click();assert.equal(get('editorPane').hidden,false);assert.ok(get('editText').value.includes('review_status: generated'));
 get('editText').value+='\n候選補充';get('validateEdit').click();assert.ok(get('editorResult').textContent.startsWith('PASS'));
 get('writeToken').value='write-fixture';get('submitEdit').click();await new Promise(resolve=>setImmediate(resolve));assert.equal(get('writeToken').value,'');assert.ok(get('submitEdit').disabled);assert.ok(get('editorResult').children[0].href.includes('/pull/999'));
 get('cancelEdit').click();assert.equal(get('editorPane').hidden,true);
 get('sourcePage').children.find(x=>x.textContent==='在書內開啟既有 GEN2 工作台').click();assert.ok(get('readerFrame').src.endsWith('/packagecanvas/?gen2ref=main'));
 get('readerBack').click();assert.equal(get('readerShell').hidden,true);assert.ok(get('drawerWrap').classList.contains('open'));
 get('readerClose').click();assert.ok(!get('drawerWrap').classList.contains('open'));
 get('searchGen2').click();assert.ok(get('mdSearch').open);get('mdQuery').value='Private synthetic';get('mdQuery').oninput();assert.equal(get('mdResults').children.length,1);get('mdResults').children[0].click();assert.ok(get('sourcePage').children.at(-1).textContent.includes('Private synthetic'));
 get('disconnectGen2').click();assert.equal(window.LivingLibrary.books.length,12);assert.ok(!window.LivingLibrary.books.some(b=>b.id==='KB-TEST'));assert.equal(get('sourcePage').children.length,0);assert.equal(get('drawerFunctions').children.length,0);assert.equal(get('drawerTitle').textContent,'');assert.ok(![...store.values()].join('').includes('synthetic-token'));assert.ok(![...store.values()].join('').includes('KB-TEST'));assert.ok(![...store.values()].join('').includes('SK-TEST'));
});


test('startup research card is available offline, and remains outside GEN2 capabilities',()=>{
 const C=require('../living-library/public-catalog.js');const offline=C.buildCatalog();
 assert.ok(offline.bookCards.startup.includes('KW-STARTUP-PLATFORM-20261013'));
 assert.equal(offline.cards['KW-STARTUP-PLATFORM-20261013'].kind,'external_reference');
 const s=fixture();s.files[0].text+='\n| KB-STARTUP | 創業孵化 | 測試 | active | KB-STARTUP_創業孵化 |';
 s.files.push(doc('02_KNOWLEDGE_BASES/KB-STARTUP_創業孵化/KB_CONTRACT.md','kb_contract','kb_id: KB-STARTUP\nstatus: draft'));
 const p=G.buildLibraryProjection(s),v=C.buildCatalog(p);assert.deepEqual(p.books.find(b=>b.id==='KB-STARTUP').cardIds,[]);
 assert.ok(v.bookCards['KB-STARTUP'].includes('public.startup.tasks'));assert.ok(v.bookCards['KB-STARTUP'].includes('KW-STARTUP-PLATFORM-20261013'));
});
