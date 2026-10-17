import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {runInNewContext} from 'node:vm';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const craft=require('../living-library/live/object-craft.js');
const read=path=>readFile(new URL('../living-library/live/'+path,import.meta.url),'utf8');

test('creation spell never starts by preselecting a prior unbinding card',async()=>{
 const ui=await read('card-creation.html');
 assert.match(ui,/show\(query\?all\.find\(c=>c\.id===query\)\|\|null:null\)/);
 assert.doesNotMatch(ui,/all\[all\.length-1\]\|\|null/);
 assert.match(ui,/Craft\.createBlankCard\(localStorage,window\.crypto\)/);
 assert.match(ui,/card\.title!=='空白卡片'\|\|card\.copyInfo\|\|card\.runeBinding/);
});

test('creating cards preserves old unbinding spell card and never copies its name',()=>{
 const map=new Map();
 const storage={getItem:k=>map.get(k)??null,setItem:(k,v)=>map.set(k,v)};
 let n=0;
 const crypto={randomUUID:()=>String(++n).padStart(36,'0')};
 const spell=craft.createBlankCard(storage,crypto);
 craft.editCard(storage,spell.id,{title:'解除契約卡'});
 const before=craft.listCards(storage).find(c=>c.id===spell.id);
 for(let i=0;i<3;i++){
  const item=craft.createBlankCard(storage,crypto);
  assert.equal(item.title,'空白卡片');
  assert.equal(item.cardNumber,'');
  assert.equal(item.image,null);
  assert.equal(item.copyInfo,undefined);
  assert.equal(item.runeBinding,undefined);
  assert.notEqual(item.id,spell.id);
 }
 const after=craft.listCards(storage).find(c=>c.id===spell.id);
 assert.deepEqual(after,before);
 assert.equal(craft.listCards(storage).length,4);
});

test('construction action is inside top-right overflow, old corner emblem removed',async()=>{
 const html=await read('index.html');
 const pos=html.indexOf('id="libraryQuickMenu"');
 assert.ok(pos>=0);
 assert.match(html,/id="cleanViewToggle"[^>]*aria-expanded="false"[^>]*aria-controls="libraryQuickMenu"/);
 assert.ok(html.indexOf('id="permanentBuildToggle"',pos)>pos);
 assert.ok(html.indexOf('id="permanentBuildToggle"',pos)<html.indexOf('</div>',pos));
 assert.match(html,/#libraryQuickMenu\[hidden\]\{display:none!important\}/);
 assert.doesNotMatch(html,/<div class="brand"[^>]*>/);
 assert.match(html,/buildToggle\.hidden=!registered\|\|!!buildState/);
});

test('top-right menu retains clean-view toggle and closes after build activation',async()=>{
 const script=await read('clean-view.js');
 const makeElement=()=>({hidden:false,attrs:{},listeners:{},
  setAttribute(k,v){this.attrs[k]=v},
  addEventListener(k,cb){this.listeners[k]=cb},
  fire(k,event={}){return this.listeners[k]?.(event)},
  textContent:''});
 const trigger=makeElement(),menu=makeElement(),cleanAction=makeElement(),build=makeElement();
 menu.hidden=true;menu.contains=node=>node===cleanAction||node===build;
 const nodes={cleanViewToggle:trigger,libraryQuickMenu:menu,cleanViewAction:cleanAction,permanentBuildToggle:build};
 const document={getElementById:id=>nodes[id],listeners:{},
  body:{classList:{values:new Set(),toggle(name,on){on?this.values.add(name):this.values.delete(name)}}},
  addEventListener(k,cb){this.listeners[k]=cb}};
 const state=new Map(),storage={getItem:k=>state.get(k)??null,setItem:(k,v)=>state.set(k,v)};
 runInNewContext(script,{document,localStorage:storage});
 assert.equal(menu.hidden,true);
 trigger.fire('click');
 assert.equal(trigger.attrs['aria-expanded'],'true');
 assert.equal(menu.hidden,false);
 cleanAction.fire('click');
 assert.equal(document.body.classList.values.has('clean-view'),true);
 assert.equal(menu.hidden,true);
 trigger.fire('click');build.fire('click');
 assert.equal(menu.hidden,true);
 assert.equal(document.body.classList.values.has('clean-view'),true);
 document.listeners.keydown({key:'Escape'});
 assert.equal(document.body.classList.values.has('clean-view'),false);
});
