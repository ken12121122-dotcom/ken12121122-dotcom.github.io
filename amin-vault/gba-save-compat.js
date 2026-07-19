(() => {
  'use strict';

  const START_DELAY_MS = 2200;
  const FLUSH_INTERVAL_MS = 10000;
  const RETRY_DELAY_MS = 900;
  const MAX_READY_WAIT_MS = 30000;
  const COMPAT_VERSION = '1.0.1';

  let pumpTimer = null;
  let flushing = false;
  let gameStartedAt = 0;
  let lastVerifiedAt = 0;
  let lastVerifiedBytes = 0;
  let wrappedLaunchToken = null;

  const $ = id => document.getElementById(id);
  const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

  function saveGuard() {
    return window.AMIN_GBA_SAVE_GUARD || null;
  }

  function emulatorReady() {
    return Boolean(
      window.EJS_emulator
      && window.EJS_emulator.started
      && window.EJS_emulator.gameManager
    );
  }

  async function waitForEmulatorReady(timeoutMs = MAX_READY_WAIT_MS) {
    const startedAt = Date.now();
    while (Date.now() - startedAt < timeoutMs) {
      if (emulatorReady()) return true;
      if (window.EJS_emulator?.failedToStart) return false;
      await sleep(200);
    }
    return false;
  }

  function ensureUi() {
    if (!document.getElementById('aminSaveCompatStyle')) {
      const style = document.createElement('style');
      style.id = 'aminSaveCompatStyle';
      style.textContent = `
        .save-game-button{
          position:absolute;
          z-index:10005;
          top:max(8px,env(safe-area-inset-top));
          right:max(8px,env(safe-area-inset-right));
          min-height:38px;
          padding:8px 13px;
          border:1px solid rgba(255,255,255,.2);
          border-radius:999px;
          background:rgba(0,0,0,.58);
          color:#fff;
          font-size:.74rem;
          font-weight:850;
          opacity:.72;
          backdrop-filter:blur(10px);
        }
        .save-game-button:active,.save-game-button:focus-visible{opacity:1}
        .save-game-button[data-state="saving"]{opacity:1;color:#ffe19a}
        .save-game-button[data-state="saved"]{opacity:1;color:#8fffcf}
        .save-game-button[data-state="empty"]{opacity:1;color:#ffc6a3}
      `;
      document.head.appendChild(style);
    }

    const player = $('playerView');
    if (player && !$('saveGameNow')) {
      const button = document.createElement('button');
      button.id = 'saveGameNow';
      button.type = 'button';
      button.className = 'save-game-button';
      button.textContent = '💾 保存進度';
      button.setAttribute('aria-label', '立即將遊戲內存檔寫入 Amin 保險庫');
      button.addEventListener('click', event => {
        event.preventDefault();
        event.stopPropagation();
        flushWithRetry('manual', true);
      });
      player.appendChild(button);
    }
  }

  function setButtonState(state, text) {
    const button = $('saveGameNow');
    if (!button) return;
    button.dataset.state = state || '';
    button.textContent = text;
    if (state === 'saved') {
      setTimeout(() => {
        if (button.dataset.state === 'saved') {
          button.dataset.state = '';
          button.textContent = '💾 保存進度';
        }
      }, 2600);
    }
  }

  function normalizeDiagnostic(detail = {}) {
    const payload = {
      status: 'verified',
      detail: `${Number(detail.byteLength || 0)} bytes`,
      at: Number(detail.updatedAt || Date.now()),
      gameId: window.EJS_gameID || null,
      gameName: window.EJS_gameName || null,
      saveKey: detail.saveKey || null,
      romId: detail.romId || null,
      byteLength: Number(detail.byteLength || 0),
      native: Boolean(detail.native),
      indexedDb: Boolean(detail.indexedDb),
      source: detail.source || 'compat-pump'
    };
    try {
      localStorage.setItem('amin-gba-save-status', JSON.stringify(payload));
    } catch {}
  }

  async function flushOnce(reason) {
    const guard = saveGuard();
    if (!guard?.flush || !emulatorReady()) return false;
    return Boolean(await guard.flush(reason));
  }

  async function flushWithRetry(reason = 'compat-periodic', visible = false) {
    if (flushing || !emulatorReady()) return false;
    flushing = true;
    if (visible) setButtonState('saving', '正在保存…');
    try {
      let saved = await flushOnce(reason);
      if (!saved) {
        await sleep(RETRY_DELAY_MS);
        saved = await flushOnce(`${reason}-retry`);
      }

      if (saved) {
        const guard = saveGuard();
        const summary = guard?.getSummary
          ? await guard.getSummary().catch(() => null)
          : null;
        const bytes = Number(summary?.byteLength || lastVerifiedBytes || 0);
        const native = Boolean(summary?.native);
        lastVerifiedAt = Number(summary?.updatedAt || Date.now());
        lastVerifiedBytes = bytes;
        if (visible) {
          setButtonState('saved', `✓ 已保存 ${bytes ? `${Math.ceil(bytes / 1024)} KB` : ''}`.trim());
          window.EJS_emulator?.displayMessage?.(
            `Amin 已保存進度${native ? '（含 Android 副本）' : ''}`
          );
        }
        return true;
      }

      if (visible) {
        setButtonState('empty', '尚無遊戲存檔');
        window.EJS_emulator?.displayMessage?.('請先在遊戲選單執行一次「存檔」');
        setTimeout(() => {
          const button = $('saveGameNow');
          if (button?.dataset.state === 'empty') {
            button.dataset.state = '';
            button.textContent = '💾 保存進度';
          }
        }, 3200);
      }
      return false;
    } catch (error) {
      console.warn('[Amin Save Compat] flush failed', error);
      if (visible) setButtonState('empty', '保存失敗');
      dispatchEvent(new CustomEvent('amin-save-status', {
        detail: {
          status: 'compat-flush-failed',
          detail: error?.message || String(error),
          at: Date.now(),
          gameName: window.EJS_gameName || null
        }
      }));
      return false;
    } finally {
      flushing = false;
    }
  }

  function stopPump() {
    if (pumpTimer) clearInterval(pumpTimer);
    pumpTimer = null;
  }

  function startPump() {
    stopPump();
    pumpTimer = setInterval(() => {
      if (!document.hidden && emulatorReady()) {
        flushWithRetry('compat-periodic', false);
      }
    }, FLUSH_INTERVAL_MS);
  }

  async function onGameStarted() {
    gameStartedAt = Date.now();
    ensureUi();
    const ready = await waitForEmulatorReady();
    if (!ready) return;

    try {
      const guard = saveGuard();
      if (guard?.restore) await guard.restore();
    } catch (error) {
      console.warn('[Amin Save Compat] restore failed', error);
    }

    await sleep(START_DELAY_MS);
    await flushWithRetry('compat-startup', false);
    startPump();

    dispatchEvent(new CustomEvent('amin-save-compat-ready', {
      detail: {
        version: COMPAT_VERSION,
        emulatorVersion: window.EJS_emulator?.ejs_version || 'unknown',
        intervalMs: FLUSH_INTERVAL_MS,
        startedAt: gameStartedAt
      }
    }));
  }

  function wrapLaunchCallback(detail = {}) {
    const token = `${detail.romId || ''}:${detail.channel || ''}:${Date.now()}`;
    if (wrappedLaunchToken === token) return;
    wrappedLaunchToken = token;

    const previous = window.EJS_onGameStart;
    const wrapped = async event => {
      try {
        if (typeof previous === 'function') await previous(event);
      } finally {
        onGameStarted();
      }
    };
    wrapped.__aminSaveCompat = true;
    window.EJS_onGameStart = wrapped;
  }

  addEventListener('amin-gba-launch-status', event => {
    const detail = event.detail || {};
    if (detail.status === 'starting') {
      stopPump();
      wrapLaunchCallback(detail);
    }
    if (['failed', 'emulator-exit'].includes(detail.status)) stopPump();
  });

  addEventListener('amin-save-verified', event => {
    const detail = event.detail || {};
    lastVerifiedAt = Number(detail.updatedAt || Date.now());
    lastVerifiedBytes = Number(detail.byteLength || 0);
    normalizeDiagnostic(detail);
    setButtonState('saved', `✓ 已保存 ${lastVerifiedBytes ? `${Math.ceil(lastVerifiedBytes / 1024)} KB` : ''}`.trim());
  });

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden' && emulatorReady()) {
      flushWithRetry('compat-background', false);
    }
  });

  addEventListener('pagehide', () => {
    if (emulatorReady()) flushWithRetry('compat-pagehide', false);
    stopPump();
  });

  ensureUi();

  window.AMIN_GBA_SAVE_COMPAT = {
    version: COMPAT_VERSION,
    flush: flushWithRetry,
    startPump,
    stopPump,
    status() {
      return {
        emulatorReady: emulatorReady(),
        gameStartedAt,
        lastVerifiedAt,
        lastVerifiedBytes,
        pumpActive: Boolean(pumpTimer)
      };
    }
  };
})();
