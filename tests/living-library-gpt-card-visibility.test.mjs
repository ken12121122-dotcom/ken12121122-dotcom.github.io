import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const catalog=require('../living-library/live/public-catalog.js');
const base=new URL('../',import.meta.url);
const live=await readFile(new URL('living-library/live/index.html',base),'utf8');

test('GPT skill card is reachable from GPT Book AND System Book by one canonical id',()=>{
 const c=catalog.buildCatalog();
 const id='public.gpt.skills';
 assert.deepEqual(c.bookCards.gpt,['public.gpt.bridge',id]);
 assert.ok(c.bookCards.system.includes(id));
 assert.equal(c.bookCards.system.filter(x=>x===id).length,1);
 assert.equal(c.cards[id].url,'./gpt-skills.html');
 assert.equal(c.books.length,15);
 assert.equal(c.bookCards.gaming.length,7);
 assert.equal(c.bookCards.android.length,16);
 const post=catalog.buildCatalog({source:{commit:'a'.repeat(40)},books:[],cards:{}});
 assert.ok(post.bookCards.system.includes(id));
});
test('new live release bumps the actual catalog script URL to prevent older URL cache reuse',()=>{
 assert.match(live, /['\"]public-catalog\.js['\"]/);
 assert.match(live, /['\"]update-manager\.js['\"]/);
 assert.match(live, /id="webRuntimeStamp"/);
 assert.match(live, /WEB LIVE r2 · GPT 技能卡已載入/);
 assert.match(live, /WEB LIVE r2 · 卡片目錄仍是舊版/);
 assert.match(live, /BOOK_CARDS\.system\|\|\[\]/);
 assert.doesNotMatch(live, /id="gptSkillsShortcut"/);
 assert.ok(catalog.buildCatalog().bookCards.gpt.includes('public.gpt.skills'));
 assert.match(live, /route\.get\('book'\)==='system'/);
});
test('web hot update still retains deep link, one-click inner iframe and native GBA',()=>{
 assert.match(live, /if\(route\.get\('card'\)==='public\.gpt\.skills'\)/);
 assert.match(live, /if\(card\)openCardReader\(card\)/);
 assert.match(live, /new URL\('\.\/gpt-skills\.html',current\)\.pathname/);
 assert.match(live, /look\.x=keeper\.position\.x/);
 assert.match(live, /iframe id="readerFrame"/);
 assert.equal(catalog.buildCatalog().cards['public.gba.runtime'].nativeRoute,'gba');
});
