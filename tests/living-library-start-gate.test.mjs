import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFileSync} from 'node:fs';
const require=createRequire(import.meta.url);
const Kit=require('../living-library/live/start-gate.js');
const html=readFileSync(new URL('../living-library/live/index.html',import.meta.url),'utf8');
const mem=(init={})=>{const m=new Map(Object.entries(init));return {getItem:k=>m.has(k)?m.get(k):null,setItem:(k,v)=>m.set(k,String(v))}};

test('title shows once per tab session; deep links and verify view go straight in',()=>{
 assert.equal(Kit.shouldShow('',mem()),true);
 assert.equal(Kit.shouldShow('?book=gaming',mem()),false);
 assert.equal(Kit.shouldShow('?card=public.gpt.skills',mem()),false);
 assert.equal(Kit.shouldShow('?verify=1',mem()),false);
 const s=mem();assert.equal(Kit.markStarted(s),true);assert.equal(Kit.shouldShow('?v=123',s),false,'auto-update reload in the same tab');
 const broken={getItem(){throw Error('denied')},setItem(){throw Error('denied')}};
 assert.equal(Kit.shouldShow('',broken),true);assert.equal(Kit.markStarted(broken),false);
});

test('notices never pop while the title is up; explicit reconnect opens after start',()=>{
 let started=0,shown=[];const toasts=[];
 const g=Kit.createGate({visible:true,onStart:()=>started++,toast:m=>toasts.push(m)});
 const views=[];g.subscribe(v=>views.push(v));
 assert.equal(g.holdUpdate([{a:1},{b:2}],()=>shown.push('update'),false),'held');
 assert.equal(shown.length,0);assert.equal(g.view().update.count,2);assert.match(g.view().update.text,/2 項更新/);
 assert.equal(g.offerVault(()=>shown.push('vault')),'offered');assert.equal(g.view().vault,true);assert.equal(shown.length,0);
 g.openUpdate();g.openVault();assert.deepEqual(shown,['update','vault'],'chips open on demand');
 assert.equal(g.start(),true);assert.equal(started,1);assert.equal(g.open,false);assert.equal(g.start(),false);
 assert.deepEqual(shown,['update','vault'],'a non-explicit update is not reopened after start');
 assert.equal(views.at(-1).update,null);assert.equal(views.at(-1).vault,false);
 // After start: vault is never auto-offered; non-explicit update only toasts; explicit opens.
 assert.equal(g.offerVault(()=>shown.push('vault2')),'ignored');
 assert.equal(g.holdUpdate([{x:1}],()=>shown.push('u2'),false),'toast');assert.match(toasts[0],/1 項更新/);
 g.holdUpdate([{x:1}],()=>shown.push('u3'),true);assert.deepEqual(shown.slice(-1),['u3']);
 const g2=Kit.createGate({visible:true});const opened=[];g2.holdUpdate([],()=>opened.push(1),true);g2.start();assert.deepEqual(opened,[1],'explicit reconnect diff shows right after start');
 assert.equal(Kit.createGate({visible:false}).open,false);
});

test('index wiring: title before the library HUD, icon-only actions, no startup modals',()=>{
 const list=html.match(/const scripts=\[([^\]]+)\]/)[1];assert.ok(list.includes("'start-gate.js'"));
 assert.match(html,/<section id="startGate" hidden role="dialog"/);
 const gate=html.match(/<section id="startGate"[\s\S]*?<\/section>/)[0];
 for(const b of gate.matchAll(/<button[^>]*>([^<]*)<\/button>/g)){assert.doesNotMatch(b[1],/[\p{L}\p{N}]/u,'icon-only: '+b[0]);assert.match(b[0],/aria-label="[^"]+"/)}
 assert.match(html,/if\(gate\)gate\.offerVault\(presentWorldVaultUnlock\)/);
 assert.match(html,/if\(gate\)\{gate\.holdUpdate\(items,\(\)=>openUpdateNotice\(items,firstVisit\),explicit\);return\}/);
 assert.match(html,/showUpdateNotice\(items,!was,!!pendingUpdate\)/);
 for(const t of Kit.TIPS)assert.ok(t.length<40);
});
