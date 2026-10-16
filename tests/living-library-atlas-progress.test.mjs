import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,access} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import vm from 'node:vm';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const read=p=>readFile(path.join(root,p),'utf8');
const page='living-library/live/atlas-rulebook-review.html';
const ledger='living-library/live/atlas-development-progress.json';
const statuses=new Set(['not_started','in_progress','blocked','awaiting_review','verified']);
const impl=new Set(['source_verified','decision_pending','prototype_only','not_implemented','needs_verification']);
test('atlas progress ledger has unique source-evidenced candidate gaps and review governance',async()=>{
 const manifest=JSON.parse(await read(ledger));
 assert.equal(manifest.schema,'nw-atlas-progress-v1');
 assert.equal(manifest.card_id,'public.gaming.atlas');
 assert.equal(manifest.review_status,'generated');
 assert.equal(manifest.source_page,page);
 assert.ok(manifest.items.length>=12);
 assert.equal(new Set(manifest.items.map(x=>x.id)).size,manifest.items.length);
 for(const issue of manifest.items){
  assert.match(issue.id,/^ATLAS-\d{3}$/);
  assert.match(issue.phase,/^P[0-5]$/);
  assert.ok(issue.title&&issue.problem&&issue.next_action);
  assert.ok(statuses.has(issue.status),issue.id);
  assert.ok(impl.has(issue.implementation_state),issue.id);
  assert.ok(Number.isInteger(issue.revision)&&issue.revision>=1,'revisions must be positive');
  assert.ok(issue.sources.length,issue.id);
  for(const source of issue.sources){
   assert.equal(source.repository,'ken12121122-dotcom/ken12121122-dotcom.github.io');
   await access(path.join(root,source.path));
  }
 }
 assert.ok(manifest.items.some(x=>x.id==='ATLAS-005'&&x.human_approval_required));
 assert.ok(manifest.items.some(x=>x.id==='ATLAS-008'&&x.status==='blocked'));
});
test('standalone review adds a local-draft progress panel without changing existing interactive rules',async()=>{
 const html=await read(page);
 const catalog=await read('living-library/live/public-catalog.js');
 assert.match(html,/data-tab="progress"/);
 assert.match(html,/id="tab-progress"/);
 assert.match(html,/id="progressRows"/);
 assert.match(html,/id="progressCopy"/);
 assert.match(html,/id="progressReload"/);
 assert.match(html,/PROGRESS_URL='\.\/atlas-development-progress\.json'/);
 assert.match(html,/localStorage\.setItem\(PROGRESS_KEY/);
 assert.match(html,/progressLocal\.base_version!==progressBase\.version/);
 assert.match(html,/navigator\.clipboard\.writeText/);
 assert.match(html,/review_status:'generated'/);
 assert.match(html,/const pages=7/); // existing preview remains a prototype
 assert.match(html,/id="tab-rules"/);
 assert.match(html,/id="tab-review"/);
 assert.match(html,/data-control="UP"/);
 assert.match(catalog,/public\.gaming\.atlas/);
 assert.match(catalog,/url:'\.\/atlas-rulebook-review\.html'/);
 const scripts=[...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)];
 assert.match(html,/src="\.\/atlas-card-upgrade-plan\.js"/);
 const inline=scripts.filter(x=>x[1].trim());
 assert.equal(inline.length,1);
 assert.doesNotThrow(()=>new vm.Script(inline[0][1],{filename:page}));
});
