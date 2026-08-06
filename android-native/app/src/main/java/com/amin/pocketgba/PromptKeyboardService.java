package com.amin.pocketgba;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
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

import androidx.core.content.ContextCompat;

import java.util.List;

public final class PromptKeyboardService extends InputMethodService {
    private PromptStore store;
    private LinearLayout root;
    private LinearLayout categoryRow;
    private LinearLayout promptList;
    private TextView status;
    private String selectedCategoryId = PromptStore.DEFAULT_CATEGORY_ID;
    private boolean sessionUnlocked;
    private boolean receiverRegistered;

    private final BroadcastReceiver unlockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!PromptUnlockActivity.ACTION_PROMPT_UNLOCKED.equals(intent.getAction())) return;
            sessionUnlocked = true;
            renderPromptHome();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        store = new PromptStore(this);
        IntentFilter filter = new IntentFilter(PromptUnlockActivity.ACTION_PROMPT_UNLOCKED);
        ContextCompat.registerReceiver(
                this,
                unlockReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
        receiverRegistered = true;
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
        root.removeAllViews();

        TextView title = text("Amin 提示詞已鎖定", 17f, true, 0xff16231b);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
        ));

        TextView message = text("使用手機原本的螢幕鎖驗證後即可開啟", 13f, false, 0xff68766e);
        message.setGravity(Gravity.CENTER);
        root.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
        ));

        Button unlock = new Button(this);
        unlock.setAllCaps(false);
        unlock.setText("使用手機鎖解鎖");
        unlock.setTextSize(16f);
        unlock.setTextColor(Color.WHITE);
        unlock.setBackgroundColor(0xff19794b);
        unlock.setOnClickListener(view -> launchSystemUnlock());
        LinearLayout.LayoutParams unlockParams = fullWidth();
        unlockParams.topMargin = dp(12);
        root.addView(unlock, unlockParams);

        Button switchKeyboard = compactButton("切換鍵盤");
        switchKeyboard.setOnClickListener(view -> switchKeyboard());
        LinearLayout.LayoutParams switchParams = fullWidth();
        switchParams.topMargin = dp(10);
        root.addView(switchKeyboard, switchParams);
    }

    private void launchSystemUnlock() {
        try {
            Intent intent = new Intent(this, PromptUnlockActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivity(intent);
        } catch (RuntimeException error) {
            Toast.makeText(this, "無法開啟手機驗證", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderPromptHome() {
        if (root == null) return;
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
        if (receiverRegistered) unregisterReceiver(unlockReceiver);
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
