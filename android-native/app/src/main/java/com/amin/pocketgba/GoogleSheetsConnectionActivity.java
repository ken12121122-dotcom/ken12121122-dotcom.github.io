package com.amin.pocketgba;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Test/setup UI for abstract source mapping. Token is never persisted. */
public final class GoogleSheetsConnectionActivity extends Activity {
    static final String EXTRA_SOURCE_ID="source_id";
    private EditText sourceInput, spreadsheetInput, tokenInput;
    private GoogleSheetsConnectionStore store;
    @Override protected void onCreate(Bundle b){super.onCreate(b);store=new GoogleSheetsConnectionStore(this);LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(dp(20),dp(28),dp(20),dp(28));
        TextView title=new TextView(this);title.setText("Google Sheets Connection");title.setTextSize(24);r.addView(title);
        sourceInput=new EditText(this);sourceInput.setHint("source_id");sourceInput.setText(getIntent().getStringExtra(EXTRA_SOURCE_ID));r.addView(sourceInput);
        spreadsheetInput=new EditText(this);spreadsheetInput.setHint("spreadsheet_id");spreadsheetInput.setText(store.spreadsheetId(sourceInput.getText().toString()));r.addView(spreadsheetInput);
        tokenInput=new EditText(this);tokenInput.setHint("OAuth access token（只保留本次 App process）");tokenInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);r.addView(tokenInput);
        TextView note=new TextView(this);note.setText("Node 不保存 spreadsheet_id 或 Credential。source_id 只映射到本機連線設定；token 關閉 App process 後即失效。");r.addView(note);
        Button save=new Button(this);save.setText("套用本次連線");save.setAllCaps(false);save.setOnClickListener(v->save());r.addView(save);setContentView(r);}
    private void save(){try{String source=sourceInput.getText().toString().trim();store.saveSource(source,spreadsheetInput.getText().toString());store.setSessionToken(source,tokenInput.getText().toString());Toast.makeText(this,"Google Sheets source 已可供本次工作階段使用",Toast.LENGTH_SHORT).show();setResult(RESULT_OK);finish();}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
