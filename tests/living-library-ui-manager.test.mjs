import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const tool=require('../living-library/ui-manager-register.js');
const live=require('../living-library/live/public-catalog.js');
const dev=require('../living-library/public-catalog.js');
const path=new URL('../living-library/ui-map.html',import.meta.url);

test('Card identity stays stable across books, branches, and display numbers',()=>{
 const catalog=live.buildCatalog(),o=tool.catalogOverview(catalog),items=tool.listCards(catalog);
 assert.equal(o.bookCount,14);assert.equal(o.cardCount,29);
 assert.equal(o.placementCount,45);assert.equal(new Set(items.map(c=>c.id)).size,items.length);
 assert.ok(items.find(x=>x.id==='public.canvas.workbench').books.length>=8);
 const manager=items.find(x=>x.id==='public.system.ui.map');
 assert.deepEqual(manager.books,['system']);assert.equal(manager.number,'10');
 assert.deepEqual(manager.historicalPrHints,[209]);
 assert.ok(items.every(x=>!x.id.startsWith('SK-PRIVATE')));
});
test('Catalog comparison highlights live-only GPT and memory palace without deleting shared ids',()=>{
 const diff=tool.compareCatalogs(live.buildCatalog(),dev.buildCatalog());
 assert.equal(diff.live.books,14);assert.equal(diff.dev.books,14);
 assert.equal(diff.live.cards,29);assert.equal(diff.dev.cards,25);
 assert.ok(diff.liveOnlyCardIds.includes('public.gpt.skills'));
 assert.ok(diff.liveOnlyCardIds.includes('KW-GAME-MEMORY-PALACE-20261014'));
 assert.ok(diff.placements.find(x=>x.book==='system').liveOnly.includes('public.gpt.skills'));
 assert.equal(diff.devOnlyCardIds.length,0);
});
test('Public GitHub inspection counts ALL paged branches and returns only open PRs',async()=>{
 const a=Array.from({length:100},(_,i)=>({name:'feature/task-'+i}));
 a[1].name='feature/living-library-ui-map';a[2].name='feature/game-book-card';
 const b=[{name:'feature/last'},{name:'fix/gpt-skills-card'}];
 const opened=[{number:209,title:'UI admin',draft:true,state:'open',head:{ref:'feature/living-library-ui-map'},base:{ref:'main'}},
 {number:199,title:'Game card',draft:true,state:'open',head:{ref:'game-archive'},base:{ref:'main'}}];
 const calls=[];
 async function fetcher(url,opts){
  calls.push({url,opts});
  const data=url.includes('/commits/main')?{sha:'a'.repeat(40)}
  :url.includes('/branches?')?(new URL(url).searchParams.get('page')==='1'?a:b)
  :url.includes('/pulls?')?opened:null;
  return {ok:true,status:200,json:async()=>data};
 }
 const report=await tool.fetchGithubReport(fetcher);
 assert.equal(report.branchCount,102);assert.equal(report.relatedBranchCount,3);
 assert.equal(report.openPrCount,2);assert.equal(report.mainSha,'a'.repeat(40));
 assert.deepEqual(report.openPrs.map(x=>x.number),[209,199]);
 assert.equal(calls.length,4);assert.ok(calls.every(c=>c.opts.method==='GET'));
 assert.ok(calls.every(c=>!Object.keys(c.opts).some(x=>/token|authoriz/i.test(x))));
});
test('GitHub failures never produce fabricated counts or credentials',async()=>{
 await assert.rejects(()=>tool.fetchGithubReport(async()=>({ok:false,status:403,json:async()=>({})})),/GitHub API unavailable/);
 assert.equal(tool.BRANCH_PATTERN.test('feature/detail-card-autosize-drag'),true);
 assert.equal(tool.BRANCH_PATTERN.test('feat/missions-service'),false);
});
test('Manager UI shows live/dev comparison and explicit manual refresh with no background write',async()=>{
 const html=await readFile(path,'utf8');
 for(const x of ['ui-manager-register.js','./public-catalog.js','./live/public-catalog.js','refreshRepo','catalogDiff','cardIndex','prRows','branchRows','githubMainSha','cardFilter','webRuntimeStamp']){
  assert.ok(html.includes(x),x);
 }
 assert.doesNotMatch(html,/id="gptSkillsShortcut"/);
 assert.match(html,/\.addEventListener\('click',refreshGithub\)/);
 assert.match(html,/textContent/);
 assert.doesNotMatch(html,/PRIVATE[_-]?TOKEN|github_pat_|ghp_[A-Za-z0-9]/);
});
