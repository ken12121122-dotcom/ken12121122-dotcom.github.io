import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const read=p=>readFile(new URL('../'+p,import.meta.url),'utf8');
const live=require('../living-library/live/public-catalog.js');
const root=require('../living-library/public-catalog.js');
test('System Book retains two original cards and adds one UI glossary card',()=>{
 for(const cat of [live,root]){const d=cat.buildCatalog();assert.equal(d.books.length,14);
 const expected=cat===live?['public.vault.web','public.canvas.workbench','public.gpt.skills','public.system.ui.map']:['public.vault.web','public.canvas.workbench','public.system.ui.map'];
 assert.deepEqual(d.bookCards.system,expected);
 assert.equal(d.cards['public.system.ui.map'].number,'10');
 assert.equal(d.cards['public.system.ui.map'].url,'https://ken12121122-dotcom.github.io/living-library/ui-map.html');
 assert.equal(d.bookCards.android.length,16)}
 assert.deepEqual(live.buildCatalog().bookCards.gaming,['public.gba.runtime','public.vault.web','KW-GAME-MEMORY-PALACE-20261014']);
});
test('Glossary indexes 14 books, known selectors and public capability',async()=>{
 const h=await read('living-library/ui-map.html');
 for(const id of ["research","startup","knowledge","missions","learning","finance","safety","writing","design","teaching","gaming","system","gpt","android"])assert.ok(h.includes('"'+id+'"'),id);
 for(const selector of ['#stage','#homeBtn','#drawerFunctions','#readerShell','#exploreToggle'])assert.ok(h.includes(selector));
 assert.match(h,/\.\/live\/public-catalog\.js/);
 const j=JSON.parse(await read('living-library/public-capabilities.json'));
 assert.ok(j.capabilities.some(c=>c.id==='public.system.ui.map'&&c.book==='system'));assert.equal(j.originalProtectedCount,7);
});
test('Root and live permit exactly linked same-origin reader',async()=>{
 for(const p of ['living-library/index.html','living-library/live/index.html']){
 const h=await read(p);assert.match(h,/\/living-library\/ui-map\.html/);
 assert.match(h,/\.includes\(u\.pathname\)/);
 assert.match(h,/u\.origin===current\.origin/);
 }
});
