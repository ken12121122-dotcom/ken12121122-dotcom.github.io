(() => {
  'use strict';

  const STORAGE_KEY = 'amin-gba-controller-profile-v1';
  const NATIVE_DEFAULTS = {
    A:['NATIVE_KEY:KEYCODE_BUTTON_2','NATIVE_KEY:KEYCODE_BUTTON_A'],
    B:['NATIVE_KEY:KEYCODE_BUTTON_3','NATIVE_KEY:KEYCODE_BUTTON_B'],
    START:['NATIVE_KEY:KEYCODE_BUTTON_START'],
    SELECT:['NATIVE_KEY:KEYCODE_BUTTON_SELECT'],
    L:['NATIVE_KEY:KEYCODE_BUTTON_L1'],
    R:['NATIVE_KEY:KEYCODE_BUTTON_R1'],
    UP:['NATIVE_KEY:KEYCODE_DPAD_UP','NATIVE_AXIS:AXIS_Y:-1','NATIVE_AXIS:AXIS_HAT_Y:-1'],
    DOWN:['NATIVE_KEY:KEYCODE_DPAD_DOWN','NATIVE_AXIS:AXIS_Y:+1','NATIVE_AXIS:AXIS_HAT_Y:+1'],
    LEFT:['NATIVE_KEY:KEYCODE_DPAD_LEFT','NATIVE_AXIS:AXIS_X:-1','NATIVE_AXIS:AXIS_HAT_X:-1'],
    RIGHT:['NATIVE_KEY:KEYCODE_DPAD_RIGHT','NATIVE_AXIS:AXIS_X:+1','NATIVE_AXIS:AXIS_HAT_X:+1']
  };

  let pendingAction = null;
  let previousAxes = {};

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

  function migrateDefaults() {
    const profile = loadProfile();
    if (profile.nativeSchemaVersion >= 1) return;
    Object.entries(NATIVE_DEFAULTS).forEach(([actionId, bindings]) => {
      const current = Array.isArray(profile.actions[actionId]) ? profile.actions[actionId] : [];
      bindings.forEach(binding => {
        if (!current.includes(binding)) current.push(binding);
      });
      profile.actions[actionId] = current;
    });
    profile.nativeSchemaVersion = 1;
    saveProfile(profile);
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
      const directionName = direction === '+1' ? '正向' : '負向';
      return `Android ${axis} ${directionName}`;
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
    const detail = event.detail || {};
    if (!pendingAction || !detail.pressed || Number(detail.repeatCount || 0) > 0) return;
    addBinding(pendingAction,`NATIVE_KEY:${detail.keyName}`);
  });

  window.addEventListener('amin-native-motion',event => {
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

  const observer = new MutationObserver(decorateNativeBindings);
  observer.observe(document.documentElement,{subtree:true,childList:true});

  migrateDefaults();
  decorateNativeBindings();

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
