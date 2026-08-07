package com.amin.pocketgba;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public final class PromptKeyboardSetupActivity extends Activity {
    private TextView state;
    private PromptStore store;

    @Override protected void onCreate(Bundle b){super.onCreate(b);store=new PromptStore(this);buildUi();}
    @Override protected void onResume(){super.onResume();refreshState();}

    private void buildUi(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xfff4f7f5);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(22),dp(24),dp(22),dp(32));scroll.addView(content);
        Button back=button("← 返回控制台");back.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);back.setOnClickListener(v->finish());content.addView(back,fullWidth());
        TextView eyebrow=text("PROMPT KEYBOARD",12,true,0xff19794b);LinearLayout.LayoutParams ep=fullWidth();ep.topMargin=dp(18);content.addView(eyebrow,ep);content.addView(text("提示詞鍵盤",30,true,0xff16231b),fullWidth());
        state=text("檢查中…",15,false,0xff68766e);LinearLayout.LayoutParams sp=fullWidth();sp.topMargin=dp(12);sp.bottomMargin=dp(18);content.addView(state,sp);
        Button manage=button("管理提示詞");manage.setOnClickListener(v->startActivity(new Intent(this,PromptManagerActivity.class)));content.addView(manage,fullWidth());
        Button add=button("＋ 新增提示詞");add.setOnClickListener(v->startActivity(new Intent(this,PromptEditorActivity.class)));LinearLayout.LayoutParams ap=fullWidth();ap.topMargin=dp(8);content.addView(add,ap);
        Button enable=button("1. 啟用 Amin 提示詞鍵盤");enable.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));LinearLayout.LayoutParams epp=fullWidth();epp.topMargin=dp(16);content.addView(enable,epp);
        Button choose=button("2. 選擇目前鍵盤");choose.setOnClickListener(v->{InputMethodManager m=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(m!=null)m.showInputMethodPicker();});LinearLayout.LayoutParams cp=fullWidth();cp.topMargin=dp(8);content.addView(choose,cp);
        TextView instructions=text("鍵盤只負責搜尋、收藏、置頂、最近使用與插入。新增、編輯、刪除、垃圾桶、封存及關聯管理統一回到 App。",15,false,0xff16231b);instructions.setLineSpacing(0,1.35f);LinearLayout.LayoutParams ip=fullWidth();ip.topMargin=dp(22);content.addView(instructions,ip);setContentView(scroll);
    }

    private void refreshState(){boolean enabled=false;InputMethodManager m=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(m!=null){List<InputMethodInfo> methods=m.getEnabledInputMethodList();for(InputMethodInfo method:methods)if(getPackageName().equals(method.getPackageName())){enabled=true;break;}}state.setText((enabled?"鍵盤已啟用":"鍵盤尚未啟用")+" · Active "+store.countPrompts()+" 筆");state.setTextColor(enabled?0xff19794b:0xff9a5b00);}
    @Override protected void onDestroy(){if(store!=null)store.close();super.onDestroy();}
    private Button button(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextSize(15);b.setTextColor(0xff105f39);b.setMinHeight(dp(54));return b;}
    private TextView text(String s,float z,boolean bold,int c){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private LinearLayout.LayoutParams fullWidth(){return new LinearLayout.LayoutParams(-1,-2);}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
