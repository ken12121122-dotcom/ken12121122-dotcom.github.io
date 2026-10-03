package com.amin.pocketgba;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Fetches open GEN2 runs and notifies once per run step that waits for the OWNER. */
final class Gen2RunRepository {
    private static final String STORE = "amin_gen2_runs";
    private static final String WAITING = "waiting_keys";
    private final Context context;
    private final SharedPreferences preferences;

    Gen2RunRepository(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    synchronized List<Gen2RunView> refresh(boolean emitNotifications) throws Exception {
        List<Gen2RunView> runs = new BrainAuthSession(context).requireGen2Api().openRuns();
        Delta delta = diff(runs, waitingKeys());
        JSONArray stored = new JSONArray();
        for (String key : delta.waiting) stored.put(key);
        if (!preferences.edit().putString(WAITING, stored.toString()).commit()) {
            throw new IllegalStateException("無法儲存 GEN2 通知狀態。 ");
        }
        if (emitNotifications) {
            for (Gen2RunView run : delta.fresh) Gen2NotificationCenter.notify(context, run);
        }
        for (Integer number : delta.cleared) Gen2NotificationCenter.cancel(context, number);
        return runs;
    }

    void post(Gen2RunView run, String command) throws Exception {
        new BrainAuthSession(context).requireGen2Api().postCommand(run.number, command);
    }

    synchronized void clear() { preferences.edit().clear().commit(); }

    private Set<String> waitingKeys() {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(WAITING, "[]"));
            for (int index = 0; index < array.length(); index++) values.add(array.optString(index, ""));
        } catch (Exception ignored) { }
        return values;
    }

    /**
     * Pure decision: which waiting runs are new since the last check (notify),
     * and which runs no longer wait (clear their notification). Keys are
     * "<issue>#<run step wait key>" so a revised step that waits again notifies again.
     */
    static Delta diff(List<Gen2RunView> runs, Set<String> previous) {
        Delta delta = new Delta();
        Set<Integer> stillWaiting = new LinkedHashSet<>();
        for (Gen2RunView run : runs) {
            if (!run.needsOwner()) continue;
            String key = run.number + "#" + run.waitKey();
            delta.waiting.add(key);
            stillWaiting.add(run.number);
            if (!previous.contains(key)) delta.fresh.add(run);
        }
        for (String key : previous) {
            int hash = key.indexOf('#');
            if (hash <= 0) continue;
            try {
                int number = Integer.parseInt(key.substring(0, hash));
                if (!stillWaiting.contains(number)) delta.cleared.add(number);
            } catch (NumberFormatException ignored) { }
        }
        return delta;
    }

    static final class Delta {
        final LinkedHashSet<String> waiting = new LinkedHashSet<>();
        final java.util.ArrayList<Gen2RunView> fresh = new java.util.ArrayList<>();
        final LinkedHashSet<Integer> cleared = new LinkedHashSet<>();
    }
}
