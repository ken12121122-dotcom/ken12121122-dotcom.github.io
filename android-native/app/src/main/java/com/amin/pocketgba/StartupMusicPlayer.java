package com.amin.pocketgba;

import android.content.Context;
import android.media.MediaPlayer;
import android.util.Base64;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Plays the short user-provided startup music excerpt without network access. */
public final class StartupMusicPlayer {
    private static final long MIN_REPLAY_INTERVAL_MS = 5000L;
    private static long lastPlayedAt;
    private static MediaPlayer player;

    private StartupMusicPlayer() {}

    public static synchronized void play(Context context) {
        long now = System.currentTimeMillis();
        if (now - lastPlayedAt < MIN_REPLAY_INTERVAL_MS) return;
        lastPlayedAt = now;
        stop();
        try {
            byte[] encoded;
            try (InputStream input = context.getAssets().open("startup_music.b64")) {
                encoded = readAll(input);
            }
            byte[] audio = Base64.decode(new String(encoded, StandardCharsets.US_ASCII), Base64.DEFAULT);
            File file = new File(context.getCacheDir(), "amin_startup_music.mp3");
            try (FileOutputStream output = new FileOutputStream(file, false)) {
                output.write(audio);
            }
            MediaPlayer next = new MediaPlayer();
            next.setDataSource(file.getAbsolutePath());
            next.setVolume(0.35f, 0.35f);
            next.setOnCompletionListener(mp -> release(mp));
            next.setOnErrorListener((mp, what, extra) -> { release(mp); return true; });
            next.prepare();
            player = next;
            next.start();
        } catch (Exception ignored) {
            stop();
        }
    }

    public static synchronized void stop() {
        if (player == null) return;
        release(player);
    }

    private static synchronized void release(MediaPlayer mediaPlayer) {
        try { mediaPlayer.stop(); } catch (Exception ignored) {}
        try { mediaPlayer.release(); } catch (Exception ignored) {}
        if (player == mediaPlayer) player = null;
    }

    private static byte[] readAll(InputStream input) throws Exception {
        byte[] buffer = new byte[4096];
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        return output.toByteArray();
    }
}
