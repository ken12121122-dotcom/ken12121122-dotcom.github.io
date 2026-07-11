(() => {
  const cfg = window.VAULT_CONFIG;
  const $ = id => document.getElementById(id);
  const SNAPSHOT_KEY = 'amin_vault_snapshot_v06';
  const state = { objects: [] };
  const badge = $('connectionBadge');
  const footer = $('footerStatus');

  if (!cfg || !window.supabase) {
    badge.textContent = 'Config error';
    footer.textContent = 'configuration unavailable';
    return;
  }

  const db = window.supabase.createClient(cfg.url, cfg.publishableKey);
  const demoClients = [
    {name:'ChatGPT Connected App',client_type:'chatgpt',platform:'cloud',status:'active'},
    {name:'Google Drive Mirror',client_type:'google_drive_mirror',platform:'cloud',status:'active'},
    {name:'Obsidian Android',client_type:'obsidian_android',platform:'android',status:'active'},
    {name:'Obsidian Windows',client_type:'obsidian_windows',platform:'windows',status:'offline'},
    {name:'Codex',client_type:'codex',platform:'windows',status:'offline'},
    {name:'Amin Vault PWA',client_type:'pwa',platform:'web',status:'active'},
    {name:'Amin Vault GitHub Pages',client_type:'pwa',platform:'web',status:'active'}
  ];
  const demoTasks = [
    {title:'完成 Android 自動同步',content:{priority:'P0',next_action:'建立 Android Drive 同步層'}},
    {title:'建立 Vault MCP',content:{priority:'P1',next_action:'完成安全工具介面'}},
    {title:'建立三方衝突合併',content:{priority:'P1',next_action:'加入 diff 與 merge UI'}}
  ];

  const esc = value => String(value ?? '')
    .replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;')
    .replaceAll('"','&quot;').replaceAll("'",'&#039;');
  const textOf = value => value == null ? '' : typeof value === 'string' ? value :
    typeof value?.text === 'string' ? value.text : JSON.stringify(value,null,2);
  const message = (el,text,kind='') => { el.textContent=text; el.className=`message ${kind}`.trim(); };
  const busy = (button,on) => {
    button.disabled=on;
    button.dataset.label ||= button.textContent;
    button.textContent=on ? '處理中…' : button.dataset.label;
  };

  function renderClients(items) {
    $('clientList').innerHTML = items.map(item => `
      <div class="row"><div><div class="row-title">${esc(item.name)}</div>
      <div class="row-sub">${esc(item.client_type)} · ${esc(item.platform)}</div></div>
      <span class="status-dot ${item.status==='active'?'active':''}"></span></div>`).join('');
  }

  function renderTasks(items) {
    $('taskList').innerHTML = items.map(item => `
      <div class="row"><div><div class="row-title">${esc(item.title)}</div>
      <div class="row-sub">${esc(item.content?.priority)} · ${esc(item.content?.next_action)}</div></div></div>`).join('');
  }

  function renderChanges(items) {
    if (!items.length) {
      $('changeRequestList').innerHTML = '<div class="row"><div><div class="row-title">目前沒有待審核變更</div><div class="row-sub">新建或修改提案會出現在這裡。</div></div></div>';
      return;
    }
    $('changeRequestList').innerHTML = items.map(item => {
      const patch=item.proposed_patch||{};
      const operation=item.operation||'create';
      const title=patch.title||item.target_title||'未命名';
      const excerpt=textOf(patch.content).slice(0,180);
      const version=patch.base_version ? ` · base v${patch.base_version}` : '';
      return `<article class="review-card">
        <div class="review-meta">${esc(operation)}${esc(version)} · ${esc(item.risk_level||'low')}</div>
        <div class="row-title">${esc(title)}</div>
        <p class="review-excerpt">${esc(excerpt||'沒有內容')}</p>
        <div class="button-row"><button data-review="approve" data-id="${esc(item.id)}">核准發布</button>
        <button class="danger" data-review="reject" data-id="${esc(item.id)}">駁回</button></div>
      </article>`;
    }).join('');
  }

  function renderObjects() {
    const q=$('objectSearch').value.trim().toLowerCase();
    const type=$('objectTypeFilter').value;
    const items=state.objects.filter(item => {
      const typeOk=type==='all'||item.object_type===type;
      const queryOk=!q||`${item.title} ${textOf(item.content)}`.toLowerCase().includes(q);
      return typeOk&&queryOk;
    });
    if (!items.length) {
      $('objectList').innerHTML='<div class="row"><div><div class="row-title">沒有符合條件的正式物件</div><div class="row-sub">調整搜尋字或類型。</div></div></div>';
      return;
    }
    $('objectList').innerHTML=items.map(item => `
      <article class="object-card">
        <div class="object-head"><div><div class="review-meta">${esc(item.object_type)} · v${esc(item.canonical_version)}</div>
        <div class="row-title">${esc(item.title)}</div></div><span class="badge">${esc(item.sensitivity)}</span></div>
        <p class="review-excerpt">${esc(textOf(item.content).slice(0,220)||'沒有內容')}</p>
        <div class="object-actions">
          <button class="secondary" data-copy="${esc(item.id)}">複製 Markdown</button>
          <button data-edit="${esc(item.id)}">提出修改</button>
        </div>
      </article>`).join('');
  }

  function markdownOf(item) {
    return `---\nvault_id: "${item.id}"\nobject_type: "${item.object_type}"\nstatus: "${item.status}"\nsensitivity: "${item.sensitivity}"\ncanonical_version: ${item.canonical_version}\nupdated_at: "${item.updated_at}"\n---\n\n# ${item.title}\n\n${textOf(item.content)}\n`;
  }

  async function copyMarkdown(id) {
    const item=state.objects.find(x=>x.id===id);
    if (!item) return;
    const md=markdownOf(item);
    try { await navigator.clipboard.writeText(md); message($('explorerMessage'),`已複製「${item.title}」。`,'success'); }
    catch { window.prompt('複製以下 Markdown：',md); }
  }

  async function proposeEdit(id) {
    const item=state.objects.find(x=>x.id===id);
    if (!item) return;
    const title=window.prompt(`修改標題，基準版本 v${item.canonical_version}：`,item.title);
    if (title===null) return;
    const content=window.prompt('修改內容：',textOf(item.content));
    if (content===null) return;
    message($('explorerMessage'),'正在建立版本化修改提案…');
    try {
      const {data,error}=await db.rpc('create_update_change_request',{
        target_object_id:item.id,
        expected_version:item.canonical_version,
        proposed_title:title.trim()||item.title,
        proposed_content:{text:content,source:'pwa',captured_at:new Date().toISOString()},
        proposed_sensitivity:item.sensitivity
      });
      if (error) throw error;
      message($('explorerMessage'),`修改提案已建立：${data}`,'success');
      await loadLive();
    } catch (error) { message($('explorerMessage'),error.message||'修改提案建立失敗。','error'); }
  }

  function saveSnapshot(payload) {
    try { localStorage.setItem(SNAPSHOT_KEY,JSON.stringify({saved_at:new Date().toISOString(),...payload})); }
    catch { message($('explorerMessage'),'已載入，但瀏覽器拒絕保存離線快照。','error'); }
  }

  function loadSnapshot() {
    try {
      const snap=JSON.parse(localStorage.getItem(SNAPSHOT_KEY)||'null');
      if (!snap) return false;
      state.objects=snap.objects||[];
      renderObjects(); renderClients(snap.clients?.length?snap.clients:demoClients);
      renderTasks(snap.tasks?.length?snap.tasks:demoTasks); renderChanges(snap.changes||[]);
      $('objectCount').textContent=snap.progress?.object_count??state.objects.length;
      $('clientCount').textContent=snap.clients?.length??0;
      $('versionCount').textContent=snap.progress?.version_count??0;
      $('pendingCount').textContent=snap.progress?.pending_change_count??0;
      $('offlineBadge').textContent='Cached'; footer.textContent=`offline snapshot · ${snap.saved_at}`;
      return true;
    } catch { return false; }
  }

  async function refreshSession() {
    const {data:{session}}=await db.auth.getSession();
    const logged=Boolean(session?.user);
    ['sessionPanel','composePanel','reviewPanel','explorerPanel'].forEach(id=>$(id).classList.toggle('hidden',!logged));
    $('authPanel').classList.toggle('hidden',logged); $('logoutButton').classList.toggle('hidden',!logged);
    if (logged) {
      $('sessionEmail').textContent=session.user.email||session.user.id;
      badge.textContent='Signed in'; badge.className='badge active'; await loadLive();
    } else {
      badge.textContent='Demo'; badge.className='badge'; footer.textContent='demo snapshot';
      renderClients(demoClients); renderTasks(demoTasks); renderChanges([]);
    }
  }

  async function signup() {
    const button=$('signupButton'); busy(button,true);
    try {
      const email=$('emailInput').value.trim(),password=$('passwordInput').value;
      if (!email||password.length<6) throw new Error('請輸入有效信箱，密碼至少 6 碼。');
      const {data,error}=await db.auth.signUp({email,password,options:{emailRedirectTo:location.origin+location.pathname}});
      if (error) throw error;
      message($('authMessage'),data.session?'帳號已建立並登入。':'帳號已建立，請到信箱驗證。','success');
      if (data.session) await refreshSession();
    } catch(error) { message($('authMessage'),error.message||'建立帳號失敗。','error'); }
    finally { busy(button,false); }
  }

  async function login() {
    const button=$('loginButton'); busy(button,true);
    try {
      const {error}=await db.auth.signInWithPassword({email:$('emailInput').value.trim(),password:$('passwordInput').value});
      if (error) throw error; await refreshSession();
    } catch(error) { message($('authMessage'),error.message||'登入失敗。','error'); }
    finally { busy(button,false); }
  }

  async function claim() {
    const button=$('claimButton'); busy(button,true);
    try {
      const {data,error}=await db.rpc('claim_bootstrap_workspace',{target_slug:cfg.workspaceSlug});
      if (error) throw error;
      const result=Array.isArray(data)?data[0]:data;
      message($('claimMessage'),result?.claimed_now?'Workspace 已正式綁定。':'Workspace 已經屬於你。','success');
      await loadLive();
    } catch(error) { message($('claimMessage'),error.message||'認領失敗。','error'); }
    finally { busy(button,false); }
  }

  async function createDraft() {
    const button=$('createDraftButton'); busy(button,true);
    try {
      const title=$('draftTitle').value.trim(),body=$('draftContent').value.trim();
      if (!title) throw new Error('請先輸入標題。');
      const {data,error}=await db.rpc('create_object_change_request',{
        target_workspace_slug:cfg.workspaceSlug,target_object_type:$('draftType').value,target_title:title,
        target_content:{text:body,source:'pwa',captured_at:new Date().toISOString()}
      });
      if (error) throw error;
      $('draftTitle').value=''; $('draftContent').value='';
      message($('draftMessage'),`已建立 Change Request：${data}`,'success'); await loadLive();
    } catch(error) { message($('draftMessage'),error.message||'草稿建立失敗。','error'); }
    finally { busy(button,false); }
  }

  async function review(id,decision) {
    const note=window.prompt(decision==='approve'?'核准備註，可留空：':'駁回原因，可留空：','');
    if (note===null) return;
    message($('reviewMessage'),decision==='approve'?'正在發布…':'正在駁回…');
    try {
      const {data,error}=await db.rpc('review_change_request',{target_request_id:id,review_decision:decision,review_note_text:note||null});
      if (error) throw error;
      const result=Array.isArray(data)?data[0]:data;
      message($('reviewMessage'),decision==='approve'?`已發布：${result?.created_object_id||'完成'}`:'已駁回。','success');
      await loadLive();
    } catch(error) {
      const text=String(error.message||'審核失敗。');
      message($('reviewMessage'),text.includes('version_conflict')?`版本衝突，請重新載入後再提出修改。${text}`:text,'error');
    }
  }

  async function loadLive() {
    try {
      const [p,c,t,r,o]=await Promise.all([
        db.from('vault_workspace_progress').select('*').eq('slug',cfg.workspaceSlug).maybeSingle(),
        db.from('vault_clients').select('name,platform,status,client_type').order('client_type'),
        db.from('vault_objects').select('title,content,status').eq('object_type','task').order('title'),
        db.from('vault_change_requests').select('id,operation,object_id,proposed_patch,risk_level,status,created_at').eq('status','pending').order('created_at',{ascending:false}),
        db.from('vault_objects').select('id,object_type,title,content,status,sensitivity,canonical_version,updated_at').eq('status','active').order('updated_at',{ascending:false})
      ]);
      for (const result of [p,c,t,r,o]) if (result.error) throw result.error;
      state.objects=o.data||[]; renderObjects(); renderClients(c.data?.length?c.data:demoClients);
      renderTasks(t.data?.length?t.data:demoTasks); renderChanges(r.data||[]);
      $('objectCount').textContent=p.data?.object_count??state.objects.length;
      $('clientCount').textContent=c.data?.length??0; $('versionCount').textContent=p.data?.version_count??0;
      $('pendingCount').textContent=p.data?.pending_change_count??0;
      saveSnapshot({progress:p.data,clients:c.data||[],tasks:t.data||[],changes:r.data||[],objects:state.objects});
      badge.textContent='Live'; badge.className='badge active'; $('offlineBadge').textContent='Live';
      $('offlineBadge').className='badge active'; footer.textContent='live vault connected';
      message($('explorerMessage'),`已載入 ${state.objects.length} 個正式 Objects。`,'success');
    } catch(error) {
      if (!loadSnapshot()) { footer.textContent='signed in · claim workspace'; state.objects=[]; renderObjects(); }
    }
  }

  $('signupButton').onclick=signup; $('loginButton').onclick=login;
  $('logoutButton').onclick=async()=>{await db.auth.signOut();await refreshSession();};
  $('claimButton').onclick=claim; $('createDraftButton').onclick=createDraft;
  $('refreshButton').onclick=loadLive; $('refreshReviewButton').onclick=loadLive;
  $('objectSearch').oninput=renderObjects; $('objectTypeFilter').onchange=renderObjects;
  $('changeRequestList').addEventListener('click',event=>{
    const button=event.target.closest('button[data-review]'); if(button) review(button.dataset.id,button.dataset.review);
  });
  $('objectList').addEventListener('click',event=>{
    const copy=event.target.closest('button[data-copy]'); if(copy) copyMarkdown(copy.dataset.copy);
    const edit=event.target.closest('button[data-edit]'); if(edit) proposeEdit(edit.dataset.edit);
  });
  addEventListener('online',()=>loadLive()); addEventListener('offline',()=>loadSnapshot());
  db.auth.onAuthStateChange(()=>refreshSession()); refreshSession();
  if ('serviceWorker' in navigator) addEventListener('load',()=>navigator.serviceWorker.register('./sw.js',{scope:'./'}));
})();