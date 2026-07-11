(() => {
  'use strict';

  const nativeEvents = [];
  const observedKeys = new Set();
  const observedAxes = new Set();
  let sequence = 0;
  let startedAt = performance.now();

  const eventLog = document.getElementById('eventLog');
  const signalSummary = document.getElementById('signalSummary');
  const exportActions = document.querySelector('.export-actions');

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&','&amp;')
      .replaceAll('<','&lt;')
      .replaceAll('>','&gt;')
      .replaceAll('"','&quot;')
      .replaceAll("'",'&#039;');
  }

  function append(type,data) {
    const item = {
      seq:++sequence,
      tMs:Math.round(performance.now()-startedAt),
      at:new Date().toISOString(),
      source:'android-native',
      type,
      data
    };
    nativeEvents.push(item);
    if (nativeEvents.length > 5000) nativeEvents.shift();

    if (eventLog) {
      eventLog.querySelector('.empty-log')?.remove();
      const row = document.createElement('div');
      row.className = 'log-row keyboard';
      row.innerHTML = `
        <span class="log-time">${(item.tMs/1000).toFixed(3)}s</span>
        <span class="log-type">native:${escapeHtml(type)}</span>
        <span class="log-data">${escapeHtml(JSON.stringify(data))}</span>
      `;
      eventLog.appendChild(row);
      eventLog.scrollTop = eventLog.scrollHeight;
    }
    updateSummary();
  }

  function updateSummary() {
    if (!signalSummary) return;
    let native = document.getElementById('nativeSignalSummary');
    if (!native) {
      native = document.createElement('div');
      native.id = 'nativeSignalSummary';
      signalSummary.appendChild(native);
    }
    native.innerHTML = `
      <span>Android 原生橋接</span>
      <strong>Keys: ${escapeHtml([...observedKeys].join(', ') || '尚無')}<br>Axes: ${escapeHtml([...observedAxes].join(', ') || '尚無')}</strong>
    `;
  }

  function buildReport() {
    return {
      schema:'amin-gba-native-input-report',
      version:1,
      generatedAt:new Date().toISOString(),
      userAgent:navigator.userAgent,
      device:window.AMIN_NATIVE_INPUT?.device || null,
      observed:{
        keys:[...observedKeys],
        axes:[...observedAxes]
      },
      events:nativeEvents
    };
  }

  function downloadReport() {
    const file = new Blob([JSON.stringify(buildReport(),null,2)],{type:'application/json'});
    const url = URL.createObjectURL(file);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `amin-gba-native-signal-${new Date().toISOString().replace(/[:.]/g,'-')}.json`;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url),1500);
  }

  window.addEventListener('amin-native-device',event => append('device',event.detail || {}));
  window.addEventListener('amin-native-key',event => {
    const data = event.detail || {};
    observedKeys.add(data.keyName || String(data.keyCode));
    append(data.pressed ? 'key-down' : 'key-up',data);
  });
  window.addEventListener('amin-native-motion',event => {
    const data = event.detail || {};
    Object.entries(data.axes || {}).forEach(([name,value]) => {
      if (Math.abs(Number(value || 0)) >= 0.1) observedAxes.add(name);
    });
    append('motion',data);
  });

  if (exportActions) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'secondary';
    button.textContent = '下載 Android 原生 JSON';
    button.addEventListener('click',downloadReport);
    exportActions.appendChild(button);
  }

  updateSummary();
})();
