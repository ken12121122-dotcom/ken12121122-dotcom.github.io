(() => {
  'use strict';

  const REPORT_VERSION = 1;
  const MAX_EVENTS = 5000;
  const MAX_VISIBLE_ROWS = 450;
  const AXIS_DELTA = 0.045;
  const AXIS_MIN_INTERVAL_MS = 45;

  const $ = id => document.getElementById(id);
  const recordBadge = $('recordBadge');
  const startButton = $('startButton');
  const stopButton = $('stopButton');
  const clearButton = $('clearButton');
  const deviceName = $('deviceName');
  const gamepadStatus = $('gamepadStatus');
  const buttonCount = $('buttonCount');
  const axisCount = $('axisCount');
  const eventCount = $('eventCount');
  const buttonMonitor = $('buttonMonitor');
  const lastKeyValue = $('lastKeyValue');
  const eventLog = $('eventLog');
  const showAxesToggle = $('showAxesToggle');
  const signalSummary = $('signalSummary');
  const markerStatus = $('markerStatus');
  const exportStatus = $('exportStatus');
  const customMarkerInput = $('customMarkerInput');

  let recording = false;
  let startedAt = null;
  let stoppedAt = null;
  let sequence = 0;
  let events = [];
  let activeMarker = null;
  let animationFrame = 0;
  let historyTrapActive = false;
  const previousPads = new Map();
  const lastAxisLogAt = new Map();
  const observedButtons = new Set();
  const observedAxes = new Set();
  const observedKeys = new Set();
  const observedPointers = new Set();
  const devices = new Map();

  function nowIso() {
    return new Date().toISOString();
  }

  function elapsedMs() {
    return startedAt ? Math.round(performance.now() - startedAt.performance) : 0;
  }

  function cleanTarget(target) {
    if (!(target instanceof Element)) return null;
    return {
      tag: target.tagName.toLowerCase(),
      id: target.id || null,
      className: typeof target.className === 'string' ? target.className.slice(0,120) : null
    };
  }

  function pushEvent(source,type,data = {},cssClass = '') {
    if (!recording && source !== 'system') return;
    const item = {
      seq: ++sequence,
      tMs: elapsedMs(),
      at: nowIso(),
      source,
      type,
      marker: activeMarker,
      data
    };
    events.push(item);
    if (events.length > MAX_EVENTS) events.shift();
    eventCount.textContent = String(events.length);
    appendLogRow(item,cssClass);
    updateSummary();
  }

  function formatEvent(item) {
    const data = item.data || {};
    if (item.source === 'gamepad' && item.type.startsWith('button')) {
      return `pad ${data.padIndex} · button[${data.index}] · pressed=${data.pressed} · value=${data.value}`;
    }
    if (item.source === 'gamepad' && item.type === 'axis') {
      return `pad ${data.padIndex} · axis[${data.index}] = ${data.value}`;
    }
    if (item.source === 'keyboard') {
      return `key=${JSON.stringify(data.key)} · code=${data.code || '空'} · keyCode=${data.keyCode} · location=${data.location} · repeat=${data.repeat}`;
    }
    if (item.source === 'pointer') {
      return `${data.pointerType} · button=${data.button} · buttons=${data.buttons} · x=${data.clientX} y=${data.clientY}`;
    }
    if (item.source === 'marker') return data.label || '';
    if (item.source === 'system') return data.message || JSON.stringify(data);
    return JSON.stringify(data);
  }

  function appendLogRow(item,cssClass = '') {
    if (item.type === 'axis' && !showAxesToggle.checked) return;
    if (eventLog.querySelector('.empty-log')) eventLog.innerHTML = '';
    const row = document.createElement('div');
    row.className = `log-row ${cssClass || item.source}`;
    row.dataset.seq = String(item.seq);
    row.innerHTML = `
      <span class="log-time">${(item.tMs/1000).toFixed(3)}s</span>
      <span class="log-type">${escapeHtml(item.source)}:${escapeHtml(item.type)}</span>
      <span class="log-data">${escapeHtml(formatEvent(item))}</span>
    `;
    eventLog.appendChild(row);
    while (eventLog.children.length > MAX_VISIBLE_ROWS) eventLog.firstElementChild?.remove();
    eventLog.scrollTop = eventLog.scrollHeight;
  }

  function rerenderLog() {
    eventLog.innerHTML = '';
    const visible = events.filter(item => showAxesToggle.checked || item.type !== 'axis').slice(-MAX_VISIBLE_ROWS);
    if (!visible.length) {
      eventLog.innerHTML = '<p class="empty-log">尚無符合條件的事件。</p>';
      return;
    }
    visible.forEach(item => appendLogRow(item));
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&','&amp;')
      .replaceAll('<','&lt;')
      .replaceAll('>','&gt;')
      .replaceAll('"','&quot;')
      .replaceAll("'",'&#039;');
  }

  function currentPads() {
    return [...(navigator.getGamepads?.() || [])].filter(Boolean);
  }

  function snapshotPad(pad) {
    return {
      index: pad.index,
      id: pad.id,
      mapping: pad.mapping || '',
      connected: pad.connected,
      buttons: pad.buttons.map(button => ({
        pressed:Boolean(button.pressed),
        touched:Boolean(button.touched),
        value:Number(button.value || 0)
      })),
      axes: [...pad.axes].map(value => Number(value || 0)),
      timestamp:Number(pad.timestamp || 0)
    };
  }

  function registerDevice(pad) {
    const key = `${pad.index}:${pad.id}`;
    if (devices.has(key)) return;
    devices.set(key,{
      index:pad.index,
      id:pad.id,
      mapping:pad.mapping || '',
      buttons:pad.buttons.length,
      axes:pad.axes.length,
      firstSeenAt:nowIso()
    });
    pushEvent('system','gamepad-detected',{
      message:`偵測到 Gamepad ${pad.index}`,
      id:pad.id,
      mapping:pad.mapping || '',
      buttons:pad.buttons.length,
      axes:pad.axes.length
    });
  }

  function pollGamepads() {
    const pads = currentPads();
    const first = pads[0] || null;
    updateLiveDisplay(first);

    pads.forEach(pad => {
      registerDevice(pad);
      const current = snapshotPad(pad);
      const previous = previousPads.get(pad.index);

      if (recording && previous) {
        current.buttons.forEach((button,index) => {
          const old = previous.buttons[index] || {pressed:false,touched:false,value:0};
          const changed = button.pressed !== old.pressed || button.touched !== old.touched || Math.abs(button.value-old.value) >= 0.08;
          if (!changed) return;
          observedButtons.add(`pad${pad.index}:button${index}`);
          pushEvent('gamepad',button.pressed ? 'button-down' : 'button-up',{
            padIndex:pad.index,
            index,
            pressed:button.pressed,
            touched:button.touched,
            value:Number(button.value.toFixed(4))
          });
        });

        current.axes.forEach((value,index) => {
          const old = Number(previous.axes[index] || 0);
          const key = `${pad.index}:${index}`;
          const lastAt = lastAxisLogAt.get(key) || 0;
          const now = performance.now();
          const crossedCenter = Math.sign(value) !== Math.sign(old) && Math.abs(value) > 0.15;
          const crossedThreshold = (Math.abs(old) < 0.5 && Math.abs(value) >= 0.5) || (Math.abs(old) >= 0.5 && Math.abs(value) < 0.5);
          if (Math.abs(value-old) < AXIS_DELTA && !crossedCenter && !crossedThreshold) return;
          if (now-lastAt < AXIS_MIN_INTERVAL_MS && !crossedThreshold) return;
          lastAxisLogAt.set(key,now);
          observedAxes.add(`pad${pad.index}:axis${index}`);
          pushEvent('gamepad','axis',{
            padIndex:pad.index,
            index,
            value:Number(value.toFixed(5)),
            previous:Number(old.toFixed(5))
          },'axis');
        });
      }

      previousPads.set(pad.index,current);
    });

    const connectedIndices = new Set(pads.map(pad => pad.index));
    [...previousPads.keys()].forEach(index => {
      if (!connectedIndices.has(index)) previousPads.delete(index);
    });

    animationFrame = requestAnimationFrame(pollGamepads);
  }

  function updateLiveDisplay(pad) {
    if (!pad) {
      deviceName.textContent = '等待手把。請按任意鍵喚醒，並保持 ANALOG 模式。';
      gamepadStatus.textContent = '未偵測';
      buttonCount.textContent = '0';
      axisCount.textContent = '0';
      buttonMonitor.innerHTML = '';
      for (let index=0;index<4;index+=1) updateAxisMeter(index,0);
      return;
    }

    deviceName.textContent = `${pad.id} · mapping: ${pad.mapping || '自訂／空白'}`;
    gamepadStatus.textContent = `#${pad.index} 已連線`;
    buttonCount.textContent = String(pad.buttons.length);
    axisCount.textContent = String(pad.axes.length);
    buttonMonitor.innerHTML = pad.buttons.map((button,index) => `
      <span class="button-pill ${button.pressed || button.value > .4 ? 'active' : ''}" title="value ${Number(button.value || 0).toFixed(3)}">B${index}</span>
    `).join('');
    for (let index=0;index<4;index+=1) updateAxisMeter(index,Number(pad.axes[index] || 0));
  }

  function updateAxisMeter(index,value) {
    const meter = $(`axisMeter${index}`);
    const label = $(`axisValue${index}`);
    if (label) label.textContent = value.toFixed(3);
    if (!meter) return;
    const percentage = Math.min(50,Math.abs(value)*50);
    meter.style.width = `${percentage}%`;
    meter.style.left = value < 0 ? `${50-percentage}%` : '50%';
  }

  function onKeyEvent(event) {
    if (!recording) return;
    event.preventDefault();
    event.stopPropagation();
    const code = event.code || `(key:${event.key})`;
    observedKeys.add(`${code}|${event.key}|${event.keyCode}`);
    lastKeyValue.textContent = `${event.type} · key=${JSON.stringify(event.key)} · code=${event.code || '空'} · keyCode=${event.keyCode}`;
    pushEvent('keyboard',event.type,{
      key:event.key,
      code:event.code || '',
      keyCode:event.keyCode,
      which:event.which,
      location:event.location,
      repeat:event.repeat,
      altKey:event.altKey,
      ctrlKey:event.ctrlKey,
      metaKey:event.metaKey,
      shiftKey:event.shiftKey,
      isComposing:event.isComposing
    },'keyboard');
  }

  function onPointerEvent(event) {
    if (!recording) return;
    observedPointers.add(`${event.pointerType}:button${event.button}`);
    pushEvent('pointer',event.type,{
      pointerType:event.pointerType,
      pointerId:event.pointerId,
      button:event.button,
      buttons:event.buttons,
      pressure:Number(event.pressure || 0),
      clientX:Math.round(event.clientX),
      clientY:Math.round(event.clientY),
      target:cleanTarget(event.target)
    },'pointer');
  }

  function insertMarker(label) {
    const normalized = String(label || '').trim();
    if (!normalized) return;
    activeMarker = normalized;
    markerStatus.textContent = `目前標記：${normalized}。接下來的訊號會附上此標記。`;
    pushEvent('marker','marker',{label:normalized},'marker');
  }

  function startRecording() {
    if (recording) return;
    recording = true;
    startedAt = {iso:nowIso(),performance:performance.now()};
    stoppedAt = null;
    recordBadge.textContent = '● 錄製中';
    recordBadge.className = 'badge active recording-pulse';
    startButton.disabled = true;
    stopButton.disabled = false;
    document.body.focus({preventScroll:true});
    trapHistory();
    pushEvent('system','recording-started',{message:'開始收集原始輸入訊號'});
  }

  function stopRecording() {
    if (!recording) return;
    pushEvent('system','recording-stopped',{message:'停止收集原始輸入訊號'});
    recording = false;
    stoppedAt = nowIso();
    recordBadge.textContent = '已停止';
    recordBadge.className = 'badge done';
    startButton.disabled = false;
    stopButton.disabled = true;
    exportStatus.textContent = `已收集 ${events.length} 筆事件。現在可下載、複製或分享 JSON。`;
  }

  function clearRecording() {
    if (events.length && !confirm('清除目前所有訊號紀錄？')) return;
    events = [];
    sequence = 0;
    activeMarker = null;
    startedAt = null;
    stoppedAt = null;
    observedButtons.clear();
    observedAxes.clear();
    observedKeys.clear();
    observedPointers.clear();
    devices.clear();
    eventCount.textContent = '0';
    markerStatus.textContent = '尚未插入標記。';
    eventLog.innerHTML = '<p class="empty-log">按「開始收集」後，事件會出現在這裡。</p>';
    updateSummary();
    exportStatus.textContent = '紀錄已清除。';
  }

  function updateSummary() {
    const buttons = [...observedButtons].sort();
    const keys = [...observedKeys].sort();
    const axes = [...observedAxes].sort();
    signalSummary.innerHTML = `
      <div><span>Gamepad 按鈕</span><strong>${escapeHtml(buttons.join(', ') || '尚無')}</strong></div>
      <div><span>鍵盤代碼</span><strong>${escapeHtml(keys.join(', ') || '尚無')}</strong></div>
      <div><span>活動軸</span><strong>${escapeHtml(axes.join(', ') || '尚無')}</strong></div>
      <div><span>Pointer／滑鼠模擬</span><strong>${escapeHtml([...observedPointers].sort().join(', ') || '尚無')}</strong></div>
    `;
  }

  function buildReport() {
    const pads = currentPads().map(snapshotPad);
    return {
      schema:'amin-gba-input-signal-report',
      version:REPORT_VERSION,
      generatedAt:nowIso(),
      recording:{
        startedAt:startedAt?.iso || null,
        stoppedAt:stoppedAt,
        eventCount:events.length
      },
      environment:{
        userAgent:navigator.userAgent,
        platform:navigator.platform || null,
        language:navigator.language,
        languages:navigator.languages,
        viewport:{width:innerWidth,height:innerHeight,devicePixelRatio:devicePixelRatio || 1},
        orientation:screen.orientation ? {type:screen.orientation.type,angle:screen.orientation.angle} : null,
        secureContext:isSecureContext,
        gamepadApi:Boolean(navigator.getGamepads)
      },
      devices:[...devices.values()],
      currentGamepads:pads,
      observed:{
        gamepadButtons:[...observedButtons].sort(),
        axes:[...observedAxes].sort(),
        keyboard:[...observedKeys].sort(),
        pointer:[...observedPointers].sort()
      },
      events
    };
  }

  function reportFile() {
    const report = buildReport();
    const stamp = new Date().toISOString().replace(/[:.]/g,'-');
    return new File([JSON.stringify(report,null,2)],`amin-gba-signal-${stamp}.json`,{type:'application/json'});
  }

  async function copyReport() {
    try {
      await navigator.clipboard.writeText(JSON.stringify(buildReport(),null,2));
      exportStatus.textContent = 'JSON 已複製。可直接貼到聊天。';
    } catch {
      exportStatus.textContent = '瀏覽器拒絕剪貼簿權限，請改用下載 JSON。';
    }
  }

  function downloadReport() {
    const file = reportFile();
    const url = URL.createObjectURL(file);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = file.name;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url),1500);
    exportStatus.textContent = `已下載 ${file.name}。把它上傳到聊天即可。`;
  }

  async function shareReport() {
    const file = reportFile();
    try {
      if (!navigator.share || !navigator.canShare?.({files:[file]})) throw new Error('unsupported');
      await navigator.share({
        title:'Amin GBA 手把訊號報告',
        text:'原始手把輸入訊號，供按鍵映射分析。',
        files:[file]
      });
      exportStatus.textContent = '報告已開啟系統分享選單。';
    } catch (error) {
      if (error?.name === 'AbortError') return;
      exportStatus.textContent = '這個瀏覽器不支援檔案分享，已改用下載。';
      downloadReport();
    }
  }

  function trapHistory() {
    if (historyTrapActive) return;
    historyTrapActive = true;
    history.pushState({aminSignalLab:true},'',location.href);
  }

  addEventListener('popstate',() => {
    if (!recording) return;
    pushEvent('system','browser-back',{message:'收到瀏覽器／Android 返回訊號'});
    history.pushState({aminSignalLab:true},'',location.href);
  });

  addEventListener('gamepadconnected',event => {
    registerDevice(event.gamepad);
    pushEvent('system','gamepad-connected',{
      message:'Gamepad connected',
      index:event.gamepad.index,
      id:event.gamepad.id,
      mapping:event.gamepad.mapping || '',
      buttons:event.gamepad.buttons.length,
      axes:event.gamepad.axes.length
    });
  });

  addEventListener('gamepaddisconnected',event => {
    pushEvent('system','gamepad-disconnected',{
      message:'Gamepad disconnected',
      index:event.gamepad.index,
      id:event.gamepad.id
    });
  });

  document.addEventListener('keydown',onKeyEvent,true);
  document.addEventListener('keyup',onKeyEvent,true);
  document.addEventListener('pointerdown',onPointerEvent,true);
  document.addEventListener('pointerup',onPointerEvent,true);
  document.addEventListener('contextmenu',event => {
    if (!recording) return;
    event.preventDefault();
    pushEvent('pointer','contextmenu',{target:cleanTarget(event.target)});
  },true);
  document.addEventListener('visibilitychange',() => {
    if (recording) pushEvent('system','visibility',{message:`visibility=${document.visibilityState}`});
  });

  startButton.addEventListener('click',startRecording);
  stopButton.addEventListener('click',stopRecording);
  clearButton.addEventListener('click',clearRecording);
  $('wakeButton').addEventListener('click',() => {
    document.body.focus({preventScroll:true});
    updateLiveDisplay(currentPads()[0] || null);
  });
  document.querySelectorAll('[data-marker]').forEach(button => {
    button.addEventListener('click',() => insertMarker(button.dataset.marker));
  });
  $('customMarkerButton').addEventListener('click',() => {
    insertMarker(customMarkerInput.value);
    customMarkerInput.value = '';
  });
  customMarkerInput.addEventListener('keydown',event => {
    if (event.key !== 'Enter') return;
    event.preventDefault();
    insertMarker(customMarkerInput.value);
    customMarkerInput.value = '';
  });
  showAxesToggle.addEventListener('change',rerenderLog);
  $('copyButton').addEventListener('click',copyReport);
  $('downloadButton').addEventListener('click',downloadReport);
  $('shareButton').addEventListener('click',shareReport);

  updateSummary();
  updateLiveDisplay(currentPads()[0] || null);
  animationFrame = requestAnimationFrame(pollGamepads);
  addEventListener('pagehide',() => cancelAnimationFrame(animationFrame));
})();
