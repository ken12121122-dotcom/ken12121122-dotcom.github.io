(() => {
  'use strict';

  const $ = id => document.getElementById(id);

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

  setTimeout(async () => {
    try {
      const detail = await window.AMIN_ROM_VAULT?.recover?.();
      if (detail) setVaultStatus(describe(detail), detail.recovered ? 'success' : '');
    } catch (error) {
      setVaultStatus(error?.message || '暫時無法檢查 ROM 鏡像。', 'error');
    }
  }, 900);
})();
