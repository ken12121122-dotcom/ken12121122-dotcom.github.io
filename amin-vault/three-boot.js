(() => {
  const root = document.getElementById('threeDesktop');
  const help = document.getElementById('consoleHelp');
  if (!root) return;

  let checks = 0;
  const timer = setInterval(() => {
    checks += 1;
    if (window.AMIN_CONSOLE) {
      root.classList.remove('no-webgl');
      clearInterval(timer);
      return;
    }
    if (checks >= 8) {
      root.classList.add('no-webgl');
      if (help) help.textContent = '3D 引擎尚未載入，已切換成 2D 入口。重新整理後會再次嘗試。';
    }
    if (checks >= 30) clearInterval(timer);
  }, 1000);
})();
