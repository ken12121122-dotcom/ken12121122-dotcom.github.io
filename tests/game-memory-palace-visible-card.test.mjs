import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
const base=new URL('../',import.meta.url);
const read=p=>readFile(new URL(p,base),'utf8');
test('Knowledge World displays a visible and clickable Game Book design card',async()=>{
 const html=await read('packagecanvas/world.html');
 const page=await read('packagecanvas/game-design-memory-palace-v01.html');
 assert.equal((html.match(/class="game-design-card"/g)||[]).length,1,'exactly one visible design card');
 assert.match(html,/<h2 id="gameBookIndexTitle">♠ 遊戲之書｜卡片索引<\/h2>/);
 assert.match(html,/<a class="game-design-card" href="\.\/game-design-memory-palace-v01\.html"/);
 assert.match(html,/知識城堡｜記憶宮殿/);
 assert.match(page,/記憶宮殿/);
 assert.match(html,/href="\.\/research-navigation-v04\.html"/,'keep research entry');
 assert.match(html,/data-k="A"/,'keep GBA buttons');
 assert.match(html,/data-k="START"/,'keep GBA menu');
});
