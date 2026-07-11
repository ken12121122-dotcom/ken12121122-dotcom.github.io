(() => {
  const guard = document.createElement('script');
  guard.src = './three-boot.js';
  guard.async = true;
  document.head.appendChild(guard);

  const cfg = window.VAULT_CONFIG;
  if (!cfg || !window.supabase) return;

  const db = window.supabase.createClient(cfg.url, cfg.publishableKey);
  const $ = id => document.getElementById(id);
  const esc = value => String(value ?? '')
    .replaceAll('&','&amp;')
    .replaceAll('<','&lt;')
    .replaceAll('>','&gt;')
    .replaceAll('"','&quot;')
    .replaceAll("'",'&#039;');

  function renderArchitecture(row) {
    if (!row) return;
    const content = row.content || {};
    const layers = Array.isArray(content.layers) ? content.layers : [];

    if ($('architectureTitle')) $('architectureTitle').textContent = row.title || 'Universal Vault 四層架構';
    if ($('architectureSummary')) $('architectureSummary').textContent = row.summary || '';
    if ($('architectureVersion')) $('architectureVersion').textContent = `v${row.version || 1}`;
    if ($('architecturePrinciple')) $('architecturePrinciple').textContent = `核心原則：${content.principle || '資料屬於 Vault，不屬於任何單一 App。'}`;
    if ($('architectureUpdatedAt')) {
      const time = row.updated_at ? new Date(row.updated_at).toLocaleString('zh-TW') : '未知';
      $('architectureUpdatedAt').textContent = `架構來源：Supabase 公開發布層 · 更新時間：${time}`;
    }
    if ($('publicPwaVersion') && content.status?.pwa_version) {
      $('publicPwaVersion').textContent = `Vault v${content.status.pwa_version}`;
    }

    if (layers.length && $('architectureLayerList')) {
      $('architectureLayerList').innerHTML = layers
        .sort((a,b) => Number(a.order || 0) - Number(b.order || 0))
        .map(layer => `
          <div class="row">
            <div>
              <div class="row-title">${esc(layer.order)}. ${esc(layer.name)}</div>
              <div class="row-sub">${esc(layer.description)}</div>
            </div>
            <span class="badge ${layer.order === 1 || layer.order === 4 ? 'active' : ''}">${esc(layer.label)}</span>
          </div>
        `).join('');
    }

    const status = content.status || {};
    if ($('objectCount') && Number.isFinite(Number(status.active_objects))) $('objectCount').textContent = status.active_objects;
    if ($('versionCount') && Number.isFinite(Number(status.versions))) $('versionCount').textContent = status.versions;
  }

  async function loadPublicArchitecture() {
    const {data, error} = await db
      .from('vault_public_content')
      .select('title,summary,content,version,updated_at')
      .eq('slug','universal-vault-architecture')
      .eq('is_published',true)
      .maybeSingle();

    if (error) {
      if ($('architectureUpdatedAt')) $('architectureUpdatedAt').textContent = '公開架構暫時無法連線，目前顯示內建快照。';
      return;
    }
    renderArchitecture(data);
  }

  async function correctPrivateMetrics() {
    const {data:{session}} = await db.auth.getSession();
    if (!session) return;

    const {data, error} = await db
      .from('vault_workspace_progress')
      .select('active_object_count,version_count,pending_change_count')
      .eq('slug',cfg.workspaceSlug)
      .maybeSingle();

    if (error || !data) return;
    if ($('objectCount')) $('objectCount').textContent = data.active_object_count ?? 0;
    if ($('versionCount')) $('versionCount').textContent = data.version_count ?? 0;
    if ($('pendingCount')) $('pendingCount').textContent = data.pending_change_count ?? 0;
  }

  db.auth.onAuthStateChange(() => setTimeout(correctPrivateMetrics, 0));
  loadPublicArchitecture();
  correctPrivateMetrics();
})();
