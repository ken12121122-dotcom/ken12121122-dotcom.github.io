package com.amin.pocketgba;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
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

import java.util.List;

public final class PromptEditorActivity extends Activity {
    private PromptStore store;
    private long promptId=-1;
    private PromptStore.Prompt prompt;
    private EditText title,content,tags,targetId;
    private Spinner category,targetType,relationType;
    private CheckBox favorite,pinned;
    private LinearLayout relationList,actions;

    @Override protected void onCreate(Bundle b){super.onCreate(b);store=new PromptStore(this);promptId=getIntent().getLongExtra("prompt_id",-1);prompt=promptId>0?store.getPrompt(promptId):null;build();}

    private void build(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xfff4f7f5);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(18),dp(18),dp(32));scroll.addView(root);
        Button back=button("← 返回");back.setOnClickListener(v->finish());root.addView(back,full());
        TextView heading=new TextView(this);heading.setText(prompt==null?"新增提示詞":"編輯提示詞");heading.setTextSize(27);heading.setTextColor(0xff16231b);root.addView(heading,full());
        title=input("名稱");content=input("提示詞內容");content.setMinLines(7);tags=input("Tags（以逗號分隔）");root.addView(title,full());root.addView(content,full());root.addView(tags,full());
        category=new Spinner(this);List<PromptStore.Category> cats=store.listCategories();String[] labels=new String[cats.size()];for(int i=0;i<cats.size();i++)labels[i]=cats.get(i).name;category.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));root.addView(category,full());
        favorite=new CheckBox(this);favorite.setText("⭐ 收藏");pinned=new CheckBox(this);pinned.setText("📌 置頂");root.addView(favorite);root.addView(pinned);
        if(prompt!=null){title.setText(prompt.title);content.setText(prompt.content);tags.setText(prompt.tags);favorite.setChecked(prompt.favorite);pinned.setChecked(prompt.pinned);for(int i=0;i<cats.size();i++)if(cats.get(i).id.equals(prompt.categoryId))category.setSelection(i);}
        Button save=button("儲存");save.setOnClickListener(v->save(cats));root.addView(save,full());
        actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);root.addView(actions,full());
        relationList=new LinearLayout(this);relationList.setOrientation(LinearLayout.VERTICAL);root.addView(relationList,full());
        setContentView(scroll);refreshActions();refreshRelations();
    }

    private void save(List<PromptStore.Category> cats){
        try{
            String cid=cats.isEmpty()?PromptStore.DEFAULT_CATEGORY_ID:cats.get(category.getSelectedItemPosition()).id;
            if(prompt==null){promptId=store.createPrompt(cid,title.getText().toString(),content.getText().toString(),tags.getText().toString());prompt=store.getPrompt(promptId);Toast.makeText(this,"已建立",Toast.LENGTH_SHORT).show();}
            else{store.updatePrompt(promptId,title.getText().toString(),content.getText().toString(),cid,tags.getText().toString());store.setFavorite(promptId,favorite.isChecked());store.setPinned(promptId,pinned.isChecked());prompt=store.getPrompt(promptId);Toast.makeText(this,"已儲存",Toast.LENGTH_SHORT).show();}
            refreshActions();refreshRelations();
        }catch(RuntimeException e){Toast.makeText(this,"請輸入提示詞內容",Toast.LENGTH_SHORT).show();}
    }

    private void refreshActions(){
        if(actions==null)return;actions.removeAllViews();if(prompt==null)return;
        if(PromptStore.STATUS_DELETED.equals(prompt.status)){
            Button restore=button("復原");restore.setOnClickListener(v->{store.restore(promptId);prompt=store.getPrompt(promptId);refreshActions();});actions.addView(restore);
            Button hard=button("永久刪除");hard.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("永久刪除？").setMessage("此操作無法復原。關聯資料也會一併刪除。").setNegativeButton("取消",null).setPositiveButton("永久刪除",(d,w)->{store.hardDelete(promptId);finish();}).show());actions.addView(hard);return;
        }
        Button fav=button(prompt.favorite?"取消收藏":"收藏");fav.setOnClickListener(v->{store.setFavorite(promptId,!prompt.favorite);prompt=store.getPrompt(promptId);favorite.setChecked(prompt.favorite);refreshActions();});actions.addView(fav);
        Button pin=button(prompt.pinned?"取消置頂":"置頂");pin.setOnClickListener(v->{store.setPinned(promptId,!prompt.pinned);prompt=store.getPrompt(promptId);pinned.setChecked(prompt.pinned);refreshActions();});actions.addView(pin);
        Button archive=button(PromptStore.STATUS_ARCHIVED.equals(prompt.status)?"解除封存":"封存");archive.setOnClickListener(v->{if(PromptStore.STATUS_ARCHIVED.equals(prompt.status))store.unarchive(promptId);else store.archive(promptId);prompt=store.getPrompt(promptId);refreshActions();});actions.addView(archive);
        Button del=button("刪除");del.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("移到垃圾桶？").setNegativeButton("取消",null).setPositiveButton("刪除",(d,w)->{store.softDelete(promptId);prompt=store.getPrompt(promptId);refreshActions();}).show());actions.addView(del);
    }

    private void refreshRelations(){
        if(relationList==null)return;relationList.removeAllViews();if(prompt==null)return;
        TextView h=new TextView(this);h.setText("關聯");h.setTextSize(20);h.setTextColor(0xff16231b);relationList.addView(h);
        for(PromptStore.Relation r:store.listRelationsForPrompt(promptId)){
            Button row=button(r.sourceType+":"+r.sourceId+" —"+r.relationType+"→ "+r.targetType+":"+r.targetId+"  ✕");row.setOnClickListener(v->{store.deleteRelation(r.id);refreshRelations();});relationList.addView(row,full());
        }
        targetType=new Spinner(this);targetType.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"prompt","knowledge"}));relationType=new Spinner(this);relationType.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"derived_from","depends_on","calls","similar_to","replaces","uses_knowledge","generated_from","supports","related_to"}));targetId=input("目標 ID：Prompt 數字 ID 或 Knowledge 節點檔名");relationList.addView(targetType,full());relationList.addView(relationType,full());relationList.addView(targetId,full());
        Button add=button("＋ 新增關聯");add.setOnClickListener(v->{String id=targetId.getText().toString().trim();if(id.isEmpty()){Toast.makeText(this,"請輸入目標 ID",Toast.LENGTH_SHORT).show();return;}boolean ok=store.addRelation("prompt",String.valueOf(promptId),String.valueOf(targetType.getSelectedItem()),id,String.valueOf(relationType.getSelectedItem()));Toast.makeText(this,ok?"已新增關聯":"關聯已存在或格式無效",Toast.LENGTH_SHORT).show();refreshRelations();});relationList.addView(add,full());
        Button graph=button("在關聯圖中查看");graph.setOnClickListener(v->{Intent i=new Intent(this,WikiGraphActivity.class);i.putExtra("graph_mode","all");i.putExtra("focus_node","prompt:"+promptId);startActivity(i);});relationList.addView(graph,full());
    }

    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextColor(0xff16231b);e.setHintTextColor(0xff7a877f);e.setBackgroundColor(Color.WHITE);e.setPadding(dp(12),dp(10),dp(12),dp(10));return e;}
    private Button button(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextColor(0xff105f39);return b;}
    private LinearLayout.LayoutParams full(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);p.topMargin=dp(7);return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){if(store!=null)store.close();super.onDestroy();}
}
