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
  for (const needle of ['<script src="gen2-source.js?v=', 'id="gen2FolderBtn"', 'id="gen2FolderModal"', 'id="gen2ProjectionBtn"', "gen2_workflow:'", 'GEN2.buildProject(']) {
    assert.ok(html.includes(needle), 'index.html should contain ' + needle);
  }
  assert.ok(html.indexOf('gen2-source.js') < html.indexOf('const RAW_DATA='), 'parser loads before the app script');
});
