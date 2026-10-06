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

test('retry and unknown gate outcomes are not counted as a passed review', () => {
  const gates = [step('S1', 'input', '接收資料', 'done'), step('G1', 'gate', '審查', 'done', '核准，交給 SK 寫入'),
    step('S3', 'skill', '寫入', 'done', '發現時段衝突，未寫入'), step('G3', 'gate', '衝突處理', 'done', '依指示調整、覆蓋或另找時間後重新寫入'),
    step('G2', 'gate', '驗收', 'done', '通過，採用'), step('G4', 'gate', '其他', 'done', '看起來可以')];
  const ev = M.growthFromRuns([run(9, 'waiting_skill', null, gates)]);
  assert.deepEqual(ev.map(e => e.id), ['GE-9-G1', 'GE-9-G2']);
});

test('growth keeps finished history beyond the recent-run list', async () => {
  const G = require('../packagecanvas/gen2-source.js');
  const enc = v => Buffer.from(JSON.stringify(v)).toString('base64');
  const finished = n => ({ number: n, state: 'closed', html_url: 'https://github.com/o/r/issues/' + n, user: { login: 'github-actions[bot]' }, labels: [{ name: 'gen2-run' }, { name: 'gen2:done' }],
    body: '<!-- gen2-run-view:' + enc({ format: 'gen2-run-view', version: 1, workflow: { id: 'WF-DEMO-001' }, status: 'done', current: null, pending: null,
      steps: [step('G1', 'gate', '審查', 'done', '核准'), step('S3', 'skill', '寫入', 'done', '已寫入並回讀驗證一致')] }) + ' -->' });
  const urls = [];
  const fetchImpl = async url => { urls.push(url); const page = Number(new URL(url).searchParams.get('page'));
    const list = page === 1 ? Array.from({ length: 100 }, (_, i) => finished(300 - i)) : page === 2 ? [finished(1)] : [];
    return { ok: true, status: 200, json: async () => list }; };
  const history = await G.sources.github.listFinishedRuns({ token: 't' }, fetchImpl);
  assert.equal(history.length, 101);
  assert.ok(urls.every(u => /labels=gen2-run,gen2:done&state=closed/.test(u)));
  const recent = [run(1, 'waiting_owner', 'G1', STEPS(['done'], ['active'], ['pending']))];
  const merged = M.mergeRuns(recent, history);
  assert.equal(merged.find(r => r.number === 1).state, 'open', 'the recent view of a run wins');
  const p = M.profiles(merged)['KB-DEMO'];
  assert.equal(p.events.filter(e => e.kind === 'w').length, 100);
  assert.equal(p.stage, 2);
});

test('the fox comes to find you when a run needs you or finishes', () => {
  const waiting = M.questsFromRuns([run(4, 'waiting_owner', 'G1', STEPS(['done'], ['active'], ['pending'])),
    run(5, 'waiting_skill', 'S2', STEPS(['active'], ['pending'], ['pending']))]);
  const memo = qs => new Map(qs.map(q => [q.number, { status: q.status, step: q.pending?.step || '', who: q.who, open: q.open }]));
  // First read: only what already waits for you, once.
  assert.deepEqual(M.callouts(new Map(), waiting).map(c => c.number + ':' + c.kind), ['4:needs_owner']);
  // Nothing moved: silent.
  assert.deepEqual(M.callouts(memo(waiting), waiting), []);
  // The Agent finished S2 and the run now waits at G1 for you; #4 finished.
  const later = M.questsFromRuns([run(4, 'done', null, STEPS(['done'], ['done', '核准'], ['done', '已寫入'])),
    run(5, 'waiting_owner', 'G1', STEPS(['done'], ['active'], ['pending']))]);
  const out = M.callouts(memo(waiting), later);
  // What waits for you comes first.
  assert.deepEqual(out.map(c => c.number + ':' + c.kind), ['5:needs_owner', '4:done']);
  assert.match(out[0].text, /#5 WF-DEMO-001 在等你：G1「審查」/);
  // Sent back to the same gate after a revise counts as a new call only if the step changed.
  assert.deepEqual(M.callouts(memo(later), later), []);
});

test('the fox finds its way around buildings', () => {
  // 5x4 grid with a wall in column 2 except the bottom row.
  const wall = new Set(['2,0', '2,1', '2,2']);
  const solid = (x, y) => wall.has(x + ',' + y);
  const path = M.findPath(solid, { x: 0, y: 0 }, { x: 4, y: 0 }, 5, 4);
  assert.equal(path.length, 10);
  assert.deepEqual(path.at(-1), { x: 4, y: 0 });
  assert.ok(path.every(p => !solid(p.x, p.y)));
  assert.deepEqual(M.findPath(solid, { x: 1, y: 1 }, { x: 1, y: 1 }, 5, 4), []);
  assert.equal(M.findPath(solid, { x: 0, y: 0 }, { x: 2, y: 1 }, 5, 4), null);
  assert.equal(M.findPath((x, y) => x === 2, { x: 0, y: 0 }, { x: 4, y: 0 }, 5, 4), null);
});

test('the fox chooses: come when called or with news, otherwise go to work, otherwise wander', () => {
  const doors = { forge: { x: 10, y: 5 }, shop: { x: 17, y: 11 } };
  const quests = M.questsFromRuns([run(3, 'waiting_skill', 'S3', STEPS(['done'], ['done', '核准'], ['active'])),
    run(5, 'waiting_skill', 'S2', STEPS(['active'], ['pending'], ['pending']))]);
  assert.deepEqual(M.foxGoal({ quests, called: true, news: true, doors }), { kind: 'come', reason: 'called' });
  assert.deepEqual(M.foxGoal({ quests, news: true, doors }), { kind: 'come', reason: 'news' });
  assert.deepEqual(M.foxGoal({ quests, doors }), { kind: 'work', number: 5, where: 'forge', tile: { x: 10, y: 5 } });
  assert.equal(M.foxGoal({ quests, focus: 3, doors }).where, 'shop');
  const owner = M.questsFromRuns([run(4, 'waiting_owner', 'G1', STEPS(['done'], ['active'], ['pending']))]);
  assert.deepEqual(M.foxGoal({ quests: owner, doors }), { kind: 'wander' });
});
