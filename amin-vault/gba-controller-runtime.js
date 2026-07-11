(() => {
  'use strict';

  const STORAGE_KEY = 'amin-gba-controller-profile-v1';
  const BUTTON_LABELS = {
    0:'BUTTON_1',1:'BUTTON_2',2:'BUTTON_3',3:'BUTTON_4',
    4:'LEFT_TOP_SHOULDER',5:'RIGHT_TOP_SHOULDER',
    6:'LEFT_BOTTOM_SHOULDER',7:'RIGHT_BOTTOM_SHOULDER',
    8:'SELECT',9:'START',10:'LEFT_STICK',11:'RIGHT_STICK',
    12:'DPAD_UP',13:'DPAD_DOWN',14:'DPAD_LEFT',15:'DPAD_RIGHT'
  };

  const ACTIONS = [
    { id:'A', logicalIndex:8 },
    { id:'B', logicalIndex:0 },
    { id:'START', logicalIndex:3 },
    { id:'SELECT', logicalIndex:2 },
    { id:'L', logicalIndex:10 },
    { id:'R', logicalIndex:11 },
    { id:'UP', logicalIndex:4 },
    { id:'DOWN', logicalIndex:5 },
    { id:'LEFT', logicalIndex:6 },
    { id:'RIGHT', logicalIndex:7 }
  ];

  let animationFrame = 0;
  let currentStates = new Map();
  let prepared = false;

  function fallbackProfile() {
    return {
      version:1,
      deadzone:0.55,
      actions:{
        A:['BUTTON_2'],B:['BUTTON_3'],START:['START'],SELECT:['SELECT'],
        L:['LEFT_TOP_SHOULDER'],R:['RIGHT_TOP_SHOULDER'],
        UP:['DPAD_UP','LEFT_STICK_Y:-1'],
        DOWN:['DPAD_DOWN','LEFT_STICK_Y:+1'],
        LEFT:['DPAD_LEFT','LEFT_STICK_X:-1'],
        RIGHT:['DPAD_RIGHT','LEFT_STICK_X:+1']
      }
    };
  }

  function loadProfile() {
    try {
      const profile = JSON.parse(localStorage.getItem(STORAGE_KEY));
      if (profile?.version === 1 && profile.actions) return profile;
    } catch {}
    return fallbackProfile();
  }

  function getPad() {
    return [...(navigator.getGamepads?.() || [])].find(Boolean) || null;
  }

  function bindingActive(binding,pad,deadzone) {
    if (!pad) return false;

    const buttonEntry = Object.entries(BUTTON_LABELS).find(([,label]) => label === binding);
    if (buttonEntry) return Boolean(pad.buttons[Number(buttonEntry[0])]?.pressed);

    const generic = binding.match(/^GAMEPAD_(\d+)$/);
    if (generic) return Boolean(pad.buttons[Number(generic[1])]?.pressed);

    const match = binding.match(/^(LEFT|RIGHT)_STICK_([XY]):([+-]1)$/);
    if (!match) return false;
    const sideOffset = match[1] === 'LEFT' ? 0 : 2;
    const axisIndex = sideOffset + (match[2] === 'X' ? 0 : 1);
    const value = Number(pad.axes[axisIndex] || 0);
    return match[3] === '+1' ? value >= deadzone : value <= -deadzone;
  }

  function releaseAll() {
    if (!window.EJS_emulator?.gameManager) return;
    ACTIONS.forEach(action => {
      try { window.EJS_emulator.gameManager.simulateInput(0,action.logicalIndex,0); } catch {}
    });
    currentStates.clear();
  }

  function poll() {
    const emulator = window.EJS_emulator;
    if (!emulator?.gameManager || !emulator.started) {
      animationFrame = requestAnimationFrame(poll);
      return;
    }

    const pad = getPad();
    const profile = loadProfile();
    const deadzone = Number(profile.deadzone) || 0.55;

    ACTIONS.forEach(action => {
      const active = (profile.actions[action.id] || []).some(binding => bindingActive(binding,pad,deadzone));
      const previous = currentStates.get(action.id) || false;
      if (active !== previous) {
        try {
          emulator.gameManager.simulateInput(0,action.logicalIndex,active ? 1 : 0);
          currentStates.set(action.id,active);
        } catch {}
      }
    });

    animationFrame = requestAnimationFrame(poll);
  }

  function disableBuiltInControllerBindings() {
    const keyboardValues = {
      0:'x',2:'v',3:'enter',4:'ArrowUp',5:'ArrowDown',6:'ArrowLeft',7:'ArrowRight',8:'z',10:'q',11:'e'
    };
    const player = {};
    [0,2,3,4,5,6,7,8,10,11,16,17,18,19,20,21,22,23].forEach(index => {
      player[index] = { value:keyboardValues[index] || '', value2:'' };
    });
    window.EJS_defaultControls = { 0:player,1:{},2:{},3:{} };
  }

  function prepareRuntime() {
    if (prepared) return;
    prepared = true;
    disableBuiltInControllerBindings();

    const existingReady = window.EJS_ready;
    window.EJS_ready = (...args) => {
      if (typeof existingReady === 'function') existingReady(...args);
      cancelAnimationFrame(animationFrame);
      currentStates.clear();
      animationFrame = requestAnimationFrame(poll);
    };
  }

  document.addEventListener('click',event => {
    if (!event.target.closest('[data-play]')) return;
    prepareRuntime();
  },true);

  addEventListener('pagehide',() => {
    cancelAnimationFrame(animationFrame);
    releaseAll();
  });

  window.AMIN_GBA_CONTROLLER_RUNTIME = {
    prepare:prepareRuntime,
    releaseAll,
    version:'0.8.4'
  };
})();
