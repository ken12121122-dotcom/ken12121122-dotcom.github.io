// Knowledge World model: turns GEN2 runs (gen2-run Issues, read with
// gen2-source.js) into what the game shows. Pure functions, no DOM.
//
// - Each run is a quest; each step happens at a building (Building = Capability):
//   input → quest board, gate → Validation Hall, skill → Research Lab /
//   Knowledge Forge / Automation Workshop by what the step does.
// - A running skill step is an auto battle at its building (work in progress).
// - Growth comes only from validated results (Knowledge World V0): a gate the
//   OWNER passed, and a finished run whose final skill reported success.
//   EXP is a projection of those events, never of chat, time or clicks.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.KWModel = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const BUILDINGS = ['lab', 'forge', 'hall', 'shop', 'board'];
  const SKILL_RULES = [
    [/寫入|執行|發布|同步|回讀|派送|通知|write|publish|sync/i, 'shop'],
    [/研究|搜尋|蒐集|來源|調查|research|search|source/i, 'lab']
  ];
  const STAGES = ['L0 種子', 'L1 學徒', 'L2 工人', 'L3 專家', 'L4 整合者', 'L5 導師'];
  const NEGATIVE = /退回|重做|不寫入|未寫入|未完成|停止|中止|拒絕|否決|取消|調整|重新|revise|reject|stop|cancel|conflict|blocked|retry|adjust|衝突|阻擋/i;
  // A gate counts as passed only when its decision says so; retries and unknown labels do not.
  const PASSED = /^(核准|通過|同意|採用|驗收通過|批准)|approve|accept|\bpass/i;
  const SUCCESS = /寫入|完成|成功|通過|一致|written|done|pass|ok/i;

  function buildingFor(step) {
    if (!step) return 'board';
    if (step.type === 'gate') return 'hall';
    if (step.type === 'input') return 'board';
    const text = (step.title || '') + ' ' + (step.skill || '');
    for (const [re, b] of SKILL_RULES) if (re.test(text)) return b;
    return 'forge';
  }

  function creatureFor(workflowId) {
    const m = /^WF-([A-Z0-9]+)-/i.exec(String(workflowId || ''));
    return m ? 'KB-' + m[1].toUpperCase() : 'KB-?';
  }

  function minutesSince(iso, now) {
    const a = Date.parse(iso || ''), b = Date.parse(now || '');
    return Number.isFinite(a) && Number.isFinite(b) ? Math.max(0, Math.round((b - a) / 60000)) : null;
  }

  // One quest per run, with where the current step happens and who must act.
  function questFromRun(r, now) {
    const v = r.view || {};
    const steps = (Array.isArray(v.steps) ? v.steps : []).map(s => ({
      id: s.id, type: s.type, title: s.title || s.id, status: s.status, result: s.result || null,
      attempts: s.attempts || 0, building: buildingFor(s)
    }));
    const open = r.state === 'open' && v.status !== 'done' && v.status !== 'stopped';
    const pending = open && v.pending ? v.pending : null;
    const current = pending ? (steps.find(s => s.id === pending.step) || { id: pending.step, type: pending.type, title: pending.title }) : null;
    const where = current ? buildingFor({ ...current, skill: pending.skill }) : null;
    const who = !open ? null : v.status === 'waiting_skill' ? 'agent' : 'owner';
    return {
      number: r.number, url: r.url || '', open, status: v.status, who, where,
      workflowId: v.workflow?.id || '', workflowTitle: v.workflow?.title || '',
      creature: creatureFor(v.workflow?.id), pending, steps,
      attempt: pending?.attempt || 1, minutes: minutesSince(v.updatedAt || r.updatedAt, now)
    };
  }

  // Open runs that wait for the OWNER first, then Agent work, then finished ones.
  function questsFromRuns(runs, now) {
    const rank = q => (q.open ? (q.who === 'owner' ? 0 : 1) : 2);
    return (Array.isArray(runs) ? runs : []).map(r => questFromRun(r, now))
      .sort((a, b) => rank(a) - rank(b) || b.number - a.number);
  }

  // Growth events derived from what the runs show; ids are stable so the same
  // event is never counted twice.
  function growthFromRuns(runs) {
    const events = [];
    for (const r of Array.isArray(runs) ? runs : []) {
      const v = r.view || {}, creature = creatureFor(v.workflow?.id);
      const steps = Array.isArray(v.steps) ? v.steps : [];
      if (v.status === 'stopped') continue; // a stopped run produced no validated result
      for (const s of steps) {
        if (s.type === 'gate' && s.status === 'done' && s.result && PASSED.test(s.result) && !NEGATIVE.test(s.result)) {
          events.push({ id: `GE-${r.number}-${s.id}`, creature, run: r.number, kind: 'k', amount: 30, what: `${s.id} 通過 OWNER 審查（${s.result}）` });
        }
      }
      if (v.status === 'done') {
        const lastSkill = [...steps].reverse().find(s => s.type === 'skill' && s.status === 'done');
        if (lastSkill && (!lastSkill.result || (SUCCESS.test(lastSkill.result) && !NEGATIVE.test(lastSkill.result)))) {
          events.push({ id: `GE-${r.number}-WORK`, creature, run: r.number, kind: 'w', amount: 40, what: `#${r.number} 完成並驗證` });
          events.push({ id: `GE-${r.number}-${lastSkill.id}`, creature, run: r.number, kind: 's', amount: 20, what: `${lastSkill.id} 成功執行` });
        }
      }
    }
    const seen = new Set();
    return events.filter(e => !seen.has(e.id) && seen.add(e.id));
  }

  // Recent runs plus the finished-run history, newest view of each run wins.
  function mergeRuns(recent, history) {
    const byNumber = new Map();
    for (const r of Array.isArray(history) ? history : []) byNumber.set(r.number, r);
    for (const r of Array.isArray(recent) ? recent : []) byNumber.set(r.number, r);
    return [...byNumber.values()];
  }

  // Character sheets: three EXP channels and a development stage.
  function profiles(runs) {
    const out = {};
    const get = name => out[name] || (out[name] = { name, k: 0, w: 0, s: 0, stage: 0, events: [] });
    for (const q of questsFromRuns(runs)) get(q.creature);
    for (const e of growthFromRuns(runs)) {
      const p = get(e.creature);
      p[e.kind] = Math.min(100, p[e.kind] + e.amount);
      p.events.push(e);
    }
    for (const p of Object.values(out)) {
      const works = p.events.filter(e => e.kind === 'w').length;
      p.stage = works >= 3 ? 2 : p.events.length ? 1 : 0;
      p.gap = p.stage === 0 ? '還沒有通過驗證的成長'
        : p.stage === 1 ? `L2 需要 3 次完成並驗證的工作（目前 ${works} 次）`
        : 'L3 需要在明確領域證明可靠的技能（尚未定義門檻）';
    }
    return out;
  }

  // The /gen2 command for an OWNER action, shown for copying when the page is
  // not inside the app. The engine re-validates whatever is posted.
  function commandFor(q, optionId) {
    if (!q || !q.pending) return '';
    if (q.pending.type === 'input') return '/gen2 input ' + q.pending.step + '\n（在這一行之後貼上資料）';
    if (q.pending.type === 'gate') {
      const o = (q.pending.options || []).find(x => x.id === optionId);
      if (!o) return '';
      return '/gen2 decide ' + q.pending.step + ' ' + o.id + (o.comment === 'required' ? '\n（在這一行之後寫說明）' : '');
    }
    return '';
  }

  // When the fox should come and find the player: a run that starts waiting
  // for the OWNER (or waits at a new step), and a run that just finished.
  // prev: Map(number -> {status, step, who, open}) from the last read; the
  // first read (prev empty) only reports runs already waiting, once.
  function callouts(prev, quests) {
    const out = [];
    const first = !prev || prev.size === 0;
    for (const q of quests || []) {
      const before = prev && prev.get(q.number);
      const step = q.pending?.step || '';
      if (q.open && q.who === 'owner' && (first || !before || before.who !== 'owner' || before.step !== step)) {
        out.push({ number: q.number, kind: 'needs_owner', where: q.where, step, title: q.pending?.title || '',
          text: '#' + q.number + ' ' + q.workflowId + ' 在等你：' + step + '「' + (q.pending?.title || '') + '」' });
      } else if (!first && before && before.open && !q.open) {
        out.push({ number: q.number, kind: q.status === 'done' ? 'done' : 'stopped', where: null, step: '', title: '',
          text: '#' + q.number + ' ' + q.workflowId + (q.status === 'done' ? ' 完成了！' : ' 已經停止。') });
      }
    }
    return out;
  }

  // Signature of what the world shows, to know when something moved.
  function worldSignature(quests) {
    return quests.map(q => [q.number, q.status, q.pending?.step || '', q.attempt].join(':')).join('|');
  }

  return { BUILDINGS, STAGES, callouts, mergeRuns, buildingFor, creatureFor, questFromRun, questsFromRuns, growthFromRuns, profiles, commandFor, worldSignature };
});
