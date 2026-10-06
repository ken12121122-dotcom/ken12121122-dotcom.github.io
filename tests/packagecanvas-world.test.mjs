import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const M = require('../packagecanvas/world-model.js');

// Synthetic runs only (no knowledge-base content in this public repo).
const step = (id, type, title, status, result = null) => ({ id, type, title, status, attempts: status === 'pending' ? 0 : 1, result });
const run = (number, status, current, steps, extra = {}) => ({
  number, state: status === 'done' || status === 'stopped' ? 'closed' : 'open', url: 'https://github.com/o/r/issues/' + number, updatedAt: '2026-10-06T00:00:00Z',
  view: { format: 'gen2-run-view', version: 1, workflow: { id: 'WF-DEMO-001', title: '示範流程' }, status, current, updatedAt: '2026-10-06T00:00:00Z',
    pending: current ? { step: current, type: steps.find(s => s.id === current).type, title: steps.find(s => s.id === current).title, attempt: 1, skill: 'SK-DEMO-002',
      options: [{ id: 'approve', label: '核准', comment: null }, { id: 'revise', label: '退回', comment: 'required' }] } : null,
    steps }, ...extra });
const STEPS = (s2, g1, s3) => [step('S1', 'input', '接收資料', 'done'), step('S2', 'skill', '整理候選', s2[0], s2[1]), step('G1', 'gate', '審查', g1[0], g1[1]), step('S3', 'skill', '寫入行事曆並回讀', s3[0], s3[1])];

test('steps map to buildings by what they do', () => {
  assert.equal(M.buildingFor({ type: 'input' }), 'board');
  assert.equal(M.buildingFor({ type: 'gate' }), 'hall');
  assert.equal(M.buildingFor({ type: 'skill', title: '產出標準化候選' }), 'forge');
  assert.equal(M.buildingFor({ type: 'skill', title: '寫入 Google Calendar 並回讀' }), 'shop');
  assert.equal(M.buildingFor({ type: 'skill', title: '蒐集來源' }), 'lab');
  assert.equal(M.creatureFor('WF-TIME-001'), 'KB-TIME');
  assert.equal(M.creatureFor('nope'), 'KB-?');
});

test('quests show where the current step happens and who must act', () => {
  const runs = [
    run(3, 'waiting_skill', 'S3', STEPS(['done'], ['done', '核准'], ['active'])),
    run(4, 'waiting_owner', 'G1', STEPS(['done'], ['active'], ['pending'])),
    run(2, 'done', null, STEPS(['done'], ['done', '核准'], ['done', 'written']))];
  const qs = M.questsFromRuns(runs, '2026-10-06T00:07:00Z');
  assert.deepEqual(qs.map(q => q.number), [4, 3, 2]);
  assert.equal(qs[0].who, 'owner'); assert.equal(qs[0].where, 'hall');
  assert.equal(qs[1].who, 'agent'); assert.equal(qs[1].where, 'shop'); assert.equal(qs[1].minutes, 7);
  assert.equal(qs[2].open, false); assert.equal(qs[2].where, null);
  assert.match(M.commandFor(qs[0], 'revise'), /^\/gen2 decide G1 revise\n/);
  assert.equal(M.commandFor(qs[0], 'nope'), '');
  assert.notEqual(M.worldSignature(qs), M.worldSignature(qs.slice(1)));
});

test('growth comes only from validated results, once each', () => {
  const runs = [
    run(2, 'done', null, STEPS(['done'], ['done', '核准'], ['done', 'written'])),
    run(5, 'stopped', null, STEPS(['done'], ['done', '核准'], ['pending'])),
    run(6, 'waiting_owner', 'G1', STEPS(['done'], ['active'], ['pending'])),
    run(8, 'done', null, STEPS(['done'], ['done', '退回重做'], ['done', 'conflict']))];
  const ev = M.growthFromRuns(runs.concat([runs[0]]));
  assert.deepEqual(ev.map(e => e.id), ['GE-2-G1', 'GE-2-WORK', 'GE-2-S3']);
  const p = M.profiles(runs)['KB-DEMO'];
  assert.deepEqual([p.k, p.w, p.s, p.stage], [30, 40, 20, 1]);
  assert.match(p.gap, /目前 1 次/);
  assert.equal(M.profiles([runs[2]])['KB-DEMO'].stage, 0);
});
