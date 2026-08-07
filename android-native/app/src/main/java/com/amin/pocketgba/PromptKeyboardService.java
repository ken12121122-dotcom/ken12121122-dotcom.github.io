package com.amin.pocketgba;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.util.List;

public final class PromptKeyboardService extends InputMethodService {
    private PromptStore store;
    private LinearLayout root,filterRow,promptList;
    private TextView status;
    private EditText search;
    private String filter="all";
    private boolean sessionUnlocked,receiverRegistered;
    private final BroadcastReceiver unlockReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(PromptUnlockActivity.ACTION_PROMPT_UNLOCKED.equals(i.getAction())){sessionUnlocked=true;renderHome();}}};

    @Override public void onCreate(){super.onCreate();store=new PromptStore(this);IntentFilter f=new IntentFilter(PromptUnlockActivity.ACTION_PROMPT_UNLOCKED);ContextCompat.registerReceiver(this,unlockReceiver,f,ContextCompat.RECEIVER_NOT_EXPORTED);receiverRegistered=true;}
    @Override public View onCreateInputView(){root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(8),dp(6),dp(8),dp(6));renderState();return root;}
    @Override public void onStartInputView(EditorInfo info,boolean restarting){super.onStartInputView(info,restarting);if(root!=null)renderState();}
    @Override public void onFinishInputView(boolean finishing){lockSession();super.onFinishInputView(finishing);}
    private void renderState(){AminTheme.Palette p=AminTheme.palette(this);if(root!=null)root.setBackgroundColor(p.background);if(sessionUnlocked)renderHome();else renderLock();}

    private void renderLock(){AminTheme.Palette p=AminTheme.palette(this);root.removeAllViews();TextView t=text("Amin 提示詞已鎖定",17,true,p.text);t.setGravity(Gravity.CENTER);root.addView(t,new LinearLayout.LayoutParams(-1,dp(52)));TextView m=text("使用手機原本的螢幕鎖驗證後即可開啟",13,false,p.muted);m.setGravity(Gravity.CENTER);root.addView(m,new LinearLayout.LayoutParams(-1,dp(42)));Button u=button("使用手機鎖解鎖",p);u.setTextColor(Color.WHITE);u.setBackgroundTintList(android.content.res.ColorStateList.valueOf(p.primary));u.setOnClickListener(v->launchUnlock());root.addView(u,full());Button s=button("切換鍵盤",p);s.setOnClickListener(v->switchKeyboard());root.addView(s,full());}
    private void launchUnlock(){try{Intent i=new Intent(this,PromptUnlockActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);startActivity(i);}catch(RuntimeException e){Toast.makeText(this,"無法開啟手機驗證",Toast.LENGTH_SHORT).show();}}

    private void renderHome(){
        if(root==null)return;AminTheme.Palette p=AminTheme.palette(this);root.removeAllViews();root.setBackgroundColor(p.background);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView title=text("Amin 提示詞",16,true,p.text);head.addView(title,new LinearLayout.LayoutParams(0,dp(40),1));
        Button add=button("＋",p);add.setOnClickListener(v->openManager(true));head.addView(add);Button manage=button("管理",p);manage.setOnClickListener(v->openManager(false));head.addView(manage);Button lock=button("鎖定",p);lock.setOnClickListener(v->{lockSession();renderLock();});head.addView(lock);Button sw=button("切換",p);sw.setOnClickListener(v->switchKeyboard());head.addView(sw);root.addView(head,full());
        search=new EditText(this);search.setSingleLine(true);search.setHint("搜尋提示詞");search.setTextSize(13);search.setTextColor(p.text);search.setHintTextColor(p.muted);search.setBackgroundColor(p.surface);search.setPadding(dp(10),0,dp(10),0);search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){renderPrompts();}public void afterTextChanged(Editable e){}});root.addView(search,new LinearLayout.LayoutParams(-1,dp(42)));
        HorizontalScrollView fs=new HorizontalScrollView(this);fs.setHorizontalScrollBarEnabled(false);filterRow=new LinearLayout(this);filterRow.setOrientation(LinearLayout.HORIZONTAL);fs.addView(filterRow);root.addView(fs,new LinearLayout.LayoutParams(-1,dp(44)));String[][] defs={{"all","全部"},{"favorite","收藏"},{"pinned","置頂"},{"recent","最近"}};for(String[] d:defs)addFilter(d[0],d[1],p);for(PromptStore.Category c:store.listCategories())addFilter("cat:"+c.id,c.name,p);
        status=text("",12,false,p.muted);root.addView(status,full());ScrollView ps=new ScrollView(this);promptList=new LinearLayout(this);promptList.setOrientation(LinearLayout.VERTICAL);ps.addView(promptList);root.addView(ps,new LinearLayout.LayoutParams(-1,dp(185)));renderPrompts();
    }

    private void addFilter(String id,String label,AminTheme.Palette p){Button b=button(label,p);boolean selected=id.equals(filter);b.setTextColor(selected?Color.WHITE:p.primary);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(selected?p.primary:p.surfaceAlt));b.setOnClickListener(v->{filter=id;renderHome();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(40));lp.rightMargin=dp(5);filterRow.addView(b,lp);}

    private void renderPrompts(){if(promptList==null)return;AminTheme.Palette pal=AminTheme.palette(this);promptList.removeAllViews();if(isPasswordField(getCurrentInputEditorInfo())){status.setText("密碼欄位不顯示提示詞");return;}String q=search==null?"":search.getText().toString().trim();List<PromptStore.Prompt> rows;if(!q.isEmpty())rows=store.searchActive(q);else if("favorite".equals(filter))rows=store.listFavorites();else if("pinned".equals(filter))rows=store.listPinned();else if("recent".equals(filter))rows=store.listRecent();else if(filter.startsWith("cat:"))rows=store.listPrompts(filter.substring(4));else rows=store.listActive();status.setText(rows.isEmpty()?"沒有符合的提示詞":"共 "+rows.size()+" 筆");for(PromptStore.Prompt prompt:rows){Button b=new Button(this);b.setAllCaps(false);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setText(cardText(prompt));b.setTextSize(13);b.setTextColor(pal.text);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(pal.surface));b.setMinHeight(dp(48));b.setPadding(dp(10),dp(5),dp(10),dp(5));b.setOnClickListener(v->commit(prompt));b.setOnLongClickListener(v->{showQuickActions(prompt);return true;});LinearLayout.LayoutParams lp=full();lp.topMargin=dp(4);promptList.addView(b,lp);}}

    private String cardText(PromptStore.Prompt p){String prefix=(p.pinned?"📌 ":"")+(p.favorite?"⭐ ":"");String title=p.title==null?"":p.title.trim();String preview=PromptText.preview(p.content,78).trim();String auto=PromptText.preview(p.content.replace('\n',' '),40).trim();if(title.isEmpty()||title.equals(preview)||title.equals(auto)||preview.startsWith(title))return prefix+preview;return prefix+title+" · "+preview;}
    private void showQuickActions(PromptStore.Prompt p){String[] items={p.favorite?"取消收藏":"收藏",p.pinned?"取消置頂":"置頂","查看／管理"};new android.app.AlertDialog.Builder(this).setTitle(p.title).setItems(items,(d,w)->{if(w==0)store.setFavorite(p.id,!p.favorite);else if(w==1)store.setPinned(p.id,!p.pinned);else openPrompt(p.id);renderPrompts();}).show();}
    private void commit(PromptStore.Prompt p){InputConnection c=getCurrentInputConnection();if(c==null){Toast.makeText(this,"目前沒有可輸入的文字框",Toast.LENGTH_SHORT).show();return;}c.commitText(p.content,1);store.recordUsage(p.id);}
    private void openManager(boolean create){Intent i=new Intent(this,create?PromptEditorActivity.class:PromptManagerActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);}
    private void openPrompt(long id){Intent i=new Intent(this,PromptEditorActivity.class);i.putExtra("prompt_id",id);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);}
    private void lockSession(){sessionUnlocked=false;}
    private void switchKeyboard(){lockSession();if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P&&switchToNextInputMethod(false))return;InputMethodManager m=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);if(m!=null)m.showInputMethodPicker();}
    private boolean isPasswordField(EditorInfo i){if(i==null)return false;int c=i.inputType&InputType.TYPE_MASK_CLASS,v=i.inputType&InputType.TYPE_MASK_VARIATION;if(c==InputType.TYPE_CLASS_NUMBER)return v==InputType.TYPE_NUMBER_VARIATION_PASSWORD;if(c!=InputType.TYPE_CLASS_TEXT)return false;return v==InputType.TYPE_TEXT_VARIATION_PASSWORD||v==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD||v==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;}
    @Override public void onDestroy(){lockSession();if(receiverRegistered)unregisterReceiver(unlockReceiver);if(store!=null)store.close();super.onDestroy();}
    private Button button(String s,AminTheme.Palette p){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextSize(12);b.setTextColor(p.primary);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(p.surfaceAlt));b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(9),dp(4),dp(9),dp(4));return b;}
    private TextView text(String s,float z,boolean bold,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private LinearLayout.LayoutParams full(){return new LinearLayout.LayoutParams(-1,-2);}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
