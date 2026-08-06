package com.amin.pocketgba;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.security.GeneralSecurityException;
import java.util.List;

public final class PromptKeyboardService extends InputMethodService {
    private PromptStore store;
    private PromptAccessLock accessLock;
    private LinearLayout root;
    private LinearLayout categoryRow;
    private LinearLayout promptList;
    private TextView status;
    private TextView pinDisplay;
    private TextView lockMessage;
    private String selectedCategoryId = PromptStore.DEFAULT_CATEGORY_ID;
    private final StringBuilder enteredPin = new StringBuilder();
    private char[] pendingSetupPin;
    private boolean sessionUnlocked;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable countdownRunnable = new Runnable() {
        @Override
        public void run() {
            if (sessionUnlocked || root == null) return;
            long remaining = accessLock.getRemainingDelayMillis(System.currentTimeMillis());
            if (remaining <= 0L) {
                renderLockScreen();
                return;
            }
            updateLockMessage(remaining);
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        store = new PromptStore(this);
        accessLock = new PromptAccessLock(this);
    }

    @Override
    public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(8), dp(10), dp(8));
        root.setBackgroundColor(0xfff4f7f5);
        renderCurrentState();
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (root != null) renderCurrentState();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        lockSession();
        super.onFinishInputView(finishingInput);
    }

    private void renderCurrentState() {
        if (root == null) return;
        if (sessionUnlocked) renderPromptHome();
        else renderLockScreen();
    }

    private void renderLockScreen() {
        handler.removeCallbacks(countdownRunnable);
        enteredPin.setLength(0);
        root.removeAllViews();

        TextView title = text(accessLock.isConfigured() ? "解鎖 Amin 提示詞" : "建立 Amin 提示詞 PIN", 17f, true, 0xff16231b);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
        ));

        pinDisplay = text("○ ○ ○ ○ ○ ○", 22f, true, 0xff105f39);
        pinDisplay.setGravity(Gravity.CENTER);
        root.addView(pinDisplay, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(40)
        ));

        lockMessage = text(initialLockMessage(), 12f, false, 0xff68766e);
        lockMessage.setGravity(Gravity.CENTER);
        root.addView(lockMessage, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(30)
        ));

        long remaining = accessLock.getRemainingDelayMillis(System.currentTimeMillis());
        boolean blocked = remaining > 0L;
        LinearLayout keypad = new LinearLayout(this);
        keypad.setOrientation(LinearLayout.VERTICAL);
        for (int row = 0; row < 4; row++) {
            LinearLayout rowView = new LinearLayout(this);
            rowView.setOrientation(LinearLayout.HORIZONTAL);
            rowView.setGravity(Gravity.CENTER);
            if (row < 3) {
                for (int column = 0; column < 3; column++) {
                    int digit = row * 3 + column + 1;
                    rowView.addView(pinButton(String.valueOf(digit), blocked), weightedKey());
                }
            } else {
                rowView.addView(actionButton("清除", blocked, view -> clearEnteredPin()), weightedKey());
                rowView.addView(pinButton("0", blocked), weightedKey());
                rowView.addView(actionButton("⌫", blocked, view -> removeLastDigit()), weightedKey());
            }
            keypad.addView(rowView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(48)
            ));
        }
        root.addView(keypad, fullWidth());

        Button switchKeyboard = compactButton("切換鍵盤");
        switchKeyboard.setOnClickListener(view -> switchKeyboard());
        LinearLayout.LayoutParams switchParams = fullWidth();
        switchParams.topMargin = dp(4);
        root.addView(switchKeyboard, switchParams);

        if (blocked) {
            updateLockMessage(remaining);
            handler.postDelayed(countdownRunnable, 1000L);
        }
    }

    private String initialLockMessage() {
        if (!accessLock.isConfigured()) {
            return pendingSetupPin == null ? "請設定 6 位數 PIN" : "請再次輸入相同 PIN";
        }
        return "請輸入 6 位數 PIN";
    }

    private Button pinButton(String digit, boolean disabled) {
        return actionButton(digit, disabled, view -> appendDigit(digit.charAt(0)));
    }

    private Button actionButton(String label, boolean disabled, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(18f);
        button.setTextColor(disabled ? 0xffaeb8b2 : 0xff105f39);
        button.setEnabled(!disabled);
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout.LayoutParams weightedKey() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1f);
        params.leftMargin = dp(2);
        params.rightMargin = dp(2);
        return params;
    }

    private void appendDigit(char digit) {
        if (enteredPin.length() >= accessLock.getPinLength()) return;
        enteredPin.append(digit);
        updatePinDisplay();
        if (enteredPin.length() == accessLock.getPinLength()) submitPin();
    }

    private void removeLastDigit() {
        if (enteredPin.length() > 0) enteredPin.deleteCharAt(enteredPin.length() - 1);
        updatePinDisplay();
    }

    private void clearEnteredPin() {
        enteredPin.setLength(0);
        updatePinDisplay();
    }

    private void updatePinDisplay() {
        if (pinDisplay == null) return;
        StringBuilder display = new StringBuilder();
        for (int index = 0; index < accessLock.getPinLength(); index++) {
            if (index > 0) display.append(' ');
            display.append(index < enteredPin.length() ? '●' : '○');
        }
        pinDisplay.setText(display.toString());
    }

    private void submitPin() {
        char[] pin = enteredPin.toString().toCharArray();
        enteredPin.setLength(0);
        updatePinDisplay();
        try {
            if (!accessLock.isConfigured()) {
                handlePinSetup(pin);
                return;
            }
            PromptAccessLock.VerifyResult result = accessLock.verify(pin, System.currentTimeMillis());
            if (result.success) {
                sessionUnlocked = true;
                renderPromptHome();
            } else {
                long delay = result.retryAfterMillis;
                lockMessage.setText("PIN 錯誤；第 " + result.failureCount + " 次失敗，" + formatDelay(delay) + "後可再試");
                renderLockScreen();
            }
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            Toast.makeText(this, "無法驗證 PIN，請稍後再試", Toast.LENGTH_SHORT).show();
        } finally {
            PromptAccessLock.clear(pin);
        }
    }

    private void handlePinSetup(char[] pin) throws GeneralSecurityException {
        if (pendingSetupPin == null) {
            pendingSetupPin = pin.clone();
            lockMessage.setText("請再次輸入相同 PIN");
            return;
        }
        boolean matches = java.security.MessageDigest.isEqual(
                new String(pendingSetupPin).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                new String(pin).getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        if (!matches) {
            PromptAccessLock.clear(pendingSetupPin);
            pendingSetupPin = null;
            lockMessage.setText("兩次 PIN 不一致，請重新設定");
            return;
        }
        accessLock.configure(pin);
        PromptAccessLock.clear(pendingSetupPin);
        pendingSetupPin = null;
        sessionUnlocked = true;
        Toast.makeText(this, "PIN 已建立", Toast.LENGTH_SHORT).show();
        renderPromptHome();
    }

    private void updateLockMessage(long remainingMillis) {
        if (lockMessage != null) lockMessage.setText("嘗試次數過多，" + formatDelay(remainingMillis) + "後可再試");
    }

    private String formatDelay(long millis) {
        long seconds = Math.max(1L, (millis + 999L) / 1000L);
        if (seconds < 60L) return seconds + " 秒";
        long minutes = (seconds + 59L) / 60L;
        return minutes + " 分鐘";
    }

    private void renderPromptHome() {
        handler.removeCallbacks(countdownRunnable);
        root.removeAllViews();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("Amin 提示詞", 16f, true, 0xff16231b);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1f));

        Button lock = compactButton("鎖定");
        lock.setOnClickListener(view -> {
            lockSession();
            renderLockScreen();
        });
        header.addView(lock);

        Button refresh = compactButton("重新整理");
        refresh.setOnClickListener(view -> reload());
        header.addView(refresh);

        Button switchKeyboard = compactButton("切換鍵盤");
        switchKeyboard.setOnClickListener(view -> switchKeyboard());
        header.addView(switchKeyboard);
        root.addView(header, fullWidth());

        HorizontalScrollView categoryScroll = new HorizontalScrollView(this);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        categoryRow = new LinearLayout(this);
        categoryRow.setOrientation(LinearLayout.HORIZONTAL);
        categoryScroll.addView(categoryRow);
        root.addView(categoryScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        ));

        status = text("讀取提示詞…", 12f, false, 0xff68766e);
        root.addView(status, fullWidth());

        ScrollView promptScroll = new ScrollView(this);
        promptList = new LinearLayout(this);
        promptList.setOrientation(LinearLayout.VERTICAL);
        promptScroll.addView(promptList);
        root.addView(promptScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(190)
        ));
        reload();
    }

    private void reload() {
        if (!sessionUnlocked || categoryRow == null || promptList == null) return;
        List<PromptStore.Category> categories = store.listCategories();
        boolean selectedExists = false;
        for (PromptStore.Category category : categories) {
            if (category.id.equals(selectedCategoryId)) selectedExists = true;
        }
        if (!selectedExists && !categories.isEmpty()) selectedCategoryId = categories.get(0).id;

        categoryRow.removeAllViews();
        for (PromptStore.Category category : categories) {
            Button button = compactButton(category.name);
            boolean selected = category.id.equals(selectedCategoryId);
            button.setTextColor(selected ? Color.WHITE : 0xff105f39);
            button.setBackgroundColor(selected ? 0xff19794b : 0xffeaf3ee);
            button.setOnClickListener(view -> {
                selectedCategoryId = category.id;
                reload();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(42)
            );
            params.rightMargin = dp(6);
            categoryRow.addView(button, params);
        }
        renderPrompts();
    }

    private void renderPrompts() {
        promptList.removeAllViews();
        if (isPasswordField(getCurrentInputEditorInfo())) {
            status.setText("密碼欄位不顯示提示詞");
            return;
        }
        List<PromptStore.Prompt> prompts = store.listPrompts(selectedCategoryId);
        status.setText(prompts.isEmpty() ? "這個分類還沒有提示詞" : "共 " + prompts.size() + " 筆");
        for (PromptStore.Prompt prompt : prompts) {
            Button button = new Button(this);
            button.setAllCaps(false);
            button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            button.setText(PromptText.preview(prompt.content, 90));
            button.setTextColor(0xff16231b);
            button.setTextSize(14f);
            button.setBackgroundColor(Color.WHITE);
            button.setOnClickListener(view -> commit(prompt.content));
            LinearLayout.LayoutParams params = fullWidth();
            params.topMargin = dp(5);
            promptList.addView(button, params);
        }
    }

    private void commit(String content) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            Toast.makeText(this, "目前沒有可輸入的文字框", Toast.LENGTH_SHORT).show();
            return;
        }
        connection.commitText(content, 1);
    }

    private void lockSession() {
        sessionUnlocked = false;
        enteredPin.setLength(0);
        if (pendingSetupPin != null) {
            PromptAccessLock.clear(pendingSetupPin);
            pendingSetupPin = null;
        }
        handler.removeCallbacks(countdownRunnable);
    }

    private void switchKeyboard() {
        lockSession();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && switchToNextInputMethod(false)) return;
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) manager.showInputMethodPicker();
    }

    private boolean isPasswordField(EditorInfo info) {
        if (info == null) return false;
        int inputClass = info.inputType & InputType.TYPE_MASK_CLASS;
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        if (inputClass == InputType.TYPE_CLASS_NUMBER) {
            return variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        }
        if (inputClass != InputType.TYPE_CLASS_TEXT) return false;
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
    }

    @Override
    public void onDestroy() {
        lockSession();
        if (store != null) store.close();
        super.onDestroy();
    }

    private Button compactButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(12f);
        button.setTextColor(0xff105f39);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(10), dp(5), dp(10), dp(5));
        return button;
    }

    private TextView text(String value, float size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
