package com.amin.pocketgba;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

public final class DataViewActivity extends Activity {
    private static final int REQ_FORM=4101,REQ_SHEETS=4102;
    static final String EXTRA_NODE_ID="node_id",EXTRA_FILTER_JSON="filter_json",EXTRA_CONTEXT_JSON="input_context_json";
    private NodeMetadataStore metadataStore;private JSONObject node,filter=new JSONObject(),inputContext=new JSONObject();private DataContract contract;private StorageAdapter adapter;private TextView status,listView;
    @Override protected void onCreate(Bundle b){super.onCreate(b);metadataStore=new NodeMetadataStore(this);resolve();buildUi();refresh();}

    private void resolve(){String nodeId=getIntent().getStringExtra(EXTRA_NODE_ID);node=NodeRegistry.findNode(this,metadataStore,nodeId);if(node==null&&getIntent().getData()!=null)node=NodeRegistry.findByRoute(this,metadataStore,getIntent().getDataString());if(node==null)node=new JSONObject();filter=parse(getIntent().getStringExtra(EXTRA_FILTER_JSON));if(filter.length()==0&&node.optJSONObject("filter")!=null)filter=node.optJSONObject("filter");inputContext=parse(getIntent().getStringExtra(EXTRA_CONTEXT_JSON));if(inputContext.length()==0&&node.optJSONObject("input_context")!=null)inputContext=node.optJSONObject("input_context");String contractId=node.optString("input_contract",getIntent().getStringExtra(SchemaFormActivity.EXTRA_CONTRACT_ID));JSONObject c=new ContractRegistry(this).get(contractId);if(c==null)c=ContractRegistry.transactionV1();contract=new DataContract(c);JSONObject storage=node.optJSONObject("storage");if(storage==null)storage=new JSONObject();String adapterId=storage.optString("adapter","google_sheets"),source=storage.optString("source_id","personal_finance"),table=storage.optString("table","Transactions");if("google_sheets".equals(adapterId))adapter=new GoogleSheetsAdapter(source,table,new GoogleSheetsApiTransport(this));}

    private void buildUi(){ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(22),dp(18),dp(30));scroll.addView(root,new ScrollView.LayoutParams(-1,-2));root.addView(text(node.optString("name",node.optString("title","Data View")),25));status=text("Filter: "+filter,13);root.addView(status);LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);root.addView(actions);Button add=button("新增資料");add.setOnClickListener(v->addRecord());actions.addView(add,new LinearLayout.LayoutParams(0,dp(52),1));Button refresh=button("重新整理");refresh.setOnClickListener(v->refresh());actions.addView(refresh,new LinearLayout.LayoutParams(0,dp(52),1));Button connect=button("Sheets 連線");connect.setOnClickListener(v->connect());root.addView(connect,new LinearLayout.LayoutParams(-1,dp(52)));listView=text("讀取中…",15);listView.setTextIsSelectable(true);root.addView(listView);setContentView(scroll);}
    private void connect(){JSONObject s=node.optJSONObject("storage");String source=s==null?"personal_finance":s.optString("source_id","personal_finance");Intent i=new Intent(this,GoogleSheetsConnectionActivity.class);i.putExtra(GoogleSheetsConnectionActivity.EXTRA_SOURCE_ID,source);startActivityForResult(i,REQ_SHEETS);}
    private void addRecord(){JSONObject context=merge(filter,inputContext);Intent i=new Intent(this,SchemaFormActivity.class);i.putExtra(SchemaFormActivity.EXTRA_CONTRACT_JSON,contract.json().toString());i.putExtra(SchemaFormActivity.EXTRA_CONTEXT_JSON,context.toString());startActivityForResult(i,REQ_FORM);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;if(request==REQ_FORM&&data!=null)append(parse(data.getStringExtra(SchemaFormActivity.EXTRA_RESULT_JSON)));else if(request==REQ_SHEETS)refresh();}
    private void append(JSONObject record){status.setText("正在寫入…");new Thread(()->{try{StorageAdapter.Result r=adapter.append(contract,record);runOnUiThread(()->{Toast.makeText(this,r.message,Toast.LENGTH_SHORT).show();refresh();});}catch(Exception e){runOnUiThread(()->showError(e));}}).start();}
    private void refresh(){if(adapter==null){status.setText("Storage Adapter 未設定");return;}status.setText("Filter: "+filter+" · 讀取中…");new Thread(()->{try{StorageAdapter.Result r=adapter.list(contract,filter);JSONArray records=r.payload.optJSONArray("records");runOnUiThread(()->render(records));}catch(Exception e){runOnUiThread(()->showError(e));}}).start();}
    private void render(JSONArray records){StringBuilder out=new StringBuilder();int count=records==null?0:records.length();status.setText("Filter: "+filter+" · "+count+" records");for(int i=0;i<count;i++){JSONObject r=records.optJSONObject(i);out.append("\n#").append(i+1).append("\n").append(r==null?"":r.toString()).append("\n");}listView.setText(out.length()==0?"目前沒有資料":out.toString());listView.setOnClickListener(v->{if(records!=null&&records.length()>0)single(records.optJSONObject(0));});}
    private void single(JSONObject r){if(r!=null)new android.app.AlertDialog.Builder(this).setTitle("Record").setMessage(r.toString()).setPositiveButton("關閉",null).show();}
    private void showError(Exception e){status.setText("連線未完成");listView.setText(e.getMessage()+"\n\n請先設定 Google Sheets source 與本次 OAuth token，再重新整理。");}
    private static JSONObject merge(JSONObject a,JSONObject b){JSONObject out=new JSONObject();try{for(JSONObject s:new JSONObject[]{a,b}){if(s==null)continue;JSONArray n=s.names();if(n!=null)for(int i=0;i<n.length();i++){String k=n.optString(i);out.put(k,s.opt(k));}}}catch(Exception ignored){}return out;}
    private static JSONObject parse(String raw){try{return raw==null||raw.trim().isEmpty()?new JSONObject():new JSONObject(raw);}catch(Exception e){return new JSONObject();}}
    private TextView text(String s,float z){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setGravity(Gravity.START);t.setPadding(0,dp(8),0,dp(8));return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
