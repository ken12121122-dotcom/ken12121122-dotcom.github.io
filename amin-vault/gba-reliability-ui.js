(() => {
  'use strict';

  const $ = id => document.getElementById(id);

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
    if (runtimeLabel) runtimeLabel.textContent = 'LIVE RUNTIME · 0.9.2-rc15';

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

  // A partially loaded EmulatorJS page is unsafe to reuse. After the stable
  // fallback also fails, return to a fresh library document with the error shown.
  addEventListener('amin-gba-launch-status', event => {
    const detail = event.detail || {};
    if (detail.status !== 'failed' || detail.channel !== 'stable') return;
    const next = new URL('./gba.html', location.href);
    next.searchParams.set('native', '1');
    next.searchParams.set(
      'launchError',
      detail.emulatorMessage || detail.message || 'mGBA 啟動失敗，已回到乾淨的遊戲庫頁面。'
    );
    setTimeout(() => location.replace(next.href), 250);
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
