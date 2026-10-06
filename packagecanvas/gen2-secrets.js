// GEN2 Agent keys: lets the OWNER put the GEN2 agent's keys into the
// gen2-knowledge repository's GitHub Actions secrets from Knowledge World.
//
// - Only the names in AGENT_SECRETS can be written; nothing else.
// - The value is sealed in the browser with the repository's public key
//   (libsodium sealed box, as GitHub requires) and sent only to
//   api.github.com. It is never stored, logged or shown again.
// - GitHub never returns a secret's value; the page can only see which
//   names are set and when.
// Needs tweetnacl (vendor/nacl-fast.min.js) for X25519 + XSalsa20-Poly1305.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.Gen2Secrets = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  // ---- BLAKE2b (RFC 7693), only for the sealed-box nonce ----
  const M64 = (1n << 64n) - 1n;
  const IV = [0x6a09e667f3bcc908n, 0xbb67ae8584caa73bn, 0x3c6ef372fe94f82bn, 0xa54ff53a5f1d36f1n, 0x510e527fade682d1n, 0x9b05688c2b3e6c1fn, 0x1f83d9abfb41bd6bn, 0x5be0cd19137e2179n];
  const SIGMA = [
    [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15], [14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3],
    [11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4], [7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8],
    [9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13], [2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9],
    [12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11], [13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10],
    [6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5], [10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0]];
  const rotr = (x, n) => ((x >> BigInt(n)) | (x << BigInt(64 - n))) & M64;
  function compress(h, block, t, last) {
    const m = [];
    for (let i = 0; i < 16; i++) { let w = 0n; for (let j = 7; j >= 0; j--) w = (w << 8n) | BigInt(block[i * 8 + j]); m.push(w); }
    const v = [...h, ...IV];
    v[12] ^= t & M64; v[13] ^= (t >> 64n) & M64;
    if (last) v[14] ^= M64;
    const G = (a, b, c, d, x, y) => {
      v[a] = (v[a] + v[b] + x) & M64; v[d] = rotr(v[d] ^ v[a], 32);
      v[c] = (v[c] + v[d]) & M64; v[b] = rotr(v[b] ^ v[c], 24);
      v[a] = (v[a] + v[b] + y) & M64; v[d] = rotr(v[d] ^ v[a], 16);
      v[c] = (v[c] + v[d]) & M64; v[b] = rotr(v[b] ^ v[c], 63);
    };
    for (let r = 0; r < 12; r++) {
      const s = SIGMA[r % 10];
      G(0, 4, 8, 12, m[s[0]], m[s[1]]); G(1, 5, 9, 13, m[s[2]], m[s[3]]); G(2, 6, 10, 14, m[s[4]], m[s[5]]); G(3, 7, 11, 15, m[s[6]], m[s[7]]);
      G(0, 5, 10, 15, m[s[8]], m[s[9]]); G(1, 6, 11, 12, m[s[10]], m[s[11]]); G(2, 7, 8, 13, m[s[12]], m[s[13]]); G(3, 4, 9, 14, m[s[14]], m[s[15]]);
    }
    for (let i = 0; i < 8; i++) h[i] ^= v[i] ^ v[i + 8];
  }
  function blake2b(input, outlen) {
    if (!(outlen >= 1 && outlen <= 64)) throw new Error('blake2b outlen');
    const h = IV.slice(); h[0] ^= 0x01010000n ^ BigInt(outlen);
    const blocks = Math.max(1, Math.ceil(input.length / 128));
    for (let i = 0; i < blocks; i++) {
      const block = new Uint8Array(128); block.set(input.subarray(i * 128, i * 128 + 128));
      const last = i === blocks - 1;
      compress(h, block, BigInt(last ? input.length : (i + 1) * 128), last);
    }
    const out = new Uint8Array(outlen);
    for (let i = 0; i < outlen; i++) out[i] = Number((h[i >> 3] >> BigInt(8 * (i & 7))) & 0xffn);
    return out;
  }

  // ---- base64 / sealed box ----
  const b64encode = bytes => { let s = ''; for (const b of bytes) s += String.fromCharCode(b); return btoa(s); };
  const b64decode = str => Uint8Array.from(atob(str), c => c.charCodeAt(0));
  const utf8 = s => new TextEncoder().encode(s);

  // libsodium crypto_box_seal: ephemeral pk || crypto_box(m, blake2b(epk || pk, 24), pk, esk).
  function seal(message, publicKey, nacl) {
    const eph = nacl.box.keyPair();
    const nonceIn = new Uint8Array(64); nonceIn.set(eph.publicKey); nonceIn.set(publicKey, 32);
    const boxed = nacl.box(message, blake2b(nonceIn, 24), publicKey, eph.secretKey);
    const out = new Uint8Array(32 + boxed.length); out.set(eph.publicKey); out.set(boxed, 32);
    eph.secretKey.fill(0);
    return out;
  }

  // ---- what can be written ----
  const SERVICE = /^[a-z0-9-]+@[a-z0-9-]+\.iam\.gserviceaccount\.com$/;
  const AGENT_SECRETS = [
    { name: 'ANTHROPIC_API_KEY', label: 'Claude API key', hint: '以 sk-ant- 開頭', multiline: false,
      check(v) {
        const s = v.trim();
        if (!/^sk-ant-[A-Za-z0-9_-]{20,}$/.test(s)) return { error: '看起來不是 Claude API key（應以 sk-ant- 開頭，沒有空白）' };
        return { value: s };
      } },
    { name: 'GOOGLE_SERVICE_ACCOUNT_JSON', label: 'Google 服務帳號金鑰（JSON）', hint: '貼上下載的 JSON 檔全文', multiline: true,
      check(v) {
        let j; try { j = JSON.parse(v); } catch (_) { return { error: '不是有效的 JSON，請貼上整個金鑰檔內容' }; }
        if (j?.type !== 'service_account') return { error: 'JSON 的 type 不是 service_account' };
        if (!SERVICE.test(String(j.client_email || ''))) return { error: '找不到服務帳號 email（client_email）' };
        if (!/-----BEGIN PRIVATE KEY-----/.test(String(j.private_key || ''))) return { error: '找不到私密金鑰（private_key）' };
        return { value: JSON.stringify(j), info: { serviceAccount: j.client_email } };
      } },
    { name: 'GEN2_CALENDAR_ID', label: '行事曆 ID', hint: '主要行事曆就是你的 Gmail 地址', multiline: false, plain: true,
      check(v) {
        const s = v.trim();
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s)) return { error: '行事曆 ID 的格式像 email，例如 you@gmail.com 或 …@group.calendar.google.com' };
        return { value: s };
      } }];
  const byName = name => AGENT_SECRETS.find(s => s.name === name) || null;
  function check(name, raw) {
    const def = byName(name);
    if (!def) return { error: '不能寫入 ' + name };
    if (!String(raw || '').trim()) return { error: '沒有輸入內容' };
    return def.check(String(raw));
  }

  // ---- GitHub ----
  const API = 'https://api.github.com';
  async function call(cfg, fetchImpl, method, path, body, need = 'Secrets：Read and write') {
    if (!cfg.token) throw new Error('需要 GitHub token');
    const res = await (fetchImpl || fetch)(API + '/repos/' + encodeURIComponent(cfg.owner) + '/' + encodeURIComponent(cfg.repo) + path, {
      method,
      headers: { Accept: 'application/vnd.github+json', Authorization: 'Bearer ' + cfg.token, 'X-GitHub-Api-Version': '2022-11-28', ...(body ? { 'Content-Type': 'application/json' } : {}) },
      body: body ? JSON.stringify(body) : undefined
    });
    if (res.status === 401) throw new Error('GitHub token 無效或已過期');
    if (res.status === 403 || res.status === 404) throw Object.assign(new Error('這個 token 沒有 ' + cfg.repo + ' 的權限（要 ' + need + '）'), { needsToken: true });
    if (!res.ok) throw new Error('GitHub 回應 HTTP ' + res.status);
    return res.status === 204 || res.status === 201 ? null : res.json();
  }
  // Which agent secrets are set (names and dates only; GitHub never returns values).
  async function status(cfg, fetchImpl) {
    const data = await call(cfg, fetchImpl, 'GET', '/actions/secrets?per_page=100');
    const have = new Map((data.secrets || []).map(s => [s.name, s.updated_at]));
    return AGENT_SECRETS.map(s => ({ name: s.name, label: s.label, set: have.has(s.name), updatedAt: have.get(s.name) || null }));
  }
  async function save(cfg, name, raw, nacl, fetchImpl) {
    const ok = check(name, raw);
    if (ok.error) throw new Error(ok.error);
    const key = await call(cfg, fetchImpl, 'GET', '/actions/secrets/public-key');
    const sealed = seal(utf8(ok.value), b64decode(key.key), nacl);
    await call(cfg, fetchImpl, 'PUT', '/actions/secrets/' + name, { encrypted_value: b64encode(sealed), key_id: key.key_id });
    return { name, info: ok.info || null };
  }
  // Runs Actions › GEN2 Agent on main for one run Issue (the agent re-checks everything).
  async function dispatchAgent(cfg, issue, fetchImpl) {
    if (!(Number.isInteger(issue) && issue > 0)) throw new Error('Issue 編號無效');
    await call(cfg, fetchImpl, 'POST', '/actions/workflows/gen2-agent.yml/dispatches', { ref: 'main', inputs: { issue: String(issue) } }, 'Actions：Read and write');
  }
  // Optional check that a Claude key works (lists one model; no tokens are spent).
  async function verifyClaudeKey(apiKey, fetchImpl) {
    let res;
    try {
      res = await (fetchImpl || fetch)('https://api.anthropic.com/v1/models?limit=1', {
        headers: { 'x-api-key': apiKey.trim(), 'anthropic-version': '2023-06-01', 'anthropic-dangerous-direct-browser-access': 'true' }
      });
    } catch (_) { return { ok: null, message: '連不到 Claude，無法事先驗證' }; }
    if (res.status === 401 || res.status === 403) return { ok: false, message: 'Claude 拒絕了這個 key（HTTP ' + res.status + '）' };
    return res.ok ? { ok: true, message: 'Claude 確認 key 可用' } : { ok: null, message: 'Claude 回應 HTTP ' + res.status + '，無法確認' };
  }

  return { AGENT_SECRETS, blake2b, seal, check, status, save, dispatchAgent, verifyClaudeKey, b64encode, b64decode };
});
