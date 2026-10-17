import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,access} from 'node:fs/promises';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const c=require('../living-library/live/public-catalog.js');
const root=new URL('../',import.meta.url);
const fetchFile=x=>readFile(new URL(x,root),'utf8');

test('remote Living Library provides 14 real public books, protected native entries and the visible new card',()=>{
 const v=c.buildCatalog();
 assert.equal(v.books.length,15);
 assert.deepEqual(v.bookCards.gaming,['public.gba.runtime','public.vault.web','KW-GAME-MEMORY-PALACE-20261014','public.gaming.card.creation','public.gaming.book.creation','public.gaming.atlas','public.gaming.creation.sandbox']);
 assert.equal(v.cards['public.gba.runtime'].nativeRoute,'gba');
 assert.equal(v.bookCards.android.length,16);
 assert.equal(v.cards['KW-GAME-MEMORY-PALACE-20261014'].kind,'external_reference');
 assert.equal(v.cards['KW-GAME-MEMORY-PALACE-20261014'].sourceLayer,'public');
});

test('remote shell can fetch its own static dependencies from Pages',async()=>{
 const html=await fetchFile('living-library/live/index.html');
 for(const file of ['public-catalog.js','book-placement.js','render-capability.js','gen2-editor.js']){
  assert.match(html,new RegExp(file.replaceAll('.','\\.')));
  await access(new URL('living-library/live/'+file,root));
 }
 assert.match(html, /src="\.\.\/\.\.\/packagecanvas\/gen2-source\.js/);
 await access(new URL('packagecanvas/gen2-source.js',root));
 await access(new URL('living-library/live/gpt-library-bridge.html',root));
 assert.match(html,/readerFrame/);
 assert.match(html,/const BOOKS=\[\]/);
 assert.match(html,/function openCardReader\(card\)/);
 assert.match(html,/amin-library:\/\//);
});

test('GEN2 projection overlays public online books without deleting Game Book card',()=>{
 const p={source:{commit:'a'.repeat(40)},books:[{id:'KB-STARTUP',name:'Private startup',cardIds:['SK-DEMO']}],cards:{'SK-DEMO':{id:'SK-DEMO',kind:'skill',sourcePath:'dummy.md'}}};
 for(const v of [c.buildCatalog(),c.buildCatalog(p),c.buildCatalog()]){
  assert.ok(v.bookCards.gaming.includes('KW-GAME-MEMORY-PALACE-20261014'));
  assert.equal(v.cards['public.gba.runtime'].nativeRoute,'gba');
 }
});

test('online Game Book is reached through book catalog or direct link, without a redundant top-right shortcut',async()=>{
 const html=await fetchFile('living-library/live/index.html');
 assert.doesNotMatch(html, /id="gameBookShortcut"/);
 assert.match(html, /function renderFunctions\(b\)/);
 assert.match(html, /route\.get\('book'\)==='gaming'/);
 assert.match(html, /select\('gaming'\);openBook\(\)/);
 const model=c.buildCatalog();
 const ids=model.bookCards.gaming;
 assert.equal(ids.length,7);
 assert.equal(ids[6],'public.gaming.creation.sandbox');
 assert.equal(model.cards[ids[6]].kind,'public_function');
 assert.equal(model.cards[ids[6]].url,'./creation-sandbox-architecture.html');
 assert.equal(ids[2],'KW-GAME-MEMORY-PALACE-20261014');
 assert.equal(model.cards[ids[2]].title,'知識城堡｜遊戲設計');
});
