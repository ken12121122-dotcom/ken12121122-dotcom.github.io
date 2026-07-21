package com.amin.pocketgba;

import android.Manifest;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

final class FloatingVoiceController implements RecognitionListener {
    private static final String PREFS = "amin_floating_voice";
    private static final String KEY_X = "voice_bubble_x";
    private static final String KEY_Y = "voice_bubble_y";
    private static final long PANEL_HIDE_DELAY_MS = 5000L;
    private static final long BUBBLE_RESET_DELAY_MS = 1400L;

    private final UniversalControlAccessibilityService service;
    private final WindowManager windowManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final VoiceCommandParser parser = new VoiceCommandParser();
    private final Runnable hidePanelTask = this::hidePanel;
    private final Runnable resetBubbleTask = () -> setPhase(FloatingVoicePresentation.Phase.IDLE);

    private TextView bubble;
    private WindowManager.LayoutParams bubbleParams;
    private LinearLayout panel;
    private WindowManager.LayoutParams panelParams;
    private TextView statusView;
    private TextView transcriptView;
    private TextView resultView;

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
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
        statusView = null;
        transcriptView = null;
        resultView = null;
        bubble = null;
        bubbleParams = null;
        mainHandler.removeCallbacks(hidePanelTask);
        mainHandler.removeCallbacks(resetBubbleTask);
    }

    void destroy() {
        hide();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        recognizerIntent = null;
    }

    void onConfigurationChanged() {
        refreshScreenBounds();
        if (bubbleParams != null) {
            clampBubbleCoordinates();
            updateViewLayoutSafely(bubble, bubbleParams);
        }
        if (panelParams != null) {
            panelParams.width = Math.min(Math.max(dp(220), screenWidth - dp(32)), dp(420));
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
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(18), dp(14), dp(18), dp(14));
        panel.setBackground(roundedBackground(0xee17231d, 16f, 0x99ffffff, 1));
        panel.setVisibility(View.GONE);

        statusView = textView(18f, Color.WHITE, true);
        statusView.setText("語音待命");
        panel.addView(statusView, matchWrap());

        transcriptView = textView(16f, 0xfff5f8f6, false);
        transcriptView.setText(FloatingVoicePresentation.heardText(""));
        LinearLayout.LayoutParams transcriptParams = matchWrap();
        transcriptParams.topMargin = dp(8);
        panel.addView(transcriptView, transcriptParams);

        resultView = textView(14f, 0xffc9d5ce, false);
        resultView.setText(FloatingVoicePresentation.resultText(""));
        LinearLayout.LayoutParams resultParams = matchWrap();
        resultParams.topMargin = dp(6);
        panel.addView(resultView, resultParams);

        panelParams = baseOverlayParams(
                Math.min(Math.max(dp(220), screenWidth - dp(32)), dp(420)),
                WindowManager.LayoutParams.WRAP_CONTENT,
                "Amin Floating Voice Status",
                true
        );
        panelParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        panelParams.y = dp(54);
        windowManager.addView(panel, panelParams);
    }

    private void toggleListening() {
        mainHandler.removeCallbacks(hidePanelTask);
        mainHandler.removeCallbacks(resetBubbleTask);
        if (listening) {
            finishListening();
            return;
        }
        if (processing) {
            showPanel(
                    "正在辨識",
                    transcriptView == null ? "正在整理語音內容…" : transcriptView.getText().toString(),
                    "請稍候，完成後可以再點一次",
                    true
            );
            return;
        }
        startListening();
    }

    private void startListening() {
        if (service.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            setPhase(FloatingVoicePresentation.Phase.ERROR);
            showPanel(
                    "需要麥克風權限",
                    "尚未開始辨識",
                    "請到 Amin 的「權限與裝置」開啟麥克風權限",
                    false
            );
            Toast.makeText(service, "請先開啟麥克風權限", Toast.LENGTH_SHORT).show();
            scheduleFinishedState();
            return;
        }
        if (!prepareRecognizer()) return;

        listening = true;
        processing = false;
        ignoreNextError = false;
        setPhase(FloatingVoicePresentation.Phase.LISTENING);
        showPanel(
                "辨識中",
                "正在聽你說話…",
                "再點一次語音按鈕即可停止並辨識",
                true
        );
        try {
            speechRecognizer.startListening(recognizerIntent);
        } catch (RuntimeException error) {
            listening = false;
            setPhase(FloatingVoicePresentation.Phase.ERROR);
            showPanel("語音啟動失敗", "沒有開始錄音", error.getMessage(), false);
            scheduleFinishedState();
        }
    }

    private boolean prepareRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(service)) {
            setPhase(FloatingVoicePresentation.Phase.ERROR);
            showPanel(
                    "沒有可用的語音服務",
                    "此裝置無法啟動辨識",
                    "請確認 Google 語音服務或裝置語音服務已啟用",
                    false
            );
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
        showPanel(
                "正在辨識",
                transcriptView == null ? "已停止收音" : transcriptView.getText().toString(),
                "正在整理語音內容…",
                true
        );
        speechRecognizer.stopListening();
    }

    private void stopRecognizer(boolean destroy) {
        mainHandler.removeCallbacks(hidePanelTask);
        mainHandler.removeCallbacks(resetBubbleTask);
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
        showPanel("辨識中", "請開始說話", "再點一次即可停止", true);
    }

    @Override
    public void onBeginningOfSpeech() {
        showPanel("辨識中", "已聽到聲音，請繼續說", "正在接收語音…", true);
    }

    @Override
    public void onRmsChanged(float rmsdB) { }

    @Override
    public void onBufferReceived(byte[] buffer) { }

    @Override
    public void onEndOfSpeech() {
        listening = false;
        processing = true;
        setPhase(FloatingVoicePresentation.Phase.PROCESSING);
        showPanel(
                "正在辨識",
                transcriptView == null ? "收音完成" : transcriptView.getText().toString(),
                "正在整理語音內容…",
                true
        );
    }

    @Override
    public void onError(int error) {
        if (ignoreNextError) {
            ignoreNextError = false;
            return;
        }
        listening = false;
        processing = false;
        setPhase(FloatingVoicePresentation.Phase.ERROR);
        showPanel(
                "辨識失敗",
                transcriptView == null ? "沒有取得完整文字" : transcriptView.getText().toString(),
                errorMessage(error),
                false
        );
        scheduleFinishedState();
    }

    @Override
    public void onResults(Bundle results) {
        listening = false;
        processing = false;
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        float[] confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
        if (matches == null || matches.isEmpty()) {
            setPhase(FloatingVoicePresentation.Phase.ERROR);
            showPanel("沒有辨識到指令", "沒有取得文字", "未執行任何動作", false);
            scheduleFinishedState();
            return;
        }

        String transcript = matches.get(0);
        double confidence = confidences != null && confidences.length > 0 ? confidences[0] : -1d;
        VoiceCommandParser.Result parsed = parser.parse(transcript, confidence);
        if (parsed.getStatus() != VoiceCommandParser.Result.Status.MATCHED) {
            setPhase(FloatingVoicePresentation.Phase.ERROR);
            showPanel(
                    "未找到可執行指令",
                    FloatingVoicePresentation.heardText(transcript),
                    FloatingVoicePresentation.resultText(parsed.getMessage()),
                    false
            );
            scheduleFinishedState();
            return;
        }

        ExecutionResult result = execute(parsed.getAction());
        setPhase(
                result.success
                        ? FloatingVoicePresentation.Phase.SUCCESS
                        : FloatingVoicePresentation.Phase.ERROR
        );
        showPanel(
                result.success ? "辨識完成" : "指令未執行",
                FloatingVoicePresentation.heardText(transcript),
                FloatingVoicePresentation.resultText(result.message),
                result.success
        );
        scheduleFinishedState();
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        ArrayList<String> matches = partialResults.getStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION
        );
        if (matches == null || matches.isEmpty()) return;
        showPanel(
                "辨識中",
                FloatingVoicePresentation.heardText(matches.get(0)),
                "正在接收語音…",
                true
        );
    }

    @Override
    public void onEvent(int eventType, Bundle params) { }

    private ExecutionResult execute(AminAction action) {
        if (action == null) return ExecutionResult.fail("缺少可執行的指令");
        switch (action.getAction()) {
            case "OPEN_GBA":
                return launchActivity(new Intent(service, MainActivity.class))
                        ? ExecutionResult.ok("已開啟 GBA 遊戲庫")
                        : ExecutionResult.fail("無法開啟 GBA 遊戲庫");
            case "OPEN_CONTROLLER_SETTINGS":
                Intent controller = new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://ken12121122-dotcom.github.io/amin-vault/gba-controller.html")
                );
                return launchActivity(controller)
                        ? ExecutionResult.ok("已開啟控制器設定")
                        : ExecutionResult.fail("無法開啟控制器設定");
            case "OVERLAY_OPEN":
                return service.showControlsFromVoice()
                        ? ExecutionResult.ok("已開啟鍵盤浮動按鈕與控制盤")
                        : ExecutionResult.fail("無法開啟鍵盤控制");
            case "OVERLAY_CLOSE":
                return service.hideControlsFromVoice()
                        ? ExecutionResult.ok("已關閉控制盤與鍵盤浮動按鈕")
                        : ExecutionResult.fail("無法關閉鍵盤控制");
            case "VOICE_BUBBLE_OPEN":
                return service.showVoiceBubbleFromVoice()
                        ? ExecutionResult.ok("已開啟語音浮動按鈕")
                        : ExecutionResult.fail("無法開啟語音浮動按鈕");
            case "VOICE_BUBBLE_CLOSE":
                return service.hideVoiceBubbleFromVoice()
                        ? ExecutionResult.ok("已關閉語音浮動按鈕")
                        : ExecutionResult.fail("無法關閉語音浮動按鈕");
            case "CONTROL_MODE_SET":
                String mode = action.getParameters().optString(
                        "mode",
                        UniversalControlAccessibilityService.MODE_CURSOR
                );
                UniversalControlAccessibilityService.setControlMode(service, mode);
                return ExecutionResult.ok(
                        UniversalControlAccessibilityService.MODE_SCROLL.equals(mode)
                                ? "已切換捲動模式"
                                : "已切換游標模式"
                );
            case "VOICE_STOP":
                return ExecutionResult.ok("已停止聆聽");
            default:
                return UniversalControlAccessibilityService.executeAminAction(action)
                        ? ExecutionResult.ok(successMessage(action.getAction()))
                        : ExecutionResult.fail("請先啟用 Amin 全域控制服務");
        }
    }

    private boolean launchActivity(Intent intent) {
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );
        int flags = PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        int requestCode = 7200 + Math.abs(intent.filterHashCode() % 500);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions creatorOptions = ActivityOptions.makeBasic();
                creatorOptions.setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                );
                PendingIntent pendingIntent = PendingIntent.getActivity(
                        service,
                        requestCode,
                        intent,
                        flags,
                        creatorOptions.toBundle()
                );
                ActivityOptions senderOptions = ActivityOptions.makeBasic();
                senderOptions.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                );
                pendingIntent.send(senderOptions.toBundle());
            } else {
                PendingIntent pendingIntent = PendingIntent.getActivity(
                        service,
                        requestCode,
                        intent,
                        flags
                );
                pendingIntent.send();
            }
            return true;
        } catch (PendingIntent.CanceledException | RuntimeException error) {
            return false;
        }
    }

    private String successMessage(String action) {
        switch (action) {
            case "SYSTEM_BACK": return "已執行全域返回";
            case "SYSTEM_HOME": return "已回到首頁";
            case "CURSOR_TAP": return "已點擊游標位置";
            case "CURSOR_LONG_PRESS": return "已長按游標位置";
            case "DIRECTION_UP": return "已向上執行";
            case "DIRECTION_DOWN": return "已向下執行";
            case "DIRECTION_LEFT": return "已向左執行";
            case "DIRECTION_RIGHT": return "已向右執行";
            default: return "已執行語音指令";
        }
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

    private void showPanel(
            String status,
            String transcript,
            String result,
            boolean success
    ) {
        if (panel == null) return;
        mainHandler.removeCallbacks(hidePanelTask);
        statusView.setText(status == null ? "語音狀態" : status);
        statusView.setTextColor(success ? Color.WHITE : 0xffffc2ad);
        transcriptView.setText(transcript == null ? "" : transcript);
        resultView.setText(result == null ? "" : result);
        panel.setVisibility(View.VISIBLE);
    }

    private void hidePanel() {
        if (panel != null && !listening && !processing) panel.setVisibility(View.GONE);
    }

    private void scheduleFinishedState() {
        mainHandler.removeCallbacks(resetBubbleTask);
        mainHandler.removeCallbacks(hidePanelTask);
        mainHandler.postDelayed(resetBubbleTask, BUBBLE_RESET_DELAY_MS);
        mainHandler.postDelayed(hidePanelTask, PANEL_HIDE_DELAY_MS);
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
        view.setGravity(Gravity.CENTER);
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

    private static final class ExecutionResult {
        private final boolean success;
        private final String message;

        private ExecutionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        private static ExecutionResult ok(String message) {
            return new ExecutionResult(true, message);
        }

        private static ExecutionResult fail(String message) {
            return new ExecutionResult(false, message);
        }
    }
}
