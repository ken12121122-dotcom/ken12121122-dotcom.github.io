import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import test from 'node:test';

const require = createRequire(import.meta.url);
const Sec = require('../packagecanvas/gen2-secrets.js');
const nacl = require('../packagecanvas/vendor/nacl-fast.min.js');
const hex = b => Buffer.from(b).toString('hex');
const utf8 = s => new TextEncoder().encode(s);

test('BLAKE2b matches RFC 7693 and libsodium vectors', () => {
  assert.equal(hex(Sec.blake2b(utf8('abc'), 64)), 'ba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6fdbffa2d17d87c5392aab792dc252d5de4533cc9518d38aa8dbf1925ab92386edd4009923');
  // crypto_generichash(24, …) from libsodium 0.7.15
  assert.equal(hex(Sec.blake2b(utf8('abc'), 24)), '56a17e38cc371a46b12c32f18e0c61de2a84e9c2555b114e');
  assert.equal(hex(Sec.blake2b(Uint8Array.from({ length: 64 }, (_, i) => i), 24)), 'aa054507b4916837a6d2b35b1ce7c525facdb7868ed55a8a');
});

// Fixed keypair made with libsodium crypto_box_keypair; sealed boxes from seal() were also opened with crypto_box_seal_open.
const PK = Sec.b64decode('gmF/Isdl3qQW4YkF8N/htaFat9dRdCsLOZJ25LUtnG4=');
const SK = Sec.b64decode('Wsc9ZDoEmN9GKdew+p1VQD3FuB2HzY5rLNeZYxNLKoI=');
function open(sealed) {
  const epk = sealed.subarray(0, 32), n = new Uint8Array(64);
  n.set(epk); n.set(PK, 32);
  const m = nacl.box.open(sealed.subarray(32), Sec.blake2b(n, 24), epk, SK);
  return m && new TextDecoder().decode(m);
}

test('sealed box opens with the repository key and is fresh every time', () => {
  const a = Sec.seal(utf8('sk-ant-秘密'), PK, nacl), b = Sec.seal(utf8('sk-ant-秘密'), PK, nacl);
  assert.equal(a.length, 32 + 16 + utf8('sk-ant-秘密').length);
  assert.equal(open(a), 'sk-ant-秘密');
  assert.notEqual(hex(a), hex(b));
  a[40] ^= 1;
  assert.equal(open(a), null);
});

const SA = { type: 'service_account', project_id: 'p', client_email: 'gen2-agent@p.iam.gserviceaccount.com', private_key: '-----BEGIN PRIVATE KEY-----\nMII\n-----END PRIVATE KEY-----\n' };

test('only the agent secrets can be written, and each value is checked', () => {
  assert.deepEqual(Sec.AGENT_SECRETS.map(s => s.name), ['CLAUDE_CODE_OAUTH_TOKEN', 'ANTHROPIC_API_KEY', 'GOOGLE_SERVICE_ACCOUNT_JSON', 'GEN2_CALENDAR_ID']);
  assert.equal(Sec.check('CLAUDE_CODE_OAUTH_TOKEN', ' sk-ant-oat01-' + 'b'.repeat(40) + '\n').value, 'sk-ant-oat01-' + 'b'.repeat(40));
  assert.match(Sec.check('CLAUDE_CODE_OAUTH_TOKEN', 'sk-ant-api03-' + 'a'.repeat(40)).error, /Claude API key/);
  assert.match(Sec.check('CLAUDE_CODE_OAUTH_TOKEN', 'abc').error, /sk-ant-oat/);
  assert.equal(Sec.check('CLAUDE_CODE_OAUTH_TOKEN', 'sk-ant-oat01-' + 'b'.repeat(30) + '\n' + 'b'.repeat(30) + ' ').value, 'sk-ant-oat01-' + 'b'.repeat(60));
  assert.match(Sec.check('ANTHROPIC_API_KEY', 'sk-ant-oat01-' + 'b'.repeat(40)).error, /訂閱 token/);
  assert.match(Sec.check('GITHUB_TOKEN', 'x').error, /不能寫入/);
  assert.match(Sec.check('ANTHROPIC_API_KEY', '').error, /沒有輸入/);
  assert.match(Sec.check('ANTHROPIC_API_KEY', 'sk-proj-abc').error, /sk-ant-/);
  assert.equal(Sec.check('ANTHROPIC_API_KEY', '  sk-ant-api03-' + 'a'.repeat(40) + '\n').value, 'sk-ant-api03-' + 'a'.repeat(40));
  assert.match(Sec.check('GOOGLE_SERVICE_ACCOUNT_JSON', '{').error, /JSON/);
  assert.match(Sec.check('GOOGLE_SERVICE_ACCOUNT_JSON', JSON.stringify({ ...SA, type: 'authorized_user' })).error, /service_account/);
  assert.match(Sec.check('GOOGLE_SERVICE_ACCOUNT_JSON', JSON.stringify({ ...SA, private_key: '' })).error, /private_key/);
  const sa = Sec.check('GOOGLE_SERVICE_ACCOUNT_JSON', JSON.stringify(SA, null, 2));
  assert.equal(sa.info.serviceAccount, SA.client_email);
  assert.deepEqual(JSON.parse(sa.value), SA);
  assert.match(Sec.check('GEN2_CALENDAR_ID', 'me').error, /email/);
  assert.equal(Sec.check('GEN2_CALENDAR_ID', ' me@gmail.com ').value, 'me@gmail.com');
});

function github(handler) {
  const calls = [];
  const f = async (url, init = {}) => {
    calls.push({ url, method: init.method || 'GET', headers: init.headers, body: init.body ? JSON.parse(init.body) : null });
    const [status, body] = handler(url, init.method || 'GET');
    return { status, ok: status >= 200 && status < 300, json: async () => body };
  };
  return { f, calls };
}
const CFG = { owner: 'ken12121122-dotcom', repo: 'gen2-knowledge', token: 't0k' };

test('save seals the value with the repository public key and sends only that', async () => {
  const gh = github((url, m) => url.endsWith('/actions/secrets/public-key') ? [200, { key_id: 'KID', key: Sec.b64encode(PK) }] : m === 'PUT' ? [201, null] : [500, null]);
  const r = await Sec.save(CFG, 'GOOGLE_SERVICE_ACCOUNT_JSON', JSON.stringify(SA), nacl, gh.f);
  assert.equal(r.info.serviceAccount, SA.client_email);
  const put = gh.calls[1];
  assert.equal(put.method, 'PUT');
  assert.equal(put.url, 'https://api.github.com/repos/ken12121122-dotcom/gen2-knowledge/actions/secrets/GOOGLE_SERVICE_ACCOUNT_JSON');
  assert.deepEqual(Object.keys(put.body).sort(), ['encrypted_value', 'key_id']);
  assert.equal(put.body.key_id, 'KID');
  assert.deepEqual(JSON.parse(open(Sec.b64decode(put.body.encrypted_value))), SA);
  assert.ok(!JSON.stringify(gh.calls).includes('BEGIN PRIVATE KEY'), 'the plain value never leaves the page');
  assert.equal(put.headers.Authorization, 'Bearer t0k');
  await assert.rejects(Sec.save(CFG, 'NTFY_TOPIC', 'x', nacl, gh.f), /不能寫入/);
  await assert.rejects(Sec.save({ ...CFG, token: '' }, 'GEN2_CALENDAR_ID', 'me@gmail.com', nacl, gh.f), /token/);
});

test('missing permissions ask for a token; status lists names and dates only', async () => {
  const denied = github(() => [403, {}]);
  await assert.rejects(Sec.save(CFG, 'GEN2_CALENDAR_ID', 'me@gmail.com', nacl, denied.f), e => e.needsToken && /Secrets：Read and write/.test(e.message));
  await assert.rejects(Sec.dispatchAgent(CFG, 12, denied.f), e => e.needsToken && /Actions：Read and write/.test(e.message));
  const gh = github(() => [200, { secrets: [{ name: 'ANTHROPIC_API_KEY', updated_at: '2026-10-07T01:00:00Z' }, { name: 'NTFY_TOPIC', updated_at: 'x' }] }]);
  const st = await Sec.status(CFG, gh.f);
  assert.deepEqual(st.map(s => [s.name, s.set]), [['CLAUDE_CODE_OAUTH_TOKEN', false], ['ANTHROPIC_API_KEY', true], ['GOOGLE_SERVICE_ACCOUNT_JSON', false], ['GEN2_CALENDAR_ID', false]]);
});

test('either Claude secret is enough; the subscription token is asked for first', () => {
  const st = names => Sec.AGENT_SECRETS.map(d => ({ name: d.name, set: names.includes(d.name) }));
  const label = n => Sec.AGENT_SECRETS.find(d => d.name === n).label;
  assert.deepEqual(Sec.missing(st([])), [label('CLAUDE_CODE_OAUTH_TOKEN'), label('GOOGLE_SERVICE_ACCOUNT_JSON'), label('GEN2_CALENDAR_ID')]);
  assert.deepEqual(Sec.missing(st(['ANTHROPIC_API_KEY'])), [label('GOOGLE_SERVICE_ACCOUNT_JSON'), label('GEN2_CALENDAR_ID')]);
  assert.deepEqual(Sec.missing(st(['CLAUDE_CODE_OAUTH_TOKEN', 'GOOGLE_SERVICE_ACCOUNT_JSON', 'GEN2_CALENDAR_ID'])), []);
});

test('dispatch runs GEN2 Agent on main for one Issue', async () => {
  const gh = github(() => [204, null]);
  await Sec.dispatchAgent(CFG, 12, gh.f);
  assert.equal(gh.calls[0].url, 'https://api.github.com/repos/ken12121122-dotcom/gen2-knowledge/actions/workflows/gen2-agent.yml/dispatches');
  assert.deepEqual(gh.calls[0].body, { ref: 'main', inputs: { issue: '12' } });
  await assert.rejects(Sec.dispatchAgent(CFG, 0, gh.f), /Issue/);
});

test('Claude key check: rejected keys are refused, unreachable is not fatal', async () => {
  const at = status => async (url, init) => { assert.equal(init.headers['anthropic-dangerous-direct-browser-access'], 'true'); return { status, ok: status === 200 }; };
  assert.equal((await Sec.verifyClaudeKey('sk-ant-x', at(200))).ok, true);
  assert.equal((await Sec.verifyClaudeKey('sk-ant-x', at(401))).ok, false);
  assert.equal((await Sec.verifyClaudeKey('sk-ant-x', async () => { throw new TypeError('Failed to fetch'); })).ok, null);
});
