(() => {
  const cfg = window.VAULT_CONFIG;
  const badge = document.getElementById("connectionBadge");
  const footer = document.getElementById("footerStatus");
  if (!cfg || !window.supabase) { badge.textContent = "Config error"; footer.textContent = "configuration unavailable"; return; }
  const client = window.supabase.createClient(cfg.url, cfg.publishableKey);
  const $ = id => document.getElementById(id);
  const demoClients = [
    {name:"ChatGPT Connected App",client_type:"chatgpt",platform:"cloud",status:"active"},
    {name:"Google Drive Mirror",client_type:"google_drive_mirror",platform:"cloud",status:"active"},
    {name:"Obsidian Android",client_type:"obsidian_android",platform:"android",status:"active"},
    {name:"Obsidian Windows",client_type:"obsidian_windows",platform:"windows",status:"offline"},
    {name:"Codex",client_type:"codex",platform:"windows",status:"offline"},
    {name:"Amin Vault PWA",client_type:"pwa",platform:"web",status:"active"}
  ];
  const demoTasks = [
    {title:"建立正式登入與 Workspace 綁定",content:{priority:"P0",next_action:"登入後認領 Workspace"}},
    {title:"完成 Android 自動同步",content:{priority:"P0",next_action:"建立 Android Drive 同步層"}},
    {title:"建立 Vault MCP",content:{priority:"P1",next_action:"Auth 與 API 完成後接入"}},
    {title:"建立 PWA 管理介面",content:{priority:"P1",next_action:"擴充建立與編輯功能"}}
  ];
  const esc = v => String(v ?? "").replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;").replaceAll("'","&#039;");
  const msg = (el,text,kind="") => { el.textContent=text; el.className=`message ${kind}`.trim(); };
  const busy = (btn,on) => { btn.disabled=on; btn.dataset.t ||= btn.textContent; btn.textContent=on?"處理中…":btn.dataset.t; };
  function renderClients(items) { $("clientList").innerHTML = items.map(x => `<div class="row"><div><div class="row-title">${esc(x.name)}</div><div class="row-sub">${esc(x.client_type)} · ${esc(x.platform)}</div></div><span class="status-dot ${x.status==="active"?"active":""}"></span></div>`).join(""); }
  function renderTasks(items) { $("taskList").innerHTML = items.map(x => `<div class="row"><div><div class="row-title">${esc(x.title)}</div><div class="row-sub">${esc(x.content?.priority)} · ${esc(x.content?.next_action)}</div></div></div>`).join(""); }
  async function refreshSession() {
    const {data:{session}} = await client.auth.getSession();
    const logged = Boolean(session?.user);
    $("authPanel").classList.toggle("hidden", logged);
    $("sessionPanel").classList.toggle("hidden", !logged);
    $("logoutButton").classList.toggle("hidden", !logged);
    if (logged) { $("sessionEmail").textContent = session.user.email || session.user.id; badge.textContent = "Signed in"; badge.className = "badge active"; await loadLive(); }
    else { badge.textContent = "Demo"; badge.className = "badge"; footer.textContent = "demo snapshot"; renderClients(demoClients); renderTasks(demoTasks); }
  }
  async function signup() {
    const btn=$("signupButton"); busy(btn,true);
    try {
      const email=$("emailInput").value.trim(), password=$("passwordInput").value;
      if(!email || password.length<6) throw new Error("請輸入有效信箱，密碼至少 6 碼。");
      const {data,error}=await client.auth.signUp({email,password,options:{emailRedirectTo:location.origin + location.pathname}});
      if(error) throw error;
      if(data.session){msg($("authMessage"),"帳號已建立並登入。","success");await refreshSession();}
      else msg($("authMessage"),"帳號已建立，請先到信箱驗證。","success");
    } catch(e){msg($("authMessage"),e.message||"建立帳號失敗。","error");}
    finally{busy(btn,false);}
  }
  async function login() {
    const btn=$("loginButton"); busy(btn,true);
    try { const {error}=await client.auth.signInWithPassword({email:$("emailInput").value.trim(),password:$("passwordInput").value}); if(error) throw error; await refreshSession(); }
    catch(e){msg($("authMessage"),e.message||"登入失敗。","error");}
    finally{busy(btn,false);}
  }
  async function claim() {
    const btn=$("claimButton"); busy(btn,true);
    try {
      const {data,error}=await client.rpc("claim_bootstrap_workspace",{target_slug:cfg.workspaceSlug});
      if(error) throw error;
      const r=Array.isArray(data)?data[0]:data;
      msg($("claimMessage"),r?.claimed_now?"Workspace 已正式綁定到你的帳號。":"這個 Workspace 已經屬於你。","success");
      await loadLive();
    } catch(e){msg($("claimMessage"),e.message||"認領失敗。","error");}
    finally{busy(btn,false);}
  }
  async function loadLive() {
    try {
      const [p,c,t] = await Promise.all([
        client.from("vault_workspace_progress").select("*").eq("slug",cfg.workspaceSlug).maybeSingle(),
        client.from("vault_clients").select("name,platform,status,client_type").order("client_type"),
        client.from("vault_objects").select("title,content,status").eq("object_type","task").order("title")
      ]);
      if(p.error) throw p.error; if(c.error) throw c.error; if(t.error) throw t.error;
      if(p.data){ $("objectCount").textContent=p.data.object_count??0; $("clientCount").textContent=c.data?.length??0; $("versionCount").textContent=p.data.version_count??0; $("pendingCount").textContent=p.data.pending_change_count??0; badge.textContent="Live"; badge.className="badge active"; footer.textContent="live vault connected"; }
      renderClients(c.data?.length?c.data:demoClients); renderTasks(t.data?.length?t.data:demoTasks);
    } catch(e) { footer.textContent="signed in · claim workspace"; renderClients(demoClients); renderTasks(demoTasks); }
  }
  $("signupButton").onclick=signup;
  $("loginButton").onclick=login;
  $("logoutButton").onclick=async()=>{await client.auth.signOut();await refreshSession();};
  $("claimButton").onclick=claim;
  $("refreshButton").onclick=loadLive;
  client.auth.onAuthStateChange(()=>refreshSession());
  refreshSession();
  if("serviceWorker" in navigator) addEventListener("load",()=>navigator.serviceWorker.register("./sw.js",{scope:"./"}));
})();