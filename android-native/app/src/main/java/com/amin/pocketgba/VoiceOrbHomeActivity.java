package com.amin.pocketgba;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Bridge launcher and voice core. Node voice aliases are derived from Node Registry metadata. */
public final class VoiceOrbHomeActivity extends Activity implements RecognitionListener {
    private static final int REQUEST_RECORD_AUDIO = 6501;
    private static final int REQUEST_PICK_MUSIC = 6502;
    private static final long SILENCE_TIMEOUT_MS = 8000L;
    private static final long QUICK_REOPEN_MS = 10L * 60L * 1000L;
    private static final String PREFS_GREETING = "amin_startup_greeting";
    private static final String PREF_LAST_GREETING_AT = "last_greeting_at";
    private static final String GREETING_UTTERANCE_ID = "amin_startup_greeting";

    private static final String[] MORNING_GREETINGS = {
            "早安，今天需要我協助什麼？",
            "早安，我已經準備好了。",
            "早上好，請告訴我今天要處理什麼。"
    };
    private static final String[] AFTERNOON_GREETINGS = {
            "午安，請問需要我協助什麼？",
            "下午好，我已經準備好了。"
    };
    private static final String[] EVENING_GREETINGS = {
            "晚上好，請問需要我協助什麼？",
            "晚安前還有什麼需要我處理的嗎？"
    };
    private static final String[] LATE_NIGHT_GREETINGS = {
            "這麼晚了，還有什麼需要我協助？",
            "我還在線，請告訴我要處理什麼。"
    };
    private static final String[] GENERAL_GREETINGS = {
            "您好，請問有什麼需要為您協助的？",
            "我是最棒的已準備完成。",
            "好的，我在，請下達指令。"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final VoiceCommandParser parser = new VoiceCommandParser();
    private final Runnable silenceTimeout = this::enterIdleState;
    private VoiceOrbView orbView;
    private TextView statusView;
    private TextView transcriptView;
    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private TextToSpeech textToSpeech;
    private boolean listening;
    private boolean launchedFeature;
    private boolean firstResume = true;
    private boolean greetingInProgress;
    private NodeMetadataStore nodeMetadataStore;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        nodeMetadataStore = new NodeMetadataStore(this);
        getWindow().setStatusBarColor(0xff08130e);
        getWindow().setNavigationBarColor(0xff08130e);
        buildUi();
        prepareRecognizer();
    }

    @Override protected void onResume() {
        super.onResume();
        UniversalControlAccessibilityService.setVoiceBubbleEnabled(this, false);
        if (firstResume) {
            firstResume=false;
            handler.postDelayed(this::speakStartupGreetingThenListen,180L);
        } else if (launchedFeature) {
            launchedFeature=false;
            handler.postDelayed(this::startListeningWithPermission,320L);
        }
    }

    @Override protected void onPause() {
        stopListeningQuietly();
        if(textToSpeech!=null && greetingInProgress) textToSpeech.stop();
        greetingInProgress=false;
        unduckBackgroundMusic();
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if(recognizer!=null){recognizer.destroy();recognizer=null;}
        if(textToSpeech!=null){textToSpeech.stop();textToSpeech.shutdown();textToSpeech=null;}
        super.onDestroy();
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQUEST_PICK_MUSIC || resultCode!=RESULT_OK || data==null || data.getData()==null) return;
        Uri uri=data.getData();
        try{
            if((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0){
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        }catch(Exception ignored){}
        getSharedPreferences(BackgroundMusicService.PREFS,MODE_PRIVATE).edit()
                .putString(BackgroundMusicService.KEY_URI,uri.toString())
                .putString(BackgroundMusicService.KEY_TITLE,"Wonders of the Earth")
                .apply();
        transcriptView.setText("背景音樂已選擇，按播放後可切到其他 App 或鎖定螢幕。");
        startBackgroundMusic();
    }

    private void buildUi() {
        FrameLayout root=new FrameLayout(this); root.setBackgroundColor(0xff08130e);
        LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setGravity(Gravity.CENTER_HORIZONTAL); content.setPadding(dp(24),dp(26),dp(24),dp(24));
        root.addView(content,new FrameLayout.LayoutParams(-1,-1));

        Button update=button("版本更新"); update.setOnClickListener(v->{stopListeningQuietly();NativeUpdateRouter.openExistingUpdateFlow(this);});
        FrameLayout.LayoutParams up=new FrameLayout.LayoutParams(dp(104),dp(46));up.gravity=Gravity.TOP|Gravity.END;up.topMargin=dp(10);up.rightMargin=dp(10);root.addView(update,up);

        TextView brand=text("AMIN",13,true,0xff59e39b);brand.setGravity(Gravity.CENTER);content.addView(brand,wrap());
        TextView title=text("語音核心",28,true,Color.WHITE);title.setGravity(Gravity.CENTER);content.addView(title,wrap());
        TextView sub=text("Voice Registry → Node Registry → Capability",13,false,0xff8eaaa0);sub.setGravity(Gravity.CENTER);content.addView(sub,wrap());

        orbView=new VoiceOrbView(this);orbView.setOnClickListener(v->{if(listening)stopAndProcess();else startListeningWithPermission();});
        LinearLayout.LayoutParams orb=new LinearLayout.LayoutParams(-1,0,1f);orb.topMargin=dp(8);content.addView(orbView,orb);
        statusView=text("準備中",18,true,Color.WHITE);statusView.setGravity(Gravity.CENTER);content.addView(statusView,wrap());
        transcriptView=text("請說出功能名稱或既有語音指令",15,false,0xffb9c8c0);transcriptView.setGravity(Gravity.CENTER);transcriptView.setMaxLines(4);content.addView(transcriptView,wrap());

        LinearLayout musicActions=new LinearLayout(this);musicActions.setOrientation(LinearLayout.HORIZONTAL);LinearLayout.LayoutParams map=wrap();map.topMargin=dp(12);content.addView(musicActions,map);
        Button chooseMusic=button("♫ 選擇音樂");chooseMusic.setOnClickListener(v->pickBackgroundMusic());musicActions.addView(chooseMusic,new LinearLayout.LayoutParams(0,dp(50),1));
        Button playMusic=button("▶ 背景播放");playMusic.setOnClickListener(v->startBackgroundMusic());LinearLayout.LayoutParams pmp=new LinearLayout.LayoutParams(0,dp(50),1);pmp.leftMargin=dp(8);musicActions.addView(playMusic,pmp);

        LinearLayout musicControls=new LinearLayout(this);musicControls.setOrientation(LinearLayout.HORIZONTAL);LinearLayout.LayoutParams mcp=wrap();mcp.topMargin=dp(8);content.addView(musicControls,mcp);
        Button pauseMusic=button("⏸ 暫停");pauseMusic.setOnClickListener(v->BackgroundMusicService.start(this,BackgroundMusicService.ACTION_PAUSE));musicControls.addView(pauseMusic,new LinearLayout.LayoutParams(0,dp(46),1));
        Button stopMusic=button("■ 停止");stopMusic.setOnClickListener(v->BackgroundMusicService.start(this,BackgroundMusicService.ACTION_STOP));LinearLayout.LayoutParams smp=new LinearLayout.LayoutParams(0,dp(46),1);smp.leftMargin=dp(8);musicControls.addView(stopMusic,smp);

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);LinearLayout.LayoutParams ap=wrap();ap.topMargin=dp(10);content.addView(actions,ap);
        Button graph=button("功能地圖");graph.setOnClickListener(v->openSystemFeatureMap());actions.addView(graph,new LinearLayout.LayoutParams(0,dp(52),1));
        Button collapse=button("收合");collapse.setOnClickListener(v->collapseToFloatingButton());LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(52),1);cp.leftMargin=dp(10);actions.addView(collapse,cp);
        setContentView(root);
    }

    private void pickBackgroundMusic(){
        stopListeningQuietly();
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        launchedFeature=true;
        startActivityForResult(intent,REQUEST_PICK_MUSIC);
    }

    private void startBackgroundMusic(){
        String uri=getSharedPreferences(BackgroundMusicService.PREFS,MODE_PRIVATE).getString(BackgroundMusicService.KEY_URI,"");
        if(uri==null || uri.isEmpty()){
            transcriptView.setText("請先按『選擇音樂』，選一次後 App 會記住它。");
            pickBackgroundMusic();
            return;
        }
        BackgroundMusicService.start(this,BackgroundMusicService.ACTION_PLAY);
        transcriptView.setText("背景音樂已開始，可切換 App 或鎖定螢幕。通知列可暫停或停止。");
    }

    private void duckBackgroundMusic(){
        try{BackgroundMusicService.start(this,BackgroundMusicService.ACTION_DUCK);}catch(Exception ignored){}
    }
    private void unduckBackgroundMusic(){
        try{BackgroundMusicService.start(this,BackgroundMusicService.ACTION_UNDUCK);}catch(Exception ignored){}
    }

    private void speakStartupGreetingThenListen() {
        if(isFinishing() || isDestroyed()) return;
        final String greeting=selectStartupGreeting();
        status("向您問候",VoiceOrbView.Phase.IDLE);
        transcriptView.setText(greeting);
        greetingInProgress=true;
        duckBackgroundMusic();

        textToSpeech=new TextToSpeech(this,status->{
            if(status!=TextToSpeech.SUCCESS){
                greetingInProgress=false;
                unduckBackgroundMusic();
                handler.postDelayed(this::startListeningWithPermission,120L);
                return;
            }
            textToSpeech.setLanguage(Locale.TAIWAN);
            textToSpeech.setSpeechRate(0.95f);
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                @Override public void onStart(String utteranceId) {}
                @Override public void onDone(String utteranceId) {
                    if(!GREETING_UTTERANCE_ID.equals(utteranceId)) return;
                    handler.post(()->{
                        greetingInProgress=false;
                        if(!isFinishing()&&!isDestroyed()) handler.postDelayed(VoiceOrbHomeActivity.this::startListeningWithPermission,150L);
                    });
                }
                @Override public void onError(String utteranceId) {
                    handler.post(()->{
                        greetingInProgress=false;
                        unduckBackgroundMusic();
                        if(!isFinishing()&&!isDestroyed()) startListeningWithPermission();
                    });
                }
            });
            int result=textToSpeech.speak(greeting,TextToSpeech.QUEUE_FLUSH,null,GREETING_UTTERANCE_ID);
            if(result==TextToSpeech.ERROR){
                greetingInProgress=false;
                unduckBackgroundMusic();
                handler.postDelayed(this::startListeningWithPermission,120L);
            }
        });
    }

    private String selectStartupGreeting() {
        SharedPreferences prefs=getSharedPreferences(PREFS_GREETING,MODE_PRIVATE);
        long now=System.currentTimeMillis();
        long last=prefs.getLong(PREF_LAST_GREETING_AT,0L);
        prefs.edit().putLong(PREF_LAST_GREETING_AT,now).apply();
        if(last>0L && now-last<QUICK_REOPEN_MS) return "我在。";

        if(ThreadLocalRandom.current().nextDouble()>=0.70d) return randomFrom(GENERAL_GREETINGS);
        int hour=LocalTime.now().getHour();
        if(hour>=5 && hour<12) return randomFrom(MORNING_GREETINGS);
        if(hour>=12 && hour<18) return randomFrom(AFTERNOON_GREETINGS);
        if(hour>=18) return randomFrom(EVENING_GREETINGS);
        return randomFrom(LATE_NIGHT_GREETINGS);
    }

    private String randomFrom(String[] values) {
        if(values==null || values.length==0) return "您好，請問有什麼需要為您協助的？";
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    private void prepareRecognizer() {
        if(!SpeechRecognizer.isRecognitionAvailable(this)){status("沒有可用的語音服務",VoiceOrbView.Phase.ERROR);return;}
        recognizer=SpeechRecognizer.createSpeechRecognizer(this);recognizer.setRecognitionListener(this);
        recognizerIntent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.TAIWAN.toLanguageTag());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,Locale.TAIWAN.toLanguageTag());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
    }

    private void startListeningWithPermission(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQUEST_RECORD_AUDIO);return;}startListening();}
    private void startListening(){if(recognizer==null||listening||greetingInProgress)return;duckBackgroundMusic();listening=true;status("正在聆聽",VoiceOrbView.Phase.LISTENING);transcriptView.setText("請說出功能名稱或指令");handler.removeCallbacks(silenceTimeout);handler.postDelayed(silenceTimeout,SILENCE_TIMEOUT_MS);try{recognizer.startListening(recognizerIntent);}catch(RuntimeException e){unduckBackgroundMusic();enterIdleState();}}
    private void stopAndProcess(){if(!listening||recognizer==null)return;handler.removeCallbacks(silenceTimeout);listening=false;status("正在理解",VoiceOrbView.Phase.PROCESSING);recognizer.stopListening();}
    private void stopListeningQuietly(){handler.removeCallbacks(silenceTimeout);if(recognizer!=null&&listening)recognizer.cancel();listening=false;unduckBackgroundMusic();}
    private void enterIdleState(){stopListeningQuietly();status("待命中",VoiceOrbView.Phase.IDLE);transcriptView.setText("點一下語音球重新開始監聽");}
    private void collapseToFloatingButton(){stopListeningQuietly();UniversalControlAccessibilityService.setVoiceBubbleEnabled(this,true);finishAndRemoveTask();}

    private void handleTranscript(String transcript,double confidence){
        unduckBackgroundMusic();
        String spoken=transcript==null?"":transcript.trim();transcriptView.setText("你說：「"+spoken+"」");
        if(spoken.contains("功能地圖")||spoken.contains("節點地圖")){openSystemFeatureMap();return;}
        if(spoken.equals("關閉")||spoken.equals("收合")||spoken.contains("關閉語音球")){collapseToFloatingButton();return;}

        NodeRegistry.Match nodeMatch=NodeRegistry.matchVoice(this,nodeMetadataStore,spoken);
        if(nodeMatch!=null){
            JSONObject node=nodeMatch.node;String rawId=node.optString("rawId","");String route=node.optString("route","");
            transcriptView.setText("你說：「"+spoken+"」\nCapability: "+node.optString("capability_id",node.optString("capabilityId","")));
            status("已匹配「"+nodeMatch.alias+"」",VoiceOrbView.Phase.SUCCESS);openGraph(rawId,route);return;
        }

        VoiceCommandParser.Result parsed=parser.parse(spoken,confidence);
        if(parsed.getStatus()!=VoiceCommandParser.Result.Status.MATCHED){status(parsed.getMessage(),VoiceOrbView.Phase.ERROR);handler.postDelayed(this::enterIdleState,1300L);return;}
        status("正在執行既有指令",VoiceOrbView.Phase.PROCESSING);
        AminActionDispatcher.DispatchResult result=AminActionDispatcher.dispatch(this,parsed.getAction());
        if(result.isSuccess()){launchedFeature=true;status(result.getMessage(),VoiceOrbView.Phase.SUCCESS);}else{status(result.getMessage(),VoiceOrbView.Phase.ERROR);handler.postDelayed(this::enterIdleState,1300L);}
    }

    private void openSystemFeatureMap(){stopListeningQuietly();launchedFeature=true;startActivity(new Intent(this,SystemGraphActivity.class));}
    private void openGraph(String focusRawId,String route){stopListeningQuietly();Intent i=new Intent(this,WikiGraphActivity.class);if(focusRawId!=null&&!focusRawId.isEmpty())i.putExtra("focus_node",focusRawId);if(route!=null&&!route.isEmpty())i.putExtra("voice_open_route",route);launchedFeature=true;startActivity(i);}
    private void status(String value,VoiceOrbView.Phase phase){if(statusView!=null)statusView.setText(value);if(orbView!=null)orbView.setPhase(phase);}

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode!=REQUEST_RECORD_AUDIO)return;if(grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED)startListening();else status("未取得麥克風權限",VoiceOrbView.Phase.ERROR);}
    @Override public void onReadyForSpeech(Bundle params){status("請開始說話",VoiceOrbView.Phase.LISTENING);}
    @Override public void onBeginningOfSpeech(){handler.removeCallbacks(silenceTimeout);status("已聽到聲音",VoiceOrbView.Phase.LISTENING);}
    @Override public void onRmsChanged(float rmsdB){if(orbView!=null)orbView.setAmplitude(rmsdB);}
    @Override public void onBufferReceived(byte[] buffer){}
    @Override public void onEndOfSpeech(){handler.removeCallbacks(silenceTimeout);listening=false;status("正在理解",VoiceOrbView.Phase.PROCESSING);}
    @Override public void onError(int error){handler.removeCallbacks(silenceTimeout);listening=false;unduckBackgroundMusic();if(error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT||error==SpeechRecognizer.ERROR_NO_MATCH){enterIdleState();return;}status("語音辨識失敗",VoiceOrbView.Phase.ERROR);handler.postDelayed(this::enterIdleState,1300L);}
    @Override public void onResults(Bundle results){handler.removeCallbacks(silenceTimeout);listening=false;unduckBackgroundMusic();ArrayList<String> matches=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);float[] c=results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);if(matches==null||matches.isEmpty()){enterIdleState();return;}handleTranscript(matches.get(0),c!=null&&c.length>0?c[0]:-1d);}
    @Override public void onPartialResults(Bundle partialResults){handler.removeCallbacks(silenceTimeout);ArrayList<String> matches=partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(matches!=null&&!matches.isEmpty())transcriptView.setText(matches.get(0));handler.postDelayed(silenceTimeout,SILENCE_TIMEOUT_MS);}
    @Override public void onEvent(int eventType,Bundle params){}

    private TextView text(String value,float size,boolean bold,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private Button button(String label){Button b=new Button(this);b.setText(label);b.setAllCaps(false);return b;}
    private LinearLayout.LayoutParams wrap(){return new LinearLayout.LayoutParams(-1,-2);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
