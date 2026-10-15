package com.amin.pocketgba;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;
import org.json.JSONException;
import org.json.JSONObject;

/** Foreground-only transport. Native code returns text; web code owns command mappings. */
final class LivingLibraryVoiceController implements RecognitionListener {
    static final int AUDIO_PERMISSION_REQUEST = 6821;
    interface Sink { void send(String json); }
    private final Activity activity;
    private final BooleanSupplier authorized;
    private final Sink sink;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private String session = "";
    private boolean wanted;
    private boolean destroyed;
    private int failures;
    private int generation;

    LivingLibraryVoiceController(Activity activity, BooleanSupplier authorized, Sink sink) {
        this.activity = activity; this.authorized = authorized; this.sink = sink;
    }
    void request(String nextSession) {
        if (destroyed || !authorized.getAsBoolean() || !LivingLibraryVoicePolicy.validSession(nextSession)) return;
        if (wanted && session.equals(nextSession)) return;
        stop(); session = nextSession; wanted = true; failures = 0;
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            send("starting", "", "permission");
            activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION_REQUEST);
        } else start();
    }
    void permissionResult(boolean granted) {
        if (!wanted || destroyed || !authorized.getAsBoolean()) return;
        if (granted) start(); else fail("microphone-denied");
    }
    private void start() {
        if (!wanted || destroyed || !authorized.getAsBoolean()) { stop(); return; }
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) { fail("recognizer-unavailable"); return; }
        try {
            if (recognizer == null) { recognizer = SpeechRecognizer.createSpeechRecognizer(activity); recognizer.setRecognitionListener(this); }
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            send("starting", "", ""); recognizer.startListening(intent);
        } catch (RuntimeException error) { fail("start-failed"); }
    }
    private void restart() {
        final int expected = generation;
        handler.postDelayed(() -> { if (expected == generation && wanted) start(); }, 650L);
    }
    private void send(String status, String text, String detail) {
        if (!authorized.getAsBoolean() || session.isEmpty()) return;
        try { sink.send(new JSONObject().put("v", 1).put("session", session).put("status", status)
                .put("text", text).put("detail", detail).toString()); }
        catch (JSONException ignored) { }
    }
    void stopSession(String expected) { if (session.equals(expected)) stop(); }
    void stop() {
        wanted = false; generation++; handler.removeCallbacksAndMessages(null);
        if (recognizer != null) { recognizer.setRecognitionListener(null); recognizer.cancel(); recognizer.destroy(); recognizer = null; }
        send("off", "", ""); session = "";
    }
    private void fail(String reason) { String previous = session; stop(); session = previous; send("error", "", reason); session = ""; }
    void destroy() { stop(); destroyed = true; }
    @Override public void onReadyForSpeech(Bundle params) { if (wanted) send("listening", "", ""); }
    @Override public void onResults(Bundle results) {
        if (!wanted || !authorized.getAsBoolean()) return;
        failures = 0;
        ArrayList<String> values = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (values != null && !values.isEmpty()) {
            String text = values.get(0); if (text.length() > 512) text = text.substring(0, 512);
            send("result", text, "");
        }
        restart();
    }
    @Override public void onError(int error) {
        if (!wanted) return;
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS || error == SpeechRecognizer.ERROR_AUDIO
                || error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                || error == SpeechRecognizer.ERROR_CLIENT) { fail("recognition-error-" + error); return; }
        // Silence is normal while the player moves; recognizer busy/server failures are bounded.
        if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT && ++failures >= 3) {
            fail("recognition-error-" + error); return;
        }
        restart();
    }
    @Override public void onBeginningOfSpeech() { }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { }
    @Override public void onPartialResults(Bundle results) { }
    @Override public void onEvent(int eventType, Bundle params) { }
}
