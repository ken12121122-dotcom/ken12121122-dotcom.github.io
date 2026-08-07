package com.amin.pocketgba;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Main graph configuration surface. Capability identity is read-only. */
public final class NodeInspectorActivity extends Activity {
    static final String EXTRA_NODE_ID="node_id", EXTRA_ROUTE="route";
    private static final int REQ_FORM=5201, REQ_SHEETS=5202;

    private NodeMetadataStore store;
    private JSONObject node;
    private EditText nameInput,descriptionInput,parentInput,aliasesInput;
    private TextView relationsView;
    private String nodeId;
    private JSONObject pendingRecord;

    @Override protected void onCreate(Bundle state){super.onCreate(state);store=new NodeMetadataStore(this);resolveNode();if(node==null){Toast.makeText(this,"找不到 Node Registry 節點",Toast.LENGTH_LONG).show();finish();return;}buildUi();}

    private void resolveNode(){nodeId=getIntent().getStringExtra(EXTRA_NODE_ID);String route=getIntent().getStringExtra(EXTRA_ROUTE);node=nodeId==null?null:NodeRegistry.findNode(this,store,nodeId);if(node==null&&route!=null)node=NodeRegistry.findByRoute(this,store,route);if(node!=null)nodeId=value(node,"node_id","nodeId");}

    private void buildUi(){
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(20),dp(18),dp(32));scroll.addView(root,new ScrollView.LayoutParams(-1,-2));
        root.addView(label("Node Inspector",25));root.addView(readOnly("Name",node.optString("name",node.optString("title",""))));root.addView(readOnly("Description",node.optString("description","")));root.addView(readOnly("Node ID",nodeId));root.addView(readOnly("Capability ID",value(node,"capability_id","capabilityId")));root.addView(readOnly("Node Type",node.optString("node_type","capability")));root.addView(readOnly("Status / Version",node.optString("status","active")+" / "+node.optString("version","1")));root.addView(readOnly("Actions",String.valueOf(node.optJSONArray("actions"))));root.addView(readOnly("Input Contract",node.optString("input_contract","")));root.addView(readOnly("Output Contract",node.optString("output_contract","")));
        nameInput=edit("Name",node.optString("name",node.optString("title","")));root.addView(nameInput);descriptionInput=edit("Description",node.optString("description",""));root.addView(descriptionInput);parentInput=edit("Parent Node ID",value(node,"parent_id","parentNodeId"));root.addView(parentInput);JSONObject voice=node.optJSONObject("voice");aliasesInput=edit("Voice aliases（逗號分隔）",aliasesText(voice));root.addView(aliasesInput);root.addView(readOnly("Voice Status",voice!=null&&voice.optBoolean("enabled",false)?"enabled":"disabled"));root.addView(readOnly("Storage",String.valueOf(node.optJSONObject("storage"))));
        Button save=button("儲存 Node Metadata");save.setOnClickListener(v->save());root.addView(save);
        relationsView=label("",14);refreshRelations();root.addView(relationsView);
        Button addEdge=button("新增 Edge");addEdge.setOnClickListener(v->edgeDialog(null));root.addView(addEdge);Button manageEdge=button("修改／刪除 Edge");manageEdge.setOnClickListener(v->chooseEdge());root.addView(manageEdge);
        Button md=button("Generate MD Preview");md.setOnClickListener(v->showMarkdown());root.addView(md);
        if(!node.optString("input_contract","").isEmpty()){Button form=button(hasStorage()?"新增資料（Form → Storage）":"Open Generated Form");form.setOnClickListener(v->openForm());root.addView(form);}
        String related=findRelatedDataViewRoute();if(!related.isEmpty()){Button data=button("Open Related Data View");data.setOnClickListener(v->openRoute(related));root.addView(data);}
        String route=node.optString("route","");if(!route.isEmpty()&&!route.startsWith("amin-node://")){Button open=button("Open Node");open.setOnClickListener(v->openRoute(route));root.addView(open);}
        setContentView(scroll);
    }

    private boolean hasStorage(){JSONObject s=node.optJSONObject("storage");return s!=null&&!s.optString("adapter","").isEmpty()&&!s.optString("source_id","").isEmpty()&&!s.optString("table","").isEmpty();}

    private void save(){JSONObject voice=new JSONObject();JSONObject storage=node.optJSONObject("storage");try{voice.put("enabled",node.optJSONObject("voice")!=null&&node.optJSONObject("voice").optBoolean("enabled",false));JSONArray aliases=new JSONArray();for(String a:aliasesInput.getText().toString().split("[,，]"))if(!a.trim().isEmpty())aliases.put(a.trim());voice.put("aliases",aliases);}catch(Exception ignored){}store.saveEditable(nodeId,nameInput.getText().toString(),descriptionInput.getText().toString(),parentInput.getText().toString(),voice,storage);node=NodeRegistry.findNode(this,store,nodeId);refreshRelations();Toast.makeText(this,"已儲存；Capability ID 保持不變",Toast.LENGTH_SHORT).show();}

    private void refreshRelations(){if(relationsView==null)return;StringBuilder text=new StringBuilder("\nRelations\n");int count=0;try{JSONArray edges=new JSONObject(NodeRegistry.typedEdgesJson(this,store)).optJSONArray("edges");if(edges!=null)for(int i=0;i<edges.length();i++){JSONObject e=edges.optJSONObject(i);if(e==null)continue;String source=value(e,"source_node_id","source"),target=value(e,"target_node_id","target");if(!nodeId.equals(source)&&!nodeId.equals(target))continue;text.append(nodeId.equals(source)?"→ ":"← ").append(value(e,"relationship_type","relationshipType")).append(" · ").append(nodeId.equals(source)?target:source).append("\n");count++;}}catch(Exception ignored){}if(count==0)text.append("無\n");relationsView.setText(text.toString());}

    private String findRelatedDataViewRoute(){try{JSONArray edges=new JSONObject(NodeRegistry.typedEdgesJson(this,store)).optJSONArray("edges");if(edges==null)return"";for(int i=0;i<edges.length();i++){JSONObject e=edges.optJSONObject(i);if(e==null)continue;if(!nodeId.equals(value(e,"source_node_id","source")))continue;String type=value(e,"relationship_type","relationshipType");if(!"opens".equals(type))continue;JSONObject target=NodeRegistry.findNode(this,store,value(e,"target_node_id","target"));if(target!=null&&"data_view".equals(target.optString("node_type")))return target.optString("route","");}}catch(Exception ignored){}return"";}

    private void edgeDialog(JSONObject existing){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);int p=dp(12);box.setPadding(p,p,p,p);EditText target=edit("Target Node ID",existing==null?"":value(existing,"target_node_id","target"));box.addView(target);Spinner type=new Spinner(this);List<String> types=new ArrayList<>(Arrays.asList("contains","opens","uses","reads_from","writes_to","executes","depends_on"));type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,types));if(existing!=null){int idx=types.indexOf(value(existing,"relationship_type","relationshipType"));if(idx>=0)type.setSelection(idx);}box.addView(type);new AlertDialog.Builder(this).setTitle(existing==null?"新增 Edge":"修改 Edge").setView(box).setPositiveButton("儲存",(d,w)->{try{String edgeId=existing==null?"edge:local:"+UUID.randomUUID():value(existing,"edge_id","edgeId");JSONObject edge=GraphContract.edge(edgeId,nodeId,target.getText().toString().trim(),String.valueOf(type.getSelectedItem()),"active","1");edge.put("authority","local_candidate");store.addOrReplaceEdge(edge);refreshRelations();}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}).setNegativeButton("取消",null).show();}

    private void chooseEdge(){JSONArray custom=store.customEdges();List<JSONObject> ours=new ArrayList<>();List<String> labels=new ArrayList<>();for(int i=0;i<custom.length();i++){JSONObject e=custom.optJSONObject(i);if(e==null||!nodeId.equals(value(e,"source_node_id","source")))continue;ours.add(e);labels.add(value(e,"relationship_type","relationshipType")+" → "+value(e,"target_node_id","target"));}if(ours.isEmpty()){Toast.makeText(this,"沒有可修改的 Local Edge；系統投影 Edge 為唯讀",Toast.LENGTH_SHORT).show();return;}new AlertDialog.Builder(this).setTitle("選擇 Edge").setItems(labels.toArray(new String[0]),(d,which)->{JSONObject selected=ours.get(which);new AlertDialog.Builder(this).setTitle(labels.get(which)).setItems(new String[]{"修改 relationship type / target","刪除"},(a,action)->{if(action==0)edgeDialog(selected);else{store.removeEdge(value(selected,"edge_id","edgeId"));refreshRelations();}}).show();}).show();}

    private void showMarkdown(){try{JSONArray edges=new JSONObject(NodeRegistry.typedEdgesJson(this,store)).optJSONArray("edges");String md=MarkdownPreviewGenerator.generate(node,edges);TextView preview=label(md,14);preview.setTextIsSelectable(true);ScrollView wrap=new ScrollView(this);wrap.setPadding(dp(16),dp(8),dp(16),dp(8));wrap.addView(preview);new AlertDialog.Builder(this).setTitle("MD Preview").setView(wrap).setPositiveButton("Copy",(d,w)->copy(md)).setNegativeButton("關閉",null).show();}catch(Exception e){Toast.makeText(this,"無法產生 Preview",Toast.LENGTH_SHORT).show();}}

    private void openForm(){Intent i=new Intent(this,SchemaFormActivity.class);i.putExtra(SchemaFormActivity.EXTRA_CONTRACT_ID,node.optString("input_contract"));startActivityForResult(i,REQ_FORM);}

    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;if(request==REQ_FORM&&data!=null){pendingRecord=parse(data.getStringExtra(SchemaFormActivity.EXTRA_RESULT_JSON));if(hasStorage())ensureStorageAndAppend();else{Toast.makeText(this,"Record 已建立；此 Node 未設定 Storage",Toast.LENGTH_LONG).show();pendingRecord=null;}}else if(request==REQ_SHEETS&&pendingRecord!=null)appendPending();}

    private void ensureStorageAndAppend(){JSONObject s=node.optJSONObject("storage");if(s==null){pendingRecord=null;return;}String adapter=s.optString("adapter","");if(!"google_sheets".equals(adapter)){Toast.makeText(this,"目前此 Inspector 只支援 google_sheets 寫入",Toast.LENGTH_LONG).show();return;}String source=s.optString("source_id","");GoogleSheetsConnectionStore connections=new GoogleSheetsConnectionStore(this);if(!connections.isReady(source)){Intent i=new Intent(this,GoogleSheetsConnectionActivity.class);i.putExtra(GoogleSheetsConnectionActivity.EXTRA_SOURCE_ID,source);startActivityForResult(i,REQ_SHEETS);return;}appendPending();}

    private void appendPending(){JSONObject record=pendingRecord;if(record==null)return;pendingRecord=null;JSONObject s=node.optJSONObject("storage");if(s==null)return;String source=s.optString("source_id",""),table=s.optString("table","");DataContract contract=new DataContract(new ContractRegistry(this).get(node.optString("input_contract","")));StorageAdapter adapter=new GoogleSheetsAdapter(source,table,new GoogleSheetsApiTransport(this));Toast.makeText(this,"正在寫入 "+table,Toast.LENGTH_SHORT).show();new Thread(()->{try{StorageAdapter.Result r=adapter.append(contract,record);runOnUiThread(()->new AlertDialog.Builder(this).setTitle(r.success?"寫入完成":"寫入失敗").setMessage(r.message).setPositiveButton("查看 Data View",(d,w)->{String route=findRelatedDataViewRoute();if(!route.isEmpty())openRoute(route);}).setNegativeButton("留在 Inspector",null).show());}catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}}).start();}

    private void openRoute(String route){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(route)).setPackage(getPackageName()));}catch(Exception e){Toast.makeText(this,"Route 無法開啟",Toast.LENGTH_SHORT).show();}}
    private void copy(String text){((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Node Markdown",text));Toast.makeText(this,"已複製",Toast.LENGTH_SHORT).show();}
    private static JSONObject parse(String raw){try{return raw==null||raw.trim().isEmpty()?new JSONObject():new JSONObject(raw);}catch(Exception e){return new JSONObject();}}
    private TextView readOnly(String title,String value){TextView t=label(title+"\n"+(value==null||value.isEmpty()?"—":value),14);t.setPadding(0,dp(8),0,dp(8));t.setTextIsSelectable(true);return t;}
    private TextView label(String s,float z){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setGravity(Gravity.START);return t;}
    private EditText edit(String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setSingleLine(false);return e;}
    private Button button(String text){Button b=new Button(this);b.setText(text);b.setAllCaps(false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.topMargin=dp(8);b.setLayoutParams(p);return b;}
    private static String aliasesText(JSONObject voice){if(voice==null)return"";JSONArray a=voice.optJSONArray("aliases");if(a==null)return"";StringBuilder s=new StringBuilder();for(int i=0;i<a.length();i++){if(i>0)s.append(", ");s.append(a.optString(i));}return s.toString();}
    private static String value(JSONObject o,String a,String b){String v=o==null?"":o.optString(a,"");if(v.isEmpty()&&o!=null)v=o.optString(b,"");return v;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
