package com.amin.pocketgba;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VoiceCommandCatalogActivity extends Activity {
    private static final int COLOR_BG = 0xfff4f7f5;
    private static final int COLOR_SURFACE = 0xffffffff;
    private static final int COLOR_TEXT = 0xff16231b;
    private static final int COLOR_MUTED = 0xff68766e;
    private static final int COLOR_ACCENT = 0xff19794b;
    private static final int COLOR_BORDER = 0xffd9e4de;
    private static final int COLOR_ACCESSIBILITY_BG = 0xfffff2d8;
    private static final int COLOR_ACCESSIBILITY_TEXT = 0xff8a5600;
    private static final int COLOR_DIRECT_BG = 0xffe5f4eb;
    private static final int COLOR_DIRECT_TEXT = 0xff16663f;

    private LinearLayout commandContainer;
    private TextView resultSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        configureWindow();
        buildUi();
    }

    private void configureWindow() {
        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().setNavigationBarColor(COLOR_BG);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(20));
        root.setBackgroundColor(COLOR_BG);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(topRow, fullWidth());

        Button back = new Button(this);
        back.setText("←");
        back.setTextSize(20f);
        back.setAllCaps(false);
        back.setContentDescription("返回語音指令");
        back.setOnClickListener(view -> finish());
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(52), dp(48));
        topRow.addView(back, backParams);

        TextView title = text("語音指令庫", 28f, true, COLOR_TEXT);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        titleParams.leftMargin = dp(10);
        topRow.addView(title, titleParams);

        LinearLayout summaryCard = new LinearLayout(this);
        summaryCard.setOrientation(LinearLayout.VERTICAL);
        summaryCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        summaryCard.setBackground(cardBackground(COLOR_SURFACE, COLOR_BORDER));
        LinearLayout.LayoutParams summaryParams = fullWidth();
        summaryParams.topMargin = dp(16);
        root.addView(summaryCard, summaryParams);

        summaryCard.addView(
                text(
                        VoiceCommandCatalog.getCommandCount() + " 個動作 · "
                                + VoiceCommandCatalog.getPhraseCount() + " 種可說法",
                        20f,
                        true,
                        COLOR_ACCENT
                ),
                fullWidth()
        );
        TextView syncNote = text(
                "這個畫面與語音辨識共用同一份指令資料。未來新增指令時，總數、分類與可說法會一起更新。",
                13f,
                false,
                COLOR_MUTED
        );
        LinearLayout.LayoutParams syncParams = fullWidth();
        syncParams.topMargin = dp(6);
        summaryCard.addView(syncNote, syncParams);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setTextSize(16f);
        search.setTextColor(COLOR_TEXT);
        search.setHintTextColor(0xff8a9690);
        search.setHint("搜尋，例如：首頁、向右、控制盤");
        search.setContentDescription("搜尋語音指令");
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setBackground(cardBackground(COLOR_SURFACE, COLOR_BORDER));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
        );
        searchParams.topMargin = dp(14);
        root.addView(search, searchParams);

        resultSummary = text("", 13f, false, COLOR_MUTED);
        LinearLayout.LayoutParams resultParams = fullWidth();
        resultParams.topMargin = dp(10);
        root.addView(resultSummary, resultParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        scrollParams.topMargin = dp(4);
        root.addView(scroll, scrollParams);

        commandContainer = new LinearLayout(this);
        commandContainer.setOrientation(LinearLayout.VERTICAL);
        commandContainer.setPadding(0, dp(4), 0, dp(24));
        scroll.addView(commandContainer, fullWidth());

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                renderCommands(value == null ? "" : value.toString());
            }
            @Override public void afterTextChanged(Editable value) { }
        });

        renderCommands("");
        setContentView(root);
    }

    private void renderCommands(String query) {
        commandContainer.removeAllViews();
        String normalizedQuery = VoiceCommandParser.normalize(query);
        Map<String, List<VoiceCommandCatalog.Command>> grouped = new LinkedHashMap<>();
        int matched = 0;

        for (VoiceCommandCatalog.Command command : VoiceCommandCatalog.getCommands()) {
            if (!matches(command, normalizedQuery)) continue;
            grouped.computeIfAbsent(command.getCategory(), ignored -> new ArrayList<>()).add(command);
            matched += 1;
        }

        resultSummary.setText(
                normalizedQuery.isEmpty()
                        ? "全部 " + matched + " 個語音動作"
                        : "找到 " + matched + " / " + VoiceCommandCatalog.getCommandCount() + " 個動作"
        );

        if (matched == 0) {
            TextView empty = text(
                    "找不到符合的指令。可以試著搜尋「控制盤」、「游標」、「首頁」或「遊戲」。",
                    15f,
                    false,
                    COLOR_MUTED
            );
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(36), dp(20), dp(36));
            commandContainer.addView(empty, fullWidth());
            return;
        }

        for (Map.Entry<String, List<VoiceCommandCatalog.Command>> entry : grouped.entrySet()) {
            TextView category = text(
                    entry.getKey() + " · " + entry.getValue().size(),
                    16f,
                    true,
                    COLOR_TEXT
            );
            LinearLayout.LayoutParams categoryParams = fullWidth();
            categoryParams.topMargin = dp(18);
            categoryParams.bottomMargin = dp(8);
            commandContainer.addView(category, categoryParams);

            for (VoiceCommandCatalog.Command command : entry.getValue()) {
                commandContainer.addView(commandCard(command), cardParams());
            }
        }
    }

    private boolean matches(VoiceCommandCatalog.Command command, String normalizedQuery) {
        if (normalizedQuery.isEmpty()) return true;
        StringBuilder searchable = new StringBuilder()
                .append(command.getCategory()).append(' ')
                .append(command.getTitle()).append(' ')
                .append(command.getDescription()).append(' ')
                .append(command.getAction()).append(' ');
        for (String phrase : command.getPhrases()) searchable.append(phrase).append(' ');
        return VoiceCommandParser.normalize(searchable.toString()).contains(normalizedQuery);
    }

    private LinearLayout commandCard(VoiceCommandCatalog.Command command) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(cardBackground(COLOR_SURFACE, COLOR_BORDER));
        card.setContentDescription("語音指令：" + command.getPrimaryPhrase());

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(heading, fullWidth());

        TextView title = text(command.getTitle(), 17f, true, COLOR_TEXT);
        heading.addView(
                title,
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        );

        TextView badge = text(
                command.requiresAccessibility() ? "需全域控制" : "可直接使用",
                11f,
                true,
                command.requiresAccessibility() ? COLOR_ACCESSIBILITY_TEXT : COLOR_DIRECT_TEXT
        );
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(9), dp(5), dp(9), dp(5));
        badge.setBackground(cardBackground(
                command.requiresAccessibility() ? COLOR_ACCESSIBILITY_BG : COLOR_DIRECT_BG,
                command.requiresAccessibility() ? 0xffffd998 : 0xffbfe4cd
        ));
        heading.addView(badge, wrapContent());

        TextView primary = text("你可以說：「" + command.getPrimaryPhrase() + "」", 16f, true, COLOR_ACCENT);
        LinearLayout.LayoutParams primaryParams = fullWidth();
        primaryParams.topMargin = dp(10);
        card.addView(primary, primaryParams);

        if (command.getPhrases().size() > 1) {
            List<String> alternatives = command.getPhrases().subList(1, command.getPhrases().size());
            TextView aliases = text("也可以說：" + join(alternatives), 13f, false, COLOR_MUTED);
            LinearLayout.LayoutParams aliasesParams = fullWidth();
            aliasesParams.topMargin = dp(5);
            card.addView(aliases, aliasesParams);
        }

        TextView description = text(command.getDescription(), 13f, false, COLOR_MUTED);
        LinearLayout.LayoutParams descriptionParams = fullWidth();
        descriptionParams.topMargin = dp(7);
        card.addView(description, descriptionParams);
        return card;
    }

    private String join(List<String> values) {
        StringBuilder joined = new StringBuilder();
        for (int index = 0; index < values.size(); index += 1) {
            if (index > 0) joined.append("、");
            joined.append(values.get(index));
        }
        return joined.toString();
    }

    private TextView text(String value, float size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable cardBackground(int fillColor, int borderColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(16));
        drawable.setStroke(dp(1), borderColor);
        return drawable;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams wrapContent() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = fullWidth();
        params.bottomMargin = dp(10);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
