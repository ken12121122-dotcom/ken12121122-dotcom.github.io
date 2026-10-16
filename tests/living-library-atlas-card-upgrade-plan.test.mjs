import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const require=createRequire(import.meta.url);
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const upgrade=require('../living-library/live/atlas-card-upgrade-plan.js');
const card={id:'local.card.abc-123',objectType:'card',templateId:'blank-card-v1',title:'初代法陣',status:'draft',revision:2,cardNumber:'001'};
const reason='這張卡具有特別的遊戲歷史意義，需要研究是否納入唯一圖鑑。';
test('P1 ordinary card application preserves original identity and defers numbering',()=>{
 const snapshot=JSON.stringify(card);
 const r=upgrade.inspect({card,reason});
 assert.equal(r.schema,'nw-atlas-card-upgrade-plan-v1');
 assert.equal(r.card_id,card.id);
 assert.equal(r.source_revision,2);
 assert.equal(r.application_ready,true);
 assert.equal(r.stage,'upgrade_application_draft_ready');
 assert.equal(r.review_status,'generated');
 assert.equal(r.requires_owner_approval,true);
 assert.equal(r.requires_ai_review,true);
 assert.equal(r.upgrade_executed,false);
 assert.equal(r.l2_certified,false);
 assert.equal(r.identity_issued,false);
 assert.equal(r.permanent_number,null);
 assert.equal(r.permanent_slot,null);
 assert.equal(r.may_assign_number,false);
 assert.deepEqual(r.workflow,['ordinary_card','upgrade_application','ai_review','owner_approval','world_binding','l2_certification','permanent_number']);
 assert.equal(JSON.stringify(card),snapshot);
});
test('rejects unsuitable cards, blank titles, invalid reasons, revoked cards',()=>{
 for(const c of [null,{}, {...card,id:'public.gaming.atlas'}, {...card,objectType:'book'}, {...card,templateId:'alien'}, {...card,status:'recycled'}, {...card,status:'locked'}, {...card,title:'空白卡片'}, {...card,revision:0}]){
  const r=upgrade.inspect({card:c,reason});
  assert.equal(r.application_ready,false);
  assert.equal(r.may_assign_number,false);
 }
 for(const why of ['',null,'太短', 'x'.repeat(501)]){
  const r=upgrade.inspect({card,reason:why});
  assert.equal(r.application_ready,false);
  assert.ok(r.errors.includes('upgrade_reason_needs_10_to_500_characters'));
 }
});
test('source module has no storage/network or actual upgrade action',async()=>{
 const src=await readFile(path.join(root,'living-library/live/atlas-card-upgrade-plan.js'),'utf8');
 for(const pattern of [/\blocalStorage\b/,/\bfetch\s*\(/,/\bsetItem\s*\(/,/XMLHttpRequest/,/https?:\/\//,/\brecycleCard\s*\(/,/\beditCard\s*\(/])assert.doesNotMatch(src,pattern);
 const progress=JSON.parse(await readFile(path.join(root,'living-library/live/atlas-development-progress.json'),'utf8'));
 const upgradeItem=progress.items.find(x=>x.id==='ATLAS-006');
 const identityItem=progress.items.find(x=>x.id==='ATLAS-002');
 assert.equal(upgradeItem.phase,'P1');
 assert.equal(upgradeItem.status,'in_progress');
 assert.equal(upgradeItem.human_approval_required,true);
 assert.equal(identityItem.phase,'P2');
 assert.equal(identityItem.status,'blocked');
 assert.equal(progress.review_status,'generated');
});
