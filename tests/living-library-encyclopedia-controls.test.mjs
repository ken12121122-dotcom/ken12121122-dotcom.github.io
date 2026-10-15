import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,access} from 'node:fs/promises';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const root=new URL('../',import.meta.url);
const live=await readFile(new URL('living-library/live/index.html',root),'utf8');
const Catalog=require('../living-library/live/public-catalog.js');
const catalog=Catalog.buildCatalog();

test('main view removes clutter instead of relocating eight controls to another toolbar',()=>{
 for(const id of ['llUpdateReconnect','gptSkillsShortcut','gameBookShortcut','searchGen2','connectGen2','motionBtn','resetBtn','aboutBtn'])
  assert.doesNotMatch(live,new RegExp('id="'+id+'"'),id);
 assert.doesNotMatch(live,/<aside class="side">/);
 assert.match(live,/#webRuntimeStamp\{position:absolute!important/);
 assert.match(live,/\.catalog #gen2Status,\.catalog \.shelf-tools,\.catalog \.catalog-bottom\{display:none!important/);
 assert.match(live,/\.bottom \.current,\.bottom \.note\{display:none!important/);
 assert.match(live,/id="readerFrame"/);
 assert.match(live,/id="bookList"/);
});

test('necessary GEN2 search, authorization and manual reconnect stay in book cards with trusted iframe messaging',async()=>{
 assert.ok(catalog.bookCards.knowledge.includes('public.knowledge.search'));
 assert.ok(catalog.bookCards.system.includes('public.system.controls'));
 assert.equal(catalog.cards['public.knowledge.search'].url,'./knowledge-search.html');
 assert.equal(catalog.cards['public.system.controls'].url,'./system-controls.html');
 assert.deepEqual(catalog.bookCards.gaming,['public.gba.runtime','public.vault.web','KW-GAME-MEMORY-PALACE-20261014','public.gaming.card.creation','public.gaming.book.creation']);
 assert.equal(catalog.bookCards.android.length,16);
 for(const file of ['knowledge-search.html','system-controls.html']) await access(new URL('living-library/live/'+file,root));
 assert.match(live,/event.origin!==window.location.origin/);
 assert.match(live,/event.source!==document.getElementById\('readerFrame'\).contentWindow/);
 assert.match(live,/event.data\?\.kind!=='living-library-public-card-action'/);
 assert.match(live,/id==='public.knowledge.search'/);
 assert.match(live,/id==='public.system.controls'/);
 assert.match(live,/action==='open-md-search'/);
 assert.match(live,/action==='connect-gen2'/);
 assert.match(live,/action==='reconnect-web'/);
 assert.match(live,/function reconnectNow\(\)/);
});

test('joystick appears only when character is tapped and dismisses on empty stage, without zoom jump',()=>{
 assert.doesNotMatch(live,/id="exploreToggle"/);
 assert.match(live,/const avatarHit=ray\.intersectObject\(keeper,true\)/);
 assert.match(live,/if\(avatarHit\)\{showJoystick\(e\);return 'avatar'\}/);
 assert.match(live,/if\(joystickVisible\)resetJoystick\(true\)/);
 assert.match(live,/function insideJoystick\(e\)/);
 assert.match(live,/function beginJoystick\(e\)/);
 assert.match(live,/if\(joystickEnd\)resetJoystick\(false\)/);
 assert.match(live,/const CAMERA_MIN=7\.5,CAMERA_MAX=27/);
 assert.doesNotMatch(live,/distance\s*=\s*6\.8/);
 assert.doesNotMatch(live,/distance=exploring\?/);
 const show=live.slice(live.indexOf('function showJoystick(e){'),live.indexOf('function insideJoystick(e){'));
 for(const unexpected of ['distance=','camera.fov','look.set','cameraUpdate'])assert.ok(!show.includes(unexpected),'joystick show may not zoom: '+unexpected);
 const release=live.slice(live.indexOf("renderer.domElement.addEventListener('pointerup'"),live.indexOf("renderer.domElement.addEventListener('pointercancel'"));
 assert.doesNotMatch(release,/distance\s*=/);
 const follow=live.slice(live.indexOf('function stepAvatar(dt){'),live.indexOf('// Dust motes'));
 assert.doesNotMatch(follow,/distance\s*=/);
 assert.match(follow,/look.x=keeper.position.x/);
});

test('pinch is the only touch zoom and resize preserves distance',()=>{
 assert.match(live,/touchPoints.size===2/);
 assert.match(live,/distance=THREE.MathUtils.clamp\(distance\+\(pinch-pair.sep\)\*\.032,CAMERA_MIN,CAMERA_MAX\)/);
 const resize=live.slice(live.indexOf('function resize(){'),live.indexOf("window.addEventListener('resize',resize)"));
 assert.match(resize,/distance=THREE.MathUtils.clamp\(distance,CAMERA_MIN,CAMERA_MAX\)/);
 assert.doesNotMatch(resize,/distance=window.innerWidth/);
 assert.match(live,/bookOrbitAnchor.set\(keeper.position.x,0,keeper.position.z\)/);
 assert.match(live,/renderer.domElement.addEventListener\('wheel'/);
});
