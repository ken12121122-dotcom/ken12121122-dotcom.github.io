(() => {
  'use strict';

  const STORAGE_KEY = 'amin-gba-controller-profile-v1';
  const ACTIONS = [
    { id:'A', label:'GBA A', desc:'確認／互動', logicalIndex:8 },
    { id:'B', label:'GBA B', desc:'取消／返回', logicalIndex:0 },
    { id:'START', label:'Start', desc:'開始／選單', logicalIndex:3 },
    { id:'SELECT', label:'Select', desc:'選擇／切換', logicalIndex:2 },
    { id:'L', label:'L', desc:'左肩鍵', logicalIndex:10 },
    { id:'R', label:'R', desc:'右肩鍵', logicalIndex:11 },
    { id:'UP', label:'方向上', desc:'十字鍵與左搖桿可並存', logicalIndex:4 },
    { id:'DOWN', label:'方向下', desc:'十字鍵與左搖桿可並存', logicalIndex:5 },
    { id:'LEFT', label:'方向左', desc:'十字鍵與左搖桿可並存', logicalIndex:6 },
    { id:'RIGHT', label:'方向右', desc:'十字鍵與左搖桿可並存', logicalIndex:7 }
  ];

  const BUTTON_LABELS = {
    0:'BUTTON_1',1:'BUTTON_2',2:'BUTTON_3',3:'BUTTON_4',
    4:'LEFT_TOP_SHOULDER',5:'RIGHT_TOP_SHOULDER',
    6:'LEFT_BOTTOM_SHOULDER',7:'RIGHT_BOTTOM_SHOULDER',
    8:'SELECT',9:'START',10:'LEFT_STICK',11:'RIGHT_STICK',
    12:'DPAD_UP',13:'DPAD_DOWN',14:'DPAD_LEFT',15:'DPAD_RIGHT'
  };

  const FRIENDLY_NAMES = {
    BUTTON_1:'按鈕 1',BUTTON_2:'按鈕 2',BUTTON_3:'按鈕 3',BUTTON_4:'按鈕 4',
    LEFT_TOP_SHOULDER:'左肩鍵 L1',RIGHT_TOP_SHOULDER:'右肩鍵 R1',
    LEFT_BOTTOM_SHOULDER:'左肩鍵 L2',RIGHT_BOTTOM_SHOULDER:'右肩鍵 R2',
    SELECT:'Select',START:'Start',LEFT_STICK:'左搖桿按下',RIGHT_STICK:'右搖桿按下',
    DPAD_UP:'十字鍵上',DPAD_DOWN:'十字鍵下',DPAD_LEFT:'十字鍵左',DPAD_RIGHT:'十字鍵右',
    'LEFT_STICK_X:+1':'左搖桿右','LEFT_STICK_X:-1':'左搖桿左',
    'LEFT_STICK_Y:+1':'左搖桿下','LEFT_STICK_Y:-1':'左搖桿上',
    'RIGHT_STICK_X:+1':'右搖桿右','RIGHT_STICK_X:-1':'右搖桿左',
    'RIGHT_STICK_Y:+1':'右搖桿下','RIGHT_STICK_Y:-1':'右搖桿上'
  };

  const AXIS_LABELS = [
    ['LEFT_STICK_X:-1','LEFT_STICK_X:+1'],
    ['LEFT_STICK_Y:-1','LEFT_STICK_Y:+1'],
    ['RIGHT_STICK_X:-1','RIGHT_STICK_X:+1'],
    ['RIGHT_STICK_Y:-1','RIGHT_STICK_Y:+1']
  ];

  const $ = id => document.getElementById(id);
  const mappingList = $('mappingList');
  const capturePanel = $('capturePanel');
  const captureTitle = $('captureTitle');
  const connectionBadge = $('connectionBadge');
  const gamepadName = $('gamepadName');
  const rawButtons = $('rawButtons');
  const logicalTest = $('logicalTest');
  const deadzoneInput = $('deadzoneInput');
  const deadzoneValue = $('deadzoneValue');

  let profile = loadProfile();
  let activePad = null;
  let pendingAction = null;
  let previousButtons = [];
  let previousAxes = [0,0,0,0];

  function suggestedProfile() {
    return {
      version:1,
      controllerId:'',
      deadzone:0.55,
      actions:{
        A:['BUTTON_2'],
        B:['BUTTON_3'],
        START:['START'],
        SELECT:['SELECT'],
        L:['LEFT_TOP_SHOULDER'],
        R:['RIGHT_TOP_SHOULDER'],
        UP:['DPAD_UP','LEFT_STICK_Y:-1'],
        DOWN:['DPAD_DOWN','LEFT_STICK_Y:+1'],
        LEFT:['DPAD_LEFT','LEFT_STICK_X:-1'],
        RIGHT:['DPAD_RIGHT','LEFT_STICK_X:+1']
      }
    };
  }

  function loadProfile() {
    try {
      const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY));
      if (parsed?.version === 1 && parsed.actions) return parsed;
    } catch {}
    return suggestedProfile();
  }

  function saveProfile() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(profile));
    const badge = $('saveBadge');
    if (badge) {
      badge.textContent = '已儲存';
      window.setTimeout(() => badge.textContent = '自動儲存', 900);
    }
  }

  function friendly(binding) {
    return FRIENDLY_NAMES[binding] || binding;
  }

  function escapeHtml(value) {
    return String(value ?? '').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;').replaceAll("'",'&#039;');
  }

  function renderMappings() {
    mappingList.innerHTML = ACTIONS.map(action => {
      const bindings = Array.isArray(profile.actions[action.id]) ? profile.actions[action.id] : [];
      const chips = bindings.length ? bindings.map(binding => `
        <span class="binding-chip" title="${escapeHtml(binding)}">
          ${escapeHtml(friendly(binding))}
          <button type="button" data-remove-binding="${escapeHtml(binding)}" data-action-id="${action.id}" aria-label="移除 ${escapeHtml(friendly(binding))}">✕</button>
        </span>
      `).join('') : '<span class="no-binding">無功能</span>';

      return `
        <article class="mapping-row" data-mapping-action="${action.id}">
          <div class="mapping-name"><strong>${action.label}</strong><span>${action.desc}</span></div>
          <div class="binding-list">${chips}</div>
          <div class="mapping-actions">
            <button type="button" data-add-binding="${action.id}">新增綁定</button>
            <button type="button" class="secondary" data-clear-action="${action.id}">清除</button>
          </div>
        </article>
      `;
    }).join('');
  }

  function renderLogicalTest() {
    logicalTest.innerHTML = ACTIONS.map(action => `<div class="logical-key" data-logical-key="${action.id}">${action.label}</div>`).join('');
  }

  function getGamepad() {
    const pads = navigator.getGamepads?.() || [];
    return [...pads].find(Boolean) || null;
  }

  function bindingActive(binding, pad) {
    if (!pad) return false;
    const buttonIndex = Object.entries(BUTTON_LABELS).find(([,label]) => label === binding)?.[0];
    if (buttonIndex !== undefined) return Boolean(pad.buttons[Number(buttonIndex)]?.pressed);

    const match = binding.match(/^(LEFT|RIGHT)_STICK_([XY]):([+-]1)$/);
    if (!match) return false;
    const sideOffset = match[1] === 'LEFT' ? 0 : 2;
    const axisIndex = sideOffset + (match[2] === 'X' ? 0 : 1);
    const value = pad.axes[axisIndex] || 0;
    const threshold = Number(profile.deadzone) || 0.55;
    return match[3] === '+1' ? value >= threshold : value <= -threshold;
  }

  function updateLogicalTest(pad) {
    ACTIONS.forEach(action => {
      const active = (profile.actions[action.id] || []).some(binding => bindingActive(binding,pad));
      document.querySelector(`[data-logical-key="${action.id}"]`)?.classList.toggle('active',active);
    });
  }

  function updatePadDisplay(pad) {
    activePad = pad;
    if (!pad) {
      connectionBadge.textContent = '等待手把';
      connectionBadge.className = 'badge';
      gamepadName.textContent = '尚未偵測到手把。請按任意鍵喚醒，並確認手把在 ANALOG 模式。';
      rawButtons.innerHTML = '';
      updateLogicalTest(null);
      return;
    }

    connectionBadge.textContent = '已連線';
    connectionBadge.className = 'badge active';
    gamepadName.textContent = `${pad.id} · mapping: ${pad.mapping || '自訂'}`;
    if (!profile.controllerId) {
      profile.controllerId = pad.id;
      saveProfile();
    }

    rawButtons.innerHTML = pad.buttons.map((button,index) => `<span class="raw-button ${button.pressed ? 'active' : ''}">${index + 1}</span>`).join('');
    for (let i=0;i<4;i+=1) {
      const value = Number(pad.axes[i] || 0);
      const meter = $(`axis${i}Meter`);
      const label = $(`axis${i}Value`);
      if (label) label.textContent = value.toFixed(2);
      if (meter) {
        const percentage = Math.min(50,Math.abs(value)*50);
        meter.style.width = `${percentage}%`;
        meter.style.left = value < 0 ? `${50-percentage}%` : '50%';
      }
    }
    updateLogicalTest(pad);
  }

  function startCapture(actionId) {
    pendingAction = actionId;
    const action = ACTIONS.find(item => item.id === actionId);
    captureTitle.textContent = `正在設定 ${action?.label || actionId}`;
    capturePanel.classList.remove('hidden');
    const pad = getGamepad();
    previousButtons = pad ? pad.buttons.map(button => button.pressed) : [];
    previousAxes = pad ? [0,1,2,3].map(index => pad.axes[index] || 0) : [0,0,0,0];
  }

  function cancelCapture() {
    pendingAction = null;
    capturePanel.classList.add('hidden');
  }

  function addBinding(actionId,binding) {
    profile.actions[actionId] = Array.isArray(profile.actions[actionId]) ? profile.actions[actionId] : [];
    if (!profile.actions[actionId].includes(binding)) profile.actions[actionId].push(binding);
    saveProfile();
    renderMappings();
    cancelCapture();
  }

  function detectCapture(pad) {
    if (!pendingAction || !pad) return;

    for (let index=0;index<pad.buttons.length;index+=1) {
      const pressed = Boolean(pad.buttons[index]?.pressed);
      if (pressed && !previousButtons[index]) {
        addBinding(pendingAction,BUTTON_LABELS[index] || `GAMEPAD_${index}`);
        return;
      }
    }

    const threshold = Number(profile.deadzone) || 0.55;
    for (let index=0;index<Math.min(4,pad.axes.length);index+=1) {
      const value = Number(pad.axes[index] || 0);
      const previous = Number(previousAxes[index] || 0);
      if (Math.abs(value) >= threshold && Math.abs(previous) < threshold) {
        const binding = value < 0 ? AXIS_LABELS[index][0] : AXIS_LABELS[index][1];
        addBinding(pendingAction,binding);
        return;
      }
    }

    previousButtons = pad.buttons.map(button => button.pressed);
    previousAxes = [0,1,2,3].map(index => pad.axes[index] || 0);
  }

  mappingList.addEventListener('click',event => {
    const add = event.target.closest('[data-add-binding]');
    if (add) {
      startCapture(add.dataset.addBinding);
      return;
    }

    const clear = event.target.closest('[data-clear-action]');
    if (clear) {
      profile.actions[clear.dataset.clearAction] = [];
      saveProfile();
      renderMappings();
      return;
    }

    const remove = event.target.closest('[data-remove-binding]');
    if (remove) {
      const actionId = remove.dataset.actionId;
      profile.actions[actionId] = (profile.actions[actionId] || []).filter(binding => binding !== remove.dataset.removeBinding);
      saveProfile();
      renderMappings();
    }
  });

  $('applySuggestedButton').addEventListener('click',() => {
    const controllerId = activePad?.id || profile.controllerId || '';
    profile = suggestedProfile();
    profile.controllerId = controllerId;
    deadzoneInput.value = profile.deadzone;
    deadzoneValue.textContent = profile.deadzone.toFixed(2);
    saveProfile();
    renderMappings();
  });

  $('clearAllButton').addEventListener('click',() => {
    if (!confirm('清除所有按鈕與搖桿綁定？')) return;
    ACTIONS.forEach(action => profile.actions[action.id] = []);
    saveProfile();
    renderMappings();
  });

  $('cancelCaptureButton').addEventListener('click',cancelCapture);
  $('refreshGamepadButton').addEventListener('click',() => updatePadDisplay(getGamepad()));
  deadzoneInput.addEventListener('input',() => {
    profile.deadzone = Number(deadzoneInput.value);
    deadzoneValue.textContent = profile.deadzone.toFixed(2);
    saveProfile();
  });

  addEventListener('gamepadconnected',event => {
    profile.controllerId = event.gamepad.id;
    saveProfile();
    updatePadDisplay(event.gamepad);
  });
  addEventListener('gamepaddisconnected',() => updatePadDisplay(getGamepad()));

  function loop() {
    const pad = getGamepad();
    updatePadDisplay(pad);
    detectCapture(pad);
    requestAnimationFrame(loop);
  }

  deadzoneInput.value = profile.deadzone || 0.55;
  deadzoneValue.textContent = Number(profile.deadzone || 0.55).toFixed(2);
  renderMappings();
  renderLogicalTest();
  requestAnimationFrame(loop);
})();
