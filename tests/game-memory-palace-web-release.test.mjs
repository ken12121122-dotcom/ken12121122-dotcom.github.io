import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {runInNewContext} from 'node:vm';
const page=()=>readFile(new URL('../packagecanvas/game-design-memory-palace-v01.html',import.meta.url),'utf8');
const world=()=>readFile(new URL('../packagecanvas/world.html',import.meta.url),'utf8');

test('public knowledge world links to published game design without changing existing research link',async()=>{
 const s=await world();
 assert.match(s,/href="\.\/game-design-memory-palace-v01\.html"/);
 assert.match(s,/遊戲之書｜知識城堡設計圖鑑/);
 assert.match(s,/href="\.\/research-navigation-v04\.html"/);
});

test('game design page is standalone, with valid return to live knowledge world',async()=>{
 const s=await page();
 assert.match(s,/<title>知識城堡｜記憶宮殿遊戲設計卡 v0\.1<\/title>/);
 assert.equal((s.match(/data-room="/g)||[]).length,8);
 assert.equal((s.match(/class="object"/g)||[]).length,6);
 assert.equal((s.match(/data-step="/g)||[]).length,4);
 assert.ok(s.includes('href="./world.html"'));
 assert.ok(!s.includes('href="../living-library/index.html"'));
 assert.match(s,/設計草案・尚未實作完整遊戲/);
 assert.ok(!/github_pat_|ghp_[A-Za-z0-9]{10}|sk-proj-/.test(s));
});

test('interactive room and spell controls switch content without any network activity',async()=>{
 const html=await page();
 const script=[...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)].at(-1)?.[1];
 assert.ok(script);
 class B{constructor(dataset){this.dataset=dataset;this.aria='false'}closest(){return this}setAttribute(k,v){if(k==='aria-pressed')this.aria=v}}
 const roomButtons=['library','summon','familiar','forge','guild','trial','map','history'].map(x=>new B({room:x}));
 const stepButtons=[0,1,2,3].map(x=>new B({step:x}));
 const el={};
 for(const id of ['roomId','roomName','roomDescription','roomData','spellTitle','spellText'])el[id]={textContent:''};
 for(const [id,items] of [['roomGrid',roomButtons],['spellSteps',stepButtons]])el[id]={addEventListener(type,fn){this.handler=fn},querySelectorAll(){return items}};
 runInNewContext(script,{document:{getElementById:id=>el[id]}});
 el.roomGrid.handler({target:roomButtons[2]});
 assert.equal(el.roomName.textContent,'使魔庭園');
 assert.match(el.roomData.textContent,/Agent/);
 assert.equal(roomButtons[2].aria,'true');
 el.spellSteps.handler({target:stepButtons[3]});
 assert.match(el.spellText.textContent,/證據/);
 assert.equal(stepButtons[3].aria,'true');
});
