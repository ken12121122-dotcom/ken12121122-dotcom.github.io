(() => {
  'use strict';

  const EXIT_DELAY_MS = 1500;
  let exiting = false;
  let lastFlushAt = 0;

  const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

  function emulatorReady() {
    return Boolean(
      window.EJS_emulator &&
      window.EJS_emulator.started &&
      window.EJS_emulator.gameManager
    );
  }

  function showSaveMessage(text) {
    const loading = document.getElementById('gameLoading');
    const title = document.getElementById('activeGameTitle');
    if (title) title.textContent = text;
    loading?.classList.remove('hidden');
  }

  function recordSaveStatus(status, detail = '') {
    try {
      localStorage.setItem('amin-gba-save-status', JSON.stringify({
        status,
        detail,
        at: Date.now(),
        gameId: window.EJS_gameID || null,
        gameName: window.EJS_gameName || null
      }));
    } catch {
      // Storage may be unavailable in private browsing.
    }
  }

  function flushCartridgeSave(reason = 'manual') {
    if (!emulatorReady()) return false;
    const now = Date.now();
    if (reason !== 'exit' && now - lastFlushAt < 1200) return true;

    try {
      window.EJS_emulator.gameManager.saveSaveFiles();
      lastFlushAt = now;
      recordSaveStatus('flushed', reason);
      return true;
    } catch (error) {
      console.warn('Amin GBA save flush failed:', error);
      recordSaveStatus('flush-failed', error?.message || reason);
      return false;
    }
  }

  async function exitSafely() {
    if (exiting) return;
    const confirmed = window.confirm('離開遊戲並保存目前進度？請先在遊戲內使用「儲存」。');
    if (!confirmed) return;

    exiting = true;
    showSaveMessage('正在保存遊戲進度…');

    const flushed = flushCartridgeSave('exit');

    try {
      if (emulatorReady() && typeof window.EJS_emulator.callEvent === 'function') {
        // EmulatorJS' official exit path triggers another save, unmounts IDBFS,
        // and unloads the core. This was missing from the previous implementation.
        window.EJS_emulator.callEvent('exit');
      }
    } catch (error) {
      console.warn('Amin GBA exit event failed:', error);
      recordSaveStatus('exit-event-failed', error?.message || 'unknown');
    }

    if (flushed) recordSaveStatus('saving-before-exit');
    await sleep(EXIT_DELAY_MS);

    try {
      screen.orientation?.unlock?.();
    } catch {}
    try {
      if (document.fullscreenElement) await document.exitFullscreen();
    } catch {}

    location.replace('./gba.html?v=083');
  }

  // Run before the older click listener in gba.js and stop its immediate redirect.
  document.addEventListener('click', event => {
    const exitButton = event.target.closest('#backToLibrary');
    if (!exitButton) return;
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
    exitSafely();
  }, true);

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden' && !exiting) {
      flushCartridgeSave('background');
    }
  });

  addEventListener('pagehide', () => {
    if (!exiting) flushCartridgeSave('pagehide');
  });

  // Official EmulatorJS save-change callback. It confirms that a changed
  // cartridge save reached the emulator's persistence cycle.
  window.EJS_onSaveUpdate = event => {
    const bytes = event?.save?.byteLength ?? event?.save?.length ?? 0;
    recordSaveStatus('save-updated', `${bytes} bytes`);
  };

  window.EJS_onExit = () => {
    recordSaveStatus('emulator-exited');
  };

  window.AMIN_GBA_SAVE_GUARD = {
    flush: flushCartridgeSave,
    exitSafely,
    version: '0.8.3'
  };
})();
