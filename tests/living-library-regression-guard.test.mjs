import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile, access} from 'node:fs/promises';
import {runInNewContext} from 'node:vm';
import {createRequire} from 'node:module';
const Catalog=createRequire(import.meta.url)('../living-library/public-catalog.js');

const root=new URL('../',import.meta.url);
const getText=path=>readFile(new URL(path,root),'utf8');
const required=['public.research.navigation','public.startup.tasks','public.knowledge.world','public.gba.runtime'];
const featureManifest=async()=>JSON.parse(await getText('living-library/public-capabilities.json'));

test('public capabilities retain canonical unique IDs and valid existing endpoints',async()=>{
  const x=await featureManifest();
  assert.equal(x.capabilities.length,8,'7 legacy entries plus new GPT navigation card are protected');
  assert.equal(x.originalProtectedCount,7,'the 7 original functional entries remain a distinct non-regression baseline');
  const ids=x.capabilities.map(c=>c.id);
  assert.equal(new Set(ids).size,ids.length,'each public function has exactly one canonical ID');
  assert.deepEqual([...x.requiredWithoutGen2].sort(),[...required].sort());
  assert.ok(ids.includes('public.gpt.bridge'),'GPT bridge is a separate public function; never a formal GEN2 Skill');
  for(const c of x.capabilities){
    assert.ok(c.name&&c.book&&c.url,'missing capability descriptor for '+c.id);
    if(!c.external){
      assert.ok(c.url.startsWith('/'),'public local URLs must stay rooted');
      const path=c.url.endsWith('/')?c.url+'index.html':c.url;
      await access(new URL('.'+path,root));
    }
  }
});

function makeSimulatedUI(script,projection){
  const elements=new Map();
  class Elem{
    constructor(tag='div'){
      this.tagName=tag.toUpperCase();
      this.children=[];this.attributes={};this.style={};this.dataset={};
      this.textContent='';this.value='';this.href='';this.src='';this.hidden=false;
      this.disabled=false;this.className='';this.listeners={};
      this.classList={
        contains:k=>this.className.split(' ').includes(k),
        add:k=>{if(!this.classList.contains(k))this.className+=' '+k;},
        remove:k=>{this.className=this.className.split(' ').filter(x=>x!==k).join(' ');},
        toggle:(k,force)=>{const state=force??!this.classList.contains(k);state?this.classList.add(k):this.classList.remove(k);return state}
      };
    }
    replaceChildren(...nodes){this.children=nodes}
    append(...nodes){this.children.push(...nodes)}
    addEventListener(k,f){this.listeners[k]=f}
    setAttribute(k,v){this.attributes[k]=v}
    removeAttribute(k){delete this.attributes[k];if(k==='src')delete this.src}
    focus(){}
    close(){this.open=false}
    showModal(){this.open=true}
    querySelector(selector){if(selector==='.drawer')return this.children[0]||null;return null}
    querySelectorAll(selector){if(selector==='.codex-slot')return this.children;return []}
    click(){this.onclick?.({target:this});this.listeners.click?.({target:this})}
  }
  const get=id=>{if(!elements.has(id))elements.set(id,new Elem());return elements.get(id)};
  get('readerShell').hidden=true;get('editorPane').hidden=true;
  get('drawerWrap').append(new Elem('section'));
  const document={getElementById:get,createElement:tag=>new Elem(tag),addEventListener(){},hidden:false,body:new Elem('body')};
  const saved=new Map();
  const github={defaults:{owner:'ken12121122-dotcom',repo:'gen2-knowledge'},collect:async()=>({commit:'a'.repeat(40)}),clearCache(){}};
  const window={
    location:{href:'https://example.test/living-library/index.html'},
    addEventListener(){},
    PackageCanvasGen2:{sources:{github},buildLibraryProjection:()=>projection},
    LivingLibraryDrafts:{},
    LivingBookPlacement:createRequire(import.meta.url)('../living-library/book-placement.js'),
    LivingLibraryCatalog:Catalog
  };
  const localStorage={getItem:k=>saved.get(k)||null,setItem:(k,v)=>saved.set(k,v)};
  runInNewContext(script,{window,document,localStorage,setInterval(){},setTimeout(){},clearTimeout(){},URL,console,AbortController});
  return {window,get};
}
async function ui(){
  const html=await getText('living-library/index.html');
  const script=[...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)].map(x=>x[1]).find(t=>t.includes('const BOOKS='));
  assert.ok(script,'Living Library controller must exist');
  return makeSimulatedUI(script,{
    source:{commit:'a'.repeat(40)},
    books:[{id:'KB-STARTUP',name:'創業孵化',description:'fixture',cardIds:['SK-STARTUP-TEST'],status:'draft'}],
    cards:{'SK-STARTUP-TEST':{id:'SK-STARTUP-TEST',kb_id:'KB-STARTUP',title:'合成驗證 Skill',kind:'skill',status:'draft',sourcePath:'02_KNOWLEDGE_BASES/KB-STARTUP_測試/05_SKILLS/SK-STARTUP-TEST.md',sourceCommit:'a'.repeat(40),body:'# fixture'}},
    diagnostics:[]
  });
}
function verifyPublicBooks(books){
  for(const name of ['research','startup','knowledge','gaming']){
    assert.ok(books.some(b=>b.id===name||b.publicId===name),name+' public functional book must remain usable without GEN2');
  }
}
function cardsFor(get,id){
  const b=get('drawerFunctions').children;
  return b.map(x=>x.dataset?.cardId||x.attributes?.['data-card-id']||'').filter(Boolean);
}
test('public functional books remain available before private GEN2 login',async()=>{
  const {window,get}=await ui();
  verifyPublicBooks(window.LivingLibrary.books);
  const startup=window.LivingLibrary.books.find(b=>b.id==='startup'||b.publicId==='startup');
  window.LivingLibrary.select(startup.id);get('openBtn').click();
  assert.ok(cardsFor(get).includes('public.startup.tasks'),'startup task card must remain accessible');
});
test('GEN2 authorization adds cards without deleting public books or startup task',async()=>{
  const {window,get}=await ui();
  get('gen2Token').value='synthetic-only';get('readGen2').click();
  await new Promise(resolve=>setImmediate(resolve));
  verifyPublicBooks(window.LivingLibrary.books);
  const startup=window.LivingLibrary.books.find(b=>b.id==='startup'||b.publicId==='startup');
  const startupContainers=window.LivingLibrary.books.filter(b=>b.id==='startup'||b.publicId==='startup'||b.id==='KB-STARTUP'||b.kbId==='KB-STARTUP');
  assert.equal(startupContainers.length,1,'public startup and private KB-STARTUP must share one book, not form parallel copies');
  assert.ok(startupContainers[0].id==='KB-STARTUP'||startupContainers[0].kbId==='KB-STARTUP','GEN2 startup source must map to the same book');
  window.LivingLibrary.select(startup.id);get('openBtn').click();
  const ids=cardsFor(get);
  assert.ok(ids.includes('public.startup.tasks'),'private projection must not displace startup tasks');
  assert.ok(ids.includes('SK-STARTUP-TEST'),'GEN2 must be additive to the same book');
  get('disconnectGen2').click();
  verifyPublicBooks(window.LivingLibrary.books);
  assert.ok(!window.LivingLibrary.books.some(b=>b.privateOnly),'private cards must clear on disconnect');
});

test('GPT book is available without GEN2 and preserved across authorization changes',async()=>{
 const {window,get}=await ui();
 assert.ok(window.LivingLibrary.books.some(b=>b.publicId==='gpt'&&b.name==='GPT 之書'),'GPT book is available to everyone');
 window.LivingLibrary.select('gpt');get('openBtn').click();
 const card=get('drawerFunctions').children.find(x=>x.dataset.cardId==='public.gpt.bridge');
 assert.ok(card,'GPT source navigation card is clickable');
 card.click();
 assert.equal(get('readerShell').hidden,false);
 assert.equal(get('readerFrame').src,'./gpt-library-bridge.html','guide opens in the existing in-book reader');
 get('readerBack').click();
 get('gen2Token').value='synthetic-only';get('readGen2').click();await new Promise(resolve=>setImmediate(resolve));
 assert.ok(window.LivingLibrary.books.some(b=>b.publicId==='gpt'),'GEN2 may add private books but not remove GPT book');
 get('disconnectGen2').click();
 assert.ok(window.LivingLibrary.books.some(b=>b.publicId==='gpt'),'disconnect only removes private projection');
});
