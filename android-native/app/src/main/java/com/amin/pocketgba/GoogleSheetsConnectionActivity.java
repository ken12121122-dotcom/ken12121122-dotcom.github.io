package com.amin.pocketgba;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;

import java.util.Collections;

/** Google Sheets source binding + Android Google OAuth authorization. Access token remains process-memory only. */
public final class GoogleSheetsConnectionActivity extends Activity {
    static final String EXTRA_SOURCE_ID="source_id";
    private static final int REQUEST_AUTHORIZE=7301;
    private static final String SHEETS_SCOPE="https://www.googleapis.com/auth/spreadsheets";

    private GoogleSheetsConnectionStore store;
    private String sourceId;
    private TextView status;
    private Button authorizeButton;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        store=new GoogleSheetsConnectionStore(this);
        sourceId=clean(getIntent().getStringExtra(EXTRA_SOURCE_ID));
        if(sourceId.isEmpty())sourceId=FinanceStorageConfig.SOURCE_ID;
        FinanceStorageConfig.ensureSourceMapping(this);
        buildUi();
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(28),dp(20),dp(28));
        TextView title=text("Google Sheets 授權",26);root.addView(title);
        root.addView(text("Source ID\n"+sourceId,14));
        root.addView(text("Spreadsheet ID\n"+store.spreadsheetId(sourceId),14));
        root.addView(text("權限用途\n讀寫 Amin Pocket 財務資料庫。OAuth access token 只保留在本次 App process，不寫入 Node、GitHub 或永久儲存。",14));
        status=text(store.isReady(sourceId)?"狀態：本次工作階段已授權":"狀態：尚未授權",15);root.addView(status);
        authorizeButton=new Button(this);authorizeButton.setAllCaps(false);authorizeButton.setText(store.isReady(sourceId)?"重新取得 Google 授權":"使用 Google 帳號授權");authorizeButton.setOnClickListener(v->authorize());root.addView(authorizeButton,new LinearLayout.LayoutParams(-1,dp(56)));
        Button cancel=new Button(this);cancel.setAllCaps(false);cancel.setText("取消");cancel.setOnClickListener(v->finish());root.addView(cancel,new LinearLayout.LayoutParams(-1,dp(52)));
        setContentView(root);
    }

    private void authorize(){
        authorizeButton.setEnabled(false);status.setText("狀態：正在要求 Google Sheets 權限…");
        AuthorizationRequest request=AuthorizationRequest.builder()
                .setRequestedScopes(Collections.singletonList(new Scope(SHEETS_SCOPE)))
                .setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
                .build();
        Identity.getAuthorizationClient(this).authorize(request)
                .addOnSuccessListener(result->{
                    if(result.hasResolution()){
                        try{
                            startIntentSenderForResult(result.getPendingIntent().getIntentSender(),REQUEST_AUTHORIZE,null,0,0,0,null);
                        }catch(IntentSender.SendIntentException e){fail("無法開啟 Google 授權畫面："+safe(e));}
                    }else{
                        accept(result);
                    }
                })
                .addOnFailureListener(e->fail("Google 授權失敗："+safe(e)));
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQUEST_AUTHORIZE)return;
        if(resultCode!=RESULT_OK||data==null){fail("未完成 Google 授權");return;}
        try{
            AuthorizationResult result=Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data);
            accept(result);
        }catch(ApiException e){fail("Google 授權結果無效："+e.getStatusCode()+" · "+safe(e));}
    }

    private void accept(AuthorizationResult result){
        String token=clean(result.getAccessToken());
        if(token.isEmpty()){fail("Google 沒有回傳 access token");return;}
        store.setSessionToken(sourceId,token);
        status.setText("狀態：已取得 Google Sheets 授權");
        Toast.makeText(this,"Google Sheets 已連線",Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private void fail(String message){authorizeButton.setEnabled(true);status.setText("狀態："+message);Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private TextView text(String value,float size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setGravity(Gravity.START);t.setPadding(0,dp(8),0,dp(12));return t;}
    private static String clean(String s){return s==null?"":s.trim();}
    private static String safe(Throwable e){String m=e.getMessage();return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
