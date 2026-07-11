(() => {
  const cfg = window.VAULT_CONFIG;
  const badge = document.getElementById("connectionBadge");
  const footer = document.getElementById("footerStatus");

  if (!cfg || !window.supabase) {
    badge.textContent = "Config error";
    footer.textContent = "configuration unavailable";
    return;
  }

  const client = window.supabase.createClient(cfg.url, cfg.publishableKey);
  const $ = id => document.getElementById(id);

  const demoClients = [
    {name:"ChatGPT Connected App",client_type:"chatgpt",platform:"cloud",status:"active"},
    {name:"Google Drive Mirror",client_type:"google_drive_mirror",platform:"cloud",status:"active"},
    {name:"Obsidian Android",client_type:"obsidian_android",platform:"android",status:"active"},
    {name:"Obsidian Windows",client_type:"obsidian_windows",platform:"windows",status:"offline"},
    {name:"Codex",client_type:"codex",platform:"windows",status:"offline"},
    {name:"Amin Vault PWA",client_type:"pwa",platform:"web",status:"active"},
    {name:"Amin Vault GitHub Pages",client_type:"pwa",platform:"web",status:"active"}
  ];

  const demoTasks = [
    {title:"完成 Android 自動同步",content:{priority:"P0",next_action:"建立 Android Drive 同步層"}},
    {title:"建立 Vault MCP",content:{priority:"P1",next_action:"Auth 與治理閉環完成後接入"}},
    {title:"建立跨裝置衝突合併",content:{priority:"P1",next_action:"加入版本差異與合併介面"}}
  ];

  const esc = value => String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");

  const setMessage = (element, text, kind = "") => {
    element.textContent = text;
    element.className = `message ${kind}`.trim();
  };

  const setBusy = (button, busy) => {
    button.disabled = busy;
    button.dataset.originalText ||= button.textContent;
    button.textContent = busy ? "處理中…" : button.dataset.originalText;
  };

  function renderClients(items) {
    $("clientList").innerHTML = items.map(item => `
      <div class="row">
        <div>
          <div class="row-title">${esc(item.name)}</div>
          <div class="row-sub">${esc(item.client_type)} · ${esc(item.platform)}</div>
        </div>
        <span class="status-dot ${item.status === "active" ? "active" : ""}"></span>
      </div>
    `).join("");
  }

  function renderTasks(items) {
    $("taskList").innerHTML = items.map(item => `
      <div class="row">
        <div>
          <div class="row-title">${esc(item.title)}</div>
          <div class="row-sub">${esc(item.content?.priority)} · ${esc(item.content?.next_action)}</div>
        </div>
      </div>
    `).join("");
  }

  function renderChangeRequests(items) {
    if (!items.length) {
      $("changeRequestList").innerHTML = `
        <div class="row">
          <div>
            <div class="row-title">目前沒有待審核變更</div>
            <div class="row-sub">從上方建立一筆草稿，就會出現在這裡。</div>
          </div>
        </div>
      `;
      return;
    }

    $("changeRequestList").innerHTML = items.map(item => {
      const patch = item.proposed_patch || {};
      const content = patch.content?.text ?? JSON.stringify(patch.content ?? {});
      const excerpt = String(content).slice(0, 180);
      return `
        <article class="review-card">
          <div class="review-meta">${esc(patch.object_type || "unknown")} · ${esc(item.risk_level || "low")}</div>
          <div class="row-title">${esc(patch.title || "未命名")}</div>
          <p class="review-excerpt">${esc(excerpt || "沒有內容")}</p>
          <div class="button-row">
            <button data-review-action="approve" data-request-id="${esc(item.id)}">核准發布</button>
            <button class="danger" data-review-action="reject" data-request-id="${esc(item.id)}">駁回</button>
          </div>
        </article>
      `;
    }).join("");
  }

  async function refreshSession() {
    const {data: {session}} = await client.auth.getSession();
    const loggedIn = Boolean(session?.user);

    $("authPanel").classList.toggle("hidden", loggedIn);
    $("sessionPanel").classList.toggle("hidden", !loggedIn);
    $("composePanel").classList.toggle("hidden", !loggedIn);
    $("reviewPanel").classList.toggle("hidden", !loggedIn);
    $("logoutButton").classList.toggle("hidden", !loggedIn);

    if (loggedIn) {
      $("sessionEmail").textContent = session.user.email || session.user.id;
      badge.textContent = "Signed in";
      badge.className = "badge active";
      await loadLive();
    } else {
      badge.textContent = "Demo";
      badge.className = "badge";
      footer.textContent = "demo snapshot";
      renderClients(demoClients);
      renderTasks(demoTasks);
      renderChangeRequests([]);
    }
  }

  async function signup() {
    const button = $("signupButton");
    setBusy(button, true);
    try {
      const email = $("emailInput").value.trim();
      const password = $("passwordInput").value;
      if (!email || password.length < 6) throw new Error("請輸入有效信箱，密碼至少 6 碼。");

      const {data, error} = await client.auth.signUp({
        email,
        password,
        options: {emailRedirectTo: location.origin + location.pathname}
      });
      if (error) throw error;

      if (data.session) {
        setMessage($("authMessage"), "帳號已建立並登入。", "success");
        await refreshSession();
      } else {
        setMessage($("authMessage"), "帳號已建立，請先到信箱驗證。", "success");
      }
    } catch (error) {
      setMessage($("authMessage"), error.message || "建立帳號失敗。", "error");
    } finally {
      setBusy(button, false);
    }
  }

  async function login() {
    const button = $("loginButton");
    setBusy(button, true);
    try {
      const {error} = await client.auth.signInWithPassword({
        email: $("emailInput").value.trim(),
        password: $("passwordInput").value
      });
      if (error) throw error;
      await refreshSession();
    } catch (error) {
      setMessage($("authMessage"), error.message || "登入失敗。", "error");
    } finally {
      setBusy(button, false);
    }
  }

  async function claimWorkspace() {
    const button = $("claimButton");
    setBusy(button, true);
    try {
      const {data, error} = await client.rpc("claim_bootstrap_workspace", {
        target_slug: cfg.workspaceSlug
      });
      if (error) throw error;

      const result = Array.isArray(data) ? data[0] : data;
      setMessage(
        $("claimMessage"),
        result?.claimed_now ? "Workspace 已正式綁定到你的帳號。" : "這個 Workspace 已經屬於你。",
        "success"
      );
      await loadLive();
    } catch (error) {
      setMessage($("claimMessage"), error.message || "認領失敗。", "error");
    } finally {
      setBusy(button, false);
    }
  }

  async function createDraft() {
    const button = $("createDraftButton");
    setBusy(button, true);
    try {
      const title = $("draftTitle").value.trim();
      const body = $("draftContent").value.trim();
      const type = $("draftType").value;
      if (!title) throw new Error("請先輸入標題。");

      const {data, error} = await client.rpc("create_object_change_request", {
        target_workspace_slug: cfg.workspaceSlug,
        target_object_type: type,
        target_title: title,
        target_content: {
          text: body,
          source: "pwa",
          captured_at: new Date().toISOString()
        }
      });
      if (error) throw error;

      $("draftTitle").value = "";
      $("draftContent").value = "";
      setMessage($("draftMessage"), `已建立 Change Request：${data}`, "success");
      await loadLive();
    } catch (error) {
      setMessage($("draftMessage"), error.message || "草稿建立失敗。", "error");
    } finally {
      setBusy(button, false);
    }
  }

  async function reviewChangeRequest(requestId, decision) {
    const note = window.prompt(
      decision === "approve" ? "核准備註，可留空：" : "駁回原因，可留空：",
      ""
    );
    if (note === null) return;

    setMessage($("reviewMessage"), decision === "approve" ? "正在發布…" : "正在駁回…");

    try {
      const {data, error} = await client.rpc("review_change_request", {
        target_request_id: requestId,
        review_decision: decision,
        review_note_text: note || null
      });
      if (error) throw error;

      const result = Array.isArray(data) ? data[0] : data;
      const message = decision === "approve"
        ? `已發布為正式 Object：${result?.created_object_id || "完成"}`
        : "Change Request 已駁回。";
      setMessage($("reviewMessage"), message, "success");
      await loadLive();
    } catch (error) {
      setMessage($("reviewMessage"), error.message || "審核失敗。", "error");
    }
  }

  async function loadLive() {
    try {
      const [progressResult, clientResult, taskResult, changeResult] = await Promise.all([
        client.from("vault_workspace_progress").select("*").eq("slug", cfg.workspaceSlug).maybeSingle(),
        client.from("vault_clients").select("name,platform,status,client_type").order("client_type"),
        client.from("vault_objects").select("title,content,status").eq("object_type", "task").order("title"),
        client.from("vault_change_requests")
          .select("id,proposed_patch,risk_level,status,created_at")
          .eq("status", "pending")
          .order("created_at", {ascending: false})
      ]);

      if (progressResult.error) throw progressResult.error;
      if (clientResult.error) throw clientResult.error;
      if (taskResult.error) throw taskResult.error;
      if (changeResult.error) throw changeResult.error;

      if (progressResult.data) {
        $("objectCount").textContent = progressResult.data.object_count ?? 0;
        $("clientCount").textContent = clientResult.data?.length ?? 0;
        $("versionCount").textContent = progressResult.data.version_count ?? 0;
        $("pendingCount").textContent = progressResult.data.pending_change_count ?? 0;
        badge.textContent = "Live";
        badge.className = "badge active";
        footer.textContent = "live vault connected";
      }

      renderClients(clientResult.data?.length ? clientResult.data : demoClients);
      renderTasks(taskResult.data?.length ? taskResult.data : demoTasks);
      renderChangeRequests(changeResult.data || []);
    } catch (error) {
      footer.textContent = "signed in · claim workspace";
      renderClients(demoClients);
      renderTasks(demoTasks);
      renderChangeRequests([]);
    }
  }

  $("signupButton").onclick = signup;
  $("loginButton").onclick = login;
  $("logoutButton").onclick = async () => {
    await client.auth.signOut();
    await refreshSession();
  };
  $("claimButton").onclick = claimWorkspace;
  $("createDraftButton").onclick = createDraft;
  $("refreshButton").onclick = loadLive;
  $("refreshReviewButton").onclick = loadLive;

  $("changeRequestList").addEventListener("click", event => {
    const button = event.target.closest("button[data-review-action]");
    if (!button) return;
    reviewChangeRequest(button.dataset.requestId, button.dataset.reviewAction);
  });

  client.auth.onAuthStateChange(() => refreshSession());
  refreshSession();

  if ("serviceWorker" in navigator) {
    addEventListener("load", () => navigator.serviceWorker.register("./sw.js", {scope: "./"}));
  }
})();