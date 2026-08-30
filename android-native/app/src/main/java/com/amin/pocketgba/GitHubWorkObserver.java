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
        sync(context, onFinished, true);
    }

    static void syncIfStaleAsync(Context context, Runnable onFinished) {
        sync(context, onFinished, false);
    }

    private static void sync(Context context, Runnable onFinished, boolean forced) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        if (!GitHubWorkSyncState.canRefresh(app, System.currentTimeMillis())) {
            if (onFinished != null) onFinished.run();
            return;
        }
        if (!forced && !GitHubWorkSyncState.shouldAutoRefresh(app, System.currentTimeMillis())) {
            if (onFinished != null) onFinished.run();
            return;
        }
        if (!SYNC_IN_FLIGHT.compareAndSet(false, true)) {
            if (onFinished != null) onFinished.run();
            return;
        }
        GitHubWorkSyncState.syncing(app);
        new Thread(() -> {
            GitHubWorkApi api = new GitHubWorkApi(new GitHubUrlConnectionTransport());
            try {
                long observedAt = System.currentTimeMillis();
                JSONObject observation = api.fetchSnapshot(observedAt);
                JSONArray batches = GitHubWorkAdapter.toBatches(observation);
                new GitHubWorkSyncStore(app).sync(batches, observedAt);
                GitHubWorkSyncState.ready(app, observation);
                UnifiedGraphProvider.notifyChanged(app);
            } catch (Exception error) {
                GitHubWorkSyncState.failed(app, error, api.rateLimitSnapshot());
            } finally {
                SYNC_IN_FLIGHT.set(false);
                if (onFinished != null) onFinished.run();
            }
        }, "amin-github-work-observer").start();
    }
}
