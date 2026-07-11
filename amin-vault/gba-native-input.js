(() => {
  'use strict';

  const pressedKeys = new Map();
  const axes = new Map();
  let device = null;
  let lastKey = null;
  let lastMotion = null;

  function parsePayload(payload) {
    if (payload && typeof payload === 'object') return payload;
    try { return JSON.parse(String(payload || '{}')); } catch { return {}; }
  }

  function emit(name, detail) {
    window.dispatchEvent(new CustomEvent(name, { detail }));
  }

  function receiveKey(payload) {
    const data = parsePayload(payload);
    const keyName = data.keyName || `KEYCODE_${data.keyCode ?? 'UNKNOWN'}`;
    const down = data.action === 'down' || data.action === 0 || data.pressed === true;
    data.keyName = keyName;
    data.pressed = down;
    data.receivedAt = Date.now();
    pressedKeys.set(keyName, down);
    lastKey = data;
    emit('amin-native-key', data);
    return true;
  }

  function receiveMotion(payload) {
    const data = parsePayload(payload);
    const nextAxes = data.axes || {};
    Object.entries(nextAxes).forEach(([name, value]) => axes.set(name, Number(value) || 0));
    data.receivedAt = Date.now();
    lastMotion = data;
    emit('amin-native-motion', data);
    return true;
  }

  function receiveDevice(payload) {
    device = parsePayload(payload);
    emit('amin-native-device', device);
    return true;
  }

  function isKeyPressed(keyName) {
    return pressedKeys.get(keyName) === true;
  }

  function axis(name) {
    return Number(axes.get(name) || 0);
  }

  function reset() {
    pressedKeys.clear();
    axes.clear();
    lastKey = null;
    lastMotion = null;
    emit('amin-native-reset', {});
  }

  window.AMIN_NATIVE_INPUT = {
    receiveKey,
    receiveMotion,
    receiveDevice,
    isKeyPressed,
    axis,
    reset,
    get device() { return device; },
    get lastKey() { return lastKey; },
    get lastMotion() { return lastMotion; },
    get active() { return Boolean(device || /AminPocketGBA/i.test(navigator.userAgent)); },
    version: '0.9.0'
  };
})();
