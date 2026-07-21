package com.amin.pocketgba;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class VoiceCommandCatalog {
    public static final class Command {
        private final String id;
        private final String action;
        private final String mode;
        private final String category;
        private final String title;
        private final String description;
        private final boolean requiresAccessibility;
        private final List<String> phrases;

        private Command(
                String id,
                String action,
                String mode,
                String category,
                String title,
                String description,
                boolean requiresAccessibility,
                String... phrases
        ) {
            if (phrases == null || phrases.length == 0) {
                throw new IllegalArgumentException("Voice command requires at least one spoken phrase: " + id);
            }
            this.id = id;
            this.action = action;
            this.mode = mode;
            this.category = category;
            this.title = title;
            this.description = description;
            this.requiresAccessibility = requiresAccessibility;
            this.phrases = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(phrases)));
        }

        public String getId() { return id; }
        public String getAction() { return action; }
        public String getMode() { return mode; }
        public String getCategory() { return category; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public boolean requiresAccessibility() { return requiresAccessibility; }
        public List<String> getPhrases() { return phrases; }
        public String getPrimaryPhrase() { return phrases.get(0); }

        public AminAction createAction(double confidence) {
            JSONObject parameters = new JSONObject();
            if (mode != null) {
                try {
                    parameters.put("mode", mode);
                } catch (JSONException error) {
                    throw new IllegalStateException("Unable to build voice command parameters", error);
                }
            }
            return new AminAction(action, parameters, "voice", confidence);
        }
    }

    private static final List<Command> COMMANDS;

    static {
        List<Command> commands = new ArrayList<>();
        commands.add(command(
                "overlay_open", "OVERLAY_OPEN", null,
                "浮動按鈕", "開啟鍵盤控制", "顯示鍵盤浮動按鈕並展開 Amin 控制盤。", true,
                "開啟控制盤", "打開控制盤", "顯示控制盤",
                "開啟鍵盤浮動按鈕", "開啟鍵盤浮球", "顯示鍵盤浮球"
        ));
        commands.add(command(
                "overlay_close", "OVERLAY_CLOSE", null,
                "浮動按鈕", "關閉鍵盤控制", "收起控制盤並關閉鍵盤浮動按鈕。", true,
                "關閉控制盤", "收起控制盤", "隱藏控制盤",
                "關閉鍵盤浮動按鈕", "關閉鍵盤浮球", "隱藏鍵盤浮球"
        ));
        commands.add(command(
                "voice_bubble_open", "VOICE_BUBBLE_OPEN", null,
                "浮動按鈕", "開啟語音浮動按鈕", "顯示可點擊的 Amin 語音浮動按鈕。", true,
                "開啟語音按鈕", "開啟語音浮動按鈕", "開啟語音浮球", "顯示語音浮球"
        ));
        commands.add(command(
                "voice_bubble_close", "VOICE_BUBBLE_CLOSE", null,
                "浮動按鈕", "關閉語音浮動按鈕", "停止目前語音流程並關閉語音浮動按鈕。", true,
                "關閉語音按鈕", "關閉語音浮動按鈕", "關閉語音浮球", "隱藏語音浮球"
        ));
        commands.add(command(
                "mode_cursor", "CONTROL_MODE_SET", UniversalControlAccessibilityService.MODE_CURSOR,
                "控制模式", "游標模式", "方向指令改為移動游標。", true,
                "游標模式", "切換游標模式"
        ));
        commands.add(command(
                "mode_scroll", "CONTROL_MODE_SET", UniversalControlAccessibilityService.MODE_SCROLL,
                "控制模式", "捲動模式", "方向指令改為捲動畫面。", true,
                "捲動模式", "滾動模式", "切換捲動模式"
        ));
        commands.add(command(
                "system_back", "SYSTEM_BACK", null,
                "系統", "返回上一頁", "執行 Android 全域返回。", true,
                "返回", "回上一頁", "上一頁"
        ));
        commands.add(command(
                "system_home", "SYSTEM_HOME", null,
                "系統", "回到首頁", "回到 Android 手機桌面。", false,
                "回首頁", "回到首頁", "回桌面", "回到桌面"
        ));
        commands.add(command(
                "cursor_tap", "CURSOR_TAP", null,
                "游標與捲動", "點擊游標位置", "點擊目前游標所在位置。", true,
                "點一下", "點擊", "按一下"
        ));
        commands.add(command(
                "cursor_long_press", "CURSOR_LONG_PRESS", null,
                "游標與捲動", "長按游標位置", "長按目前游標所在位置。", true,
                "長按", "按住"
        ));
        commands.add(command(
                "direction_up", "DIRECTION_UP", null,
                "游標與捲動", "向上", "依目前模式向上移動游標或捲動畫面。", true,
                "往上", "向上"
        ));
        commands.add(command(
                "direction_down", "DIRECTION_DOWN", null,
                "游標與捲動", "向下", "依目前模式向下移動游標或捲動畫面。", true,
                "往下", "向下"
        ));
        commands.add(command(
                "direction_left", "DIRECTION_LEFT", null,
                "游標與捲動", "向左", "依目前模式向左移動游標或切換頁面。", true,
                "往左", "向左"
        ));
        commands.add(command(
                "direction_right", "DIRECTION_RIGHT", null,
                "游標與捲動", "向右", "依目前模式向右移動游標或切換頁面。", true,
                "往右", "向右"
        ));
        commands.add(command(
                "open_gba", "OPEN_GBA", null,
                "遊戲與設定", "開啟遊戲庫", "開啟 Amin GBA 遊戲庫。", false,
                "開啟遊戲", "打開遊戲", "開啟遊戲庫", "打開遊戲庫"
        ));
        commands.add(command(
                "open_controller_settings", "OPEN_CONTROLLER_SETTINGS", null,
                "遊戲與設定", "開啟控制器設定", "開啟手把與控制器設定頁。", false,
                "開啟控制器設定", "控制器設定", "打開控制器設定"
        ));
        commands.add(command(
                "voice_stop", "VOICE_STOP", null,
                "語音", "停止語音", "結束目前語音指令流程，但保留語音浮動按鈕。", false,
                "停止聆聽", "停止語音"
        ));
        COMMANDS = Collections.unmodifiableList(commands);
    }

    private VoiceCommandCatalog() { }

    public static List<Command> getCommands() {
        return COMMANDS;
    }

    public static int getCommandCount() {
        return COMMANDS.size();
    }

    public static int getPhraseCount() {
        int total = 0;
        for (Command command : COMMANDS) total += command.getPhrases().size();
        return total;
    }

    public static String getQuickExamples(int limit) {
        if (limit <= 0) return "";
        StringBuilder result = new StringBuilder();
        int count = Math.min(limit, COMMANDS.size());
        for (int index = 0; index < count; index += 1) {
            if (index > 0) result.append("、");
            result.append(COMMANDS.get(index).getPrimaryPhrase());
        }
        return result.toString();
    }

    private static Command command(
            String id,
            String action,
            String mode,
            String category,
            String title,
            String description,
            boolean requiresAccessibility,
            String... phrases
    ) {
        return new Command(
                id,
                action,
                mode,
                category,
                title,
                description,
                requiresAccessibility,
                phrases
        );
    }
}
