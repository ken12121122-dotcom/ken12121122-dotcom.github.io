package com.amin.pocketgba;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/** Foreground background-music service for a user-selected local audio document. */
public final class BackgroundMusicService extends Service {
    public static final String ACTION_PLAY = "com.amin.pocketgba.music.PLAY";
    public static final String ACTION_PAUSE = "com.amin.pocketgba.music.PAUSE";
    public static final String ACTION_STOP = "com.amin.pocketgba.music.STOP";
    public static final String ACTION_DUCK = "com.amin.pocketgba.music.DUCK";
    public static final String ACTION_UNDUCK = "com.amin.pocketgba.music.UNDUCK";
    public static final String PREFS = "amin_background_music";
    public static final String KEY_URI = "music_uri";
    public static final String KEY_TITLE = "music_title";

    private static final String CHANNEL_ID = "amin_background_music";
    private static final int NOTIFICATION_ID = 4208;

    private MediaPlayer player;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;

    @Override public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        ensureChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_PLAY : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopPlayback();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_PAUSE.equals(action)) {
            if (player != null && player.isPlaying()) player.pause();
            if (player != null) startForeground(NOTIFICATION_ID, buildNotification(false));
            return START_STICKY;
        }
        if (ACTION_DUCK.equals(action)) {
            if (player != null) player.setVolume(0.15f, 0.15f);
            return START_STICKY;
        }
        if (ACTION_UNDUCK.equals(action)) {
            if (player != null) player.setVolume(0.75f, 0.75f);
            return START_STICKY;
        }
        playSelectedTrack();
        return START_STICKY;
    }

    private void playSelectedTrack() {
        String uriValue = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_URI, "");
        if (uriValue == null || uriValue.isEmpty()) {
            stopSelf();
            return;
        }
        requestAudioFocus();
        try {
            if (player == null) {
                player = new MediaPlayer();
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
                player.setDataSource(this, Uri.parse(uriValue));
                player.setLooping(false);
                player.setVolume(0.75f, 0.75f);
                player.setOnCompletionListener(mp -> {
                    stopPlayback();
                    stopForeground(true);
                    stopSelf();
                });
                player.prepare();
            }
            startForeground(NOTIFICATION_ID, buildNotification(true));
            player.start();
        } catch (Exception error) {
            stopPlayback();
            stopForeground(true);
            stopSelf();
        }
    }

    private Notification buildNotification(boolean playing) {
        String title = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_TITLE, "背景音樂");
        Intent open = new Intent(this, VoiceOrbHomeActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent togglePending = PendingIntent.getService(this, 2,
                new Intent(this, BackgroundMusicService.class).setAction(playing ? ACTION_PAUSE : ACTION_PLAY),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stopPending = PendingIntent.getService(this, 3,
                new Intent(this, BackgroundMusicService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title == null || title.isEmpty() ? "背景音樂" : title)
                .setContentText(playing ? "正在背景播放" : "已暫停")
                .setContentIntent(openPending)
                .setOnlyAlertOnce(true)
                .setOngoing(playing)
                .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? "暫停" : "繼續", togglePending)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPending)
                .build();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "背景音樂", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("我是最棒的背景音樂播放控制");
        manager.createNotificationChannel(channel);
    }

    private void requestAudioFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                    .setOnAudioFocusChangeListener(change -> {
                        if (player == null) return;
                        if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) player.setVolume(0.15f, 0.15f);
                        else if (change == AudioManager.AUDIOFOCUS_GAIN) player.setVolume(0.75f, 0.75f);
                        else if (change == AudioManager.AUDIOFOCUS_LOSS && player.isPlaying()) player.pause();
                    }).build();
            audioManager.requestAudioFocus(audioFocusRequest);
        }
    }

    private void stopPlayback() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioManager != null && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
            audioFocusRequest = null;
        }
    }

    @Override public void onDestroy() {
        stopPlayback();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    public static void start(Context context, String action) {
        Intent intent = new Intent(context, BackgroundMusicService.class).setAction(action);
        if (ACTION_PLAY.equals(action)) {
            String uriValue = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_URI, "");
            if (uriValue == null || uriValue.isEmpty()) return;
            ContextCompat.startForegroundService(context, intent);
        } else {
            try { context.startService(intent); } catch (Exception ignored) {}
        }
    }
}
