package com.amin.pocketgba;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public final class PromptManagerActivity extends Activity {
    private PromptStore store;
    private LinearLayout list;
    private TextView state;
    private String mode = "active";

    @Override protected void onCreate(Bundle b){ super.onCreate(b); store=new PromptStore(this); build(); }
    @Override protected void onResume(){ super.onResume(); if(list!=null) render(); }

    private void build(){
        ScrollView scroll=new ScrollView(this); scroll.setBackgroundColor(0xfff4f7f5);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(20),dp(18),dp(30)); scroll.addView(root);
        LinearLayout top=new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
        Button back=button("← 返回"); back.setOnClickListener(v->finish()); top.addView(back);
        TextView title=text("提示詞管理",26,true,0xff16231b); top.addView(title,new LinearLayout.LayoutParams(0,dp(54),1));
        Button add=button("＋ 新增"); add.setOnClickListener(v->startActivity(new Intent(this,PromptEditorActivity.class))); top.addView(add); root.addView(top);

        LinearLayout modes=new LinearLayout(this); modes.setOrientation(LinearLayout.HORIZONTAL);
        String[][] defs={{"active","全部"},{"favorite","收藏"},{"pinned","置頂"},{"recent","最近"},{"archived","封存"},{"deleted","垃圾桶"}};
        for(String[] d:defs){ Button b=button(d[1]); b.setOnClickListener(v->{mode=d[0];render();}); modes.addView(b); }
        ScrollView dummy=null;
        root.addView(modes,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(52)));
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button graph=button("關聯圖"); graph.setOnClickListener(v->{Intent i=new Intent(this,WikiGraphActivity.class);i.putExtra("graph_mode","all");startActivity(i);}); actions.addView(graph);
        Button keyboard=button("鍵盤設定"); keyboard.setOnClickListener(v->startActivity(new Intent(this,PromptKeyboardSetupActivity.class))); actions.addView(keyboard);
        root.addView(actions);
        state=text("",13,false,0xff68766e); root.addView(state);
        list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); root.addView(list);
        setContentView(scroll); render();
    }

    private void render(){
        list.removeAllViews(); List<PromptStore.Prompt> rows;
        switch(mode){case "favorite":rows=store.listFavorites();break;case "pinned":rows=store.listPinned();break;case "recent":rows=store.listRecent();break;case "archived":rows=store.listArchived();break;case "deleted":rows=store.listDeleted();break;default:rows=store.listActive();}
        state.setText("共 "+rows.size()+" 筆");
        for(PromptStore.Prompt p:rows){
            Button b=new Button(this); b.setAllCaps(false); b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); b.setText((p.pinned?"📌 ":"")+(p.favorite?"⭐ ":"")+p.title+"\n"+PromptText.preview(p.content,80)); b.setTextColor(0xff16231b); b.setBackgroundColor(Color.WHITE);
            b.setOnClickListener(v->{Intent i=new Intent(this,PromptEditorActivity.class);i.putExtra("prompt_id",p.id);startActivity(i);});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT); lp.topMargin=dp(7); list.addView(b,lp);
        }
    }

    private Button button(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextColor(0xff105f39);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(10),dp(5),dp(10),dp(5));return b;}
    private TextView text(String s,float z,boolean bold,int c){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){if(store!=null)store.close();super.onDestroy();}
}
