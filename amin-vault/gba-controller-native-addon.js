(() => {
  'use strict';

  const STORAGE_KEY = 'amin-gba-controller-profile-v1';
  const ACTION_IDS = ['A','B','START','SELECT','L','R','UP','DOWN','LEFT','RIGHT'];
  const NATIVE_DEFAULTS = {
    A:['NATIVE_KEY:KEYCODE_BUTTON_2','NATIVE_KEY:KEYCODE_BUTTON_A'],
    B:['NATIVE_KEY:KEYCODE_BUTTON_3','NATIVE_KEY:KEYCODE_BUTTON_B'],
    START:['NATIVE_KEY:KEYCODE_BUTTON_START','NATIVE_KEY:KEYCODE_BUTTON_10'],
    SELECT:['NATIVE_KEY:KEYCODE_BUTTON_SELECT','NATIVE_KEY:KEYCODE_BUTTON_9'],
    L:['NATIVE_KEY:KEYCODE_BUTTON_L1','NATIVE_KEY:KEYCODE_BUTTON_5'],
    R:['NATIVE_KEY:KEYCODE_BUTTON_R1','NATIVE_KEY:KEYCODE_BUTTON_6'],
    UP:['NATIVE_KEY:KEYCODE_DPAD_UP','NATIVE_AXIS:AXIS_Y:-1','NATIVE_AXIS:AXIS_HAT_Y:-1'],
    DOWN:['NATIVE_KEY:KEYCODE_DPAD_DOWN','NATIVE_AXIS:AXIS_Y:+1','NATIVE_AXIS:AXIS_HAT_Y:+1'],
    LEFT:['NATIVE_KEY:KEYCODE_DPAD_LEFT','NATIVE_AXIS:AXIS_X:-1','NATIVE_AXIS:AXIS_HAT_X:-1'],
    RIGHT:['NATIVE_KEY:KEYCODE_DPAD_RIGHT','NATIVE_AXIS:AXIS_X:+1','NATIVE_AXIS:AXIS_HAT_X:+1']
  };

  let pendingAction = null;
  let previousAxes = {};
  let lastNativeActivityAt = 0;

  function loadProfile() {
    try {
      const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY));
      if (parsed?.version === 1 && parsed.actions) return parsed;
    } catch {}
    return { version:1, deadzone:0.55, actions:{} };
  }

  function saveProfile(profile) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(profile));
  }

  function ensureNativeDefaults(force = false) {
    const profile = loadProfile();
    if (!force && profile.nativeSchemaVersion >= 2) return profile;
    Object.entries(NATIVE_DEFAULTS).forEach(([actionId, bindings]) => {
      const current = Array.isArray(profile.actions[actionId]) ? profile.actions[actionId] : [];
      bindings.forEach(binding => {
        if (!current.includes(binding)) current.push(binding);
      });
      profile.actions[actionId] = current;
    });
    profile.nativeSchemaVersion = 2;
    saveProfile(profile);
    return profile;
  }

  function addBinding(actionId,binding) {
    const profile = loadProfile();
    profile.actions[actionId] = Array.isArray(profile.actions[actionId]) ? profile.actions[actionId] : [];
    if (!profile.actions[actionId].includes(binding)) profile.actions[actionId].push(binding);
    saveProfile(profile);
    sessionStorage.setItem('amin-native-binding-added', `${actionId}|${binding}`);
    location.reload();
  }

  function friendly(binding) {
    if (binding.startsWith('NATIVE_KEY:')) return `Android ${binding.slice(11).replace('KEYCODE_','')}`;
    if (binding.startsWith('NATIVE_AXIS:')) {
      const [,axis,direction] = binding.split(':');
      return `Android ${axis} ${direction === '+1' ? '正向' : '負向'}`;
    }
    return binding;
  }

  function decorateNativeBindings() {
    document.querySelectorAll('.binding-chip').forEach(chip => {
      const binding = chip.getAttribute('title') || '';
      if (!binding.startsWith('NATIVE_') || chip.dataset.nativeDecorated === binding) return;
      const button = chip.querySelector('button');
      chip.childNodes.forEach(node => {
        if (node.nodeType === Node.TEXT_NODE) node.textContent = '';
      });
      chip.insertBefore(document.createTextNode(`${friendly(binding)} `),button || null);
      chip.classList.add('native-binding-chip');
      chip.dataset.nativeDecorated = binding;
    });
  }

  function nativeBindingActive(binding,deadzone) {
    if (binding.startsWith('NATIVE_KEY:')) {
      return Boolean(window.AMIN_NATIVE_INPUT?.isKeyPressed?.(binding.slice(11)));
    }
    const match = binding.match(/^NATIVE_AXIS:([^:]+):([+-]1)$/);
    if (!match) return false;
    const value = Number(window.AMIN_NATIVE_INPUT?.axis?.(match[1]) || 0);
    return match[2] === '+1' ? value >= deadzone : value <= -deadzone;
  }

  function updateNativeLogicalTest() {
    const profile = loadProfile();
    const deadzone = Number(profile.deadzone || 0.55);
    ACTION_IDS.forEach(actionId => {
      const bindings = Array.isArray(profile.actions[actionId]) ? profile.actions[actionId] : [];
      const active = bindings.some(binding => nativeBindingActive(binding,deadzone));
      if (active) {
        document.querySelector(`[data-logical-key="${actionId}"]`)?.classList.add('active');
      }
    });
  }

  function updateNativeDeviceStatus() {
    const nativeInput = window.AMIN_NATIVE_INPUT;
    const device = nativeInput?.device;
    const recentlyActive = Date.now() - lastNativeActivityAt < 2500;
    if (!device && !recentlyActive && !nativeInput?.lastKey && !nativeInput?.lastMotion) return;

    const badge = document.getElementById('connectionBadge');
    const name = document.getElementById('gamepadName');
    if (badge) {
      badge.textContent = 'Android 原生已連線';
      badge.className = 'badge active';
    }
    if (name) {
      const label = device?.name || nativeInput?.lastKey?.deviceName || nativeInput?.lastMotion?.deviceName || 'Android 原生手把';
      const vid = device?.vendorId ?? nativeInput?.lastKey?.vendorId ?? nativeInput?.lastMotion?.vendorId;
      const pid = device?.productId ?? nativeInput?.lastKey?.productId ?? nativeInput?.lastMotion?.productId;
      name.textContent = `${label}${Number.isFinite(Number(vid)) ? ` · VID ${vid}` : ''}${Number.isFinite(Number(pid)) ? ` / PID ${pid}` : ''}`;
    }
  }

  function updateNativeAxisMeters() {
    const axisNames = ['AXIS_X','AXIS_Y','AXIS_Z','AXIS_RZ'];
    axisNames.forEach((axisName,index) => {
      const value = Number(window.AMIN_NATIVE_INPUT?.axis?.(axisName) || 0);
      const meter = document.getElementById(`axis${index}Meter`);
      const label = document.getElementById(`axis${index}Value`);
      if (label) label.textContent = value.toFixed(2);
      if (meter) {
        const percentage = Math.min(50,Math.abs(value)*50);
        meter.style.width = `${percentage}%`;
        meter.style.left = value < 0 ? `${50-percentage}%` : '50%';
      }
    });
  }

  function nativeUiLoop() {
    updateNativeLogicalTest();
    updateNativeDeviceStatus();
    updateNativeAxisMeters();
    requestAnimationFrame(nativeUiLoop);
  }

  document.addEventListener('click',event => {
    const add = event.target.closest('[data-add-binding]');
    if (add) {
      pendingAction = add.dataset.addBinding;
      previousAxes = { ...(window.AMIN_NATIVE_INPUT?.lastMotion?.axes || {}) };
      return;
    }
    if (event.target.closest('#cancelCaptureButton')) pendingAction = null;
  },true);

  window.addEventListener('amin-native-key',event => {
    lastNativeActivityAt = Date.now();
    const detail = event.detail || {};
    if (!pendingAction || !detail.pressed || Number(detail.repeatCount || 0) > 0) return;
    addBinding(pendingAction,`NATIVE_KEY:${detail.keyName}`);
  });

  window.addEventListener('amin-native-motion',event => {
    lastNativeActivityAt = Date.now();
    if (!pendingAction) return;
    const axes = event.detail?.axes || {};
    const deadzone = Number(loadProfile().deadzone || 0.55);
    for (const [name,valueRaw] of Object.entries(axes)) {
      const value = Number(valueRaw || 0);
      const previous = Number(previousAxes[name] || 0);
      if (Math.abs(value) >= deadzone && Math.abs(previous) < deadzone) {
        addBinding(pendingAction,`NATIVE_AXIS:${name}:${value > 0 ? '+1' : '-1'}`);
        return;
      }
    }
    previousAxes = { ...axes };
  });

  window.addEventListener('amin-native-device',() => {
    lastNativeActivityAt = Date.now();
    updateNativeDeviceStatus();
  });

  document.getElementById('applySuggestedButton')?.addEventListener('click',() => {
    setTimeout(() => {
      ensureNativeDefaults(true);
      location.reload();
    },0);
  });

  const observer = new MutationObserver(decorateNativeBindings);
  observer.observe(document.documentElement,{subtree:true,childList:true});

  ensureNativeDefaults();
  decorateNativeBindings();
  requestAnimationFrame(nativeUiLoop);

  const saved = sessionStorage.getItem('amin-native-binding-added');
  if (saved) {
    sessionStorage.removeItem('amin-native-binding-added');
    const [action,binding] = saved.split('|');
    const notice = document.createElement('p');
    notice.className = 'message success';
    notice.textContent = `已擷取 ${action}：${friendly(binding)}`;
    document.querySelector('.hero')?.appendChild(notice);
  }
})();
