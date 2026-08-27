package com.amin.pocketgba;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class LlmClient {
    interface Callback { void onSuccess(String text); void onError(String message); }
    static final class Message {
        final String role;
        final String text;
        Message(String role, String text) { this.role = role; this.text = text; }
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private LlmClient() {}

    static void send(Context context, List<Message> history, Callback callback) {
        final String provider = LlmConfigStore.provider(context);
        final String model = LlmConfigStore.model(context);
        final String apiKey = LlmConfigStore.apiKey(context);
        if (apiKey.isEmpty()) { callback.onError("尚未設定 API Key"); return; }
        EXECUTOR.execute(() -> {
            try {
                String reply;
                if (LlmConfigStore.PROVIDER_OPENAI.equals(provider)) reply = callOpenAi(model, apiKey, history);
                else if (LlmConfigStore.PROVIDER_CLAUDE.equals(provider)) reply = callClaude(model, apiKey, history);
                else reply = callGemini(model, apiKey, history);
                recordConversationNode(context, history, reply);
                callback.onSuccess(reply);
            } catch (Exception error) {
                String message = error.getMessage();
                callback.onError(message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message);
            }
        });
    }

    static void test(Context context, Callback callback) {
        java.util.ArrayList<Message> messages = new java.util.ArrayList<>();
        messages.add(new Message("user", "請只回答 OK"));
        send(context, messages, callback);
    }

    private static void recordConversationNode(Context context, List<Message> history, String reply) {
        if (context == null || history == null || history.isEmpty()) return;
        for (int i = history.size() - 1; i >= 0; i--) {
            Message message = history.get(i);
            if (message != null && "user".equals(message.role)) {
                new MemoryNodeStore(context).recordConversation(message.text, reply);
                return;
            }
        }
    }

    private static String callGemini(String model, String apiKey, List<Message> history) throws Exception {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
        JSONArray contents = new JSONArray();
        for (Message message : history) {
            JSONObject item = new JSONObject();
            item.put("role", "assistant".equals(message.role) ? "model" : "user");
            item.put("parts", new JSONArray().put(new JSONObject().put("text", message.text)));
            contents.put(item);
        }
        JSONObject response = postJson(endpoint, null, new JSONObject().put("contents", contents));
        JSONArray candidates = response.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) throw new IllegalStateException("Gemini 沒有回傳內容");
        JSONArray parts = candidates.getJSONObject(0).getJSONObject("content").optJSONArray("parts");
        if (parts == null || parts.length() == 0) throw new IllegalStateException("Gemini 回覆格式異常");
        return parts.getJSONObject(0).optString("text", "").trim();
    }

    private static String callOpenAi(String model, String apiKey, List<Message> history) throws Exception {
        JSONArray messages = new JSONArray();
        for (Message message : history) messages.put(new JSONObject().put("role", message.role).put("content", message.text));
        JSONObject body = new JSONObject().put("model", model).put("messages", messages);
        JSONObject response = postJson("https://api.openai.com/v1/chat/completions", "Bearer " + apiKey, body);
        JSONArray choices = response.optJSONArray("choices");
        if (choices == null || choices.length() == 0) throw new IllegalStateException("OpenAI 沒有回傳內容");
        return choices.getJSONObject(0).getJSONObject("message").optString("content", "").trim();
    }

    private static String callClaude(String model, String apiKey, List<Message> history) throws Exception {
        JSONArray messages = new JSONArray();
        for (Message message : history) messages.put(new JSONObject().put("role", message.role).put("content", message.text));
        JSONObject body = new JSONObject().put("model", model).put("max_tokens", 1024).put("messages", messages);
        HttpURLConnection connection = open("https://api.anthropic.com/v1/messages");
        connection.setRequestProperty("x-api-key", apiKey);
        connection.setRequestProperty("anthropic-version", "2023-06-01");
        JSONObject response = execute(connection, body);
        JSONArray content = response.optJSONArray("content");
        if (content == null || content.length() == 0) throw new IllegalStateException("Claude 沒有回傳內容");
        return content.getJSONObject(0).optString("text", "").trim();
    }

    private static JSONObject postJson(String endpoint, String authorization, JSONObject body) throws Exception {
        HttpURLConnection connection = open(endpoint);
        if (authorization != null) connection.setRequestProperty("Authorization", authorization);
        return execute(connection, body);
    }

    private static HttpURLConnection open(String endpoint) throws Exception {
        URL url = new URL(endpoint);
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new SecurityException("只允許 HTTPS LLM API");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }

    private static JSONObject execute(HttpURLConnection connection, JSONObject body) throws Exception {
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = connection.getOutputStream()) { output.write(payload); }
        int code = connection.getResponseCode();
        java.io.InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String text = read(stream);
        connection.disconnect();
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code + " · " + compactError(text));
        return new JSONObject(text);
    }

    private static String read(java.io.InputStream input) throws Exception {
        if (input == null) return "";
        try (BufferedInputStream in = new BufferedInputStream(input); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int read; int total = 0;
            while ((read = in.read(buffer)) != -1) {
                total += read; if (total > 2 * 1024 * 1024) throw new IllegalStateException("LLM 回應過大");
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String compactError(String text) {
        if (text == null) return "未知錯誤";
        String compact = text.replace('\n', ' ').trim();
        return compact.length() > 220 ? compact.substring(0, 220) : compact;
    }
}
