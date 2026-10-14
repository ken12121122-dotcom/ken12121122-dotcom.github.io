import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const root=new URL('../',import.meta.url);
const source=await readFile(new URL('living-library/live/index.html',root),'utf8');
const Movement=require('../living-library/live/avatar-movement.js');
const Catalog=require('../living-library/live/public-catalog.js');

test('production live WebView URL includes moving avatar, camera target tracking and two input modes',()=>{
 assert.match(source,/id="exploreToggle"/);
 assert.match(source,/id="walkPad"/);
 assert.match(source,/id="walkKnob"/);
 assert.match(source,/['\"]avatar-movement\.js['\"]/);
 assert.match(source,/window\.LivingAvatarMovement/);
 assert.match(source,/camera\.position\.set\(look\.x\+Math\.sin\(theta\)\*horizontal/);
 assert.match(source,/bookOrbitAnchor\.set\(keeper\.position\.x,0,keeper\.position\.z\)/);
 assert.match(source,/p\.x\+bookOrbitAnchor\.x/);
 assert.match(source,/look\.set\(keeper\.position\.x,2\.1,keeper\.position\.z\)/);
 assert.match(source,/setPointerCapture/);
});
test('walking input is bounded, camera relative, and does not move real-world map origin',()=>{
 assert.equal(Movement.WALK_SPEED,3.45);
 const a=Movement.move({x:0,z:0},{x:1,y:0},0,1);
 assert.ok(a.x>0&&a.x<.27);
 assert.equal(a.z,0);
 const b=Movement.move({x:0,z:0},{x:0,y:1},0,.03);
 assert.ok(b.z<0);
 const c=Movement.move({x:9.99,z:0},{x:1,y:0},0,.075);
 assert.ok(Math.hypot(c.x,c.z)<=Movement.WALK_RADIUS+1e-8);
 assert.equal(Movement.normalize(100,0).x,1);
});
test('book titles are printed on the mesh cover/spine, without separate orbit nameplate',()=>{
 assert.match(source,/c\.width=512;c\.height=768/);
 assert.match(source,/font='900 '\+size/);
 assert.match(source,/function spineTitleTex\(b\)/);
 assert.match(source,/function bookMesh\(b\)/);
 assert.match(source,/map:coverTex\(b\)/);
 assert.doesNotMatch(source,/function orbitTitleTex\(b\)/);
 assert.doesNotMatch(source,/nameplateWidth\(b\)/);
 assert.match(source,/g\.position\.copy\(target\.position\)/);
});
test('live Game Book, all original public books and native route stay intact',()=>{
 assert.match(source,/id="gameBookShortcut"/);
 assert.match(source,/get\('book'\)==='gaming'/);
 assert.match(source,/src="\.\.\/\.\.\/packagecanvas\/gen2-source\.js/);
 assert.match(source,/['\"]public-catalog\.js['\"]/);
 const c=Catalog.buildCatalog();
 assert.equal(c.books.length,14);
 assert.equal(c.bookCards.gaming.length,3);
 assert.equal(c.cards['KW-GAME-MEMORY-PALACE-20261014'].title,'知識城堡｜遊戲設計');
 assert.equal(c.cards['public.gba.runtime'].nativeRoute,'gba');
 assert.equal(c.bookCards.android.length,16);
 assert.match(source,/window\.LivingLibrary=\{books:BOOKS,select,openBook/);
});
test('HTML inline scripts still parse after hot-only reconciliation',()=>{
 const scripts=[...source.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)];
 assert.ok(scripts.length>3);
 for(let i=0;i<scripts.length;i++){
  const [_,attrs,body]=scripts[i];
  assert.doesNotThrow(()=>new Function(attrs.includes('module')?'return async function(){'+body+'}':body),'script '+i);
 }
});
