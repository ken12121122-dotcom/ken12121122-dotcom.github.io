(() => {
  'use strict';

  const VERSION = 1;
  const MAX_ERRORS = 30;
  const errors = [];
  const $ = id => document.getElementById(id);
  const summary = $('diagnosticSummary');
  const refreshButton = $('refreshDiagnosticsButton');
  const exportButton = $('exportDiagnosticsButton');

  function safeError(error) {
    if (!error) return { message: 'Unknown error' };
    return {
      name: error.name || 'Error',
      message: error.message || String(error),
      stack: error.stack ? String(error.stack).split('\n').slice(0, 8).join('\n') : null,
      at: new Date().toISOString()
    };
  }

  function pushError(error, source = 'runtime') {
    errors.push({ source, ...safeError(error) });
    if (errors.length > MAX_ERRORS) errors.splice(0, errors.length - MAX_ERRORS);
  }

  addEventListener('error', event => {
    pushError(event.error || new Error(event.message), 'window.error');
  });

  addEventListener('unhandledrejection', event => {
    pushError(event.reason instanceof Error ? event.reason : new Error(String(event.reason)), 'unhandledrejection');
  });

  function requestToPromise(request) {
    return new Promise((resolve, reject) => {
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error('IndexedDB request failed'));
    });
  }

  async function readRomStats() {
    try {
      const db = await requestToPromise(indexedDB.open('amin-pocket-rom-library'));
      try {
        if (!db.objectStoreNames.contains('roms')) return { count: 0, bytes: 0 };
        const values = await requestToPromise(db.transaction('roms', 'readonly').objectStore('roms').getAll());
        return {
          count: values.length,
          bytes: values.reduce((sum, record) => sum + Number(record?.size || record?.blob?.size || 0), 0),
          entries: values.map(record => ({
            id: record.id,
            name: record.name,
            size: record.size,
            addedAt: record.addedAt,
            lastPlayedAt: record.lastPlayedAt,
            hasBlob: record.blob instanceof Blob
          }))
        };
      } finally {
        db.close();
      }
    } catch (error) {
      return { error: safeError(error), count: null, bytes: null };
    }
  }

  async function databaseInventory() {
    if (typeof indexedDB.databases !== 'function') {
      return { supported: false, databases: [] };
    }
    try {
      const databases = await indexedDB.databases();
      return {
        supported: true,
        databases: databases.filter(item => item?.name).map(item => ({
          name: item.name,
          version: item.version || null
        }))
      };
    } catch (error) {
      return { supported: true, error: safeError(error), databases: [] };
    }
  }

  async function cacheInventory() {
    if (!('caches' in window)) return { supported: false, names: [] };
    try {
      const names = await caches.keys();
      return { supported: true, names };
    } catch (error) {
      return { supported: true, error: safeError(error), names: [] };
    }
  }

  async function serviceWorkerStatus() {
    if (!('serviceWorker' in navigator)) return { supported: false };
    try {
      const registration = await navigator.serviceWorker.getRegistration('./');
      return {
        supported: true,
        controlled: Boolean(navigator.serviceWorker.controller),
        controllerScriptURL: navigator.serviceWorker.controller?.scriptURL || null,
        active: registration?.active?.state || null,
        waiting: registration?.waiting?.state || null,
        installing: registration?.installing?.state || null,
        scope: registration?.scope || null
      };
    } catch (error) {
      return { supported: true, error: safeError(error) };
    }
  }

  async function storageStatus() {
    const result = {
      estimateSupported: Boolean(navigator.storage?.estimate),
      persistSupported: Boolean(navigator.storage?.persist),
      usage: null,
      quota: null,
      persisted: null
    };
    try {
      if (navigator.storage?.estimate) {
        const estimate = await navigator.storage.estimate();
        result.usage = estimate.usage ?? null;
        result.quota = estimate.quota ?? null;
      }
      if (navigator.storage?.persisted) result.persisted = await navigator.storage.persisted();
    } catch (error) {
      result.error = safeError(error);
    }
    return result;
  }

  function gamepads() {
    try {
      return [...(navigator.getGamepads?.() || [])].filter(Boolean).map(gamepad => ({
        id: gamepad.id,
        index: gamepad.index,
        mapping: gamepad.mapping,
        connected: gamepad.connected,
        buttonCount: gamepad.buttons.length,
        axisCount: gamepad.axes.length
      }));
    } catch (error) {
      return [{ error: safeError(error) }];
    }
  }

  function aminStorage() {
    const result = {};
    for (let index = 0; index < localStorage.length; index += 1) {
      const key = localStorage.key(index);
      if (!key || !key.startsWith('amin')) continue;
      const value = localStorage.getItem(key);
      result[key] = {
        bytes: new Blob([value || '']).size,
        preview: key.includes('token') || key.includes('secret')
          ? '[redacted]'
          : String(value || '').slice(0, 500)
      };
    }
    return result;
  }

  async function runtimeManifest() {
    try {
      const response = await fetch(`./runtime-manifest.json?diagnostic=${Date.now()}`, { cache: 'no-store' });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const manifest = await response.json();
      return {
        runtimeId: manifest.runtimeId,
        runtimeVersion: manifest.runtimeVersion,
        publishedAt: manifest.publishedAt,
        minimumNativeVersion: manifest.minimumNativeVersion,
        requiredCapabilities: manifest.requiredCapabilities || [],
        optionalCapabilities: manifest.optionalCapabilities || [],
        assetCount: manifest.assets?.length || 0
      };
    } catch (error) {
      return { error: safeError(error), cachedRuntimeVersion: localStorage.getItem('amin.runtime.version') };
    }
  }

  async function buildReport() {
    const [manifest, storage, sw, cachesInfo, databases, roms] = await Promise.all([
      runtimeManifest(),
      storageStatus(),
      serviceWorkerStatus(),
      cacheInventory(),
      databaseInventory(),
      readRomStats()
    ]);

    return {
      format: 'amin-gba-diagnostic',
      formatVersion: VERSION,
      generatedAt: new Date().toISOString(),
      location: {
        origin: location.origin,
        pathname: location.pathname,
        secureContext: window.isSecureContext,
        online: navigator.onLine,
        visibilityState: document.visibilityState
      },
      browser: {
        userAgent: navigator.userAgent,
        language: navigator.language,
        platform: navigator.platform,
        hardwareConcurrency: navigator.hardwareConcurrency || null,
        deviceMemory: navigator.deviceMemory || null,
        maxTouchPoints: navigator.maxTouchPoints || 0
      },
      nativeShell: {
        capabilities: window.AMIN_NATIVE_SHELL?.state?.capabilities || null,
        network: window.AMIN_NATIVE_SHELL?.state?.network || null,
        pendingCartridgeCount: window.AMIN_NATIVE_SHELL?.state?.cartridges?.length || 0
      },
      runtime: manifest,
      storage,
      serviceWorker: sw,
      cacheStorage: cachesInfo,
      indexedDB: databases,
      romLibrary: roms,
      gamepads: gamepads(),
      saveStatus: (() => {
        try { return JSON.parse(localStorage.getItem('amin-gba-save-status')); } catch { return null; }
      })(),
      lastRestore: (() => {
        try { return JSON.parse(localStorage.getItem('amin-gba-last-restore')); } catch { return null; }
      })(),
      localStorage: aminStorage(),
      capturedErrors: [...errors]
    };
  }

  function formatBytes(bytes) {
    if (!Number.isFinite(bytes)) return '未知';
    const units = ['B', 'KB', 'MB', 'GB'];
    let value = bytes;
    let index = 0;
    while (value >= 1024 && index < units.length - 1) {
      value /= 1024;
      index += 1;
    }
    return `${value.toFixed(index ? 1 : 0)} ${units[index]}`;
  }

  function render(report) {
    if (!summary) return;
    const native = report.nativeShell.capabilities;
    const network = report.nativeShell.network;
    const sw = report.serviceWorker;
    const rows = [
      ['Native', native?.nativeVersion || '純網頁模式'],
      ['Runtime', report.runtime.runtimeVersion || report.runtime.cachedRuntimeVersion || '未知'],
      ['網路', network ? `${network.transport} · ${network.validated ? '已驗證' : '未驗證'}` : (navigator.onLine ? '瀏覽器顯示在線' : '離線')],
      ['Service Worker', sw.controlled ? `已控制 · ${sw.active || 'active'}` : '未控制'],
      ['IndexedDB', report.indexedDB.supported ? `${report.indexedDB.databases.length} 個` : '無法完整列舉'],
      ['ROM', report.romLibrary.count === null ? '讀取失敗' : `${report.romLibrary.count} 款 · ${formatBytes(report.romLibrary.bytes)}`],
      ['本機空間', `${formatBytes(report.storage.usage)} / ${formatBytes(report.storage.quota)}`],
      ['錯誤記錄', `${report.capturedErrors.length} 筆`]
    ];
    summary.innerHTML = rows.map(([label, value]) => `<div><span>${label}</span><strong>${String(value)}</strong></div>`).join('');
  }

  async function refresh() {
    refreshButton.disabled = true;
    if (summary) summary.textContent = '正在收集診斷資料…';
    try {
      const report = await buildReport();
      render(report);
      window.AMIN_GBA_DIAGNOSTICS.lastReport = report;
      return report;
    } catch (error) {
      pushError(error, 'diagnostic');
      if (summary) summary.textContent = error.message || '診斷失敗。';
      throw error;
    } finally {
      refreshButton.disabled = false;
    }
  }

  function download(report) {
    const blob = new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `Amin-Diagnostic-${new Date().toISOString().replace(/[:.]/g, '-')}.json`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 30000);
  }

  refreshButton?.addEventListener('click', refresh);
  exportButton?.addEventListener('click', async () => {
    exportButton.disabled = true;
    try {
      download(await buildReport());
    } catch (error) {
      pushError(error, 'diagnostic-export');
      window.alert(error.message || '診斷報告匯出失敗。');
    } finally {
      exportButton.disabled = false;
    }
  });

  window.AMIN_GBA_DIAGNOSTICS = {
    version: VERSION,
    buildReport,
    refresh,
    lastReport: null,
    errors
  };

  setTimeout(refresh, 800);
})();
