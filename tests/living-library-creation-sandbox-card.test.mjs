import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createRequire} from 'node:module';

const require=createRequire(import.meta.url);
const catalog=require('../living-library/live/public-catalog.js');
const read=path=>readFile(new URL('../'+path,import.meta.url),'utf8');
const ID='public.gaming.creation.sandbox';

test('Game Book appends exactly one new canonical design card and preserves six existing routes',()=>{
 const c=catalog.buildCatalog();
 const original=[
  'public.gba.runtime','public.vault.web','KW-GAME-MEMORY-PALACE-20261014',
  'public.gaming.card.creation','public.gaming.book.creation','public.gaming.atlas'
 ];
 assert.deepEqual(c.bookCards.gaming.slice(0,6),original);
 assert.deepEqual(c.bookCards.gaming.slice(6),[ID]);
 assert.equal(c.bookCards.gaming.filter(id=>id===ID).length,1);
 assert.equal(c.books.length,15);
 assert.equal(c.cards[ID].kind,'public_function');
 assert.equal(c.cards[ID].sourceLayer,'public');
 assert.equal(c.cards[ID].url,'./creation-sandbox-architecture.html');
 assert.equal(c.cards[ID].number,'16');
 assert.ok(c.cards['KW-GAME-MEMORY-PALACE-20261014']);
 for(const k of ['research','startup','knowledge','missions','system','gpt','android']){
  assert.ok(c.bookCards[k].length>0,k);
 }
});

test('Sandbox concept is only a reference page and remains reachable inside the same-origin reader',async()=>{
 const html=await read('living-library/live/creation-sandbox-architecture.html');
 const shell=await read('living-library/live/index.html');
 assert.match(html,/概念草案 v0\.1/);
 for(const term of ['物件盤點','玩家決定','動作卡','符文卡','音效卡','動畫編排卡','法術卡','工作流卡','AI 分身','監控中心','真實狀態','測試模式','權限']){
  assert.ok(html.includes(term),'Missing concept: '+term);
 }
 assert.doesNotMatch(html,/<script\b/i,'reference page must not request runtime authority');
 assert.match(shell,/new URL\('\.\/creation-sandbox-architecture\.html',current\)\.pathname/);
 assert.match(shell,/\.includes\(u\.pathname\)/);
 assert.match(shell,/function openCardReader\(card\)/);
 assert.doesNotMatch(shell,/id="creationSandboxShortcut"/);
});
