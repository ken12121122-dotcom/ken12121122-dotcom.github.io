(() => {
  'use strict';

  const $ = id => document.getElementById(id);
  let launchCompatReady = Boolean(window.AMIN_GBA_COMPAT_LAUNCHER);
  let pendingPlayId = null;

  function setImportStatus(text, kind = '') {
    const target = $('importStatus');
    if (!target) return;
    target.textContent = text;
    target.className = `storage-status ${kind}`.trim();
  }

  function earlyPlayGuard(event) {
    const button = event.target.closest?.('[data-play]');
    if (!button || launchCompatReady) return;
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
    pendingPlayId = button.dataset.play;
    setImportStatus('正在準備乾淨的 Legacy mGBA 啟動器，完成後會自動開始。', 'working');
  }

  // Window capture executes before the older document-level launch listener.
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
      setImportStatus('Legacy mGBA 相容啟動器載入失敗，請保持網路連線後重新開啟 App。', 'error');
    };
    document.head.appendChild(script);
  }

  function loadSaveCompatibilityLayer() {
    if (window.AMIN_GBA_SAVE_COMPAT || document.getElementById('aminGbaSaveCompatScript')) return;
    const script = document.createElement('script');
    script.id = 'aminGbaSaveCompatScript';
    script.src = './gba-save-compat.js';
    script.async = false;
    script.onerror = () => {
      const target = $('saveVaultStatus');
      if (target) {
        target.textContent = '4.2.3 存檔適配器載入失敗，請保持連線後重新開啟。';
        target.className = 'storage-status error';
      }
    };
    document.head.appendChild(script);
  }

  // EmulatorJS 4.2.3 ships zh-CN but not zh-TW. Keep the Amin shell in
  // Traditional Chinese while mapping the embedded emulator to a real locale.
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
    if (runtimeLabel) runtimeLabel.textContent = 'LIVE RUNTIME · 0.9.2-rc17';

    const status = $('diagnosticReporterStatus');
    const card = status?.closest('article');
    if (!card) return;

    const heading = card.querySelector('h2');
    const badge = card.querySelector('.core-badge');
    const description = card.querySelector('.section-head + p');
    if (heading) heading.textContent = '系統診斷與錯誤收集';
    if (badge) badge.textContent = 'ERROR LOG';
    if (description) {
      description.textContent = '遊戲啟動、JavaScript、存檔或原生崩潰時，會傳送去識別化診斷到錯誤收集站。需要排查時，我可以直接讀取最新回報；不再自動建立 GitHub Issue，也不傳送 ROM、存檔內容、檔名、Email 或裝置路徑。';
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
      return `保護正常：遊戲庫 ${total} 款，已確認 ${mirrored || total} 款第二層 ROM 鏡像。`;
    }
    return '遊戲庫目前沒有 ROM。匯入後會自動建立第二層鏡像。';
  }

  addEventListener('amin-rom-vault-ready', event => {
    const detail = event.detail || {};
    setVaultStatus(describe(detail), detail.recovered ? 'success' : '');
  });

  const button = $('repairRomLibraryButton');
  button?.addEventListener('click', async () => {
    button.disabled = true;
    setVaultStatus('正在比對 IndexedDB 與 ROM 鏡像…', 'working');
    try {
      const detail = await window.AMIN_ROM_VAULT?.recover?.();
      if (!detail) throw new Error('ROM 可靠性引擎尚未準備完成。');
      setVaultStatus(describe(detail), 'success');
      if (detail.recovered > 0) {
        setTimeout(() => location.reload(), 700);
      }
    } catch (error) {
      setVaultStatus(error?.message || '遊戲庫修復失敗。', 'error');
    } finally {
      button.disabled = false;
    }
  });

  loadLaunchCompatibilityLayer();
  loadSaveCompatibilityLayer();
  updateReleaseCopy();

  setTimeout(async () => {
    try {
      const detail = await window.AMIN_ROM_VAULT?.recover?.();
      if (detail) setVaultStatus(describe(detail), detail.recovered ? 'success' : '');
    } catch (error) {
      setVaultStatus(error?.message || '暫時無法檢查 ROM 鏡像。', 'error');
    }
  }, 900);
})();
