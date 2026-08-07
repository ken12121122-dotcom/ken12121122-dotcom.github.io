package com.amin.pocketgba;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public final class ResourceMappingManagerActivity extends Activity {
    private ResourceMappingStore store;
    private EditText nodeId, nodeTitle, resourceTitle, url;
    private Spinner provider, status;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new ResourceMappingStore(this);
        build();
    }

    private void build() {
        AminTheme.Palette p = AminTheme.palette(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(p.background);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(root);

        Button back = button("← 返回", p); back.setOnClickListener(v -> finish()); root.addView(back, full());
        TextView title = text("外部資源管理", 26, true, p.text); root.addView(title, margin(14, 4));
        TextView note = text("手機綁定先保存為 Local Mapping；不會直接改寫正式 GitHub Registry。需要正式治理時再由 WF-001 / 人工核准處理。", 13, false, p.muted); root.addView(note, margin(0, 12));

        nodeId = field("Node ID", p); nodeTitle = field("節點名稱", p); resourceTitle = field("資源名稱，例如 02 通用資料契約", p); url = field("HTTPS URL（可含 Google Sheets gid）", p);
        root.addView(nodeId, full()); root.addView(nodeTitle, full()); root.addView(resourceTitle, full()); root.addView(url, full());

        provider = spinner(new String[]{"google_sheets","n8n","github","google_calendar","web"});
        status = spinner(new String[]{"candidate","active"});
        root.addView(label("Provider", p)); root.addView(provider, full()); root.addView(label("Status", p)); root.addView(status, full());

        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button save = button("儲存 Mapping", p); save.setOnClickListener(v -> save()); actions.addView(save, new LinearLayout.LayoutParams(0, dp(54), 1));
        Button remove = button("解除", p); remove.setOnClickListener(v -> remove()); actions.addView(remove, new LinearLayout.LayoutParams(0, dp(54), 1));
        root.addView(actions, margin(12, 0));

        Button test = button("測試開啟連結", p); test.setOnClickListener(v -> {
            String value = url.getText().toString().trim();
            if (!ExternalResourcePolicy.isAllowedHttps(value)) { Toast.makeText(this,"請輸入有效 HTTPS URL",Toast.LENGTH_SHORT).show(); return; }
            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(value)));
        }); root.addView(test, margin(8,0));

        String id = getIntent().getStringExtra("node_id");
        String name = getIntent().getStringExtra("node_title");
        if (id != null) nodeId.setText(id);
        if (name != null) nodeTitle.setText(name);
        loadExisting(id);
        setContentView(scroll);
    }

    private void loadExisting(String id) {
        if (id == null) return;
        JSONObject o = store.get(id);
        if (o == null) return;
        nodeTitle.setText(o.optString("nodeTitle", nodeTitle.getText().toString()));
        resourceTitle.setText(o.optString("title", ""));
        url.setText(o.optString("url", ""));
        select(provider, o.optString("provider", "web"));
        select(status, o.optString("status", "candidate"));
    }

    private void save() {
        try {
            store.save(nodeId.getText().toString(), nodeTitle.getText().toString(), String.valueOf(provider.getSelectedItem()), resourceTitle.getText().toString(), url.getText().toString(), String.valueOf(status.getSelectedItem()));
            Toast.makeText(this, "Mapping 已儲存", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
        } catch (Exception e) { Toast.makeText(this, "儲存失敗：" + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void remove() {
        store.delete(nodeId.getText().toString());
        Toast.makeText(this, "Mapping 已解除", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private EditText field(String hint, AminTheme.Palette p) { EditText e = new EditText(this); e.setHint(hint); e.setSingleLine(true); e.setTextColor(p.text); e.setHintTextColor(p.muted); e.setPadding(dp(8),dp(8),dp(8),dp(8)); return e; }
    private Spinner spinner(String[] items) { Spinner s = new Spinner(this); s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items)); return s; }
    private void select(Spinner spinner, String value) { for(int i=0;i<spinner.getCount();i++) if(value.equals(String.valueOf(spinner.getItemAtPosition(i)))) { spinner.setSelection(i); return; } }
    private TextView label(String s, AminTheme.Palette p){ TextView t=text(s,13,true,p.text); t.setPadding(0,dp(8),0,0); return t; }
    private TextView text(String s,float z,boolean bold,int c){ TextView t=new TextView(this); t.setText(s); t.setTextSize(z); t.setTextColor(c); if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); return t; }
    private Button button(String s,AminTheme.Palette p){ Button b=new Button(this); b.setAllCaps(false); b.setText(s); b.setTextColor(p.primary); return b; }
    private LinearLayout.LayoutParams full(){ return new LinearLayout.LayoutParams(-1,-2); }
    private LinearLayout.LayoutParams margin(int top,int bottom){ LinearLayout.LayoutParams lp=full(); lp.topMargin=dp(top); lp.bottomMargin=dp(bottom); return lp; }
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
}
