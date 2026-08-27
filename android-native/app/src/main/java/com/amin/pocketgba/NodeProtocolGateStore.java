package com.amin.pocketgba;

import android.content.Context;

/** Persistent pass-light state for Neural Flow node protocol gates. */
final class NodeProtocolGateStore {
    static final String INPUT = "input";
    static final String ROUTER = "router";
    static final String NODE = "node";
    static final String COMMAND = "command";
    static final String LLM = "llm";
    static final String JUDGE = "judge";
    static final String COMPRESS = "compress";
    static final String APPROVAL = "approval";
    static final String STORE = "store";

    private static final String PREFS = "amin_node_protocol_gate";
    private static final String PREFIX = "auto_";

    private NodeProtocolGateStore() { }

    static boolean isAuto(Context context, String key) {
        if (context == null || key == null) return true;
        boolean defaultValue = !(NODE.equals(key) || COMMAND.equals(key) || LLM.equals(key));
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREFIX + key, defaultValue);
    }

    static boolean toggle(Context context, String key) {
        boolean next = !isAuto(context, key);
        setAuto(context, key, next);
        return next;
    }

    static void setAuto(Context context, String key, boolean auto) {
        if (context == null || key == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(PREFIX + key, auto).apply();
    }
}
