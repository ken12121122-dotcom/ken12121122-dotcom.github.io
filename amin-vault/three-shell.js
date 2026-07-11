import * as THREE from 'https://cdn.jsdelivr.net/npm/three@0.185.0/build/three.module.js';

(() => {
  const root = document.getElementById('threeDesktop');
  const host = document.getElementById('threeCanvas');
  const controller = document.getElementById('gbaController');
  const cursor = document.getElementById('consoleCursor');
  const focusLabel = document.getElementById('consoleFocusLabel');
  const modeLabel = document.getElementById('consoleModeLabel');
  const helpLabel = document.getElementById('consoleHelp');
  const menu = document.getElementById('consoleMenu');
  const returnButton = document.getElementById('returnConsoleButton');
  if (!root || !host) return;

  const portalData = [
    { id:'core', label:'VAULT CORE', sub:'架構與核心狀態', target:'architecturePanel', color:0x8da2ff, position:[0,1.25,0] },
    { id:'identity', label:'IDENTITY', sub:'登入與認領', target:'identityPanel', color:0x5ee3b2, position:[-2.5,.25,.55] },
    { id:'capture', label:'CAPTURE', sub:'建立草稿', target:'composePanel', fallback:'identityPanel', color:0xffc878, position:[2.5,.25,.55] },
    { id:'explorer', label:'EXPLORER', sub:'瀏覽正式節點', target:'explorerPanel', fallback:'identityPanel', color:0xd49cff, position:[-1.9,-1.35,.05] },
    { id:'platforms', label:'PLATFORMS', sub:'裝置與同步', target:'platformPanel', color:0x55c9ff, position:[0,-1.55,-.35] },
    { id:'tasks', label:'NEXT TASKS', sub:'下一步任務', target:'nextActionsPanel', color:0xff8d9e, position:[1.9,-1.35,.05] }
  ];

  const state = {
    focusIndex: 0,
    mode: 'focus',
    pointer: new THREE.Vector2(0, 0),
    menuOpen: false,
    portalMeshes: [],
    portalGroups: [],
    pressState: new Map(),
    lastFrame: performance.now()
  };

  const actionLabels = {
    UP:'上', DOWN:'下', LEFT:'左', RIGHT:'右', A:'A', B:'B', L:'L', R:'R', START:'Start', SELECT:'Select'
  };

  function setStatus(text) {
    if (helpLabel) helpLabel.textContent = text;
  }

  function selectedPortal() {
    return portalData[state.focusIndex];
  }

  function updateHud() {
    const portal = selectedPortal();
    if (focusLabel) focusLabel.textContent = `${portal.label} · ${portal.sub}`;
    if (modeLabel) modeLabel.textContent = state.mode === 'focus' ? 'FOCUS MODE' : 'POINTER MODE';
    if (cursor) cursor.classList.toggle('active', state.mode === 'pointer');
  }

  function scrollToPortal(portal) {
    let target = document.getElementById(portal.target);
    if (!target || target.classList.contains('hidden')) {
      target = document.getElementById(portal.fallback || 'identityPanel');
      setStatus('這個入口需要先登入。已帶你前往帳號區。');
    } else {
      setStatus(`OPEN · ${portal.label}`);
    }
    target?.scrollIntoView({ behavior:'smooth', block:'start' });
  }

  function openSelected() {
    if (state.mode === 'pointer') {
      activatePointerTarget();
      return;
    }
    scrollToPortal(selectedPortal());
  }

  function backToConsole() {
    if (state.menuOpen) {
      toggleMenu(false);
      return;
    }
    root.scrollIntoView({ behavior:'smooth', block:'start' });
    setStatus('BACK · 回到掌機桌面');
  }

  function toggleMenu(force) {
    state.menuOpen = typeof force === 'boolean' ? force : !state.menuOpen;
    menu?.classList.toggle('hidden', !state.menuOpen);
    setStatus(state.menuOpen ? 'SYSTEM MENU' : '選擇入口，A 開啟，B 返回');
  }

  function toggleMode() {
    state.mode = state.mode === 'focus' ? 'pointer' : 'focus';
    updateHud();
    setStatus(state.mode === 'focus' ? '十字鍵切換節點' : '十字鍵移動游標，A 點擊');
  }

  function cycle(step) {
    state.focusIndex = (state.focusIndex + step + portalData.length) % portalData.length;
    updateHud();
  }

  function moveFocus(direction) {
    const current = portalData[state.focusIndex];
    const currentPos = new THREE.Vector2(current.position[0], current.position[1]);
    const directionVector = {
      LEFT:new THREE.Vector2(-1,0), RIGHT:new THREE.Vector2(1,0),
      UP:new THREE.Vector2(0,1), DOWN:new THREE.Vector2(0,-1)
    }[direction];
    if (!directionVector) return;

    let best = null;
    portalData.forEach((portal, index) => {
      if (index === state.focusIndex) return;
      const delta = new THREE.Vector2(portal.position[0], portal.position[1]).sub(currentPos);
      const distance = delta.length();
      const alignment = delta.clone().normalize().dot(directionVector);
      if (alignment < .25) return;
      const score = distance - alignment * 2.2;
      if (!best || score < best.score) best = { index, score };
    });
    if (best) {
      state.focusIndex = best.index;
      updateHud();
    }
  }

  function movePointer(direction) {
    const step = .12;
    if (direction === 'LEFT') state.pointer.x -= step;
    if (direction === 'RIGHT') state.pointer.x += step;
    if (direction === 'UP') state.pointer.y += step;
    if (direction === 'DOWN') state.pointer.y -= step;
    state.pointer.x = THREE.MathUtils.clamp(state.pointer.x, -.96, .96);
    state.pointer.y = THREE.MathUtils.clamp(state.pointer.y, -.9, .9);
    updateCursorPosition();
  }

  function dispatchAction(action) {
    setStatus(`${actionLabels[action] || action} · ${state.mode === 'focus' ? 'FOCUS' : 'POINTER'}`);
    if (['UP','DOWN','LEFT','RIGHT'].includes(action)) {
      state.mode === 'focus' ? moveFocus(action) : movePointer(action);
      return;
    }
    if (action === 'A') openSelected();
    if (action === 'B') backToConsole();
    if (action === 'L') cycle(-1);
    if (action === 'R') cycle(1);
    if (action === 'START') toggleMenu();
    if (action === 'SELECT') toggleMode();
  }

  function canvasTextSprite(title, subtitle, color) {
    const canvas = document.createElement('canvas');
    canvas.width = 768;
    canvas.height = 256;
    const ctx = canvas.getContext('2d');
    ctx.clearRect(0,0,canvas.width,canvas.height);
    ctx.fillStyle = 'rgba(5,8,14,.86)';
    ctx.strokeStyle = `#${new THREE.Color(color).getHexString()}`;
    ctx.lineWidth = 8;
    ctx.beginPath();
    ctx.roundRect(12,12,744,232,36);
    ctx.fill();
    ctx.stroke();
    ctx.fillStyle = '#ffffff';
    ctx.font = '800 54px system-ui, sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText(title,384,112);
    ctx.fillStyle = '#b8c0cf';
    ctx.font = '500 32px system-ui, sans-serif';
    ctx.fillText(subtitle,384,172);
    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    const material = new THREE.SpriteMaterial({ map:texture, transparent:true, depthTest:false });
    const sprite = new THREE.Sprite(material);
    sprite.scale.set(2.65,.88,1);
    sprite.position.set(0,0,.38);
    return sprite;
  }

  let renderer;
  let scene;
  let camera;
  let raycaster;
  let core;
  let ring;

  function initScene() {
    try {
      renderer = new THREE.WebGLRenderer({ antialias:false, alpha:true, powerPreference:'high-performance' });
    } catch (error) {
      root.classList.add('no-webgl');
      setStatus('此瀏覽器無法啟動 WebGL，已切換成 2D 入口。');
      return false;
    }

    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5));
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.setClearColor(0x03050a, 1);
    host.prepend(renderer.domElement);

    scene = new THREE.Scene();
    scene.fog = new THREE.FogExp2(0x03050a,.075);
    camera = new THREE.PerspectiveCamera(45,1,.1,100);
    camera.position.set(0,.35,9.2);
    camera.lookAt(0,0,0);
    raycaster = new THREE.Raycaster();

    scene.add(new THREE.HemisphereLight(0xaabaff,0x08090d,2.2));
    const key = new THREE.DirectionalLight(0xffffff,2.6);
    key.position.set(3,5,6);
    scene.add(key);

    const grid = new THREE.GridHelper(20,24,0x334466,0x152033);
    grid.position.y = -2.25;
    grid.rotation.x = 0;
    scene.add(grid);

    const starGeometry = new THREE.BufferGeometry();
    const stars = [];
    for (let i=0;i<650;i++) stars.push((Math.random()-.5)*26,(Math.random()-.5)*18,(Math.random()-.5)*18-4);
    starGeometry.setAttribute('position',new THREE.Float32BufferAttribute(stars,3));
    scene.add(new THREE.Points(starGeometry,new THREE.PointsMaterial({ color:0x8da2ff,size:.025,transparent:true,opacity:.75 })));

    core = new THREE.Mesh(
      new THREE.IcosahedronGeometry(.7,2),
      new THREE.MeshStandardMaterial({ color:0x8da2ff,emissive:0x4455ff,emissiveIntensity:1.4,metalness:.55,roughness:.18 })
    );
    core.position.set(0,0,-1.35);
    scene.add(core);
    ring = new THREE.Mesh(
      new THREE.TorusGeometry(1.15,.025,12,96),
      new THREE.MeshBasicMaterial({ color:0x5ee3b2,transparent:true,opacity:.75 })
    );
    ring.position.copy(core.position);
    ring.rotation.x = 1.1;
    scene.add(ring);

    portalData.forEach((portal,index) => {
      const group = new THREE.Group();
      group.position.set(...portal.position);
      group.userData.baseY = portal.position[1];
      group.userData.index = index;
      const material = new THREE.MeshStandardMaterial({
        color:portal.color, emissive:portal.color, emissiveIntensity:.28,
        metalness:.5, roughness:.35, transparent:true, opacity:.92
      });
      const box = new THREE.Mesh(new THREE.BoxGeometry(1.82,.82,.32),material);
      box.userData.portalIndex = index;
      const halo = new THREE.Mesh(
        new THREE.BoxGeometry(1.98,.98,.2),
        new THREE.MeshBasicMaterial({ color:portal.color,wireframe:true,transparent:true,opacity:.2 })
      );
      halo.position.z = -.08;
      const sprite = canvasTextSprite(portal.label,portal.sub,portal.color);
      group.add(halo,box,sprite);
      scene.add(group);
      state.portalMeshes.push(box);
      state.portalGroups.push(group);
    });

    renderer.domElement.addEventListener('pointerdown',event => {
      const rect = renderer.domElement.getBoundingClientRect();
      state.pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
      state.pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;
      raycaster.setFromCamera(state.pointer,camera);
      const hit = raycaster.intersectObjects(state.portalMeshes,false)[0];
      if (hit) {
        state.focusIndex = hit.object.userData.portalIndex;
        updateHud();
        scrollToPortal(selectedPortal());
      }
    });

    resize();
    return true;
  }

  function updateCursorPosition() {
    if (!cursor) return;
    cursor.style.left = `${(state.pointer.x + 1) * 50}%`;
    cursor.style.top = `${(1 - state.pointer.y) * 50}%`;
  }

  function activatePointerTarget() {
    if (!raycaster || !camera) return;
    raycaster.setFromCamera(state.pointer,camera);
    const hit = raycaster.intersectObjects(state.portalMeshes,false)[0];
    if (!hit) {
      setStatus('游標目前沒有對準入口。');
      return;
    }
    state.focusIndex = hit.object.userData.portalIndex;
    updateHud();
    scrollToPortal(selectedPortal());
  }

  function resize() {
    if (!renderer || !camera) return;
    const width = Math.max(host.clientWidth,1);
    const height = Math.max(host.clientHeight,1);
    renderer.setSize(width,height,false);
    camera.aspect = width/height;
    camera.updateProjectionMatrix();
  }

  function animate(time) {
    requestAnimationFrame(animate);
    if (!renderer || !scene || !camera) return;
    const seconds = time * .001;
    core.rotation.x = seconds * .22;
    core.rotation.y = seconds * .35;
    ring.rotation.z = seconds * .2;
    ring.rotation.y = seconds * .16;
    state.portalGroups.forEach((group,index) => {
      const selected = index === state.focusIndex;
      const scale = selected ? 1.14 : 1;
      group.scale.lerp(new THREE.Vector3(scale,scale,scale),.12);
      group.position.y = group.userData.baseY + Math.sin(seconds*1.3+index)*.045;
      const mesh = state.portalMeshes[index];
      mesh.material.emissiveIntensity = THREE.MathUtils.lerp(mesh.material.emissiveIntensity,selected?1.15:.28,.12);
      group.children[0].material.opacity = THREE.MathUtils.lerp(group.children[0].material.opacity,selected?.85:.2,.12);
    });
    camera.position.x = THREE.MathUtils.lerp(camera.position.x, portalData[state.focusIndex].position[0]*.08,.05);
    camera.position.y = THREE.MathUtils.lerp(camera.position.y,.35+portalData[state.focusIndex].position[1]*.05,.05);
    camera.lookAt(0,-.1,0);
    renderer.render(scene,camera);
  }

  const keyboardMap = {
    ArrowUp:'UP',ArrowDown:'DOWN',ArrowLeft:'LEFT',ArrowRight:'RIGHT',
    z:'A',Z:'A',x:'B',X:'B',q:'L',Q:'L',e:'R',E:'R',Enter:'START',Shift:'SELECT',Escape:'B'
  };
  addEventListener('keydown',event => {
    const action = keyboardMap[event.key];
    if (!action || /INPUT|TEXTAREA|SELECT/.test(document.activeElement?.tagName || '')) return;
    event.preventDefault();
    if (!event.repeat || ['UP','DOWN','LEFT','RIGHT'].includes(action)) dispatchAction(action);
  });

  controller?.addEventListener('pointerdown',event => {
    const button = event.target.closest('[data-action]');
    if (!button) return;
    event.preventDefault();
    button.classList.add('pressed');
    dispatchAction(button.dataset.action);
  });
  controller?.addEventListener('pointerup',event => event.target.closest('[data-action]')?.classList.remove('pressed'));
  controller?.addEventListener('pointercancel',event => event.target.closest('[data-action]')?.classList.remove('pressed'));

  document.querySelectorAll('[data-console-target]').forEach(button => {
    button.addEventListener('click',() => {
      const portal = portalData.find(item => item.id === button.dataset.consoleTarget);
      if (portal) scrollToPortal(portal);
      toggleMenu(false);
    });
  });
  document.querySelector('[data-console-command="fullscreen"]')?.addEventListener('click',async () => {
    try {
      if (!document.fullscreenElement) await root.requestFullscreen();
      else await document.exitFullscreen();
    } catch { setStatus('瀏覽器拒絕全螢幕要求。'); }
    toggleMenu(false);
  });
  document.querySelector('[data-console-command="reload"]')?.addEventListener('click',() => location.reload());
  returnButton?.addEventListener('click',backToConsole);

  const observer = new IntersectionObserver(entries => {
    const active = entries.some(entry => entry.isIntersecting);
    controller?.classList.toggle('active',active || state.menuOpen);
    returnButton?.classList.toggle('visible',!active);
  },{ threshold:.18 });
  observer.observe(root);

  function pollGamepad(now) {
    const pad = [...(navigator.getGamepads?.() || [])].find(Boolean);
    if (!pad) return;
    const pressed = {
      A:pad.buttons[0]?.pressed,
      B:pad.buttons[1]?.pressed,
      L:pad.buttons[4]?.pressed,
      R:pad.buttons[5]?.pressed,
      SELECT:pad.buttons[8]?.pressed,
      START:pad.buttons[9]?.pressed,
      UP:pad.buttons[12]?.pressed || pad.axes[1] < -.55,
      DOWN:pad.buttons[13]?.pressed || pad.axes[1] > .55,
      LEFT:pad.buttons[14]?.pressed || pad.axes[0] < -.55,
      RIGHT:pad.buttons[15]?.pressed || pad.axes[0] > .55
    };
    Object.entries(pressed).forEach(([action,isPressed]) => {
      const record = state.pressState.get(action) || { down:false,next:0 };
      if (isPressed && !record.down) {
        dispatchAction(action);
        record.down = true;
        record.next = now + 360;
      } else if (isPressed && now >= record.next && ['UP','DOWN','LEFT','RIGHT'].includes(action)) {
        dispatchAction(action);
        record.next = now + 135;
      } else if (!isPressed) {
        record.down = false;
      }
      state.pressState.set(action,record);
    });
  }

  function inputLoop(now) {
    pollGamepad(now);
    requestAnimationFrame(inputLoop);
  }

  addEventListener('resize',resize,{ passive:true });
  addEventListener('gamepadconnected',event => setStatus(`手把已連線：${event.gamepad.id}`));
  addEventListener('gamepaddisconnected',() => setStatus('手把已中斷，仍可使用觸控按鍵。'));

  updateHud();
  updateCursorPosition();
  const started = initScene();
  if (started) requestAnimationFrame(animate);
  requestAnimationFrame(inputLoop);
  window.AMIN_CONSOLE = { dispatchAction, openSelected, backToConsole, version:'0.7.0' };
})();
