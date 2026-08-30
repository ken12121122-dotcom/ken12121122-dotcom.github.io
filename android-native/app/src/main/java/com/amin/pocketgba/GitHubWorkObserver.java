package com.amin.pocketgba;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

/** Read-only Phase 1 observer. It cannot dispatch workflows, merge, release, or write GitHub. */
final class GitHubWorkObserver {
    private static final AtomicBoolean SYNC_IN_FLIGHT = new AtomicBoolean(false);

    private GitHubWorkObserver() { }

    static void syncAsync(Context context, Runnable onFinished) {
        if (context == null) return;
        if (!SYNC_IN_FLIGHT.compareAndSet(false, true)) {
            if (onFinished != null) onFinished.run();
            return;
        }
        Context app = context.getApplicationContext();
        GitHubWorkSyncState.syncing();
        new Thread(() -> {
            try {
                long observedAt = System.currentTimeMillis();
                JSONObject observation = new GitHubWorkApi(new GitHubUrlConnectionTransport())
                        .fetchSnapshot(observedAt);
                JSONArray batches = GitHubWorkAdapter.toBatches(observation);
                new GitHubWorkSyncStore(app).sync(batches, observedAt);
                GitHubWorkSyncState.ready(observation.optString("revision", ""));
                UnifiedGraphProvider.notifyChanged(app);
            } catch (Exception error) {
                GitHubWorkSyncState.failed(error);
            } finally {
                SYNC_IN_FLIGHT.set(false);
                if (onFinished != null) onFinished.run();
            }
        }, "amin-github-work-observer").start();
    }
}
