import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,access} from 'node:fs/promises';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const root=new URL('../',import.meta.url);
const read=p=>readFile(new URL(p,root),'utf8');
const [html,searchPage,systemPage]=await Promise.all([
 read('living-library/live/index.html'),
 read('living-library/live/knowledge-search.html'),
 read('living-library/live/system-controls.html')
]);
const Catalog=require('../living-library/live/public-catalog.js');
const catalog=Catalog.buildCatalog();

test('keep 14 books, GBA route, game atlas and native Android routes; relocate only approved actions',()=>{
 assert.equal(catalog.books.length,14);
 assert.equal(catalog.bookCards.gaming.length,3);
 assert.equal(catalog.bookCards.android.length,16);
 assert.equal(catalog.cards['public.gba.runtime'].nativeRoute,'gba');
 assert.equal(catalog.cards['public.knowledge.search'].url,'./knowledge-search.html');
 assert.equal(catalog.cards['public.system.controls'].url,'./system-controls.html');
 assert.ok(catalog.bookCards.knowledge.includes('public.knowledge.search'));
 assert.ok(catalog.bookCards.system.includes('public.system.controls'));
 assert.ok(catalog.bookCards.gpt.includes('public.gpt.skills'));
 assert.ok(catalog.bookCards.system.includes('public.system.ui.map'));
 const projections=Catalog.buildCatalog({source:{commit:'a'.repeat(40)},books:[],cards:{}});
 assert.ok(projections.bookCards.knowledge.includes('public.knowledge.search'));
 assert.ok(projections.bookCards.system.includes('public.system.controls'));
});

test('remove eight redundant control buttons entirely from 3D homepage, not hide functionality behind more floating buttons',()=>{
 assert.doesNotMatch(html,/<div class="top-actions">/);
 for(const id of ['llUpdateReconnect','gptSkillsShortcut','gameBookShortcut','searchGen2','connectGen2','motionBtn','resetBtn','aboutBtn','homeBtn','exploreToggle'])
  assert.doesNotMatch(html,new RegExp('id="'+id+'"'));
 assert.match(html,/function renderFunctions\(b\)/);
 assert.match(html,/route\.get\('book'\)==='gaming'/);
 assert.match(html,/if\(route\.get\('card'\)==='public\.gpt\.skills'\)/);
 assert.match(html,/#webRuntimeStamp\{position:absolute!important/);
 assert.doesNotMatch(html,/<aside class="side">/);
 assert.match(html,/\.catalog #gen2Status,\.catalog \.shelf-tools,\.catalog \.catalog-bottom\{display:none!important\}/);
 assert.match(html,/\.bottom \.current,\.bottom \.note\{display:none!important\}/);
});

test('tap real character to reveal fixed anchored joystick; release stops walking but pad persists, empty tap hides',()=>{
 assert.match(html,/const avatarHit=ray\.intersectObject\(keeper,true\)/);
 assert.match(html,/ray\.ray\.distanceToPoint\(new THREE\.Vector3\(keeper\.position\.x/);
 assert.match(html,/if\(avatarHit\)\{showJoystick\(e\);return 'avatar'\}/);
 assert.match(html,/if\(joystickVisible\)resetJoystick\(true\)/);
 assert.match(html,/function showJoystick\(e\)/);
 assert.match(html,/walkPad\.style\.left=joystickOrigin\.x/);
 assert.match(html,/walkPad\.style\.top=joystickOrigin\.y/);
 assert.match(html,/if\(beginJoystick\(e\)\)/);
 assert.match(html,/if\(joystickEnd\)resetJoystick\(false\)/);
 assert.match(html,/else if\(isTap\)pick\(e\)/);
 assert.match(html,/\.walk-pad\{position:fixed!important/);
 assert.match(html,/pointer-events:none!important/);
 assert.doesNotMatch(html,/walkPad\.addEventListener\('pointerdown'/);
});

test('character movement never changes camera distance or zoom target; pinch and wheel remain exclusive zoom controls',()=>{
 assert.doesNotMatch(html,/distance=6\.8/);
 assert.doesNotMatch(html,/distanceBeforeExplore/);
 assert.match(html,/const CAMERA_MIN=7\.5,CAMERA_MAX=27/);
 assert.match(html,/look\.x=keeper\.position\.x/);
 assert.match(html,/look\.z=keeper\.position\.z/);
 assert.match(html,/function positionJoystick\(x,y\)\{[\s\S]{0,600}walkPad\.hidden=false/);
 assert.match(html,/function showJoystick\(e\)\{/);
 assert.match(html,/distance=THREE\.MathUtils\.clamp\(distance\+\(pinch-pair\.sep\)\*\.032,CAMERA_MIN,CAMERA_MAX\)/);
 assert.match(html,/distance=THREE\.MathUtils\.clamp\(distance\+e\.deltaY\*\.015,CAMERA_MIN,CAMERA_MAX\)/);
 assert.match(html,/function resize\(\)\{[\s\S]{0,450}distance=THREE\.MathUtils\.clamp\(distance,CAMERA_MIN,CAMERA_MAX\)/);
 assert.doesNotMatch(html,/if\(exploring\)distance=/);
 assert.match(html,/bookOrbitAnchor\.set\(keeper\.position\.x,0,keeper\.position\.z\)/);
});

test('public in-book controls use same-origin iframe gated messages and no credential exposure',async()=>{
 for(const [url,id] of [['knowledge-search.html','public.knowledge.search'],['system-controls.html','public.system.controls']]){
  await access(new URL('living-library/live/'+url,root));
  assert.ok(catalog.cards[id].url.endsWith(url));
  const frameLink="new URL('./"+url+"',current).pathname";
  assert.ok(html.includes(frameLink));
 }
 assert.match(html,/event\.origin!==window\.location\.origin/);
 assert.match(html,/event\.source!==document\.getElementById\('readerFrame'\)\.contentWindow/);
 assert.match(html,/event\.data\?\.kind!=='living-library-public-card-action'/);
 assert.match(html,/id==='public\.knowledge\.search'&&\(action==='open-md-search'\|\|action==='connect-gen2'\)/);
 assert.match(html,/id==='public\.system\.controls'&&action==='reconnect-web'/);
 assert.match(html,/reconnectNow\(\)/);
 assert.match(searchPage,/window\.parent\.postMessage/);
 assert.match(systemPage,/window\.parent\.postMessage/);
 assert.match(searchPage,/open-md-search/);
 assert.match(systemPage,/reconnect-web/);
 assert.match(systemPage,/connect-gen2/);
 assert.doesNotMatch(searchPage,/github_pat_|sk-proj-/);
 assert.doesNotMatch(systemPage,/github_pat_|sk-proj-/);
});

test('update notice stays automatic and booklet-styled, only private web book display preferences reset',()=>{
 assert.match(html,/id="llUpdateDialog"/);
 assert.match(html,/ll-update-dialog/);
 assert.match(html,/setInterval\(\(\)=>\{if\(!document\.hidden\)refreshWebVersion\(\)\},180000\)/);
 assert.match(html,/Updates\.resetView\(localStorage\)/);
 assert.doesNotMatch(html,/localStorage\.clear\(\)/);
 assert.match(html,/window\.location\.replace\(Updates\.reconnectUrl\(location\.href,Date\.now\(\)\)\)/);
});

test('live page and in-book pages compile without syntax errors',()=>{
 const pages=[html,searchPage,systemPage];
 for(const [pageIndex,page] of pages.entries()){
  const scripts=[...page.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)];
  assert.ok(scripts.length>0);
  for(const [i,m] of scripts.entries())assert.doesNotThrow(()=>new Function(m[1].includes('module')?'return async function(){'+m[2]+'}':m[2]),'page '+pageIndex+' script '+i);
 }
});

test('settings are kept in the existing System Book card; rotation can be disabled or set to single/dual finger',()=>{
 assert.match(systemPage,/id="rotationEnabled"/);
 assert.match(systemPage,/id="rotationMode"/);
 assert.match(systemPage,/<option value="single">/);
 assert.match(systemPage,/<option value="dual">/);
 assert.match(html,/CONTROLS_PREFS_KEY='living-library-controls-v1'/);
 assert.match(html,/rotationMode:d\.rotationMode==='single'\|\|d\.rotationMode==='dual'/);
 assert.match(html,/controlPrefs\.rotationEnabled&&controlPrefs\.rotationMode==='dual'/);
 assert.match(html,/controlPrefs\.rotationEnabled&&controlPrefs\.rotationMode==='single'/);
 assert.match(html,/action==='update-preferences'/);
 assert.match(systemPage,/data\.settings=settings/);
 assert.doesNotMatch(html,/id="rotationMode"/); // controls are in the existing in-book card
});

test('fixed or touch-based joystick spawn and card swapping persist locally without mutating catalog',()=>{
 assert.match(systemPage,/id="joystickMode"/);
 assert.match(systemPage,/id="relocateJoystick"/);
 assert.match(systemPage,/id="cardReorder"/);
 assert.match(html,/function beginJoystickPlacement\(\)/);
 assert.match(html,/function positionJoystick\(x,y\)/);
 assert.match(html,/joystickMode==='fixed'/);
 assert.match(html,/CARD_ORDER_KEY='living-library-book-card-order-v1'/);
 assert.match(html,/function orderedCardIds\(bookId\)/);
 assert.match(html,/function swapCardSlots\(bookId,first,second\)/);
 assert.match(html,/setTimeout\(\(\)=>\{[\s\S]*?520\)/);
 assert.match(html,/localStorage\.setItem\(CARD_ORDER_KEY/);
 assert.match(html,/Math\.hypot\(e\.clientX-press\.x,e\.clientY-press\.y\)>10/);
 assert.match(html,/update-preferences/);
 assert.doesNotMatch(systemPage,/github_pat_|sk-proj-/);
});

test('3D startup retries a secondary pinned Three.js source and provides actionable error diagnostics without erasing state',()=>{
 assert.match(html,/const engineSources=\[/);
 assert.match(html,/cdn\.jsdelivr\.net\/npm\/three@0\.185\.0\/build\/three\.module\.js/);
 assert.match(html,/unpkg\.com\/three@0\.185\.0\/build\/three\.module\.js/);
 assert.match(html,/if\(!THREE\)\{/);
 assert.match(html,/initPhase='WEBGL_CONTEXT'/);
 assert.match(html,/initPhase='FIRST_RENDER'/);
 assert.match(html,/id="fallbackDiag"/);
 assert.match(html,/id="retry3d"/);
 assert.match(html,/window\.LivingLibrary\?\.showFallback\(reason/);
 assert.match(html,/console\.error\('Living Library 3D init failed'/);
 assert.doesNotMatch(html,/localStorage\.clear\(\)/);
});
