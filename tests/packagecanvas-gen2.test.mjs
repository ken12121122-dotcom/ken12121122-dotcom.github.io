import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import test from 'node:test';

const require = createRequire(import.meta.url);
const Gen2 = require('../packagecanvas/gen2-source.js');

// Synthetic GEN2 folder shaped like the real controlled structure (no private content).
const KB = '02_KNOWLEDGE_BASES/KB-DEMO_示範';
const files = [
  { path: '00_ENTRY/README.md', text: '---\ndocument_id: GEN2-00-README\ntype: stage_rule\nstage: "00"\nstatus: draft\n---\n\n# 00_ENTRY｜任務入口\n\n讀取 [[KB_REGISTRY]]。' },
  { path: '00_ENTRY/KB_REGISTRY.md', text: '---\ndocument_id: GEN2-KB-REGISTRY\ntype: registry\nstatus: active\n---\n\n# 知識庫登記表\n\n| kb_id | 名稱 | 一句用途 | 狀態 | 資料夾 |\n|---|---|---|---|---|\n| KB-DEMO | 示範 | 測試 | active | KB-DEMO_示範 |\n| KB-GHOST | 幽靈 | 沒有資料夾 | active | KB-GHOST_幽靈 |\n' },
  { path: '02_KNOWLEDGE_BASES/README.md', text: '---\ndocument_id: GEN2-02-README\ntype: stage_rule\n---\n\n# 02 容器' },
  { path: `${KB}/KB_CONTRACT.md`, text: '---\ndocument_type: kb_contract\nkb_id: KB-DEMO\nkb_name: 示範\nstatus: draft\n---\n\n# KB Contract｜示範\n\n- Resource Refs：`[[KB-DEMO_示範/RESOURCE_REFS]]`' },
  { path: `${KB}/RESOURCE_REFS.md`, text: '---\ndocument_type: resource_refs\nkb_id: KB-DEMO\n---\n\n# Resource Refs\n\n- Contract：`[[KB-DEMO_示範/KB_CONTRACT]]`\n\n| resource_id | 名稱 | 用途 |\n|---|---|---|\n| RES-DEMO-001 | 行事曆 | 正式來源 |\n' },
  { path: `${KB}/03_WORKFLOW_BOM/BOM.md`, text: '---\ndocument_type: workflow_bom\nkb_id: KB-DEMO\n---\n\n# Workflow BOM\n\n- Contract：`[[KB-DEMO_示範/KB_CONTRACT]]`\n\n| workflow_id | 名稱 | 使用 Skill | 狀態 |\n|---|---|---|---|\n| [[WF-DEMO-001]] | 示範流程 | [[SK-DEMO-001]], [[SK-DEMO-009]] | 已建立 |\n| [[WF-DEMO-404]] | 不存在 | | 已建立 |\n\n| skill_id | 名稱 | 狀態 |\n|---|---|---|\n| [[SK-DEMO-001]] | 擷取 | 已建立 |\n' },
  { path: `${KB}/04_WORKFLOWS/WF-DEMO-001.md`, text: '---\ndocument_type: workflow\nworkflow_id: WF-DEMO-001\nkb_id: KB-DEMO\nstatus: draft\n---\n\n# WF-DEMO-001｜示範流程\n\n- Contract：`[[KB-DEMO_示範/KB_CONTRACT]]`\n- [[SK-DEMO-001]]\n- [[SK-DEMO-009]]\n\n## OWNER Gate\n- Gate 1' },
  { path: `${KB}/04_WORKFLOWS/WF-DEMO-002.md`, text: '---\ndocument_type: workflow\nworkflow_id: WF-DEMO-002\nkb_id: KB-DEMO\n---\n\n# WF-DEMO-002｜沒有 Gate\n\n參考 [[不存在的文件]]' },
  { path: `${KB}/05_SKILLS/SK-DEMO-001.md`, text: '---\ndocument_type: skill\nskill_id: SK-DEMO-001\nkb_id: KB-DEMO\n---\n\n# SK-DEMO-001｜擷取\n\n使用 RES-DEMO-001 與 RES-DEMO-404。' },
  { path: `${KB}/05_SKILLS/notes.md`, text: '# 隨手筆記\n\n沒有 frontmatter。' },
  { path: '02_KNOWLEDGE_BASES/KB-EXTRA_未登記/KB_CONTRACT.md', text: '---\ndocument_type: kb_contract\nkb_id: KB-EXTRA\n---\n\n# KB Contract｜未登記' },
  { path: '02_KNOWLEDGE_BASES/_KB_TEMPLATE/04_WORKFLOWS/WORKFLOW_TEMPLATE.md', text: '---\ndocument_type: workflow\nworkflow_id: WF-XXX-001\n---\n\n# 範本 [[SK-XXX-001]]' },
  { path: '_CHATGPT_GEN2_WRITEBACK/KB-BATCH-001_CANDIDATE_SKILLS/01_SK-ENG-001.md', text: '---\ndocument_type: skill\nskill_id: SK-ENG-001\nstatus: candidate\n---\n\n# 候選 Skill' },
  { path: '09_待刪除/old.md', text: '# 舊檔' }
];
const dirs = ['00_ENTRY', '02_KNOWLEDGE_BASES', KB, `${KB}/03_WORKFLOW_BOM`, `${KB}/04_WORKFLOWS`, `${KB}/05_SKILLS`, '02_KNOWLEDGE_BASES/KB-EXTRA_未登記'];

function build() {
  return Gen2.buildProject({ rootName: '第二代知識庫', files, dirs }, { now: '2026-09-30T00:00:00Z' });
}

// Mirrors validateProject() in packagecanvas/index.html.
function assertValidProject(o) {
  const nodeIds = new Set(o.nodes.map(n => n.id));
  const groupIds = new Set(o.groups.map(g => g.id));
  assert.equal(nodeIds.size, o.nodes.length, 'node ids unique');
  assert.equal(groupIds.size, o.groups.length, 'group ids unique');
  assert.equal(new Set(o.links.map(l => l.id)).size, o.links.length, 'relation ids unique');
  for (const l of o.links) assert.ok(nodeIds.has(l.from) && nodeIds.has(l.to), 'relation endpoint ' + l.id);
  for (const n of o.nodes) {
    assert.ok(groupIds.has(n.groupId), 'node parent ' + n.id);
    assert.ok([n.x, n.y, n.w, n.h].every(Number.isFinite), 'node geometry ' + n.id);
  }
  for (const g of o.groups) assert.equal(g.parentId, o.assistant.id);
}

const codes = r => r.issues.map(i => i.code);
const node = (p, pathEnd) => p.nodes.find(n => n.sourcePath.endsWith(pathEnd));

test('GEN2 folder becomes a valid PackageCanvas project', () => {
  const { project, report } = build();
  assertValidProject(project);
  assert.deepEqual(report.kbIds, ['KB-DEMO', 'KB-EXTRA']);
  assert.deepEqual(report.skipped, ['09_待刪除/old.md']);
  assert.ok(project.groups.some(g => g.id === 'g2grp_KB-DEMO' && g.title === 'KB-DEMO｜示範'));
  assert.ok(project.groups.some(g => g.id === 'g2grp_template'));
  assert.ok(project.groups.some(g => g.id === 'g2grp_candidates'));
  assert.equal(node(project, 'WF-DEMO-001.md').kind, 'gen2_workflow');
  assert.equal(node(project, 'SK-DEMO-001.md').kind, 'gen2_skill');
  assert.equal(node(project, '01_SK-ENG-001.md').kind, 'gen2_candidate');
  assert.equal(node(project, 'WORKFLOW_TEMPLATE.md').kind, 'gen2_template');
  assert.ok(project.nodes.some(n => n.title.startsWith('RES-DEMO-001') && n.kind === 'gen2_resource'));
});

test('node and relation ids are stable across re-reads', () => {
  const a = build().project, b = build().project;
  assert.deepEqual(a.nodes.map(n => n.id), b.nodes.map(n => n.id));
  assert.deepEqual(a.links.map(l => l.id), b.links.map(l => l.id));
});

test('relations follow wiki links, BOM, registry and resource references', () => {
  const { project } = build();
  const rel = (fromEnd, toEnd) => {
    const f = node(project, fromEnd), t = node(project, toEnd);
    return project.links.find(l => l.from === f.id && l.to === t.id);
  };
  assert.equal(rel('WF-DEMO-001.md', 'SK-DEMO-001.md')?.type, 'main');
  assert.equal(rel('BOM.md', 'WF-DEMO-001.md')?.label, 'BOM 登記');
  assert.equal(rel('WF-DEMO-001.md', 'KB-DEMO_示範/KB_CONTRACT.md')?.label, '所屬 KB');
  assert.equal(rel('KB_REGISTRY.md', 'KB-DEMO_示範/KB_CONTRACT.md')?.label, '登記');
  const res = project.nodes.find(n => n.title.startsWith('RES-DEMO-001'));
  const sk = node(project, 'SK-DEMO-001.md');
  assert.ok(project.links.some(l => l.from === sk.id && l.to === res.id && l.label === '引用 Resource'));
});

test('architecture check finds missing skill, resource, workflow file, gate and registry gaps', () => {
  const { project, report } = build();
  const c = codes(report);
  for (const expected of ['missing_skill', 'missing_resource', 'bom_missing_file', 'workflow_no_gate', 'broken_link', 'registry_missing_folder', 'unregistered_kb', 'bom_unlisted', 'uncontrolled_md', 'kb_missing_folder']) {
    assert.ok(c.includes(expected), 'expected issue ' + expected + ' in ' + c.join(','));
  }
  assert.ok(report.issues.some(i => i.message.includes('SK-DEMO-009')));
  assert.ok(report.issues.some(i => i.message.includes('RES-DEMO-404')));
  assert.ok(report.issues.some(i => i.message.includes('KB-GHOST')));
  assert.ok(report.issues.some(i => i.message.includes('06_OUTPUTS')));
  // templates and candidates never produce missing-skill errors
  assert.ok(!report.issues.some(i => i.message.includes('SK-XXX-001')));
  const reportNode = project.nodes.find(n => n.id === 'g2_check_report');
  assert.match(reportNode.md, /GEN2 架構檢查/);
  assert.match(node(project, 'WF-DEMO-002.md').review_status, /⚠/);
});

test('re-reading keeps manual node positions', () => {
  const first = build().project;
  const moved = structuredClone(first);
  const wf = moved.nodes.find(n => n.sourcePath.endsWith('WF-DEMO-001.md'));
  wf.x += 777;
  moved.view = { zoom: 120, panX: 5, panY: 6, mode: 'md' };
  const merged = Gen2.mergeLayout(build().project, moved);
  assert.equal(merged.nodes.find(n => n.id === wf.id).x, wf.x);
  assert.equal(merged.view.mode, 'md');
  assertValidProject(merged);
});

test('PackageCanvas page loads the GEN2 folder source and keeps existing entry points', async () => {
  const html = await readFile(new URL('../packagecanvas/index.html', import.meta.url), 'utf8');
  for (const needle of ['<script src="gen2-source.js?v=', 'id="gen2GhReadBtn"', 'GEN2.sources.github.collect(', 'id="gen2FolderBtn"', 'id="gen2FolderModal"', 'id="gen2ProjectionBtn"', "gen2_workflow:'", 'GEN2.buildProject(']) {
    assert.ok(html.includes(needle), 'index.html should contain ' + needle);
  }
  assert.ok(html.indexOf('gen2-source.js') < html.indexOf('const RAW_DATA='), 'parser loads before the app script');
});

// Fake GitHub REST API serving the synthetic folder above under kb/ on two branches.
function fakeGitHub({ token = 'tok', truncate = false, pullCount = 0 } = {}) {
  const enc = new TextEncoder();
  const commits = { main: 'a'.repeat(40), 'gen2/candidate': 'b'.repeat(40) };
  const blobs = new Map();
  const tree = [{ path: 'README.md', type: 'blob', sha: 'r0', size: 10 }, { path: 'kb', type: 'tree', sha: 't0' }, { path: 'kb/.obsidian', type: 'tree', sha: 't1' }, { path: 'kb/.obsidian/x.md', type: 'blob', sha: 'o1', size: 3 }];
  // Like real GitHub: every ancestor directory of a file is a tree entry.
  const allDirs = new Set(dirs);
  for (const f of files) { const parts = f.path.split('/'); for (let i = 1; i < parts.length; i++) allDirs.add(parts.slice(0, i).join('/')); }
  [...allDirs].forEach((d, i) => tree.push({ path: 'kb/' + d, type: 'tree', sha: 'd' + i }));
  files.forEach((f, i) => { const sha = 'f' + i; blobs.set(sha, enc.encode(f.text)); tree.push({ path: 'kb/' + f.path, type: 'blob', sha, size: blobs.get(sha).length }); });
  const calls = [];
  const fetchImpl = async (url, init) => {
    calls.push({ url, init });
    const auth = init.headers.Authorization === 'Bearer ' + token;
    const json = (status, body) => ({ ok: status < 300, status, json: async () => body, text: async () => JSON.stringify(body) });
    if (!auth) return json(401, {});
    const u = new URL(url);
    const m = u.pathname.match(/^\/repos\/ken12121122-dotcom\/gen2-knowledge\/(.*)$/);
    if (!m) return json(404, {});
    let r;
    if ((r = m[1].match(/^commits\/(.+)$/))) {
      const sha = commits[decodeURIComponent(r[1])];
      assert.equal(init.headers.Accept, 'application/vnd.github.sha');
      return sha ? { ok: true, status: 200, text: async () => sha } : json(404, {});
    }
    if ((r = m[1].match(/^git\/trees\/([0-9a-f]{40})$/))) {
      if (u.searchParams.get('recursive') && !truncate) return json(200, { sha: r[1], tree, truncated: false });
      if (u.searchParams.get('recursive')) return json(200, { sha: r[1], tree: tree.slice(0, 3), truncated: true });
      return json(200, { sha: r[1], tree: tree.filter(e => !e.path.includes('/')), truncated: false });
    }
    if ((r = m[1].match(/^git\/trees\/(t0|t1|d.+)$/))) {
      // non-recursive subtree listing: entries directly under the directory owning this sha
      const dir = tree.find(e => e.type === 'tree' && e.sha === r[1] && (r[1] !== 't0' || e.path === 'kb'));
      const dirPath = r[1] === 't0' ? 'kb' : dir.path;
      return json(200, { sha: r[1], tree: tree.filter(e => e.path.startsWith(dirPath + '/') && !e.path.slice(dirPath.length + 1).includes('/')).map(e => ({ ...e, path: e.path.slice(dirPath.length + 1) })), truncated: false });
    }
    if ((r = m[1].match(/^git\/blobs\/(.+)$/))) {
      const b = blobs.get(r[1]);
      assert.equal(init.headers.Accept, 'application/vnd.github.raw+json');
      return b ? { ok: true, status: 200, arrayBuffer: async () => b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength) } : json(404, {});
    }
    if (m[1] === 'pulls' && pullCount) {
      const page = Number(u.searchParams.get('page')), per = Number(u.searchParams.get('per_page'));
      const all = Array.from({ length: pullCount }, (_, i) => ({ number: i + 1, title: 'p' + i, head: { ref: 'b' + i, repo: { full_name: 'ken12121122-dotcom/gen2-knowledge' } } }));
      return json(200, all.slice((page - 1) * per, page * per));
    }
    if (m[1] === 'pulls') return json(200, [
      { number: 7, title: '候選 MD', draft: false, updated_at: '2026-10-01T00:00:00Z', head: { ref: 'gen2/candidate', repo: { full_name: 'ken12121122-dotcom/gen2-knowledge' } } },
      { number: 8, title: 'fork', head: { ref: 'x', repo: { full_name: 'someone/fork' } } }
    ]);
    return json(404, {});
  };
  return { fetchImpl, calls };
}

test('GitHub source reads kb/ at a pinned commit and builds the same project as the folder', async () => {
  const { fetchImpl, calls } = fakeGitHub();
  const src = await Gen2.sources.github.collect({ token: 'tok' }, null, fetchImpl);
  assert.equal(src.key, 'github:ken12121122-dotcom/gen2-knowledge@main');
  assert.equal(src.commit, 'a'.repeat(40));
  assert.equal(src.layoutFrom, null);
  assert.equal(src.files.length, files.length, 'every .md under kb/ is read, dot-folders skipped');
  assert.ok(!src.dirs.some(d => d.startsWith('.')), 'dot dirs skipped');
  assert.ok(calls.every(c => c.url.startsWith('https://api.github.com/')), 'only api.github.com is contacted');
  const viaGithub = Gen2.buildProject(src, { now: '2026-09-30T00:00:00Z' });
  const viaFolder = Gen2.buildProject({ rootName: src.rootName, files, dirs }, { now: '2026-09-30T00:00:00Z' });
  assert.deepEqual(viaGithub.report.counts, viaFolder.report.counts);
  assert.deepEqual(viaGithub.project.nodes.map(n => n.id).sort(), viaFolder.project.nodes.map(n => n.id).sort());
});

test('GitHub source reads a PR branch with layout seeded from main and caches blobs', async () => {
  const { fetchImpl, calls } = fakeGitHub();
  await Gen2.sources.github.collect({ token: 'tok' }, null, fetchImpl);
  const before = calls.length;
  const src = await Gen2.sources.github.collect({ token: 'tok', ref: 'gen2/candidate' }, null, fetchImpl);
  assert.equal(src.key, 'github:ken12121122-dotcom/gen2-knowledge@gen2/candidate');
  assert.equal(src.layoutFrom, 'github:ken12121122-dotcom/gen2-knowledge@main');
  assert.ok(calls.slice(before).some(c => c.url.endsWith('/commits/gen2/candidate')), 'branch path is not percent-encoded across slashes');
  assert.equal(calls.length - before, 2, 'unchanged blobs come from cache');
  const pulls = await Gen2.sources.github.listPulls({ token: 'tok' }, fetchImpl);
  assert.deepEqual(pulls.map(p => [p.number, p.ref]), [[7, 'gen2/candidate']], 'fork PRs are ignored');
});

test('GitHub source reports token and access errors and rejects unsafe config', async () => {
  const { fetchImpl } = fakeGitHub();
  await assert.rejects(Gen2.sources.github.collect({ token: 'bad' }, null, fetchImpl), /token 無效/);
  await assert.rejects(Gen2.sources.github.collect({ token: 'tok', repo: 'other' }, null, fetchImpl), /找不到/);
  assert.throws(() => Gen2.sources.github.config({ owner: 'a/b' }), /格式/);
  assert.throws(() => Gen2.sources.github.config({ ref: '../main' }), /格式/);
});

test('GitHub source walks the tree level by level when the recursive tree is truncated', async () => {
  const { fetchImpl } = fakeGitHub({ truncate: true });
  const src = await Gen2.sources.github.collect({ token: 'tok' }, null, fetchImpl);
  const full = await Gen2.sources.github.collect({ token: 'tok' }, null, fakeGitHub().fetchImpl);
  assert.equal(src.truncated, false);
  assert.equal(src.files.length, files.length, 'every .md is still read');
  assert.deepEqual([...src.dirs].sort(), [...full.dirs].sort());
  assert.deepEqual(src.files.map(f => f.path).sort(), full.files.map(f => f.path).sort());
});

test('GitHub PR list follows every page', async () => {
  const { fetchImpl, calls } = fakeGitHub({ pullCount: 230 });
  const pulls = await Gen2.sources.github.listPulls({ token: 'tok' }, fetchImpl);
  assert.equal(pulls.length, 230);
  assert.equal(calls.filter(c => c.url.includes('/pulls?')).length, 3);
});

test('saved graph layouts are dropped when the fresh project has entities they do not cover', () => {
  const { project: prev } = build();
  prev.graphLayouts = {
    md: prev.nodes.map(n => [n.id, { x: 1, y: 2 }]),
    group: prev.groups.map(g => [g.id, { x: 3, y: 4 }]),
    assistant: [[prev.assistant.id, { x: 5, y: 6, pinned: true }]]
  };
  const same = Gen2.mergeLayout(build().project, prev);
  assert.equal(same.graphLayouts.md, prev.graphLayouts.md, 'unchanged project keeps the MD graph layout');
  assert.equal(same.graphLayouts.group, prev.graphLayouts.group);
  const extra = { path: '02_KNOWLEDGE_BASES/KB-NEW_新/KB_CONTRACT.md', text: '---\ndocument_type: kb_contract\nkb_id: KB-NEW\n---\n\n# 新' };
  const grown = Gen2.mergeLayout(Gen2.buildProject({ rootName: '第二代知識庫', files: files.concat([extra]), dirs }, { now: '2026-09-30T00:00:00Z' }).project, prev);
  assert.equal(grown.graphLayouts.md, null, 'a new node forces a fresh MD graph layout');
  assert.equal(grown.graphLayouts.group, null, 'a new group forces a fresh Group graph layout');
  assert.deepEqual(grown.graphLayouts.assistant, prev.graphLayouts.assistant);
});

// ---------- gen2-run executable workflow spec ----------
const RUN_SPEC = {
  version: 1,
  steps: [
    { id: 'S1', type: 'input', title: '提供來源', prompt: '貼上工作紀錄' },
    { id: 'S2', type: 'skill', skill: 'SK-DEMO-001', title: '擷取' },
    { id: 'G1', type: 'gate', title: '審核', review: 'S2', options: [
      { id: 'approve', label: '核准', next: 'end' },
      { id: 'revise', label: '退回', next: 'S2', comment: 'required' }
    ] }
  ]
};
const runBlock = spec => '\n## 執行定義\n\n```gen2-run\n' + JSON.stringify(spec, null, 2) + '\n```\n';

test('gen2-run spec parses and validates the step graph', () => {
  const ok = Gen2.parseRunSpec('# WF\n' + runBlock(RUN_SPEC));
  assert.equal(ok.found, true);
  assert.deepEqual(ok.errors, []);
  assert.equal(Gen2.parseRunSpec('# 沒有定義').found, false);
  assert.match(Gen2.parseRunSpec('```gen2-run\n{bad\n```').errors[0], /JSON/);
  const bad = structuredClone(RUN_SPEC);
  bad.steps[2].options[1].next = 'S9';
  bad.steps.push({ id: 'S2', type: 'skill', skill: 'X', title: '' });
  const errs = Gen2.validateRunSpec(bad);
  assert.ok(errs.some(e => /重複/.test(e)) && errs.some(e => /SK-xxx/.test(e)) && errs.some(e => /缺 title/.test(e)), errs.join('\n'));
  const loop = { version: 1, steps: [{ id: 'A', type: 'skill', skill: 'SK-A', title: 'a', next: 'B' }, { id: 'B', type: 'skill', skill: 'SK-B', title: 'b', next: 'A' }] };
  assert.ok(Gen2.validateRunSpec(loop).some(e => /走不到 end/.test(e)));
  const orphan = { version: 1, steps: [{ id: 'A', type: 'input', title: 'a', next: 'end' }, { id: 'B', type: 'input', title: 'b' }] };
  assert.ok(Gen2.validateRunSpec(orphan).some(e => /B 從起點走不到/.test(e)));
  assert.ok(Gen2.validateRunSpec(RUN_SPEC, { skillIds: new Set(['sk-other']) }).some(e => /SK-DEMO-001 不存在/.test(e)));
});

test('architecture check reports runnable, invalid and missing gen2-run definitions', () => {
  const withRun = files.map(f => f.path.endsWith('WF-DEMO-001.md') ? { ...f, text: f.text + runBlock(RUN_SPEC) } : f);
  const r1 = Gen2.buildProject({ rootName: 'x', files: withRun, dirs }).report;
  assert.ok(!r1.issues.some(i => i.code === 'run_spec_invalid'));
  assert.ok(r1.issues.some(i => i.code === 'workflow_not_runnable' && /WF-DEMO-002/.test(i.message)));
  assert.ok(!r1.issues.some(i => i.code === 'workflow_not_runnable' && /WF-DEMO-001/.test(i.message)));
  const broken = structuredClone(RUN_SPEC);
  broken.steps[1].skill = 'SK-DEMO-404';
  const withBad = files.map(f => f.path.endsWith('WF-DEMO-001.md') ? { ...f, text: f.text + runBlock(broken) } : f);
  const r2 = Gen2.buildProject({ rootName: 'x', files: withBad, dirs }).report;
  const bad = r2.issues.find(i => i.code === 'run_spec_invalid');
  assert.equal(bad?.severity, 'error');
  assert.match(bad.message, /SK-DEMO-404 不存在/);
});

test('gen2-run supports skill outcomes and a cancel terminal', () => {
  const spec = {
    version: 1,
    steps: [
      { id: 'S1', type: 'skill', skill: 'SK-A', title: '寫入', outcomes: [
        { id: 'written', label: '已寫入', next: 'G1' },
        { id: 'conflict', label: '衝突', next: 'G2' }
      ] },
      { id: 'G1', type: 'gate', title: '驗收', review: 'S1', options: [{ id: 'pass', label: '通過', next: 'end' }, { id: 'reject', label: '否決', next: 'cancel' }] },
      { id: 'G2', type: 'gate', title: '處理衝突', options: [{ id: 'retry', label: '調整', next: 'S1', comment: 'required' }, { id: 'stop', label: '停止', next: 'cancel' }] }
    ]
  };
  assert.deepEqual(Gen2.validateRunSpec(spec), []);
  const withNext = structuredClone(spec);
  withNext.steps[0].next = 'G1';
  assert.ok(Gen2.validateRunSpec(withNext).some(e => /不要另設 next/.test(e)));
  const onInput = structuredClone(spec);
  onInput.steps[0].type = 'input';
  assert.ok(Gen2.validateRunSpec(onInput).some(e => /只有 skill 可以有 outcomes/.test(e)));
  const cancelOnly = { version: 1, steps: [{ id: 'A', type: 'input', title: 'a', next: 'cancel' }] };
  assert.ok(Gen2.validateRunSpec(cancelOnly).some(e => /走不到 end/.test(e)), 'a flow must still be able to complete');
  assert.ok(Gen2.validateRunSpec({ version: 1, steps: [{ id: 'cancel', type: 'input', title: 'x' }] }).some(e => /不能是 end 或 cancel/.test(e)));
});
