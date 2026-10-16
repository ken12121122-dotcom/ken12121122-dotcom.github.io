import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const require=createRequire(import.meta.url);
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const identity=require('../living-library/live/atlas-identity-contract.js');
const check=identity.checkCandidate;
test('candidate atlas ID check is fail-closed without registration or approval',()=>{
 const result=check({candidateId:'ATLAS-0001'});
 assert.equal(result.schema,'nw-atlas-identity-check-v1');
 assert.equal(result.card_id,'public.gaming.atlas');
 assert.equal(result.container_id,'local.atlas.compendium');
 assert.equal(result.format_status,'matches_proposed_format');
 assert.equal(result.conflict_status,'not_checked');
 assert.equal(result.review_status,'generated');
 assert.equal(result.identity_issued,false);
 assert.equal(result.may_register,false);
 assert.equal(result.requires_human_review,true);
 assert.ok(result.unresolved_gaps.includes('official_atlas_id_not_approved'));
 assert.ok(result.unresolved_gaps.includes('authoritative_registry_not_available'));
 assert.equal(result.source_records.length,3);
});
test('missing, invalid and forbidden example labels never authorize',()=>{
 for(const candidateId of [undefined,'','ATLAS-0000','ATLAS-0','atlas-0001','ATLAS-0001 ','ATLAS-0001\n','ATLAS-ABC1','public.gaming.atlas','local.atlas.compendium','ATLAS-０００１','ATLAS-01-2026']){
  const checked=check({candidateId});
  assert.notEqual(checked.format_status,'matches_proposed_format',String(candidateId));
  assert.equal(checked.may_register,false);
  assert.equal(checked.review_status,'generated');
 }
});
test('duplicate detection only checks caller-supplied references; never claims global uniqueness',()=>{
 const dupe=check({candidateId:'ATLAS-0001',knownIds:['ATLAS-0002','atlas-0001']});
 assert.equal(dupe.conflict_status,'duplicate_in_supplied_references');
 assert.equal(dupe.may_register,false);
 const sample=check({candidateId:'ATLAS-0010',knownIds:['ATLAS-0002']});
 assert.equal(sample.conflict_status,'no_conflict_in_supplied_references');
 assert.ok(sample.unresolved_gaps.includes('supplied_references_are_not_authoritative'));
 assert.equal(sample.identity_issued,false);
});
test('malformed or oversized references fall back to unchecked and explicit data gap',()=>{
 for(const knownIds of ['ATLAS-0001',[null],[{id:'ATLAS-0001'}],Array(1001).fill('X')]){
  const result=check({candidateId:'ATLAS-0001',knownIds});
  assert.equal(result.conflict_status,'not_checked');
  assert.ok(result.unresolved_gaps.includes('supplied_reference_set_invalid'));
 }
});
test('source code is side-effect-free with no persistence or remote communications',async()=>{
 const code=await readFile(path.join(root,'living-library/live/atlas-identity-contract.js'),'utf8');
 for(const forbidden of [/\blocalStorage\b/,/\bfetch\s*\(/,/XMLHttpRequest/,/\.setItem\s*\(/,/https?:\/\//,/\bcreateElement\b/]){
  assert.doesNotMatch(code,forbidden);
 }
 const baseline=JSON.parse(await readFile(path.join(root,'living-library/live/atlas-development-progress.json'),'utf8'));
 const task=baseline.items.find(i=>i.id==='ATLAS-002');
 assert.equal(baseline.review_status,'generated');
 assert.equal(task.status,'in_progress');
 assert.equal(task.implementation_state,'decision_pending');
 assert.equal(task.human_approval_required,true);
});
