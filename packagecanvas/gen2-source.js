/*
 * PackageCanvas · GEN2 folder source
 *
 * Reads a GEN2 knowledge-base folder (Markdown files only, read-only) and turns it
 * into a PackageCanvas project: KB → Group, controlled MD → Node, Wiki Link /
 * BOM / Registry / Resource references → Relation, plus an architecture check.
 *
 * Pure parsing (buildProject) has no DOM dependency so it can be tested in Node.
 * Knowledge-base access goes through one of four read-only sources:
 *   native   – Amin Pocket GBA `AminPackageCanvasFiles` bridge (Android SAF)
 *   picker   – desktop browser showDirectoryPicker()
 *   input    – <input webkitdirectory> fallback
 *   github   – the official GitHub repository (read-only token, REST API)
 * Nothing here writes to the knowledge base.
 */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.PackageCanvasGen2 = api;
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const VERSION = 'gen2-source 0.3';
  const MAX_FILES = 3000;
  const MAX_FILE_BYTES = 2 * 1024 * 1024;
  const NODE_W = 380, NODE_H = 240, GAP = 40, PAD_X = 45, PAD_TOP = 60, PAD_BOTTOM = 45;
  const ORIGIN = { x: 5000, y: 3000 };

  const CONTROLLED = {
    kb_contract: { kind: 'gen2_contract', semantic: 'KBContract', order: 0 },
    resource_refs: { kind: 'gen2_resource', semantic: 'ResourceRefs', order: 1 },
    workflow_bom: { kind: 'gen2_bom', semantic: 'WorkflowBOM', order: 2 },
    workflow: { kind: 'gen2_workflow', semantic: 'Workflow', order: 3 },
    skill: { kind: 'gen2_skill', semantic: 'Skill', order: 4 },
    output: { kind: 'gen2_output', semantic: 'Output', order: 6 }
  };
  const RULE_TYPES = new Set(['stage_rule', 'registry', 'rule']);
  const KB_PALETTE = ['#5b91d0', '#55aa87', '#9a83c8', '#e0955a', '#6498ad', '#c77aa6', '#7fa35b', '#b8925a'];

  // ---------- small helpers ----------
  function hash(text) {
    let h = 0x811c9dc5;
    const s = String(text);
    for (let i = 0; i < s.length; i++) {
      h ^= s.charCodeAt(i);
      h = Math.imul(h, 0x01000193);
    }
    return (h >>> 0).toString(36);
  }
  function normPath(p) {
    return String(p || '').replace(/\\/g, '/').replace(/^\/+/, '').replace(/\/+/g, '/');
  }
  function baseName(p) {
    const parts = normPath(p).split('/');
    return parts[parts.length - 1] || '';
  }
  function stripMd(name) {
    return String(name).replace(/\.md$/i, '');
  }
  function lc(s) {
    return String(s || '').trim().toLowerCase();
  }

  // ---------- Markdown parsing ----------
  function parseFrontmatter(text) {
    const m = /^﻿?---[ \t]*\r?\n([\s\S]*?)\r?\n---[ \t]*(?:\r?\n|$)/.exec(text);
    if (!m) return { data: null, body: text };
    const data = {};
    for (const line of m[1].split(/\r?\n/)) {
      const kv = /^([A-Za-z_][A-Za-z0-9_\-]*)\s*:\s*(.*)$/.exec(line);
      if (!kv) continue;
      let v = kv[2].trim();
      if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) v = v.slice(1, -1);
      data[kv[1]] = v;
    }
    return { data, body: text.slice(m[0].length) };
  }

  function wikiLinks(text) {
    const out = [];
    const re = /\[\[([^\]\|#\n]+)(?:#[^\]\|\n]*)?(?:\|[^\]\n]*)?\]\]/g;
    let m;
    while ((m = re.exec(text))) out.push(m[1].trim());
    return out;
  }

  function splitRow(line) {
    let s = line.trim();
    if (s.startsWith('|')) s = s.slice(1);
    if (s.endsWith('|')) s = s.slice(0, -1);
    return s.split('|').map(c => c.trim());
  }

  function parseTables(text) {
    const lines = text.split(/\r?\n/);
    const tables = [];
    for (let i = 0; i < lines.length - 1; i++) {
      if (!/^\s*\|/.test(lines[i]) || !/^\s*\|?\s*:?-{2,}/.test(lines[i + 1])) continue;
      const headers = splitRow(lines[i]).map(h => h.replace(/\\_/g, '_'));
      const rows = [];
      let j = i + 2;
      for (; j < lines.length && /^\s*\|/.test(lines[j]); j++) {
        const cells = splitRow(lines[j]);
        const row = {};
        headers.forEach((h, k) => { row[h] = cells[k] ?? ''; });
        rows.push(row);
      }
      tables.push({ headers, rows });
      i = j - 1;
    }
    return tables;
  }

  function cellId(cell) {
    const link = wikiLinks(cell)[0];
    return (link || String(cell || '').replace(/[`*]/g, '')).trim();
  }

  function firstHeading(body) {
    return (/^#\s+(.+)$/m.exec(body)?.[1] || '').trim();
  }

  // ---------- classification ----------
  function classify(path, fm) {
    const segs = normPath(path).split('/');
    const info = { kbId: null, kbName: null, kbFolder: null, template: false, candidate: false };
    segs.slice(0, -1).forEach((seg, i) => {
      if (seg === '_KB_TEMPLATE') info.template = true;
      if (/CANDIDATE|WRITEBACK/i.test(seg)) info.candidate = true;
      const kb = /^(KB-[A-Za-z0-9]+)_(.+)$/.exec(seg);
      if (kb && !info.kbId) {
        info.kbId = kb[1];
        info.kbName = kb[2];
        info.kbFolder = segs.slice(0, i + 1).join('/');
      }
    });
    if (/^_TMP_/i.test(baseName(path))) info.candidate = true;
    if (fm && /candidate/i.test(fm.status || '')) info.candidate = true;
    const docType = lc(fm?.document_type || '');
    const ruleType = lc(fm?.type || '');
    if (CONTROLLED[docType]) info.docType = docType;
    else if (RULE_TYPES.has(ruleType)) info.docType = ruleType;
    else info.docType = docType || null;
    return info;
  }

  function isTrash(path) {
    return normPath(path).split('/').some(seg => /^09_/.test(seg) || seg === '.obsidian' || seg === '.trash');
  }

  // ---------- executable workflow spec (gen2-run) ----------
  // A Workflow MD may carry one ```gen2-run fenced JSON block that turns its
  // steps into a state machine: input (OWNER provides material), skill (an
  // agent runs a Skill and posts the result) and gate (OWNER picks an option).
  // GitHub runs, PackageCanvas and the Amin Pocket GBA app all read this one
  // definition, so it is kept strict and dependency free.
  const RUN_BLOCK_RE = /^```gen2-run[^\S\n]*\n([\s\S]*?)^```[^\S\n]*$/m;
  const RUN_STEP_TYPES = ['input', 'skill', 'gate'];
  const RUN_ID_RE = /^[A-Za-z][A-Za-z0-9_-]{0,31}$/;
  const RUN_OPTION_RE = /^[a-z][a-z0-9_-]{0,31}$/;

  function parseRunSpec(body) {
    const text = String(body || '');
    const m = text.match(RUN_BLOCK_RE);
    if (!m) return { found: false, spec: null, errors: [] };
    if ((text.match(/^```gen2-run/gm) || []).length > 1) return { found: true, spec: null, errors: ['只能有一個 gen2-run 區塊'] };
    let spec;
    try { spec = JSON.parse(m[1]); } catch (e) { return { found: true, spec: null, errors: ['gen2-run 不是有效的 JSON：' + e.message] }; }
    return { found: true, spec, errors: validateRunSpec(spec) };
  }

  function runNext(spec, index) {
    const step = spec.steps[index];
    if (step.next !== undefined) return step.next;
    return index + 1 < spec.steps.length ? spec.steps[index + 1].id : 'end';
  }

  function validateRunSpec(spec, context = {}) {
    const errors = [];
    const str = v => typeof v === 'string' && v.trim() !== '';
    if (!spec || typeof spec !== 'object' || Array.isArray(spec)) return ['gen2-run 必須是 JSON 物件'];
    if (spec.version !== 1) errors.push('version 必須是 1');
    if (!Array.isArray(spec.steps) || !spec.steps.length) return errors.concat('steps 至少要有一個步驟');
    if (spec.steps.length > 50) errors.push('steps 最多 50 個');
    const ids = new Set();
    spec.steps.forEach((st, i) => {
      const at = '步驟 ' + (i + 1);
      if (!st || typeof st !== 'object') { errors.push(at + ' 不是物件'); return; }
      if (!RUN_ID_RE.test(st.id || '') || lc(st.id) === 'end') errors.push(at + ' 的 id 格式不正確（英文字母開頭，最多 32 字，不能是 end）');
      else if (ids.has(st.id)) errors.push('步驟 id 重複：' + st.id);
      else ids.add(st.id);
      if (!RUN_STEP_TYPES.includes(st.type)) errors.push((st.id || at) + ' 的 type 必須是 input、skill 或 gate');
      if (!str(st.title)) errors.push((st.id || at) + ' 缺 title');
      if (st.type === 'skill') {
        if (!/^SK-[A-Za-z0-9_-]+$/.test(st.skill || '')) errors.push((st.id || at) + ' 的 skill 必須是 SK-xxx');
        else if (context.skillIds && !context.skillIds.has(lc(st.skill))) errors.push((st.id || at) + ' 使用的 ' + st.skill + ' 不存在');
      }
      if (st.type === 'gate') {
        if (!Array.isArray(st.options) || st.options.length < 2 || st.options.length > 8) errors.push((st.id || at) + ' 的 options 需要 2–8 個選項');
        else {
          const opts = new Set();
          st.options.forEach((o, j) => {
            const oat = (st.id || at) + ' 選項 ' + (j + 1);
            if (!o || !RUN_OPTION_RE.test(o.id || '')) errors.push(oat + ' 的 id 格式不正確（小寫英文字母開頭）');
            else if (opts.has(o.id)) errors.push(oat + ' 的 id 重複：' + o.id);
            else opts.add(o.id);
            if (!o || !str(o.label)) errors.push(oat + ' 缺 label');
            if (o && o.comment !== undefined && !['required', 'optional'].includes(o.comment)) errors.push(oat + ' 的 comment 只能是 required 或 optional');
            if (!o || !str(o.next)) errors.push(oat + ' 缺 next');
          });
        }
        if (st.review !== undefined && !str(st.review)) errors.push((st.id || at) + ' 的 review 必須是步驟 id');
      } else if (st.options !== undefined) errors.push((st.id || at) + ' 只有 gate 可以有 options');
    });
    if (errors.length) return errors;
    const known = id => id === 'end' || ids.has(id);
    spec.steps.forEach((st, i) => {
      if (st.type === 'gate') {
        st.options.forEach(o => { if (!known(o.next)) errors.push(st.id + ' 選項 ' + o.id + ' 的 next 指向不存在的步驟：' + o.next); });
        if (st.review !== undefined && !ids.has(st.review)) errors.push(st.id + ' 的 review 指向不存在的步驟：' + st.review);
        if (st.next !== undefined) errors.push(st.id + ' 是 gate，請在每個選項設定 next');
      } else if (!known(runNext(spec, i))) errors.push(st.id + ' 的 next 指向不存在的步驟：' + st.next);
    });
    if (spec.start !== undefined && !ids.has(spec.start)) errors.push('start 指向不存在的步驟：' + spec.start);
    if (errors.length) return errors;
    // every step reachable from start, and "end" reachable
    const edges = new Map(spec.steps.map((st, i) => [st.id, st.type === 'gate' ? st.options.map(o => o.next) : [runNext(spec, i)]]));
    const seen = new Set(), queue = [spec.start || spec.steps[0].id];
    let endReached = false;
    while (queue.length) {
      const id = queue.shift();
      if (id === 'end') { endReached = true; continue; }
      if (seen.has(id)) continue;
      seen.add(id);
      queue.push(...edges.get(id));
    }
    for (const st of spec.steps) if (!seen.has(st.id)) errors.push('步驟 ' + st.id + ' 從起點走不到');
    if (!endReached) errors.push('流程永遠走不到 end');
    return errors;
  }

  // ---------- main builder ----------
  function buildProject(input, options = {}) {
    const rootName = options.rootName || input.rootName || 'GEN2';
    const dirs = Array.isArray(input.dirs) ? input.dirs.map(normPath) : null;
    const skipped = [];
    const docs = [];
    for (const f of input.files || []) {
      const path = normPath(f.path);
      if (!/\.md$/i.test(path)) continue;
      if (isTrash(path)) { skipped.push(path); continue; }
      const text = String(f.text ?? '');
      const { data: fm, body } = parseFrontmatter(text);
      const info = classify(path, fm);
      const id = fm?.workflow_id || fm?.skill_id || fm?.output_id || '';
      docs.push({
        path, text, fm, body, info,
        docId: id.trim(),
        nodeId: 'g2_' + hash(path),
        title: '',
        issues: []
      });
    }
    docs.sort((a, b) => a.path.localeCompare(b.path));

    // --- groups ---
    const groups = new Map();
    const assistantId = 'assistant_gen2';
    function group(id, title, color, type, order) {
      if (!groups.has(id)) groups.set(id, { id, title, color, type, parentId: assistantId, order, members: [] });
      return groups.get(id);
    }
    const kbIds = [...new Set(docs.filter(d => d.info.kbId && !d.info.template && !d.info.candidate).map(d => d.info.kbId))].sort();
    for (const d of docs) {
      let g;
      if (d.info.template) g = group('g2grp_template', '_KB_TEMPLATE｜範本', '#8a93a3', 'group', 900);
      else if (d.info.candidate) g = group('g2grp_candidates', '候選｜尚未正式採用', '#d8b13a', 'group', 800);
      else if (d.info.kbId) {
        const i = kbIds.indexOf(d.info.kbId);
        g = group('g2grp_' + d.info.kbId, d.info.kbId + '｜' + d.info.kbName, KB_PALETTE[i % KB_PALETTE.length], 'workflow', 100 + i);
      } else if (RULE_TYPES.has(d.info.docType) || /^0\d_/.test(d.path.split('/')[0]) || d.path.split('/').length === 1) {
        g = group('g2grp_rules', 'GEN2｜規則與入口（00–06）', '#7f8fa6', 'group', 0);
      } else g = group('g2grp_other', '其他 Markdown', '#718098', 'group', 950);
      d.groupId = g.id;
    }

    // --- node titles & kinds ---
    for (const d of docs) {
      const ctl = CONTROLLED[d.info.docType];
      const heading = firstHeading(d.body);
      d.title = (d.docId && heading && !heading.includes(d.docId) ? d.docId + '｜' + heading : heading || stripMd(baseName(d.path))).slice(0, 90);
      if (d.info.template) d.kind = 'gen2_template';
      else if (d.info.candidate) d.kind = 'gen2_candidate';
      else if (ctl) d.kind = ctl.kind;
      else if (RULE_TYPES.has(d.info.docType)) d.kind = 'gen2_rule';
      else d.kind = 'note';
      d.semanticType = ctl?.semantic || (RULE_TYPES.has(d.info.docType) ? 'GovernanceRule' : 'Document');
      d.order = ctl ? ctl.order : RULE_TYPES.has(d.info.docType) ? -1 : 7;
    }

    // --- resolver index ---
    const index = new Map();
    function addKey(key, target) {
      const k = lc(key);
      if (!k) return;
      if (!index.has(k)) index.set(k, []);
      const list = index.get(k);
      if (!list.includes(target)) list.push(target);
    }
    for (const d of docs) {
      const noExt = stripMd(d.path);
      const segs = noExt.split('/');
      for (let i = 0; i < segs.length; i++) addKey(segs.slice(i).join('/'), d);
      if (d.docId) addKey(d.docId, d);
      if (d.fm?.document_id) addKey(d.fm.document_id, d);
    }
    function resolve(target, from) {
      const list = index.get(lc(stripMd(normPath(target)))) || [];
      if (list.length <= 1) return list[0] || null;
      const sameKb = list.filter(d => d.info.kbFolder && d.info.kbFolder === from.info.kbFolder && d.info.template === from.info.template);
      if (sameKb.length === 1) return sameKb[0];
      const sameGroup = list.filter(d => d.groupId === from.groupId);
      if (sameGroup.length === 1) return sameGroup[0];
      return null;
    }

    // --- resource nodes from RESOURCE_REFS tables ---
    const resources = [];
    const resourceByKey = new Map();
    for (const d of docs.filter(x => x.info.docType === 'resource_refs')) {
      for (const t of parseTables(d.body)) {
        if (!t.headers.map(lc).includes('resource_id')) continue;
        const idKey = t.headers.find(h => lc(h) === 'resource_id');
        const nameKey = t.headers.find(h => /名稱|name/i.test(h));
        for (const row of t.rows) {
          const resId = cellId(row[idKey]);
          if (!/^RES-/i.test(resId)) continue;
          const node = {
            resId,
            nodeId: 'g2res_' + hash(d.path + '#' + resId),
            title: resId + (nameKey && row[nameKey] ? '｜' + row[nameKey] : ''),
            groupId: d.groupId,
            kind: d.info.candidate ? 'gen2_candidate' : d.info.template ? 'gen2_template' : 'gen2_resource',
            refsDoc: d,
            md: '# ' + resId + '\n\n' + t.headers.map(h => '- **' + h + '**：' + (row[h] || '—')).join('\n'),
            kbFolder: d.info.kbFolder,
            issues: []
          };
          resources.push(node);
          resourceByKey.set((d.info.kbFolder || d.groupId) + '|' + lc(resId), node);
        }
      }
    }

    // --- relations ---
    const links = new Map();
    const degree = new Map();
    function link(fromId, toId, type, label) {
      if (!fromId || !toId || fromId === toId) return;
      const key = fromId + '>' + toId;
      if (links.has(key)) return;
      links.set(key, { id: 'g2l_' + hash(key), from: fromId, to: toId, type, label, layer: 'gen2', visibility: 'visible', origin: 'gen2_folder', reviewStatus: 'source' });
      degree.set(fromId, (degree.get(fromId) || 0) + 1);
      degree.set(toId, (degree.get(toId) || 0) + 1);
    }

    const issues = [];
    function issue(code, severity, message, nodeIds = []) {
      issues.push({ code, severity, message, nodeIds });
      for (const id of nodeIds) {
        const d = docs.find(x => x.nodeId === id) || resources.find(r => r.nodeId === id);
        if (d) d.issues.push(message);
      }
    }

    const byNodeId = new Map(docs.map(d => [d.nodeId, d]));
    for (const d of docs) {
      const seen = new Set();
      for (const raw of wikiLinks(d.text)) {
        const key = lc(raw);
        if (seen.has(key)) continue;
        seen.add(key);
        const t = resolve(raw, d);
        if (!t) {
          if (d.info.template) continue;
          const looksSkill = /^SK-/i.test(raw), looksWf = /^WF-/i.test(raw);
          if (looksSkill) issue('missing_skill', 'error', '缺 Skill：' + d.title + ' 引用 [[' + raw + ']]，但資料夾中找不到', [d.nodeId]);
          else if (looksWf) issue('missing_workflow', 'error', '缺 Workflow：' + d.title + ' 引用 [[' + raw + ']]，但資料夾中找不到', [d.nodeId]);
          else issue('broken_link', 'warn', '斷裂連結：' + d.title + ' → [[' + raw + ']]', [d.nodeId]);
          continue;
        }
        const tt = t.info.docType;
        if (d.info.docType === 'workflow_bom') link(d.nodeId, t.nodeId, 'output', 'BOM 登記');
        else if (d.info.docType === 'workflow' && tt === 'skill') link(d.nodeId, t.nodeId, 'main', '使用 Skill');
        else if (tt === 'kb_contract') link(d.nodeId, t.nodeId, 'cross', '所屬 KB');
        else if (tt === 'resource_refs') link(d.nodeId, t.nodeId, 'cross', 'Resource 清單');
        else link(d.nodeId, t.nodeId, 'govern', 'Wiki Link');
      }
    }

    for (const r of resources) link(r.refsDoc.nodeId, r.nodeId, 'output', '登記 Resource');
    for (const d of docs) {
      if (d.info.docType === 'resource_refs' || d.info.template) continue;
      const ids = new Set((d.text.match(/\bRES-[A-Za-z0-9]+-\d+\b/g) || []).map(lc));
      for (const id of ids) {
        const r = resourceByKey.get((d.info.kbFolder || d.groupId) + '|' + id);
        if (r) link(d.nodeId, r.nodeId, 'govern', '引用 Resource');
        else if (d.info.kbId && !d.info.candidate) issue('missing_resource', 'error', '缺 Resource：' + d.title + ' 引用 ' + id.toUpperCase() + '，但本 KB 的 RESOURCE_REFS 沒有登記', [d.nodeId]);
      }
    }

    // --- registry ---
    const registryDoc = docs.find(d => d.info.docType === 'registry' || /^KB_REGISTRY\.md$/i.test(baseName(d.path)));
    const registered = new Map();
    if (registryDoc) {
      for (const t of parseTables(registryDoc.body)) {
        const idKey = t.headers.find(h => lc(h) === 'kb_id');
        if (!idKey) continue;
        for (const row of t.rows) {
          const kbId = cellId(row[idKey]);
          if (/^KB-/i.test(kbId)) registered.set(kbId.toUpperCase(), row);
        }
      }
    }
    const contracts = new Map();
    for (const d of docs) if (d.info.docType === 'kb_contract' && d.info.kbId && !d.info.template && !d.info.candidate) contracts.set(d.info.kbId.toUpperCase(), d);
    if (registryDoc) {
      for (const [kbId] of registered) {
        const c = contracts.get(kbId);
        if (c) link(registryDoc.nodeId, c.nodeId, 'govern', '登記');
        else if (!kbIds.map(x => x.toUpperCase()).includes(kbId)) issue('registry_missing_folder', 'error', '登記表有 ' + kbId + '，但找不到對應資料夾', [registryDoc.nodeId]);
      }
      for (const kbId of kbIds) {
        if (!registered.has(kbId.toUpperCase())) {
          const members = docs.filter(d => d.info.kbId === kbId && !d.info.template && !d.info.candidate).map(d => d.nodeId);
          issue('unregistered_kb', 'warn', '未登記知識庫：' + kbId + ' 有資料夾但不在 KB_REGISTRY（依 NEW_KB 步驟零需由 OWNER 決定接續或移除）', members.slice(0, 1));
        }
      }
    }

    // --- per-KB structure checks ---
    const kbFolders = new Map();
    for (const d of docs) if (d.info.kbId && !d.info.template && !d.info.candidate) kbFolders.set(d.info.kbId, d.info.kbFolder);
    for (const [kbId, folder] of kbFolders) {
      const inKb = docs.filter(d => d.info.kbFolder === folder && !d.info.template && !d.info.candidate);
      const has = type => inKb.some(d => d.info.docType === type);
      const anchor = contracts.get(kbId.toUpperCase())?.nodeId || inKb[0]?.nodeId;
      if (!has('kb_contract')) issue('kb_missing_contract', 'error', kbId + ' 缺 KB_CONTRACT.md', [anchor]);
      if (!has('resource_refs')) issue('kb_missing_refs', 'warn', kbId + ' 缺 RESOURCE_REFS.md', [anchor]);
      for (const sub of ['03_WORKFLOW_BOM', '04_WORKFLOWS', '05_SKILLS', '06_OUTPUTS']) {
        const p = folder + '/' + sub;
        const exists = (dirs && dirs.includes(p)) || inKb.some(d => d.path.startsWith(p + '/'));
        if (!exists) issue('kb_missing_folder', dirs ? 'warn' : 'info', kbId + ' ' + (dirs ? '缺資料夾 ' : '沒有 MD 檔於 ') + sub + '/', [anchor]);
      }
      const workflows = inKb.filter(d => d.info.docType === 'workflow');
      const skills = inKb.filter(d => d.info.docType === 'skill');
      if (!workflows.length) issue('kb_no_workflow', 'warn', kbId + ' 還沒有任何 Workflow（NEW_KB 登記條件需至少一個）', [anchor]);

      // BOM consistency
      const boms = inKb.filter(d => d.info.docType === 'workflow_bom');
      const bomIds = new Set();
      for (const bom of boms) {
        for (const t of parseTables(bom.body)) {
          const idKey = t.headers.find(h => ['workflow_id', 'skill_id'].includes(lc(h)));
          if (!idKey) continue;
          for (const row of t.rows) {
            const id = cellId(row[idKey]);
            if (!id) continue;
            bomIds.add(lc(id));
            if (!resolve(id, bom)) {
              const state = Object.entries(row).find(([h]) => /狀態|status/i.test(h))?.[1] || '';
              issue(/^SK-/i.test(id) ? 'missing_skill' : 'bom_missing_file', 'error', 'BOM 登記 ' + id + (state ? '（' + state + '）' : '') + '，但找不到檔案', [bom.nodeId]);
            }
          }
        }
      }
      if (boms.length) {
        for (const d of [...workflows, ...skills]) {
          if (d.docId && !bomIds.has(lc(d.docId))) issue('bom_unlisted', 'warn', d.docId + ' 存在但未登記於 03_WORKFLOW_BOM', [d.nodeId]);
        }
      } else if (workflows.length || skills.length) issue('kb_missing_bom', 'warn', kbId + ' 有 Workflow/Skill 但沒有 Workflow BOM', [anchor]);

      const kbSkillIds = new Set(skills.map(s => lc(s.docId)).filter(Boolean));
      for (const w of workflows) {
        if (!/^##\s*OWNER\s*Gate/im.test(w.body)) issue('workflow_no_gate', 'warn', 'Governance 缺口：' + w.title + ' 沒有「OWNER Gate」段落', [w.nodeId]);
        const run = parseRunSpec(w.body);
        if (!run.found) { issue('workflow_not_runnable', 'info', w.title + ' 尚未定義可執行流程（gen2-run）', [w.nodeId]); continue; }
        const errs = run.spec && !run.errors.length ? validateRunSpec(run.spec, { skillIds: kbSkillIds }) : run.errors;
        if (errs.length) issue('run_spec_invalid', 'error', w.title + ' 的 gen2-run 定義有誤：' + errs.join('；'), [w.nodeId]);
        else if (!run.spec.steps.some(st => st.type === 'gate')) issue('run_spec_no_gate', 'warn', w.title + ' 的 gen2-run 沒有任何 OWNER gate 步驟', [w.nodeId]);
      }
      for (const s of skills) {
        const usedBy = workflows.some(w => links.has(w.nodeId + '>' + s.nodeId));
        if (!usedBy) issue('skill_unused', 'info', s.title + ' 目前沒有 Workflow 使用', [s.nodeId]);
      }
      for (const d of inKb) {
        if (!d.info.docType || (!CONTROLLED[d.info.docType] && !RULE_TYPES.has(d.info.docType))) {
          issue('uncontrolled_md', 'warn', '非受控 MD 類型：' + d.path + '（document_type 不是六種範本之一）', [d.nodeId]);
        }
      }
    }

    // orphans (KB documents without any relation)
    for (const d of docs) {
      if (d.info.template || !d.info.kbId || RULE_TYPES.has(d.info.docType)) continue;
      if (!degree.get(d.nodeId)) issue('orphan', 'info', '孤立節點：' + d.title + ' 沒有任何關聯', [d.nodeId]);
    }

    // ---------- assemble nodes ----------
    const nodes = [];
    for (const d of docs) {
      const fm = d.fm || {};
      nodes.push({
        id: d.nodeId,
        title: d.title,
        kind: d.kind,
        semanticType: d.semanticType,
        groupId: d.groupId,
        md: d.body.trim() +
          (d.issues.length ? '\n\n---\n## ⚠ 架構檢查\n' + d.issues.map(x => '- ' + x).join('\n') : '') +
          (d.fm ? '\n\n---\n## Frontmatter\n' + Object.entries(d.fm).map(([k, v]) => '- **' + k + '**：' + (v || '—')).join('\n') : ''),
        status: fm.status || (d.info.candidate ? 'candidate' : ''),
        source_status: fm.status || '',
        review_status: d.issues.length ? '⚠ ' + d.issues.length + ' 項檢查' : '',
        sourcePath: rootName + '/' + d.path,
        sourceOrigin: 'gen2_folder',
        gen2: { docType: d.info.docType, docId: d.docId, kbId: d.info.kbId, version: fm.version || '', owner: fm.owner || '' },
        tags: d.issues.length ? ['gen2-issue'] : [],
        order: d.order
      });
    }
    for (const r of resources) {
      nodes.push({
        id: r.nodeId, title: r.title, kind: r.kind, semanticType: 'Resource', groupId: r.groupId,
        md: r.md + (r.issues.length ? '\n\n---\n## ⚠ 架構檢查\n' + r.issues.map(x => '- ' + x).join('\n') : ''),
        status: '', source_status: '', review_status: r.issues.length ? '⚠ ' + r.issues.length + ' 項檢查' : '',
        sourcePath: rootName + '/' + r.refsDoc.path + '#' + r.resId, sourceOrigin: 'gen2_folder',
        gen2: { docType: 'resource', docId: r.resId, kbId: r.refsDoc.info.kbId }, tags: [], order: 5
      });
    }

    // check report node
    const sev = { error: 0, warn: 1, info: 2 };
    issues.sort((a, b) => sev[a.severity] - sev[b.severity]);
    const counts = { error: 0, warn: 0, info: 0 };
    issues.forEach(i => counts[i.severity]++);
    const label = { error: '🔴 錯誤', warn: '🟠 警告', info: '🔵 提示' };
    const reportMd = '# GEN2 架構檢查\n\n' +
      '- 來源資料夾：' + rootName + '\n- MD 檔：' + docs.length + '（略過 09_待刪除 等 ' + skipped.length + '）\n' +
      '- 知識庫：' + (kbIds.join('、') || '無') + '\n- 錯誤 ' + counts.error + '／警告 ' + counts.warn + '／提示 ' + counts.info + '\n\n' +
      (issues.length ? ['error', 'warn', 'info'].map(s => {
        const list = issues.filter(i => i.severity === s);
        return list.length ? '## ' + label[s] + '\n' + list.map(i => '- ' + i.message).join('\n') : '';
      }).filter(Boolean).join('\n\n') : '## ✅ 沒有發現問題');
    group('g2grp_checks', '架構檢查', '#db7580', 'group', -10);
    nodes.push({
      id: 'g2_check_report', title: 'GEN2 架構檢查｜' + (issues.length ? counts.error + ' 錯誤 · ' + counts.warn + ' 警告' : '通過'),
      kind: 'gen2_check', semanticType: 'Gap', groupId: 'g2grp_checks', md: reportMd,
      status: '', source_status: '', review_status: '', sourcePath: rootName, sourceOrigin: 'gen2_folder',
      gen2: { docType: 'check_report' }, tags: [], order: 0, w: 460, h: 420
    });

    // ---------- layout ----------
    const groupList = [...groups.values()].sort((a, b) => a.order - b.order || a.title.localeCompare(b.title));
    for (const g of groupList) g.members = nodes.filter(n => n.groupId === g.id).sort((a, b) => a.order - b.order || a.title.localeCompare(b.title));
    const GROUP_COLS = 3, GROUP_GAP = 140;
    let cursorY = ORIGIN.y;
    for (let r = 0; r < groupList.length; r += GROUP_COLS) {
      const row = groupList.slice(r, r + GROUP_COLS);
      let cursorX = ORIGIN.x, rowH = 0;
      for (const g of row) {
        const n = g.members.length || 1;
        const cols = Math.min(4, Math.max(1, Math.ceil(Math.sqrt(n))));
        let y = 0;
        g.members.forEach((m, i) => {
          const c = i % cols, rr = Math.floor(i / cols);
          m.w = m.w || NODE_W;
          m.h = m.h || NODE_H;
          m.x = cursorX + PAD_X + c * (NODE_W + GAP);
          m.y = cursorY + PAD_TOP + rr * (NODE_H + GAP);
          y = Math.max(y, m.y + m.h);
        });
        g.x = cursorX;
        g.y = cursorY;
        g.w = PAD_X * 2 + cols * NODE_W + (cols - 1) * GAP + (g.members.some(m => m.w > NODE_W) ? 80 : 0);
        g.h = Math.max(PAD_TOP + NODE_H + PAD_BOTTOM, (y || cursorY + PAD_TOP + NODE_H) - cursorY + PAD_BOTTOM);
        cursorX += g.w + GROUP_GAP;
        rowH = Math.max(rowH, g.h);
      }
      cursorY += rowH + GROUP_GAP;
    }

    const project = {
      version: '0.6a',
      schemaVersion: 1,
      nodes: nodes.map(({ order, ...n }) => n),
      links: [...links.values()],
      groups: groupList.map(({ members, order, ...g }) => g),
      assistant: { id: assistantId, title: 'GEN2 知識庫｜' + rootName, type: 'assistant', color: '#e2bd45' },
      assets: [],
      vault: null,
      view: { zoom: 200, panX: 0, panY: 0, mode: 'canvas' },
      settings: {},
      graphLayouts: { md: null, group: null, assistant: null },
      packageMetadata: { source: 'gen2_folder', generator: VERSION, rootName, fileCount: docs.length, generatedAt: options.now || new Date().toISOString() }
    };
    return {
      project,
      report: { counts, issues, kbIds, fileCount: docs.length, skipped, resourceCount: resources.length, linkCount: links.size }
    };
  }

  // Keep the user's manual arrangement when a folder is re-read.
  function mergeLayout(fresh, previous) {
    if (!previous) return fresh;
    const oldNodes = new Map((previous.nodes || []).map(n => [n.id, n]));
    const oldGroups = new Map((previous.groups || []).map(g => [g.id, g]));
    for (const n of fresh.nodes) {
      const o = oldNodes.get(n.id);
      if (o && [o.x, o.y, o.w, o.h].every(Number.isFinite)) Object.assign(n, { x: o.x, y: o.y, w: o.w, h: o.h });
    }
    for (const g of fresh.groups) {
      const o = oldGroups.get(g.id);
      if (!o || ![o.x, o.y, o.w, o.h].every(Number.isFinite)) continue;
      const members = fresh.nodes.filter(n => n.groupId === g.id);
      const fits = members.every(n => n.x >= o.x && n.y >= o.y && n.x + n.w <= o.x + o.w && n.y + n.h <= o.y + o.h);
      if (fits) Object.assign(g, { x: o.x, y: o.y, w: o.w, h: o.h });
      else if (members.length) {
        g.x = Math.min(o.x, ...members.map(n => n.x - PAD_X));
        g.y = Math.min(o.y, ...members.map(n => n.y - PAD_TOP));
        g.w = Math.max(o.x + o.w, ...members.map(n => n.x + n.w + PAD_X)) - g.x;
        g.h = Math.max(o.y + o.h, ...members.map(n => n.y + n.h + PAD_BOTTOM)) - g.y;
      }
    }
    if (previous.view) fresh.view = { ...previous.view };
    if (previous.graphLayouts) {
      // A saved graph layout is reused only when it has a position for every
      // entity of the fresh project; otherwise the graph would be drawn with
      // missing positions, so that level is laid out again.
      const gl = previous.graphLayouts;
      const covers = (layout, ids) => {
        if (!Array.isArray(layout)) return false;
        const have = new Set(layout.map(e => Array.isArray(e) ? e[0] : null));
        return ids.every(id => have.has(id));
      };
      fresh.graphLayouts = {
        md: covers(gl.md, fresh.nodes.map(n => n.id)) ? gl.md : null,
        group: covers(gl.group, fresh.groups.map(g => g.id)) ? gl.group : null,
        assistant: fresh.assistant && covers(gl.assistant, [fresh.assistant.id]) ? gl.assistant : null
      };
    }
    if (previous.settings) fresh.settings = previous.settings;
    return fresh;
  }

  // ---------- folder sources (browser only) ----------
  const yieldToUi = () => new Promise(r => setTimeout(r, 0));

  function nativeBridge() {
    const b = typeof window !== 'undefined' ? window.AminPackageCanvasFiles : null;
    return b && typeof b.listMarkdown === 'function' && typeof b.readText === 'function' ? b : null;
  }
  function parseJson(text, fallback) {
    try { return JSON.parse(text); } catch (e) { return fallback; }
  }

  const native = {
    id: 'native',
    label: 'Amin Pocket GBA 手機資料夾',
    available: () => !!nativeBridge(),
    listFolders() {
      const b = nativeBridge();
      if (!b) return [];
      const r = parseJson(b.listFolders(), { folders: [] });
      return Array.isArray(r.folders) ? r.folders : [];
    },
    pick() {
      const b = nativeBridge();
      if (!b) return Promise.reject(new Error('此 App 沒有手機資料夾功能'));
      return new Promise((resolve, reject) => {
        window.__pcGen2FolderPicked = result => {
          window.__pcGen2FolderPicked = null;
          const r = typeof result === 'string' ? parseJson(result, {}) : (result || {});
          if (r.ok) resolve(r);
          else reject(new Error(r.cancelled ? '已取消選擇資料夾' : (r.error || '選擇資料夾失敗')));
        };
        if (!b.pickFolder()) {
          window.__pcGen2FolderPicked = null;
          reject(new Error('無法開啟資料夾選擇器'));
        }
      });
    },
    forget(folderId) {
      const b = nativeBridge();
      return !!(b && b.forgetFolder(folderId));
    },
    async collect(folder, onProgress) {
      const b = nativeBridge();
      if (!b) throw new Error('此 App 沒有手機資料夾功能');
      const listing = parseJson(b.listMarkdown(folder.folderId), null);
      if (!listing || !listing.ok) throw new Error(listing?.error || '無法讀取資料夾（可能需要重新授權）');
      const files = [];
      const list = (listing.files || []).slice(0, MAX_FILES);
      for (let i = 0; i < list.length; i++) {
        const f = list[i];
        if (f.size > MAX_FILE_BYTES) continue;
        const r = parseJson(b.readText(folder.folderId, f.path), null);
        if (r && r.ok) files.push({ path: f.path, text: r.text, modified: f.modified });
        if (i % 12 === 11) { onProgress && onProgress(i + 1, list.length); await yieldToUi(); }
      }
      return { key: 'native:' + folder.folderId, rootName: listing.rootName || folder.name, files, dirs: listing.dirs || [], truncated: !!listing.truncated };
    }
  };

  const picker = {
    id: 'picker',
    label: '瀏覽器資料夾',
    available: () => typeof window !== 'undefined' && typeof window.showDirectoryPicker === 'function',
    async pickAndCollect(onProgress) {
      const handle = await window.showDirectoryPicker({ mode: 'read' });
      const files = [], dirs = [];
      async function walk(dir, prefix) {
        for await (const [name, entry] of dir.entries()) {
          if (files.length >= MAX_FILES) return;
          const p = prefix ? prefix + '/' + name : name;
          if (entry.kind === 'directory') {
            if (name.startsWith('.')) continue;
            dirs.push(p);
            await walk(entry, p);
          } else if (/\.md$/i.test(name)) {
            const file = await entry.getFile();
            if (file.size <= MAX_FILE_BYTES) files.push({ path: p, text: await file.text(), modified: file.lastModified });
            if (files.length % 12 === 0) { onProgress && onProgress(files.length); await yieldToUi(); }
          }
        }
      }
      await walk(handle, '');
      return { key: 'picker:' + handle.name, rootName: handle.name, files, dirs };
    }
  };

  const input = {
    id: 'input',
    label: '選擇資料夾（上傳）',
    available: () => typeof document !== 'undefined' && 'webkitdirectory' in document.createElement('input'),
    pickAndCollect() {
      return new Promise((resolve, reject) => {
        const el = document.createElement('input');
        el.type = 'file';
        el.multiple = true;
        el.webkitdirectory = true;
        el.style.display = 'none';
        document.body.appendChild(el);
        el.onchange = async () => {
          try {
            const list = [...el.files].filter(f => /\.md$/i.test(f.name) && f.size <= MAX_FILE_BYTES).slice(0, MAX_FILES);
            if (!list.length) throw new Error('資料夾中沒有 .md 檔');
            const rootName = (list[0].webkitRelativePath || '').split('/')[0] || 'GEN2';
            const files = [];
            for (const f of list) {
              const rel = (f.webkitRelativePath || f.name).split('/').slice(1).join('/') || f.name;
              files.push({ path: rel, text: await f.text(), modified: f.lastModified });
            }
            resolve({ key: 'input:' + rootName, rootName, files, dirs: null });
          } catch (e) { reject(e); } finally { el.remove(); }
        };
        el.click();
      });
    }
  };

  // ---------- GitHub source (official copy) ----------
  // Reads the private GEN2 repository through the GitHub REST API with a
  // read-only token. Same parser, so GitHub, the phone folder and the PR gate
  // all show the same graph. Nothing is written back.
  const GITHUB_API = 'https://api.github.com';
  const GITHUB_DEFAULTS = Object.freeze({ owner: 'ken12121122-dotcom', repo: 'gen2-knowledge', ref: 'main', root: 'kb' });
  const blobCache = new Map();
  const NAME_RE = /^[A-Za-z0-9_.-]+$/;

  function githubConfig(cfg) {
    const c = Object.assign({}, GITHUB_DEFAULTS, cfg || {});
    c.owner = String(c.owner || '').trim();
    c.repo = String(c.repo || '').trim();
    c.ref = String(c.ref || '').trim() || 'main';
    c.root = String(c.root ?? '').trim().replace(/^\/+|\/+$/g, '');
    if (!NAME_RE.test(c.owner) || !NAME_RE.test(c.repo)) throw new Error('GitHub owner／repo 格式不正確');
    if (/\.\.|^\/|\s/.test(c.ref)) throw new Error('分支名稱格式不正確');
    return c;
  }

  async function githubRequest(path, cfg, accept, fetchImpl) {
    // Only Accept and Authorization, so the browser CORS preflight stays minimal.
    const headers = { Accept: accept || 'application/vnd.github+json' };
    if (cfg.token) headers.Authorization = 'Bearer ' + cfg.token;
    const res = await (fetchImpl || fetch)(GITHUB_API + path, { headers, cache: 'no-store' });
    if (res.ok) return res;
    if (res.status === 401) throw new Error('GitHub token 無效或已過期');
    if (res.status === 404) throw new Error('找不到 ' + cfg.owner + '/' + cfg.repo + '（' + cfg.ref + '）；確認 token 有此 repo 的讀取權限');
    if (res.status === 403 || res.status === 429) throw new Error('GitHub 拒絕或超過速率限制（HTTP ' + res.status + '）');
    throw new Error('GitHub 讀取失敗 HTTP ' + res.status);
  }

  async function walkTree(base, sha, cfg, fetchImpl) {
    const out = [], queue = [{ sha, prefix: '' }];
    while (queue.length) {
      const { sha: treeSha, prefix } = queue.shift();
      const t = await (await githubRequest(base + '/git/trees/' + treeSha, cfg, null, fetchImpl)).json();
      if (t.truncated) throw new Error('GitHub 樹狀結構過大，無法完整讀取');
      for (const e of t.tree || []) {
        const path = prefix + e.path;
        const seg = e.path;
        if (e.type === 'tree') {
          if (seg.startsWith('.')) continue;
          const inside = !cfg.root || path === cfg.root || path.startsWith(cfg.root + '/') || cfg.root.startsWith(path + '/');
          if (!inside) continue;
          out.push({ path, type: 'tree', sha: e.sha });
          if (out.length > MAX_FILES * 4) throw new Error('GitHub 樹狀結構過大，無法完整讀取');
          queue.push({ sha: e.sha, prefix: path + '/' });
        } else out.push({ path, type: e.type, sha: e.sha, size: e.size });
      }
    }
    return out;
  }

  const encodeRef = ref => ref.split('/').map(encodeURIComponent).join('/');

  async function mapLimit(items, limit, fn) {
    let next = 0;
    const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
      while (next < items.length) { const i = next++; await fn(items[i], i); }
    });
    await Promise.all(workers);
  }

  const github = {
    id: 'github',
    label: 'GitHub 正本',
    defaults: GITHUB_DEFAULTS,
    available: () => typeof fetch === 'function',
    config: githubConfig,
    key: cfg => { const c = githubConfig(cfg); return 'github:' + c.owner + '/' + c.repo + '@' + c.ref; },
    async listPulls(cfg, fetchImpl) {
      const c = githubConfig(cfg);
      const list = [];
      for (let page = 1; page <= 10; page++) {
        const res = await githubRequest('/repos/' + c.owner + '/' + c.repo + '/pulls?state=open&per_page=100&page=' + page, c, null, fetchImpl);
        const batch = await res.json();
        if (!Array.isArray(batch)) break;
        list.push(...batch);
        if (batch.length < 100) break;
      }
      return list
        .filter(p => p.head && p.head.repo && p.head.repo.full_name === c.owner + '/' + c.repo)
        .map(p => ({ number: p.number, title: p.title, ref: p.head.ref, draft: !!p.draft, updatedAt: p.updated_at }));
    },
    async collect(cfg, onProgress, fetchImpl) {
      const c = githubConfig(cfg);
      const base = '/repos/' + c.owner + '/' + c.repo;
      const commit = (await (await githubRequest(base + '/commits/' + encodeRef(c.ref), c, 'application/vnd.github.sha', fetchImpl)).text()).trim();
      if (!/^[0-9a-f]{40}$/.test(commit)) throw new Error('無法取得 ' + c.ref + ' 的 commit');
      let tree = await (await githubRequest(base + '/git/trees/' + commit + '?recursive=1', c, null, fetchImpl)).json();
      // GitHub truncates very large recursive trees; walk them level by level instead.
      if (tree.truncated) tree = { tree: await walkTree(base, commit, c, fetchImpl), truncated: false };
      const prefix = c.root ? c.root + '/' : '';
      const dirs = [], wanted = [];
      for (const e of tree.tree || []) {
        if (prefix && !e.path.startsWith(prefix)) continue;
        const rel = e.path.slice(prefix.length);
        if (!rel || rel.split('/').some(seg => seg.startsWith('.'))) continue;
        if (e.type === 'tree') dirs.push(rel);
        else if (e.type === 'blob' && /\.md$/i.test(rel) && (e.size || 0) <= MAX_FILE_BYTES) wanted.push({ path: rel, sha: e.sha });
      }
      if (!dirs.length && !wanted.length) throw new Error(c.ref + ' 沒有 ' + (c.root || '根目錄') + '/ 知識庫內容');
      const list = wanted.slice(0, MAX_FILES), files = new Array(list.length);
      const decoder = new TextDecoder('utf-8');
      let done = 0;
      await mapLimit(list, 6, async (f, i) => {
        let text = blobCache.get(f.sha);
        if (text === undefined) {
          const res = await githubRequest(base + '/git/blobs/' + f.sha, c, 'application/vnd.github.raw+json', fetchImpl);
          text = decoder.decode(await res.arrayBuffer());
          blobCache.set(f.sha, text);
        }
        files[i] = { path: f.path, text };
        if (++done % 12 === 0 && onProgress) onProgress(done, list.length);
      });
      return {
        key: 'github:' + c.owner + '/' + c.repo + '@' + c.ref,
        layoutFrom: c.ref === GITHUB_DEFAULTS.ref ? null : 'github:' + c.owner + '/' + c.repo + '@' + GITHUB_DEFAULTS.ref,
        rootName: c.repo + '@' + c.ref,
        files, dirs, commit, ref: c.ref,
        truncated: !!tree.truncated || wanted.length > MAX_FILES
      };
    }
  };

  return { VERSION, buildProject, mergeLayout, parseFrontmatter, parseTables, wikiLinks, parseRunSpec, validateRunSpec, sources: { native, picker, input, github } };
});
