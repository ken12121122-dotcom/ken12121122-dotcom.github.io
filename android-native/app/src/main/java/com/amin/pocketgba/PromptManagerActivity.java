package com.amin.pocketgba;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.HorizontalScrollView;
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
        AminTheme.Palette p=AminTheme.palette(this);
        ScrollView scroll=new ScrollView(this); scroll.setBackgroundColor(p.background);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16),dp(18),dp(16),dp(28)); scroll.addView(root);

        LinearLayout top=new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
        Button back=button("← 返回",p); back.setOnClickListener(v->finish()); top.addView(back);
        TextView title=text("提示詞管理",26,true,p.text); top.addView(title,new LinearLayout.LayoutParams(0,dp(54),1));
        Button add=button("＋ 新增",p); add.setOnClickListener(v->startActivity(new Intent(this,PromptEditorActivity.class))); top.addView(add); root.addView(top);

        HorizontalScrollView modeScroll=new HorizontalScrollView(this); modeScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout modes=new LinearLayout(this); modes.setOrientation(LinearLayout.HORIZONTAL); modeScroll.addView(modes);
        String[][] defs={{"active","全部"},{"favorite","收藏"},{"pinned","置頂"},{"recent","最近"},{"archived","封存"},{"deleted","垃圾桶"}};
        for(String[] d:defs){ Button b=chip(d[1],d[0].equals(mode),p); b.setOnClickListener(v->{mode=d[0];render();buildModeStyles(modes,defs,p);}); modes.addView(b,new LinearLayout.LayoutParams(dp(88),dp(44))); }
        root.addView(modeScroll,new LinearLayout.LayoutParams(-1,dp(48)));

        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button graph=button("關聯圖",p); graph.setOnClickListener(v->{Intent i=new Intent(this,WikiGraphActivity.class);i.putExtra("graph_mode","all");startActivity(i);}); actions.addView(graph);
        Button keyboard=button("鍵盤設定",p); keyboard.setOnClickListener(v->startActivity(new Intent(this,PromptKeyboardSetupActivity.class))); actions.addView(keyboard);
        Button appearance=button("外觀",p); appearance.setOnClickListener(v->startActivity(new Intent(this,AppearanceSettingsActivity.class))); actions.addView(appearance);
        root.addView(actions);

        state=text("",13,false,p.muted); root.addView(state);
        list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); root.addView(list);
        setContentView(scroll); render();
    }

    private void buildModeStyles(LinearLayout modes,String[][] defs,AminTheme.Palette p){
        for(int i=0;i<modes.getChildCount()&&i<defs.length;i++){
            Button b=(Button)modes.getChildAt(i); boolean selected=defs[i][0].equals(mode);
            b.setTextColor(selected?0xffffffff:p.primary); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(selected?p.primary:p.surfaceAlt));
        }
    }

    private void render(){
        AminTheme.Palette p=AminTheme.palette(this);
        list.removeAllViews(); List<PromptStore.Prompt> rows;
        switch(mode){case "favorite":rows=store.listFavorites();break;case "pinned":rows=store.listPinned();break;case "recent":rows=store.listRecent();break;case "archived":rows=store.listArchived();break;case "deleted":rows=store.listDeleted();break;default:rows=store.listActive();}
        state.setText(("deleted".equals(mode)?"垃圾桶 · ":"")+"共 "+rows.size()+" 筆");
        for(PromptStore.Prompt prompt:rows){
            Button card=new Button(this); card.setAllCaps(false); card.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); card.setText(cardText(prompt)); card.setTextColor(p.text); card.setTextSize(15f); card.setBackgroundTintList(android.content.res.ColorStateList.valueOf(p.surface)); card.setMinHeight(dp(64)); card.setPadding(dp(12),dp(8),dp(12),dp(8));
            card.setOnClickListener(v->{Intent i=new Intent(this,PromptEditorActivity.class);i.putExtra("prompt_id",prompt.id);startActivity(i);});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(6); list.addView(card,lp);
        }
    }

    private String cardText(PromptStore.Prompt p){
        String prefix=(p.pinned?"📌 ":"")+(p.favorite?"⭐ ":"");
        String title=p.title==null?"":p.title.trim();
        String preview=PromptText.preview(p.content,100).trim();
        String normalizedContentTitle=PromptText.preview(p.content.replace('\n',' '),40).trim();
        if(title.isEmpty() || title.equals(preview) || title.equals(normalizedContentTitle) || preview.startsWith(title)) return prefix+preview;
        return prefix+title+"\n"+preview;
    }

    private Button chip(String s,boolean selected,AminTheme.Palette p){Button b=button(s,p);b.setTextColor(selected?0xffffffff:p.primary);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(selected?p.primary:p.surfaceAlt));return b;}
    private Button button(String s,AminTheme.Palette p){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextColor(p.primary);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(10),dp(5),dp(10),dp(5));b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(p.surfaceAlt));return b;}
    private TextView text(String s,float z,boolean bold,int c){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){if(store!=null)store.close();super.onDestroy();}
}
