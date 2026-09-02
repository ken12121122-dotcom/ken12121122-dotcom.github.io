import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';
import { validateGovernance } from '../tools/governance-gate/validate-governance.mjs';

const policy = JSON.parse(fs.readFileSync('.github/governance/policy-v1.json', 'utf8'));
const agent = {
  agent_id: 'agent:codex', agent_type: 'codex', role: 'Backend / Platform Engineer',
  issue_number: 120, current_task: 'Step 12.1 governance gate', status: 'working',
  blocking_reason: '', owner_attention_required: false, owner_attention_reason: '',
  started_at: '2026-09-02T00:00:00Z',
};

function marker(name, value) { return `<!-- ${name} ${JSON.stringify(value)} -->`; }
function event(body, association = 'OWNER', headRepo = 'owner/repo') {
  return { pull_request: { body, author_association: association,
    head: { repo: { full_name: headRepo } }, base: { repo: { full_name: 'owner/repo' } } } };
}
function skill(path = '.codex/skills/example/SKILL.md') {
  return { schema_version: 'skill-submission-v1', submissions: [{
    path, skill_id: 'skill:example', version: '0.1.0', review_status: 'generated',
    canonical: false, execution_enabled: false,
    source_records: [{ source_type: 'issue', source_ref: 'github:issue:120', authority: 'owner-approved' }],
    provenance: { agent_id: 'agent:codex', method: 'amin-agent-execution' }, unresolved_gaps: [],
  }] };
}

test('accepts one trusted Agent marker for ordinary engineering changes', () => {
  const result = validateGovernance({ event: event(marker('amin-agent-execution', agent)),
    changedFiles: ['android-native/docs/example.md'], policy });
  assert.equal(result.agent.agent_id, 'agent:codex');
  assert.equal(result.skill_submission_count, 0);
});

test('fails closed for missing, duplicate, fork, or untrusted Agent evidence', () => {
  assert.throws(() => validateGovernance({ event: event(''), changedFiles: [], policy }), /AGENT_MARKER_REQUIRED/);
  const twice = marker('amin-agent-execution', agent).repeat(2);
  assert.throws(() => validateGovernance({ event: event(twice), changedFiles: [], policy }), /AMBIGUOUS/);
  assert.throws(() => validateGovernance({ event: event(marker('amin-agent-execution', agent), 'OWNER', 'fork/repo'), changedFiles: [], policy }), /FORK_PR/);
  assert.throws(() => validateGovernance({ event: event(marker('amin-agent-execution', agent), 'CONTRIBUTOR'), changedFiles: [], policy }), /AUTHOR_NOT_TRUSTED/);
});

test('reuses the Android Agent identity vocabulary and blocks receipt injection', () => {
  const java = fs.readFileSync('android-native/app/src/main/java/com/amin/pocketgba/AgentExecutionStatusContract.java', 'utf8');
  for (const value of ['agent:codex', 'agent:claude-code', 'agent:chatgpt',
    'working', 'waiting', 'blocked', 'idle', 'review']) assert.match(java, new RegExp(`"${value}"`));
  const injected = { ...agent, context_node_ids: ['invented:node'] };
  assert.throws(() => validateGovernance({ event: event(marker('amin-agent-execution', injected)),
    changedFiles: [], policy }), /AUTHORITY_INJECTION/);
});

test('requires exact source-backed candidate metadata for every changed Skill', () => {
  const path = '.codex/skills/example/SKILL.md';
  const body = marker('amin-agent-execution', agent) + marker('amin-skill-submission', skill(path));
  const result = validateGovernance({ event: event(body), changedFiles: [path], policy });
  assert.equal(result.skill_submission_count, 1);
});

test('blocks Agent self-approval, canonical claims, and unverifiable provenance', () => {
  const path = '.codex/skills/example/SKILL.md';
  for (const mutate of [
    value => { value.submissions[0].review_status = 'approved'; },
    value => { value.submissions[0].canonical = true; },
    value => { value.submissions[0].provenance.agent_id = 'agent:chatgpt'; },
  ]) {
    const value = skill(path); mutate(value);
    const body = marker('amin-agent-execution', agent) + marker('amin-skill-submission', value);
    assert.throws(() => validateGovernance({ event: event(body), changedFiles: [path], policy }));
  }
});

test('prevents stale Skill declarations and incomplete path coverage', () => {
  const agentOnly = marker('amin-agent-execution', agent);
  assert.throws(() => validateGovernance({ event: event(agentOnly),
    changedFiles: ['skills/one/SKILL.md'], policy }), /SKILL_MARKER_REQUIRED/);
  const stale = agentOnly + marker('amin-skill-submission', skill('skills/one/SKILL.md'));
  assert.throws(() => validateGovernance({ event: event(stale), changedFiles: [], policy }), /WITHOUT_SKILL_CHANGE/);
});
