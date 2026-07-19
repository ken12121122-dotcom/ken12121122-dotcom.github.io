(() => {
  'use strict';

  const APP_ID = 'com.amin.pocketgba';
  const ENDPOINT = 'https://iqftezadvzugjcvuwmzw.supabase.co/functions/v1/amin-error-reporter';
  const QUEUE_KEY = 'amin.errorReporter.queue.v1';
  const INSTALL_KEY = 'amin.errorReporter.installId.v1';
  const ENABLED_KEY = 'amin.errorReporter.enabled.v1';
  const LAST_RESULT_KEY = 'amin.errorReporter.lastResult.v1';
  const MAX_QUEUE = 20;
  const MAX_CONSOLE_ERRORS = 8;

  const consoleErrors = [];
  let sending = false;
  let reporterBusy = false;
  let installHashPromise = null;

  const $ = id => document.getElementById(id);

  function enabled() {
    return localStorage.getItem(ENABLED_KEY) !== '0';
  }

  function setEnabled(value) {
    localStorage.setItem(ENABLED_KEY, value ? '1' : '0');
    renderStatus();
    if (value) scheduleSend(0);
  }

  function safeText(value, maxLength = 2000) {
    return String(value ?? '')
      .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, '')
      .replace(/content:\/\/[^\s)]+/gi, 'content://[redacted]')
      .replace(/file:\/\/\/[^\s)]+/gi, 'file:///[redacted]')
      .replace(/\/(?:storage|sdcard|data|mnt)\/[^\s)]+/gi, '/[device-path-redacted]')
      .replace(/[^\s/\\]+\.(?:gba|bin|zip|sav|state)\b/gi, '[game-file-redacted]')
      .trim()
      .slice(0, maxLength);
  }

  function randomInstallId() {
    if (crypto.randomUUID) return crypto.randomUUID();
    const bytes = crypto.getRandomValues(new Uint8Array(24));
    return [...bytes].map(value => value.toString(16).padStart(2, '0')).join('');
  }

  async function sha256Hex(value) {
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value));
    return [...new Uint8Array(digest)].map(byte => byte.toString(16).padStart(2, '0')).join('');
  }

  function getInstallId() {
    let value = localStorage.getItem(INSTALL_KEY);
    if (!value) {
      value = randomInstallId();
      localStorage.setItem(INSTALL_KEY, value);
    }
    return value;
  }

  function getInstallHash() {
    if (!installHashPromise) installHashPromise = sha256Hex(`${APP_ID}:${getInstallId()}`);
    return installHashPromise;
  }

  function readQueue() {
    try {
      const value = JSON.parse(localStorage.getItem(QUEUE_KEY) || '[]');
      return Array.isArray(value) ? value.slice(-MAX_QUEUE) : [];
    } catch {
      return [];
    }
  }

  function writeQueue(queue) {
    try {
      localStorage.setItem(QUEUE_KEY, JSON.stringify(queue.slice(-MAX_QUEUE)));
    } catch {}
    renderStatus();
  }

  function recordLastResult(payload) {
    try {
      localStorage.setItem(LAST_RESULT_KEY, JSON.stringify({ ...payload, at: Date.now() }));
    } catch {}
    renderStatus();
  }

  function readLastResult() {
    try { return JSON.parse(localStorage.getItem(LAST_RESULT_KEY) || 'null'); } catch { return null; }
  }

  function nativeCapabilities() {
    return window.AMIN_NATIVE_SHELL?.state?.capabilities || {};
  }

  function parseWebViewVersion() {
    const match = navigator.userAgent.match(/(?:Chrome|CriOS)\/(\d+(?:\.\d+){1,3})/i);
    return match?.[1] || null;
  }

  function numberBucketMb(bytes) {
    const value = Number(bytes || 0);
    if (!Number.isFinite(value) || value <= 0) return 0;
    return Math.ceil(value / (5 * 1024 * 1024)) * 5;
  }

  function requestToPromise(request) {
    return new Promise((resolve, reject) => {
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error('IndexedDB request failed'));
    });
  }

  async function romEnvironment() {
    try {
      const db = await requestToPromise(indexedDB.open('amin-pocket-rom-library'));
      try {
        if (!db.objectStoreNames.contains('roms')) return { romCount: 0, romSizeBucketMb: 0 };
        const values = await requestToPromise(db.transaction('roms', 'readonly').objectStore('roms').getAll());
        const bytes = values.reduce((sum, record) => sum + Number(record?.size || record?.blob?.size || 0), 0);
        return { romCount: values.length, romSizeBucketMb: numberBucketMb(bytes) };
      } finally {
        db.close();
      }
    } catch {
      return {};
    }
  }

  async function buildEnvironment(extra = {}) {
    const capabilities = nativeCapabilities();
    const network = window.AMIN_NATIVE_SHELL?.state?.network || {};
    let estimate = {};
    try { estimate = await navigator.storage?.estimate?.() || {}; } catch {}
    let persisted = null;
    try { persisted = await navigator.storage?.persisted?.(); } catch {}
    const rom = await romEnvironment();
    const lastSave = (() => {
      try { return JSON.parse(localStorage.getItem('amin-gba-save-status') || 'null'); } catch { return null; }
    })();
    const query = new URLSearchParams(location.search);
    return {
      androidSdk: capabilities.androidSdk ?? null,
      androidRelease: capabilities.androidRelease ?? null,
      deviceManufacturer: capabilities.deviceManufacturer ?? null,
      deviceModel: capabilities.deviceModel ?? null,
      webViewVersion: capabilities.webViewVersion || parseWebViewVersion(),
      userAgentFamily: navigator.userAgent.includes('; wv)') ? 'Android WebView' : 'Browser',
      networkTransport: network.transport || (navigator.onLine ? 'online' : 'offline'),
      networkValidated: network.validated ?? navigator.onLine,
      storagePersisted: persisted,
      storageUsageBucketMb: numberBucketMb(estimate.usage),
      storageQuotaBucketMb: numberBucketMb(estimate.quota),
      serviceWorkerControlled: Boolean(navigator.serviceWorker?.controller),
      emulatorChannel: query.get('ejs') || 'pinned',
      saveBytes: lastSave?.detail ? Number((String(lastSave.detail).match(/(\d+) bytes/) || [])[1] || 0) : 0,
      saveNativeProtected: Boolean(lastSave?.native),
      ...rom,
      ...extra,
    };
  }

  function versionInfo() {
    const capabilities = nativeCapabilities();
    return {
      appVersionName: capabilities.nativeVersion || capabilities.versionName || null,
      appVersionCode: Number.isInteger(capabilities.versionCode) ? capabilities.versionCode : null,
      runtimeVersion: localStorage.getItem('amin.runtime.version') || null,
    };
  }

  function reportIdentity(category, stage, message) {
    return `${category}|${stage || ''}|${safeText(message, 400).toLowerCase().replace(/\b\d+\b/g, '#')}`;
  }

  async function enqueue({ category, stage, message, stack, environment, nativeReportId = null }) {
    if (!enabled() || reporterBusy) return null;
    reporterBusy = true;
    try {
      const installHash = await getInstallHash();
      const payload = {
        appId: APP_ID,
        installHash,
        category,
        stage: safeText(stage, 120) || null,
        message: safeText(message, 2000) || 'Unknown failure',
        stack: safeText(stack, 8000) || null,
        environment: await buildEnvironment(environment || {}),
        ...versionInfo(),
      };
      const identity = reportIdentity(category, stage, message);
      const queue = readQueue();
      const duplicate = queue.find(item => item.identity === identity && Date.now() - Number(item.queuedAt || 0) < 5 * 60 * 1000);
      if (duplicate) return duplicate.localId;
      const item = {
        localId: crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`,
        identity,
        payload,
        nativeReportId,
        queuedAt: Date.now(),
        attempts: 0,
        nextAttemptAt: 0,
      };
      queue.push(item);
      writeQueue(queue);
      scheduleSend(0);
      return item.localId;
    } finally {
      reporterBusy = false;
    }
  }

  function retryDelay(attempts) {
    return Math.min(6 * 60 * 60 * 1000, 15_000 * (2 ** Math.min(8, attempts)));
  }

  async function sendItem(item) {
    const response = await fetch(ENDPOINT, {
      method: 'POST',
      cache: 'no-store',
      headers: {
        'Content-Type': 'application/json',
        'X-Amin-Client': 'android-runtime-v1',
      },
      body: JSON.stringify(item.payload),
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok || result.accepted !== true) {
      const error = new Error(result.error || `HTTP ${response.status}`);
      error.status = response.status;
      throw error;
    }
    return result;
  }

  function acknowledgeNative(id) {
    if (!id || !window.AminNativeDiagnostics?.ackPendingReport) return;
    try { window.AminNativeDiagnostics.ackPendingReport(String(id)); } catch {}
  }

  async function sendQueue() {
    if (sending || !enabled() || !navigator.onLine) return;
    sending = true;
    try {
      const queue = readQueue();
      const remaining = [];
      for (const item of queue) {
        if (Number(item.nextAttemptAt || 0) > Date.now()) {
          remaining.push(item);
          continue;
        }
        try {
          const result = await sendItem(item);
          acknowledgeNative(item.nativeReportId);
          recordLastResult({ ok: true, reportId: result.reportId, fingerprint: result.fingerprint });
        } catch (error) {
          item.attempts = Number(item.attempts || 0) + 1;
          item.nextAttemptAt = Date.now() + retryDelay(item.attempts);
          item.lastError = safeText(error?.message || error, 200);
          remaining.push(item);
          recordLastResult({ ok: false, error: item.lastError });
        }
      }
      writeQueue(remaining);
    } finally {
      sending = false;
    }
  }

  function scheduleSend(delay = 500) {
    setTimeout(() => sendQueue().catch(() => {}), delay);
  }

  function stackFrom(value) {
    if (value?.stack) return String(value.stack);
    if (value instanceof Error) return value.stack || `${value.name}: ${value.message}`;
    return consoleErrors.join('\n');
  }

  function renderStatus() {
    const status = $('diagnosticReporterStatus');
    const toggle = $('diagnosticReporterEnabled');
    if (toggle) toggle.checked = enabled();
    if (!status) return;
    const queue = readQueue();
    const last = readLastResult();
    if (!enabled()) {
      status.textContent = '自動回報已停用。錯誤仍會留在本機診斷報告。';
      status.className = 'storage-status';
      return;
    }
    if (queue.length) {
      status.textContent = `自動回報已啟用 · 尚有 ${queue.length} 筆等待傳送${navigator.onLine ? '' : '（目前離線）'}。`;
      status.className = 'storage-status working';
      return;
    }
    if (last?.ok) {
      status.textContent = `自動回報正常 · 最近一筆已送達，報告編號 ${String(last.reportId || '').slice(0, 8)}。`;
      status.className = 'storage-status success';
      return;
    }
    status.textContent = '自動回報已啟用。只傳送去識別化錯誤，不包含 ROM、存檔內容或檔名。';
    status.className = 'storage-status';
  }

  async function importNativeCrash() {
    if (!window.AminNativeDiagnostics?.getPendingReport) return;
    try {
      const raw = window.AminNativeDiagnostics.getPendingReport();
      if (!raw) return;
      const report = JSON.parse(raw);
      if (!report?.id) return;
      await enqueue({
        category: 'native-crash',
        stage: report.stage || 'uncaught-exception',
        message: report.message || 'Android native crash',
        stack: report.stack || null,
        environment: report.environment || {},
        nativeReportId: report.id,
      });
    } catch {}
  }

  const originalConsoleError = console.error.bind(console);
  console.error = (...args) => {
    try {
      consoleErrors.push(args.map(value => safeText(value?.stack || value, 1000)).join(' '));
      if (consoleErrors.length > MAX_CONSOLE_ERRORS) consoleErrors.shift();
    } catch {}
    originalConsoleError(...args);
  };

  addEventListener('error', event => {
    enqueue({
      category: 'javascript-error',
      stage: 'window.error',
      message: event.message || event.error?.message || 'JavaScript error',
      stack: stackFrom(event.error),
    });
  });

  addEventListener('unhandledrejection', event => {
    const reason = event.reason;
    enqueue({
      category: 'unhandled-rejection',
      stage: 'promise',
      message: reason?.message || String(reason || 'Unhandled rejection'),
      stack: stackFrom(reason),
    });
  });

  addEventListener('amin-gba-launch-status', event => {
    const detail = event.detail || {};
    if (!['failed', 'loader-error'].includes(detail.status)) return;
    enqueue({
      category: 'emulator-launch',
      stage: `${detail.channel || 'unknown'}:${detail.status}`,
      message: detail.message || detail.reason || 'mGBA launch failed',
      stack: consoleErrors.join('\n'),
      environment: {
        emulatorChannel: detail.channel || null,
        romHashPrefix: /^[a-f0-9]{8,64}$/i.test(detail.romId || '') ? String(detail.romId).slice(0, 12) : null,
        lastSuccessfulStage: detail.status,
      },
    });
  });

  addEventListener('amin-save-status', event => {
    const detail = event.detail || {};
    if (!String(detail.status || '').includes('failed')) return;
    enqueue({
      category: 'save-error',
      stage: detail.status,
      message: detail.detail || 'GBA save operation failed',
      stack: consoleErrors.join('\n'),
      environment: {
        romHashPrefix: /^[a-f0-9]{8,64}$/i.test(detail.saveKey || '') ? String(detail.saveKey).slice(0, 12) : null,
      },
    });
  });

  $('diagnosticReporterEnabled')?.addEventListener('change', event => setEnabled(event.target.checked));
  $('sendDiagnosticTestButton')?.addEventListener('click', async event => {
    const button = event.currentTarget;
    button.disabled = true;
    try {
      await enqueue({
        category: 'manual-diagnostic',
        stage: 'user-test',
        message: 'Amin automatic diagnostic pipeline test',
      });
      await sendQueue();
      renderStatus();
    } finally {
      button.disabled = false;
    }
  });

  addEventListener('online', () => scheduleSend(0));
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') scheduleSend(100);
  });
  addEventListener('amin-native-capabilities', () => importNativeCrash().then(() => scheduleSend(0)));

  window.AMIN_ERROR_REPORTER = {
    version: '1.0.0',
    enqueue,
    sendQueue,
    enabled,
    setEnabled,
    reportManual(message = 'Manual diagnostic report') {
      return enqueue({ category: 'manual-diagnostic', stage: 'manual', message });
    },
  };

  renderStatus();
  importNativeCrash().finally(() => scheduleSend(800));
})();
