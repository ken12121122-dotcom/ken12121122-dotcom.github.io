#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const AGENT_MARKER = /<!--\s*amin-agent-execution\s*(\{.*?\})\s*-->/gs;
const SKILL_MARKER = /<!--\s*amin-skill-submission\s*(\{.*?\})\s*-->/gs;
const AGENT_ID = /^agent:[a-z0-9][a-z0-9._-]{1,48}$/;
const SKILL_ID = /^skill:[a-z0-9][a-z0-9._/-]{1,96}$/;
const SEMVER = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z.-]+)?$/;
const AGENT_TYPES = new Set(['claude_code', 'codex', 'chatgpt', 'other']);
const STATUSES = new Set(['working', 'waiting', 'blocked', 'idle', 'review']);
const PERMANENT_AGENTS = new Map([
  ['agent:codex', 'codex'],
  ['agent:claude-code', 'claude_code'],
  ['agent:chatgpt', 'chatgpt'],
]);

function clean(value) {
  return typeof value === 'string' ? value.trim() : '';
}

function oneJsonMarker(body, pattern, requiredCode, ambiguousCode, invalidCode) {
  const matches = [...String(body || '').matchAll(pattern)];
  if (matches.length === 0) throw new Error(requiredCode);
  if (matches.length !== 1) throw new Error(ambiguousCode);
  if (Buffer.byteLength(matches[0][1], 'utf8') > 4096) throw new Error(`${invalidCode}_TOO_LARGE`);
  try {
    return JSON.parse(matches[0][1]);
  } catch {
    throw new Error(invalidCode);
  }
}

export function validateAgentMarker(body) {
  const marker = oneJsonMarker(body, AGENT_MARKER, 'AGENT_MARKER_REQUIRED',
    'AGENT_MARKER_AMBIGUOUS', 'AGENT_MARKER_INVALID');
  const id = clean(marker.agent_id);
  const type = clean(marker.agent_type).toLowerCase();
  const status = clean(marker.status).toLowerCase();
  for (const forbidden of ['context_node_ids', 'receipt_id', 'execution_enabled', 'approved', 'canonical']) {
    if (Object.hasOwn(marker, forbidden)) throw new Error('AGENT_MARKER_AUTHORITY_INJECTION');
  }
  if (!AGENT_ID.test(id)) throw new Error('INVALID_AGENT_ID');
  if (!AGENT_TYPES.has(type)) throw new Error('INVALID_AGENT_TYPE');
  if (PERMANENT_AGENTS.has(id) && PERMANENT_AGENTS.get(id) !== type) {
    throw new Error('AGENT_ID_TYPE_MISMATCH');
  }
  if (!STATUSES.has(status)) throw new Error('INVALID_AGENT_STATUS');
  if (!clean(marker.role) || clean(marker.role).length > 120) throw new Error('INVALID_AGENT_ROLE');
  if (status !== 'idle' && (!clean(marker.current_task) || clean(marker.current_task).length > 240)) {
    throw new Error('CURRENT_TASK_REQUIRED');
  }
  if (!Number.isInteger(marker.issue_number) || marker.issue_number < 0) {
    throw new Error('INVALID_ISSUE_NUMBER');
  }
  if (status === 'blocked' && !clean(marker.blocking_reason)) throw new Error('BLOCKING_REASON_REQUIRED');
  if (marker.owner_attention_required === true && !clean(marker.owner_attention_reason)) {
    throw new Error('OWNER_ATTENTION_REASON_REQUIRED');
  }
  return { agent_id: id, agent_type: type, status };
}

function skillPaths(changedFiles, policy) {
  const patterns = policy.skill_file_patterns.map(pattern => new RegExp(pattern));
  return changedFiles.filter(file => patterns.some(pattern => pattern.test(file))).sort();
}

function validateSource(source) {
  return source && typeof source === 'object'
    && clean(source.source_type)
    && clean(source.source_ref)
    && clean(source.authority);
}

function validateSkillEntry(entry, agent, policy) {
  if (!entry || typeof entry !== 'object') throw new Error('SKILL_SUBMISSION_INVALID');
  if (!SKILL_ID.test(clean(entry.skill_id))) throw new Error('SKILL_ID_INVALID');
  if (!SEMVER.test(clean(entry.version))) throw new Error('SKILL_VERSION_INVALID');
  const review = clean(entry.review_status).toLowerCase();
  if (policy.forbidden_agent_skill_statuses.includes(review)
      || !policy.candidate_skill_statuses.includes(review)) {
    throw new Error('SKILL_REVIEW_STATUS_FORBIDDEN');
  }
  if (entry.canonical !== false) throw new Error('SKILL_CANONICAL_CLAIM_FORBIDDEN');
  if (entry.execution_enabled !== false) throw new Error('SKILL_EXECUTION_ENABLE_FORBIDDEN');
  if (!Array.isArray(entry.source_records) || entry.source_records.length === 0
      || !entry.source_records.every(validateSource)) throw new Error('SKILL_SOURCE_REQUIRED');
  if (!entry.provenance || entry.provenance.agent_id !== agent.agent_id
      || entry.provenance.method !== 'amin-agent-execution') {
    throw new Error('SKILL_PROVENANCE_MISMATCH');
  }
  if (!Array.isArray(entry.unresolved_gaps)) throw new Error('SKILL_GAPS_REQUIRED');
}

export function validateSkillSubmissions(body, changedFiles, agent, policy) {
  const paths = skillPaths(changedFiles, policy);
  const matches = [...String(body || '').matchAll(SKILL_MARKER)];
  if (paths.length === 0) {
    if (matches.length > 0) throw new Error('SKILL_MARKER_WITHOUT_SKILL_CHANGE');
    return [];
  }
  const marker = oneJsonMarker(body, SKILL_MARKER, 'SKILL_MARKER_REQUIRED',
    'SKILL_MARKER_AMBIGUOUS', 'SKILL_MARKER_INVALID');
  if (marker.schema_version !== 'skill-submission-v1' || !Array.isArray(marker.submissions)) {
    throw new Error('SKILL_MARKER_SCHEMA_INVALID');
  }
  const declared = marker.submissions.map(entry => clean(entry?.path)).sort();
  if (declared.length !== new Set(declared).size
      || JSON.stringify(declared) !== JSON.stringify(paths)) {
    throw new Error('SKILL_PATH_COVERAGE_MISMATCH');
  }
  marker.submissions.forEach(entry => validateSkillEntry(entry, agent, policy));
  return marker.submissions;
}

function readChangedFiles(filename) {
  const raw = fs.readFileSync(filename);
  return raw.includes(0)
    ? raw.toString('utf8').split('\0').filter(Boolean)
    : raw.toString('utf8').split(/\r?\n/).filter(Boolean);
}

export function validateGovernance({ event, changedFiles, policy }) {
  const pr = event?.pull_request;
  if (!pr) throw new Error('PULL_REQUEST_EVENT_REQUIRED');
  if (pr.head?.repo?.full_name !== pr.base?.repo?.full_name) throw new Error('FORK_PR_NOT_TRUSTED');
  if (!policy.trusted_author_associations.includes(pr.author_association)) {
    throw new Error('PR_AUTHOR_NOT_TRUSTED');
  }
  const agent = validateAgentMarker(pr.body || '');
  const skills = validateSkillSubmissions(pr.body || '', changedFiles, agent, policy);
  return { agent, skill_submission_count: skills.length, changed_file_count: changedFiles.length };
}

function option(name) {
  const index = process.argv.indexOf(name);
  if (index < 0 || !process.argv[index + 1]) throw new Error(`OPTION_REQUIRED:${name}`);
  return process.argv[index + 1];
}

function main() {
  const event = JSON.parse(fs.readFileSync(option('--event'), 'utf8'));
  const changedFiles = readChangedFiles(option('--changed-files'));
  const policy = JSON.parse(fs.readFileSync(option('--policy'), 'utf8'));
  const result = validateGovernance({ event, changedFiles, policy });
  process.stdout.write(`Governance gate passed: ${JSON.stringify(result)}\n`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { main(); } catch (error) {
    process.stderr.write(`Governance gate failed: ${error.message}\n`);
    process.exitCode = 1;
  }
}
