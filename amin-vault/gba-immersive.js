(() => {
  'use strict';

  const playerView = document.getElementById('playerView');
  const libraryView = document.getElementById('libraryView');
  const rotateHint = document.getElementById('rotateHint');
  const gameLoading = document.getElementById('gameLoading');
  const activeGameTitle = document.getElementById('activeGameTitle');
  const gameStage = document.getElementById('game');

  if (!playerView || !libraryView || !gameStage) return;

  let fullscreenRequested = false;
  let orientationLocked = false;

  function isPortrait() {
    return matchMedia('(orientation: portrait)').matches;
  }

  function updateRotateHint() {
    const playing = document.body.classList.contains('playing');
    rotateHint?.classList.toggle('hidden', !playing || !isPortrait() || orientationLocked);
  }

  async function lockLandscape() {
    orientationLocked = false;
    try {
      if (screen.orientation?.lock) {
        await screen.orientation.lock('landscape');
        orientationLocked = true;
      }
    } catch {
      orientationLocked = false;
    }
    updateRotateHint();
  }

  function beginImmersiveMode(playButton) {
    const title = playButton.closest('.rom-card')?.querySelector('.rom-info strong')?.textContent?.trim();
    if (title && activeGameTitle) activeGameTitle.textContent = `正在載入 ${title}…`;

    libraryView.classList.add('hidden');
    playerView.classList.remove('hidden');
    gameLoading?.classList.remove('hidden');
    document.body.classList.add('playing');
    updateRotateHint();

    if (!document.fullscreenElement && playerView.requestFullscreen) {
      fullscreenRequested = true;
      let request;
      try {
        request = playerView.requestFullscreen({ navigationUI: 'hide' });
      } catch {
        request = playerView.requestFullscreen();
      }

      Promise.resolve(request)
        .then(lockLandscape)
        .catch(() => {
          fullscreenRequested = false;
          lockLandscape();
        });
    } else {
      lockLandscape();
    }
  }

  function cleanupOrientation() {
    orientationLocked = false;
    try {
      screen.orientation?.unlock?.();
    } catch {
      // Some browsers expose unlock() but reject it outside fullscreen.
    }
  }

  document.addEventListener('click', event => {
    const playButton = event.target.closest('[data-play]');
    if (!playButton) return;
    beginImmersiveMode(playButton);
  }, true);

  document.addEventListener('fullscreenchange', () => {
    if (document.fullscreenElement === playerView) {
      fullscreenRequested = true;
      lockLandscape();
      return;
    }

    fullscreenRequested = false;
    cleanupOrientation();
    updateRotateHint();
  });

  addEventListener('orientationchange', updateRotateHint, { passive: true });
  matchMedia('(orientation: portrait)').addEventListener?.('change', updateRotateHint);

  const observer = new MutationObserver(() => {
    if (!gameStage.childElementCount) return;
    window.setTimeout(() => gameLoading?.classList.add('hidden'), 350);
  });
  observer.observe(gameStage, { childList: true, subtree: true });

  addEventListener('pagehide', cleanupOrientation);
  addEventListener('beforeunload', cleanupOrientation);

  window.AMIN_GBA_IMMERSIVE = {
    enter: beginImmersiveMode,
    lockLandscape,
    get fullscreenRequested() { return fullscreenRequested; },
    get orientationLocked() { return orientationLocked; },
    version: '0.8.2'
  };
})();
