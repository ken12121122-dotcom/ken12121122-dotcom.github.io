import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
const require=createRequire(import.meta.url);
const Catalog=require('../android-native/app/src/main/assets/workbench/living-library/public-catalog.js');
const ID='KW-GAME-MEMORY-PALACE-20261014';
test('Android Game Book shows new published design card alongside native GBA and Vault',()=>{
 const c=Catalog.buildCatalog();
 assert.equal(c.books.length,14);
 assert.equal(c.books.find(x=>x.id==='gaming')?.name,'遊戲之書');
 assert.deepEqual(c.bookCards.gaming,['public.gba.runtime','public.vault.web',ID]);
 assert.equal(c.cards['public.gba.runtime'].nativeRoute,'gba');
 assert.equal(c.cards[ID].kind,'external_reference');
 assert.equal(c.cards[ID].sourceLayer,'public');
 assert.equal(c.cards[ID].status,'generated');
 assert.equal(c.cards[ID].url,'https://ken12121122-dotcom.github.io/packagecanvas/game-design-memory-palace-v01.html');
 assert.equal(c.bookCards.android.length,16);
});
test('Game design card survives GEN2 authorization changes without duplicate books',()=>{
 const fake={source:{commit:'a'.repeat(40)},books:[{id:'KB-STARTUP',name:'創業',cardIds:['SK-TEST']}],cards:{'SK-TEST':{id:'SK-TEST',kind:'skill',sourcePath:'demo.md'}}};
 for(const c of [Catalog.buildCatalog(),Catalog.buildCatalog(fake),Catalog.buildCatalog()]){
  assert.ok(c.bookCards.gaming.includes(ID));
  assert.equal(c.bookCards.gaming.filter(x=>x===ID).length,1);
  assert.equal(c.cards[ID].kind,'external_reference');
 }
});
test('Android bundled book reader can open game design webpage within book',async()=>{
 const source=await readFile(new URL('../android-native/app/src/main/assets/workbench/living-library/index.html',import.meta.url),'utf8');
 assert.match(source,/function isLocalFunctionPage\(url\)/);
 assert.match(source,/u\.pathname\.startsWith\('\/packagecanvas\/'\)/);
 assert.match(source,/\('readerFrame'\)\.src=card\.url/);
 assert.match(source,/function renderFunctions\(b\)/);
 assert.match(source,/BOOK_CARDS\[b\.id\]/);
});
