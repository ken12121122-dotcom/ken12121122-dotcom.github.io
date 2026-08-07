package com.amin.pocketgba;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;

/** Native schema-driven form. Returns a contract-valid JSON record in RESULT_OK. */
public final class SchemaFormActivity extends Activity {
    static final String EXTRA_CONTRACT_ID = "contract_id";
    static final String EXTRA_CONTRACT_JSON = "contract_json";
    static final String EXTRA_CONTEXT_JSON = "context_json";
    static final String EXTRA_RECORD_JSON = "record_json";
    static final String EXTRA_RESULT_JSON = "result_json";

    private DataContract contract;
    private JSONObject context = new JSONObject();
    private JSONObject initial = new JSONObject();
    private final Map<String, View> inputs = new LinkedHashMap<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            String raw = getIntent().getStringExtra(EXTRA_CONTRACT_JSON);
            if (raw == null || raw.trim().isEmpty()) {
                JSONObject found = new ContractRegistry(this).get(getIntent().getStringExtra(EXTRA_CONTRACT_ID));
                if (found == null) throw new IllegalArgumentException("找不到 Data Contract");
                raw = found.toString();
            }
            contract = new DataContract(raw);
            DataContract.Validation schema = contract.validateSchema();
            if (!schema.valid) throw new IllegalArgumentException("Contract 無效: " + schema.errors);
            context = parse(getIntent().getStringExtra(EXTRA_CONTEXT_JSON));
            initial = contract.applyContext(context, parse(getIntent().getStringExtra(EXTRA_RECORD_JSON)));
            buildUi();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = text("資料輸入 · " + contract.id(), 24f);
        root.addView(title);
        JSONArray fields = contract.fields();
        for (int i = 0; i < fields.length(); i++) {
            JSONObject f = fields.optJSONObject(i); if (f == null) continue;
            String key = f.optString("key");
            String label = f.optString("label", key) + (f.optBoolean("required", false) ? " *" : "");
            TextView l = text(label, 14f); LinearLayout.LayoutParams lp = wrap(); lp.topMargin = dp(16); root.addView(l, lp);
            View input = createInput(f, initial.opt(key));
            inputs.put(key, input);
            root.addView(input, new LinearLayout.LayoutParams(-1, dp("boolean".equals(f.optString("type")) ? 48 : 52)));
        }

        Button submit = new Button(this); submit.setText("建立 Record"); submit.setAllCaps(false);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(56)); bp.topMargin = dp(24); root.addView(submit, bp);
        submit.setOnClickListener(v -> submit());
        setContentView(scroll);
    }

    private View createInput(JSONObject f, Object value) {
        String type = f.optString("type");
        if ("boolean".equals(type)) {
            CheckBox c = new CheckBox(this); c.setChecked(value instanceof Boolean && (Boolean)value); return c;
        }
        if ("select".equals(type)) {
            Spinner s = new Spinner(this);
            JSONArray opts = f.optJSONArray("options");
            String[] values = new String[opts == null ? 0 : opts.length()];
            int selected = 0;
            for (int i = 0; i < values.length; i++) { values[i] = opts.optString(i); if (values[i].equals(String.valueOf(value))) selected = i; }
            s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
            if (values.length > 0) s.setSelection(selected); return s;
        }
        EditText e = new EditText(this); e.setSingleLine(true);
        if ("number".equals(type)) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        else e.setInputType(InputType.TYPE_CLASS_TEXT);
        if (value != null && value != JSONObject.NULL) e.setText(String.valueOf(value));
        if ("date".equals(type)) {
            e.setFocusable(false); e.setOnClickListener(v -> pickDate(e));
        }
        return e;
    }

    private void pickDate(EditText target) {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (v, y, m, d) -> target.setText(String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m + 1, d)),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void submit() {
        JSONObject record = new JSONObject();
        JSONArray fields = contract.fields();
        try {
            for (int i = 0; i < fields.length(); i++) {
                JSONObject f = fields.optJSONObject(i); if (f == null) continue;
                String key = f.optString("key"), type = f.optString("type"); View v = inputs.get(key);
                if ("boolean".equals(type)) record.put(key, ((CheckBox)v).isChecked());
                else if ("select".equals(type)) record.put(key, ((Spinner)v).getSelectedItem() == null ? "" : String.valueOf(((Spinner)v).getSelectedItem()));
                else {
                    String raw = ((EditText)v).getText().toString().trim();
                    if ("number".equals(type) && !raw.isEmpty()) record.put(key, Double.parseDouble(raw));
                    else record.put(key, raw);
                }
            }
        } catch (Exception e) { Toast.makeText(this, "欄位格式錯誤: " + e.getMessage(), Toast.LENGTH_LONG).show(); return; }
        DataContract.Validation validation = contract.validateRecord(record);
        if (!validation.valid) { Toast.makeText(this, validation.errors.toString(), Toast.LENGTH_LONG).show(); return; }
        Intent result = new Intent(); result.putExtra(EXTRA_RESULT_JSON, record.toString()); setResult(RESULT_OK, result); finish();
    }

    private static JSONObject parse(String raw) { try { return raw == null || raw.trim().isEmpty() ? new JSONObject() : new JSONObject(raw); } catch (Exception e) { return new JSONObject(); } }
    private TextView text(String value, float size) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setGravity(Gravity.START); return t; }
    private LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
