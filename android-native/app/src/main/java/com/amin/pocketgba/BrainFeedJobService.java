package com.amin.pocketgba;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BrainFeedJobService extends JobService {
    private static final int JOB_ID = 0x414d494e;
    private static final long PERIOD_MILLIS = 15L * 60L * 1000L;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        JobInfo info = new JobInfo.Builder(JOB_ID,
                new ComponentName(context, BrainFeedJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(PERIOD_MILLIS)
                .setPersisted(false)
                .build();
        scheduler.schedule(info);
    }

    static void cancel(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) scheduler.cancel(JOB_ID);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        executor.execute(() -> {
            try { new BrainFeedRepository(this).refresh(true); }
            catch (Exception ignored) { }
            finally { jobFinished(params, false); }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) { return true; }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
