import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {runInNewContext} from 'node:vm';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const catalog=require('../living-library/public-catalog.js');
const GAME_ID='KW-GAME-MEMORY-PALACE-20261014';
const PAGE=new URL('../packagecanvas/game-design-memory-palace-v01.html',import.meta.url);

test('Game Book keeps GBA and Vault and adds one public design card without altering book count',()=>{
  const model=catalog.buildCatalog();
  const game=model.books.find(b=>b.id==='gaming');
  assert.equal(game?.name,'遊戲之書');
  assert.equal(model.books.length,14,'no fake new book');
  assert.deepEqual(model.bookCards.gaming,['public.gba.runtime','public.vault.web',GAME_ID]);
  assert.equal(model.cards['public.gba.runtime'].nativeRoute,'gba','native GBA remains');
  const design=model.cards[GAME_ID];
  assert.equal(design.kind,'external_reference','a design document cannot impersonate a GEN2 Skill');
  assert.equal(design.status,'generated');
  assert.equal(design.sourceLayer,'public');
  assert.ok(!design.nativeRoute);
  assert.equal(new URL(design.url).pathname,'/packagecanvas/game-design-memory-palace-v01.html');
  assert.equal(model.bookCards.android.length,16);
  assert.equal(model.bookCards.startup.length,4,'startup untouched');
});

test('Game Book design card remains after private projection and logout',()=>{
  const data={source:{commit:'a'.repeat(40)},books:[{id:'KB-TEST',name:'測試正式書',cardIds:['SK-TEST']}],cards:{'SK-TEST':{id:'SK-TEST',kind:'skill',sourcePath:'fixture.md'}}};
  for(const model of [catalog.buildCatalog(),catalog.buildCatalog(data),catalog.buildCatalog()]){
    assert.ok(model.bookCards.gaming.includes(GAME_ID));
    assert.equal(model.cards[GAME_ID].kind,'external_reference');
    assert.equal(model.cards['public.gba.runtime'].nativeRoute,'gba');
  }
});

test('Design page provides all rooms, objects, steps and clear unimplemented boundaries',async()=>{
  const html=await readFile(PAGE,'utf8');
  assert.match(html,/<title>知識城堡｜記憶宮殿遊戲設計卡 v0\.1<\/title>/);
  assert.equal((html.match(/data-room="/g)||[]).length,8);
  assert.equal((html.match(/class="object"/g)||[]).length,6);
  assert.equal((html.match(/data-step="/g)||[]).length,4);
  assert.match(html,/generated \/ experimental candidate/);
  assert.match(html,/不呼叫 AI／工具/);
  assert.match(html,/驗證後/);
  assert.ok(!/github_pat_|ghp_[A-Za-z0-9]{10}|sk-proj-/.test(html),'no credentials');
});

test('Room and spell tabs update the actual content without network or writeback',async()=>{
  const html=await readFile(PAGE,'utf8');
  const script=[...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)].at(-1)?.[1];
  assert.ok(script);
  class Button {
    constructor(dataset){this.dataset=dataset;this.aria='false'}
    setAttribute(k,v){if(k==='aria-pressed')this.aria=v}
    closest(){return this}
  }
  const elems=Object.create(null),rooms=['library','summon','familiar','forge','guild','trial','map','history'].map(room=>new Button({room})),steps=[0,1,2,3].map(step=>new Button({step}));
  for(const id of ['roomId','roomName','roomDescription','roomData','spellTitle','spellText'])elems[id]={textContent:''};
  for(const [id,buttons] of [['roomGrid',rooms],['spellSteps',steps]])elems[id]={addEventListener(event,handler){this.handler=handler},querySelectorAll(){return buttons}};
  runInNewContext(script,{document:{getElementById:id=>elems[id]}});
  elems.roomGrid.handler({target:rooms[2]});
  assert.equal(elems.roomName.textContent,'使魔庭園');
  assert.match(elems.roomData.textContent,/AI Agent/);
  assert.equal(rooms[2].aria,'true');
  assert.equal(rooms[0].aria,'false');
  elems.spellSteps.handler({target:steps[3]});
  assert.match(elems.spellTitle.textContent,/驗收/);
  assert.match(elems.spellText.textContent,/證據/);
  assert.equal(steps[3].aria,'true');
});
