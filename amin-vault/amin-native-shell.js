(() => {
  'use strict';

  const state = {
    capabilities: null,
    network: null,
    cartridges: []
  };

  const importedTokens = new Set();

  function emit(type, detail) {
    window.dispatchEvent(new CustomEvent(type, { detail }));
  }

  function setDataset(name, value) {
    if (!document.documentElement) return;
    if (value === undefined || value === null) {
      delete document.documentElement.dataset[name];
      return;
    }
    document.documentElement.dataset[name] = String(value);
  }

  function parseVersion(value) {
    return String(value || '')
      .replace(/^[^0-9]*/, '')
      .split(/[.+-]/)
      .slice(0, 3)
      .map(part => Number.parseInt(part, 10) || 0);
  }

  function versionAtLeast(value, minimum) {
    const current = parseVersion(value);
    const target = parseVersion(minimum);
    for (let index = 0; index < 3; index += 1) {
      if (current[index] > target[index]) return true;
      if (current[index] < target[index]) return false;
    }
    return true;
  }

  function updateNativeControls(payload) {
    const updateLink = document.getElementById('nativeUpdateLink');
    if (!updateLink) return;
    const declared = Array.isArray(payload?.capabilities)
      && payload.capabilities.includes('native-update-center');
    const compatibleVersion = versionAtLeast(payload?.nativeVersion, '0.9.2');
    updateLink.classList.toggle('hidden', !(declared || compatibleVersion));
  }

  async function waitForRomInput(timeoutMs = 15000) {
    const started = Date.now();
    while (Date.now() - started < timeoutMs) {
      const input = document.getElementById('romInput');
      if (input) return input;
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    throw new Error('遊戲庫尚未準備完成。');
  }

  async function importNativeCartridge(cartridge) {
    if (!cartridge?.token || !cartridge?.downloadUrl || importedTokens.has(cartridge.token)) {
      return;
    }

    const sessionKey = `amin.native.cartridge.${cartridge.token}`;
    if (sessionStorage.getItem(sessionKey) === 'imported') {
      importedTokens.add(cartridge.token);
      return;
    }

    const status = document.getElementById('importStatus');
    if (status) {
      status.textContent = `正在接收 Android 卡匣：${cartridge.name || '未命名檔案'}…`;
      status.className = 'storage-status working';
    }

    try {
      const response = await fetch(cartridge.downloadUrl, {
        cache: 'no-store',
        credentials: 'same-origin'
      });
      if (!response.ok) {
        throw new Error(`原生卡匣讀取失敗（HTTP ${response.status}）。`);
      }

      const blob = await response.blob();
      const file = new File(
        [blob],
        cartridge.name || 'cartridge.gba',
        {
          type: cartridge.mimeType || blob.type || 'application/octet-stream',
          lastModified: Date.now()
        }
      );

      const input = await waitForRomInput();
      const transfer = new DataTransfer();
      transfer.items.add(file);
      input.files = transfer.files;
      importedTokens.add(cartridge.token);
      sessionStorage.setItem(sessionKey, 'imported');
      input.dispatchEvent(new Event('change', { bubbles: true }));
      emit('amin-native-cartridge-imported', {
        ...cartridge,
        receivedBytes: blob.size
      });
    } catch (error) {
      if (status) {
        status.textContent = error.message || 'Android 卡匣匯入失敗。';
        status.className = 'storage-status error';
      }
      emit('amin-native-cartridge-error', {
        cartridge,
        message: error.message || String(error)
      });
      console.warn('[Amin Native Cartridge]', error);
    }
  }

  const api = {
    state,

    receiveCapabilities(payload) {
      state.capabilities = payload || null;
      setDataset('nativeShell', payload?.nativeVersion || 'connected');
      updateNativeControls(payload);
      emit('amin-native-capabilities', payload || {});
    },

    receiveNetwork(payload) {
      state.network = payload || null;
      setDataset('nativeNetwork', payload?.transport || 'unknown');
      setDataset('nativeNetworkValidated', Boolean(payload?.validated));
      emit('amin-native-network', payload || {});
    },

    receiveCartridge(payload) {
      if (!payload?.token) return;
      state.cartridges.push(payload);
      emit('amin-native-cartridge', payload);
      importNativeCartridge(payload);
    }
  };

  window.AMIN_NATIVE_SHELL = api;
  emit('amin-native-shell-ready', { connected: true });
})();
