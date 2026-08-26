package com.amin.pocketgba;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

final class FloatingVoiceController implements RecognitionListener {
    private static final String PREFS = "amin_floating_voice";
    private static final String KEY_X = "voice_bubble_x";
    private static final String KEY_Y = "voice_bubble_y";
    private static final long BUBBLE_RESET_DELAY_MS = 1400L;
    private static final long LISTENING_IDLE_TIMEOUT_MS = 8000L;
    private static final long FAKE_REPLY_DELAY_MS = 650L;
    private static final int MAX_ACTIVE_TURNS = 10;
    private static final int MAX_ACTIVE_MESSAGES = MAX_ACTIVE_TURNS * 2;

    private final UniversalControlAccessibilityService service;
    private final WindowManager windowManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final VoiceCommandParser parser = new VoiceCommandParser();
    private final Runnable resetBubbleTask = () -> setPhase(FloatingVoicePresentation.Phase.IDLE);
    private final Runnable listeningTimeoutTask = this::collapseListeningToIdle;

    private TextView bubble;
    private WindowManager.LayoutParams bubbleParams;
    private LinearLayout panel;
    private WindowManager.LayoutParams panelParams;
    private TextView modeView;
    private TextView statusView;
    private TextView transcriptView;
    private TextView resultView;
    private ScrollView chatScroll;
    private LinearLayout chatContainer;

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private TextToSpeech textToSpeech;
    private boolean ttsReady;
    private boolean listening;
    private boolean processing;
    private boolean ignoreNextError;
    private int screenWidth;
    private int screenHeight;

    FloatingVoiceController(
            UniversalControlAccessibilityService service,
            WindowManager windowManager
    ) {
        this.service = service;
        this.windowManager = windowManager;
        refreshScreenBounds();
    }

    void show() {
        if (windowManager == null || bubble != null) return;
        refreshScreenBounds();
        createStatusPanel();
        createVoiceBubble();
        ensureTts();
    }

    boolean isVisible() {
        return bubble != null;
    }

    void hide() {
        stopRecognizer(true);
        removeViewSafely(panel);
        removeViewSafely(bubble);
        panel = null;
        panelParams = null;
        modeView = null;
        statusView = null;
        transcriptView = null;
        resultView = null;
        chatScroll = null;
        chatContainer = null;
        bubble = null;
        bubbleParams = null;
        mainHandler.removeCallbacks(resetBubbleTask);
    }

    void destroy() {
        hide();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        recognizerIntent = null;
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        ttsReady = false;
    }

    void onConfigurationChanged() {
        refreshScreenBounds();
        if (bubbleParams != null) {
            clampBubbleCoordinates();
            updateViewLayoutSafely(bubble, bubbleParams);
        }
        if (panelParams != null) {
            panelParams.width = Math.min(Math.max(dp(240), screenWidth - dp(28)), dp(420));
            panelParams.height = Math.min(dp(390), Math.max(dp(300), screenHeight / 2));
            updateViewLayoutSafely(panel, panelParams);
        }
    }

    private void createVoiceBubble() {
        bubble = new TextView(service);
        bubble.setText(FloatingVoicePresentation.bubbleText(FloatingVoicePresentation.Phase.IDLE));
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(20f);
        bubble.setGravity(Gravity.CENTER);
        bubble.setTypeface(Typeface.DEFAULT_BOLD);
        bubble.setContentDescription("Amin 語音浮動按鈕，點一下開始或停止辨識，拖曳可移動");
        bubble.setBackground(circleBackground(0xe61f7a4d, Color.WHITE, 1));
        bubble.setElevation(dp(8));
        bubble.setOnClickListener(view -> toggleListening());

        int size = dp(56);
        bubbleParams = baseOverlayParams(size, size, "Amin Floating Voice Button", false);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        SharedPreferences prefs = preferences();
        bubbleParams.x = prefs.getInt(KEY_X, Math.max(0, screenWidth - size - dp(8)));
        bubbleParams.y = prefs.getInt(KEY_Y, Math.max(dp(144), screenHeight / 3 + dp(72)));
        clampBubbleCoordinates();

        bubble.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX;
            private float downRawY;
            private int downWindowX;
            private int downWindowY;
            private boolean dragging;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        downWindowX = bubbleParams.x;
                        downWindowY = bubbleParams.y;
                        dragging = false;
                        view.setPressed(true);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        if (!dragging && (Math.abs(dx) > dp(7) || Math.abs(dy) > dp(7))) {
                            dragging = true;
                        }
                        if (dragging) {
                            bubbleParams.x = downWindowX + Math.round(dx);
                            bubbleParams.y = downWindowY + Math.round(dy);
                            clampBubbleCoordinates();
                            updateViewLayoutSafely(bubble, bubbleParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        view.setPressed(false);
                        if (dragging) {
                            snapBubbleToEdge();
                        } else {
                            view.performClick();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        view.setPressed(false);
                        return true;
                    default:
                        return true;
                }
            }
        });

        windowManager.addView(bubble, bubbleParams);
    }

    private void createStatusPanel() {
        panel = new LinearLayout(service);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setBackground(roundedBackground(0xdd17231d, 18f, 0x55ffffff, 1));

        modeView = textView(13f, 0xffd7e6de, true);
        modeView.setGravity(Gravity.START);
        panel.addView(modeView, matchWrap());

        statusView = textView(17f, Color.WHITE, true);
        statusView.setGravity(Gravity.START);
        statusView.setText("UI 測試模式 · 語音待命");
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(4);
        panel.addView(statusView, statusParams);

        transcriptView = textView(15f, 0xfff5f8f6, false);
        transcriptView.setGravity(Gravity.START);
        transcriptView.setText("點語音球開始說話");
        LinearLayout.LayoutParams transcriptParams = matchWrap();
        transcriptParams.topMargin = dp(6);
        panel.addView(transcriptView, transcriptParams);

        chatScroll = new ScrollView(service);
        chatScroll.setFillViewport(false);
        chatScroll.setVerticalScrollBarEnabled(true);
        chatScroll.setScrollbarFadingEnabled(false);

        chatContainer = new LinearLayout(service);
        chatContainer.setOrientation(LinearLayout.VERTICAL);
        chatContainer.setPadding(0, dp(8), 0, dp(8));
        chatScroll.addView(
                chatContainer,
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT
                )
        );
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        scrollParams.topMargin = dp(6);
        panel.addView(chatScroll, scrollParams);

        resultView = textView(13f, 0xffc9d5ce, false);
        resultView.setGravity(Gravity.START);
        resultView.setText("等待語音輸入");
        LinearLayout.LayoutParams resultParams = matchWrap();
        resultParams.topMargin = dp(6);
        panel.addView(resultView, resultParams);

        updateModeLabel();

        panelParams = baseOverlayParams(
                Math.min(Math.max(dp(240), screenWidth - dp(28)), dp(420)),
                Math.min(dp(390), Math.max(dp(300), screenHeight / 2)),
                "Amin Voice Chat UI Test",
                false
        );
        panelParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        panelParams.y = dp(46);
        windowManager.addView(panel, panelParams);
    }

    private void toggleListening() {
        mainHandler.removeCallbacks(resetBubbleTask);
        if (listening) {
            finishListening();
            return;
        }
        if (processing) {
            statusView.setText("UI 測試模式 · 等待回覆");
            resultView.setText("請稍候，測試回覆完成後再說下一句");
            return;
        }
        startListening();
    }

    private void startListening() {
        if (service.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            setPhase(FloatingVoicePresentation.Phase.ERROR);
            statusView.setText("需要麥克風權限");
            transcriptView.setText("尚未開始辨識");
            resultView.setText("請到 Amin 的「權限與裝置」開啟麥克風權限");
            Toast.makeText(service, "請先開啟麥克風權限", Toast.LENGTH_SHORT).show();
            scheduleFinishedState();
            return;
        }
        if (!prepareRecognizer()) return;

        listening = true;
        processing = false;
        ignoreNextError = false;
        setPhase(FloatingVoicePresentation.Phase.LISTENING);
        statusView.setText("UI 測試模式 · 正在聆聽");
        transcriptView.setText("正在聽你說話…");
        resultView.setText("再點一次語音球可停止並送出");
        try {
            mainHandler.removeCallbacks(listeningTimeoutTask);
            mainHandler.postDelayed(listeningTimeoutTask, LISTENING_IDLE_TIMEOUT_MS);
            speechRecognizer.startListening(recognizerIntent);
        } catch (RuntimeException error) {
            listening = false;
            setPhase(FloatingVoicePresentation.Phase.ERROR);
            statusView.setText("語音啟動失敗");
            resultView.setText(error.getMessage() == null ? "無法啟動語音辨識" : error.getMessage());
            scheduleFinishedState();
        }
    }

    private boolean prepareRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(service)) {
            setPhase(FloatingVoicePresentation.Phase.ERROR);
            statusView.setText("沒有可用的語音服務");
            transcriptView.setText("此裝置無法啟動辨識");
            resultView.setText("請確認 Google 語音服務或裝置語音服務已啟用");
            scheduleFinishedState();
            return false;
        }
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(service);
            speechRecognizer.setRecognitionListener(this);
            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            );
            recognizerIntent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    Locale.TAIWAN.toLanguageTag()
            );
            recognizerIntent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    Locale.TAIWAN.toLanguageTag()
            );
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        }
        return true;
    }

    private void finishListening() {
        if (speechRecognizer == null || !listening) return;
        listening = false;
        processing = true;
        setPhase(FloatingVoicePresentation.Phase.PROCESSING);
        statusView.setText("UI 測試模式 · 正在整理語音");
        resultView.setText("正在把語音轉成正式訊息…");
        speechRecognizer.stopListening();
    }

    private void stopRecognizer(boolean destroy) {
        mainHandler.removeCallbacks(resetBubbleTask);
        mainHandler.removeCallbacks(listeningTimeoutTask);
        if (speechRecognizer != null) {
            ignoreNextError = true;
            speechRecognizer.cancel();
            if (destroy) {
                speechRecognizer.destroy();
                speechRecognizer = null;
                recognizerIntent = null;
            }
        }
        listening = false;
        processing = false;
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
        statusView.setText("UI 測試模式 · 正在聆聽");
        transcriptView.setText("請開始說話");
    }

    @Override
    public void onBeginningOfSpeech() {
        mainHandler.removeCallbacks(listeningTimeoutTask);
        resultView.setText("已聽到聲音，正在即時轉成文字…");
    }

    @Override
    public void onRmsChanged(float rmsdB) { }

    @Override
    public void onBufferReceived(byte[] buffer) { }

    @Override
    public void onEndOfSpeech() {
        mainHandler.removeCallbacks(listeningTimeoutTask);
        listening = false;
        processing = true;
        setPhase(FloatingVoicePresentation.Phase.PROCESSING);
        statusView.setText("UI 測試模式 · 等待送出");
        resultView.setText("語音已結束，正在取得完整文字…");
    }

    @Override
    public void onError(int error) {
        mainHandler.removeCallbacks(listeningTimeoutTask);
        if (ignoreNextError) {
            ignoreNextError = false;
            return;
        }
        listening = false;
        processing = false;
        setPhase(FloatingVoicePresentation.Phase.ERROR);
        statusView.setText("語音辨識失敗");
        resultView.setText(errorMessage(error));
        scheduleFinishedState();
    }

    @Override
    public void onResults(Bundle results) {
        mainHandler.removeCallbacks(listeningTimeoutTask);
        listening = false;
        processing = false;
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        float[] confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
        if (matches == null || matches.isEmpty()) {
            setPhase(FloatingVoicePresentation.Phase.ERROR);
            statusView.setText("沒有辨識到文字");
            resultView.setText("請再試一次");
            scheduleFinishedState();
            return;
        }

        String transcript = matches.get(0).trim();
        transcriptView.setText(transcript);
        double confidence = confidences != null && confidences.length > 0 ? confidences[0] : -1d;
        VoiceCommandParser.Result parsed = parser.parse(transcript, confidence);

        if (parsed.getStatus() == VoiceCommandParser.Result.Status.MATCHED) {
            executeLegacyVoiceCommand(transcript, parsed);
            return;
        }

        runFakeChatReply(transcript);
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        mainHandler.removeCallbacks(listeningTimeoutTask);
        ArrayList<String> matches = partialResults.getStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION
        );
        if (matches == null || matches.isEmpty()) return;
        statusView.setText("UI 測試模式 · 即時語音轉文字");
        transcriptView.setText(matches.get(0));
        resultView.setText("持續接收中…");
    }

    @Override
    public void onEvent(int eventType, Bundle params) { }

    private void runFakeChatReply(String transcript) {
        if (transcript == null || transcript.trim().isEmpty()) {
            setPhase(FloatingVoicePresentation.Phase.ERROR);
            statusView.setText("沒有可送出的文字");
            scheduleFinishedState();
            return;
        }

        addChatMessage("你", transcript, true);
        processing = true;
        setPhase(FloatingVoicePresentation.Phase.PROCESSING);
        statusView.setText("UI 測試模式 · 等待回覆");
        transcriptView.setText(transcript);
        resultView.setText("模擬上位 LLM 回覆中…");

        mainHandler.postDelayed(() -> {
            addChatMessage("AI", "測試", false);
            processing = false;
            setPhase(FloatingVoicePresentation.Phase.SUCCESS);
            statusView.setText("UI 測試模式 · 回覆完成");
            resultView.setText("固定測試回覆已完成；下一階段才接 LLM API");
            speak("測試");
            scheduleFinishedState();
        }, FAKE_REPLY_DELAY_MS);
    }

    private void executeLegacyVoiceCommand(
            String transcript,
            VoiceCommandParser.Result parsed
    ) {
        AminAction parsedAction = parsed.getAction();
        AminAction voiceAction = new AminAction(
                parsedAction.getAction(),
                parsedAction.getParameters(),
                "floating_voice",
                parsedAction.getConfidence(),
                parsedAction.getRequestId(),
                parsedAction.getCreatedAt()
        );
        AminInputGateway gateway = AminInputGateway.get(service);
        statusView.setText("既有語音指令模式");
        transcriptView.setText(transcript);
        resultView.setText("正在執行既有指令…");
        setPhase(FloatingVoicePresentation.Phase.PROCESSING);
        processing = true;

        if ("VOICE_BUBBLE_CLOSE".equals(voiceAction.getAction())) {
            resultView.setText("已辨識關閉語音球指令");
            mainHandler.postDelayed(
                    () -> gateway.execute(voiceAction, ignored -> { }),
                    900L
            );
            return;
        }

        gateway.execute(voiceAction, result -> mainHandler.post(() -> {
            processing = false;
            boolean success = result.isSuccess();
            setPhase(
                    success
                            ? FloatingVoicePresentation.Phase.SUCCESS
                            : FloatingVoicePresentation.Phase.ERROR
            );
            statusView.setText(success ? "既有語音指令完成" : "既有語音指令失敗");
            resultView.setText(result.getMessage());
            scheduleFinishedState();
        }));
    }

    private void addChatMessage(String role, String text, boolean user) {
        if (chatContainer == null) return;
        TextView message = textView(14f, Color.WHITE, false);
        message.setText(role + "：" + text);
        message.setGravity(user ? Gravity.END : Gravity.START);
        message.setPadding(dp(10), dp(7), dp(10), dp(7));
        message.setBackground(roundedBackground(
                user ? 0xaa315647 : 0xaa26352f,
                12f,
                0x33ffffff,
                1
        ));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(5);
        chatContainer.addView(message, params);

        while (chatContainer.getChildCount() > MAX_ACTIVE_MESSAGES) {
            chatContainer.removeViewAt(0);
        }
        updateModeLabel();
        if (chatScroll != null) {
            chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void updateModeLabel() {
        if (modeView == null) return;
        int messages = chatContainer == null ? 0 : chatContainer.getChildCount();
        int turns = Math.min(MAX_ACTIVE_TURNS, messages / 2);
        modeView.setText("LLM：UI 測試模式 · Context " + turns + "/" + MAX_ACTIVE_TURNS);
    }

    private void ensureTts() {
        if (textToSpeech != null) return;
        textToSpeech = new TextToSpeech(service, status -> {
            if (status == TextToSpeech.SUCCESS && textToSpeech != null) {
                int result = textToSpeech.setLanguage(Locale.TAIWAN);
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED;
            } else {
                ttsReady = false;
            }
        });
    }

    private void speak(String text) {
        if (!ttsReady || textToSpeech == null || text == null || text.trim().isEmpty()) return;
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "amin-ui-test");
    }

    private void collapseListeningToIdle() {
        if (!listening || processing) return;
        stopRecognizer(false);
        setPhase(FloatingVoicePresentation.Phase.IDLE);
        statusView.setText("UI 測試模式 · 語音待命");
        resultView.setText("沒有持續收到語音，已回到待命");
    }

    private String errorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: return "麥克風音訊錯誤";
            case SpeechRecognizer.ERROR_CLIENT: return "語音服務已取消";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "沒有麥克風權限";
            case SpeechRecognizer.ERROR_NETWORK: return "語音服務網路錯誤";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "語音服務逾時";
            case SpeechRecognizer.ERROR_NO_MATCH: return "沒有辨識到符合的語句";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "語音服務忙碌，請稍後再試";
            case SpeechRecognizer.ERROR_SERVER: return "語音服務暫時無法使用";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "沒有聽到語音";
            default: return "語音辨識失敗（" + error + "）";
        }
    }

    private void setPhase(FloatingVoicePresentation.Phase phase) {
        if (bubble == null) return;
        bubble.setText(FloatingVoicePresentation.bubbleText(phase));
        switch (phase) {
            case LISTENING:
                bubble.setBackground(circleBackground(0xeece7a1b, Color.WHITE, 1));
                break;
            case PROCESSING:
                bubble.setBackground(circleBackground(0xee3b6fa1, Color.WHITE, 1));
                break;
            case SUCCESS:
                bubble.setBackground(circleBackground(0xee19794b, Color.WHITE, 1));
                break;
            case ERROR:
                bubble.setBackground(circleBackground(0xee9a3d25, Color.WHITE, 1));
                break;
            case IDLE:
            default:
                bubble.setBackground(circleBackground(0xe61f7a4d, Color.WHITE, 1));
                break;
        }
    }

    private void scheduleFinishedState() {
        mainHandler.removeCallbacks(resetBubbleTask);
        mainHandler.postDelayed(resetBubbleTask, BUBBLE_RESET_DELAY_MS);
    }

    private void snapBubbleToEdge() {
        if (bubbleParams == null) return;
        int size = bubbleParams.width;
        bubbleParams.x = bubbleParams.x + size / 2 < screenWidth / 2
                ? dp(4)
                : Math.max(dp(4), screenWidth - size - dp(4));
        clampBubbleCoordinates();
        updateViewLayoutSafely(bubble, bubbleParams);
        preferences().edit()
                .putInt(KEY_X, bubbleParams.x)
                .putInt(KEY_Y, bubbleParams.y)
                .apply();
    }

    private void clampBubbleCoordinates() {
        if (bubbleParams == null) return;
        int width = bubbleParams.width > 0 ? bubbleParams.width : dp(56);
        int height = bubbleParams.height > 0 ? bubbleParams.height : dp(56);
        bubbleParams.x = Math.max(0, Math.min(bubbleParams.x, Math.max(0, screenWidth - width)));
        bubbleParams.y = Math.max(
                dp(24),
                Math.min(bubbleParams.y, Math.max(dp(24), screenHeight - height - dp(24)))
        );
    }

    private WindowManager.LayoutParams baseOverlayParams(
            int width,
            int height,
            String title,
            boolean notTouchable
    ) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        flags |= notTouchable
                ? WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                : WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT
        );
        params.setTitle(title);
        return params;
    }

    private TextView textView(float sizeSp, int color, boolean bold) {
        TextView view = new TextView(service);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private GradientDrawable roundedBackground(
            int fill,
            float radiusDp,
            int stroke,
            int strokeDp
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
    }

    private GradientDrawable circleBackground(int fill, int stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
    }

    private SharedPreferences preferences() {
        return service.getSharedPreferences(PREFS, UniversalControlAccessibilityService.MODE_PRIVATE);
    }

    private void refreshScreenBounds() {
        if (windowManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect bounds = windowManager.getCurrentWindowMetrics().getBounds();
            screenWidth = bounds.width();
            screenHeight = bounds.height();
        } else {
            DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
        }
        if (screenWidth <= 0) {
            screenWidth = service.getResources().getDisplayMetrics().widthPixels;
        }
        if (screenHeight <= 0) {
            screenHeight = service.getResources().getDisplayMetrics().heightPixels;
        }
    }

    private void updateViewLayoutSafely(View view, WindowManager.LayoutParams params) {
        if (windowManager == null || view == null || params == null) return;
        try {
            windowManager.updateViewLayout(view, params);
        } catch (IllegalArgumentException ignored) {
            // The system may already be removing the overlay.
        }
    }

    private void removeViewSafely(View view) {
        if (windowManager == null || view == null) return;
        try {
            windowManager.removeView(view);
        } catch (IllegalArgumentException ignored) {
            // Already removed by the system.
        }
    }

    private int dp(float value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
    }
}
