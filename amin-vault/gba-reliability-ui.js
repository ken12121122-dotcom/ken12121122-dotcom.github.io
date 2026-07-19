(() => {
  'use strict';

  const $ = id => document.getElementById(id);
  let launchCompatReady = Boolean(window.AMIN_GBA_COMPAT_LAUNCHER);
  let pendingPlayId = null;

  function nativeVaultV2Available() {
    return Boolean(window.AMIN_NATIVE_CARTRIDGE_VAULT_V2?.available?.());
  }

  function setImportStatus(text, kind = '') {
    const target = $('importStatus');
    if (!target) return;
    target.textContent = text;
    target.className = `storage-status ${kind}`.trim();
  }

  function earlyPlayGuard(event) {
    if (nativeVaultV2Available()) return;
    const button = event.target.closest?.('[data-play]');
    if (!button || launchCompatReady) return;
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
    pendingPlayId = button.dataset.play;
    setImportStatus('正在準備 Legacy mGBA 備援啟動器，完成後會自動開始。', 'working');
  }

  window.addEventListener('click', earlyPlayGuard, true);

  function finishLaunchCompatibilityLoad() {
    launchCompatReady = Boolean(window.AMIN_GBA_COMPAT_LAUNCHER);
    if (!launchCompatReady) return;
    window.removeEventListener('click', earlyPlayGuard, true);
    if (pendingPlayId) {
      const id = pendingPlayId;
      pendingPlayId = null;
      setTimeout(() => window.AMIN_GBA_COMPAT_LAUNCHER.play(id), 80);
    }
  }

  function loadLaunchCompatibilityLayer() {
    if (nativeVaultV2Available()) {
      window.removeEventListener('click', earlyPlayGuard, true);
      return;
    }
    if (window.AMIN_GBA_COMPAT_LAUNCHER) {
      finishLaunchCompatibilityLoad();
      return;
    }
    if (document.getElementById('aminGbaLaunchCompatScript')) return;
    const script = document.createElement('script');
    script.id = 'aminGbaLaunchCompatScript';
    script.src = './gba-launch-compat.js';
    script.async = true;
    script.onload = finishLaunchCompatibilityLoad;
    script.onerror = () => {
      setImportStatus('Legacy mGBA 備援啟動器載入失敗，請保持網路連線後重新開啟 App。', 'error');
    };
    document.head.appendChild(script);
  }

  function loadSaveCompatibilityLayer() {
    // Bridge 10 restores and flushes through Android .sav/.bak. The older periodic
    // adapter is intentionally disabled because it may call saveSaveFiles too early.
    if (nativeVaultV2Available()) return;
    if (window.AMIN_GBA_SAVE_COMPAT || document.getElementById('aminGbaSaveCompatScript')) return;
    const script = document.createElement('script');
    script.id = 'aminGbaSaveCompatScript';
    script.src = './gba-save-compat.js';
    script.async = false;
    script.onerror = () => {
      const target = $('saveVaultStatus');
      if (target) {
        target.textContent = '舊版存檔適配器載入失敗，請保持連線後重新開啟。';
        target.className = 'storage-status error';
      }
    };
    document.head.appendChild(script);
  }

  let emulatorLanguage = 'zh-CN';
  try {
    Object.defineProperty(window, 'EJS_language', {
      configurable: true,
      enumerable: true,
      get() {
        return emulatorLanguage;
      },
      set(value) {
        emulatorLanguage = value === 'zh-TW' ? 'zh-CN' : String(value || 'zh-CN');
      }
    });
  } catch {
    window.EJS_language = 'zh-CN';
  }

  function updateReleaseCopy() {
    const runtimeLabel = document.querySelector('#runtimeProofCard .eyebrow');
    if (runtimeLabel) runtimeLabel.textContent = 'LIVE RUNTIME · 0.9.2-rc18';

    const status = $('diagnosticReporterStatus');
    const card = status?.closest('article');
    if (!card) return;
    const heading = card.querySelector('h2');
    const badge = card.querySelector('.core-badge');
    const description = card.querySelector('.section-head + p');
    if (heading) heading.textContent = '系統診斷與錯誤收集';
    if (badge) badge.textContent = 'ERROR LOG';
    if (description) {
      description.textContent = '遊戲啟動、JavaScript、存檔或原生崩潰時，只傳送去識別化診斷到錯誤收集站；不再自動建立 GitHub Issue，也不傳送 ROM、存檔內容、檔名、Email 或裝置路徑。';
    }
  }

  function setVaultStatus(text, kind = '') {
    const target = $('romVaultStatus');
    if (!target) return;
    target.textContent = text;
    target.className = `storage-status ${kind}`.trim();
  }

  function describe(detail = {}) {
    const total = Number(detail.total || 0);
    const mirrored = Number(detail.mirrored || 0);
    const recovered = Number(detail.recovered || 0);
    if (recovered > 0) {
      return `修復完成：恢復 ${recovered} 款，確認 ${mirrored} 款鏡像，遊戲庫目前 ${Math.max(total, recovered)} 款。`;
    }
    if (total > 0) {
      return `WebView 備援正常：遊戲庫 ${total} 款，已確認 ${mirrored || total} 款鏡像。`;
    }
    return '遊戲庫目前沒有 ROM。匯入後會建立 Android 原生卡匣。';
  }

  addEventListener('amin-rom-vault-ready', event => {
    if (nativeVaultV2Available()) return;
    const detail = event.detail || {};
    setVaultStatus(describe(detail), detail.recovered ? 'success' : '');
  });

  const button = $('repairRomLibraryButton');
  button?.addEventListener('click', async () => {
    button.disabled = true;
    try {
      if (nativeVaultV2Available()) {
        setVaultStatus('正在驗證並搬移 Android 原生卡匣…', 'working');
        const detail = await window.AMIN_NATIVE_CARTRIDGE_VAULT_V2.migrateLibrary();
        setVaultStatus(
          detail.total
            ? `Android 原生卡匣庫：${detail.total} 款已保護${detail.migrated ? `，本次搬移 ${detail.migrated} 款` : ''}。`
            : '尚無可搬移的 ROM。',
          detail.total ? 'success' : ''
        );
        return;
      }

      setVaultStatus('正在比對 IndexedDB 與 ROM 鏡像…', 'working');
      const detail = await window.AMIN_ROM_VAULT?.recover?.();
      if (!detail) throw new Error('ROM 可靠性引擎尚未準備完成。');
      setVaultStatus(describe(detail), 'success');
      if (detail.recovered > 0) setTimeout(() => location.reload(), 700);
    } catch (error) {
      setVaultStatus(error?.message || '遊戲庫修復失敗。', 'error');
    } finally {
      button.disabled = false;
    }
  });

  loadLaunchCompatibilityLayer();
  loadSaveCompatibilityLayer();
  updateReleaseCopy();

  if (!nativeVaultV2Available()) {
    setTimeout(async () => {
      try {
        const detail = await window.AMIN_ROM_VAULT?.recover?.();
        if (detail) setVaultStatus(describe(detail), detail.recovered ? 'success' : '');
      } catch (error) {
        setVaultStatus(error?.message || '暫時無法檢查 ROM 鏡像。', 'error');
      }
    }, 900);
  }
})();
