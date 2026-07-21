package com.amin.pocketgba;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AminControlHttpServer {
    public interface StateListener {
        void onStarted(String endpoint);
        void onStopped();
        void onError(String message);
    }

    private static final int MAX_HEADER = 32768;
    private static final int MAX_BODY = 65536;
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final AminControlApiConfig config;
    private final AminInputGateway gateway;
    private final AminEventStore events;
    private final StateListener listener;
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<String, RateWindow> rates = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile ServerSocket server;
    private volatile Thread acceptThread;

    public AminControlHttpServer(Context context, StateListener listener) {
        Context app = context.getApplicationContext();
        config = new AminControlApiConfig(app);
        gateway = AminInputGateway.get(app);
        events = gateway.getEventStore();
        this.listener = listener;
    }

    public synchronized void start() {
        if (running.get()) return;
        try {
            if (config.isLanEnabled()) config.ensureToken();
            ServerSocket socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(config.getBindHost(), config.getPort()), 32);
            server = socket;
            running.set(true);
            acceptThread = new Thread(this::acceptLoop, "amin-api-accept");
            acceptThread.start();
            JSONObject audit = new JSONObject();
            audit.put("host", config.getBindHost());
            audit.put("port", config.getPort());
            audit.put("lanMode", config.isLanEnabled());
            events.recordAudit("api_started", audit);
            if (listener != null) listener.onStarted("http://" + config.getBindHost() + ":" + config.getPort());
        } catch (Exception error) {
            running.set(false);
            close(server);
            server = null;
            if (listener != null) listener.onError(message(error));
        }
    }

    public synchronized void stop() {
        if (!running.getAndSet(false)) return;
        close(server);
        server = null;
        if (acceptThread != null) acceptThread.interrupt();
        acceptThread = null;
        workers.shutdownNow();
        events.recordAudit("api_stopped", new JSONObject());
        if (listener != null) listener.onStopped();
    }

    public boolean isRunning() { return running.get(); }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = server.accept();
                socket.setSoTimeout(30000);
                workers.execute(() -> handle(socket));
            } catch (IOException error) {
                if (running.get() && listener != null) listener.onError(message(error));
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket client = socket; InputStream input = client.getInputStream(); OutputStream output = client.getOutputStream()) {
            Request request = readRequest(input, client.getInetAddress());
            if (request == null) return;
            Decision decision = authorize(request);
            if (!decision.allowed) {
                JSONObject audit = new JSONObject();
                audit.put("client", request.remote.getHostAddress());
                audit.put("path", request.path);
                audit.put("status", decision.status);
                events.recordAudit("api_rejected", audit);
                json(output, decision.status, error(decision.code, decision.message));
                return;
            }
            if ("websocket".equalsIgnoreCase(request.headers.get("upgrade"))) {
                if (!"/v1/events".equals(request.path)) json(output, 404, error("NOT_FOUND", "Unknown WebSocket endpoint"));
                else websocket(request, input, output);
                return;
            }
            route(request, output);
        } catch (Exception error) {
            try { json(socket.getOutputStream(), 500, error("INTERNAL_ERROR", message(error))); }
            catch (Exception ignored) { }
        }
    }

    private Decision authorize(Request request) {
        if (!config.isRemoteAllowed(request.remote)) return Decision.reject(403, "REMOTE_NOT_ALLOWED", "Remote address is not allowed");
        if (!allowRate(request.remote.getHostAddress())) return Decision.reject(429, "RATE_LIMITED", "Rate limit exceeded");
        if (config.isLanEnabled()) {
            String supplied = request.headers.get("x-amin-token");
            String authorization = request.headers.get("authorization");
            if ((supplied == null || supplied.isBlank()) && authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) supplied = authorization.substring(7).trim();
            if (!constantEquals(config.ensureToken(), supplied)) return Decision.reject(401, "TOKEN_REQUIRED", "Valid API token required");
        }
        return Decision.allow();
    }

    private boolean allowRate(String key) {
        long minute = System.currentTimeMillis() / 60000L;
        RateWindow window = rates.compute(key, (ignored, current) -> current == null || current.minute != minute ? new RateWindow(minute) : current);
        synchronized (window) {
            window.count += 1;
            return window.count <= config.getRateLimitPerMinute();
        }
    }

    private void route(Request request, OutputStream output) throws Exception {
        if ("GET".equals(request.method) && "/v1/status".equals(request.path)) {
            JSONObject body = new JSONObject();
            body.put("service", "amin-control-api-v1");
            body.put("running", true);
            body.put("bindHost", config.getBindHost());
            body.put("port", config.getPort());
            body.put("lanMode", config.isLanEnabled());
            body.put("tokenRequired", config.isLanEnabled());
            body.put("automationEnabled", config.isAutomationEnabled());
            body.put("timestamp", Instant.now().toString());
            json(output, 200, body);
            return;
        }
        if ("GET".equals(request.method) && "/v1/actions".equals(request.path)) {
            JSONArray list = new JSONArray();
            for (VoiceCommandCatalog.Command command : VoiceCommandCatalog.getCommands()) {
                JSONObject item = new JSONObject();
                item.put("id", command.getId());
                item.put("action", command.getAction());
                item.put("title", command.getTitle());
                item.put("description", command.getDescription());
                item.put("requiresAccessibility", command.requiresAccessibility());
                item.put("phrases", new JSONArray(command.getPhrases()));
                list.put(item);
            }
            JSONObject body = new JSONObject();
            body.put("count", list.length());
            body.put("actions", list);
            json(output, 200, body);
            return;
        }
        if ("GET".equals(request.method) && "/v1/events".equals(request.path)) {
            JSONObject body = new JSONObject();
            body.put("events", events.getRecent(integer(request.query.get("limit"), 50, 1, 200)));
            json(output, 200, body);
            return;
        }
        if ("POST".equals(request.method) && ("/v1/actions".equals(request.path) || request.path.startsWith("/v1/actions/"))) {
            JSONObject body = request.body.length == 0 ? new JSONObject() : new JSONObject(new String(request.body, StandardCharsets.UTF_8));
            String name = request.path.startsWith("/v1/actions/") ? decode(request.path.substring(12)) : body.optString("action", "");
            AminAction action = gateway.createAction(name, body.optJSONObject("parameters"), "rest", body.optDouble("confidence", 1d), body.optString("requestId", ""));
            ExecutionResult result = gateway.executeBlocking(action, 6000L);
            JSONObject audit = new JSONObject();
            audit.put("client", request.remote.getHostAddress());
            audit.put("path", request.path);
            audit.put("requestId", result.getRequestId());
            audit.put("success", result.isSuccess());
            events.recordAudit("api_request", audit);
            json(output, result.isSuccess() ? 200 : 422, result.toJsonObject());
            return;
        }
        json(output, 404, error("NOT_FOUND", "Unknown endpoint"));
    }

    private void websocket(Request request, InputStream input, OutputStream output) throws Exception {
        String key = request.headers.get("sec-websocket-key");
        if (key == null || key.isBlank()) { json(output, 400, error("WEBSOCKET_KEY_REQUIRED", "Missing Sec-WebSocket-Key")); return; }
        String accept = Base64.encodeToString(MessageDigest.getInstance("SHA-1").digest((key.trim() + WS_GUID).getBytes(StandardCharsets.ISO_8859_1)), Base64.NO_WRAP);
        String response = "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n";
        output.write(response.getBytes(StandardCharsets.ISO_8859_1));
        output.flush();
        Object writeLock = new Object();
        AminEventStore.Listener eventListener = event -> {
            try { synchronized (writeLock) { sendFrame(output, 1, event.toString().getBytes(StandardCharsets.UTF_8)); } }
            catch (IOException ignored) { }
        };
        events.addListener(eventListener);
        try {
            JSONObject ready = new JSONObject();
            ready.put("type", "connected");
            ready.put("service", "amin-control-api-v1");
            synchronized (writeLock) { sendText(output, ready.toString()); }
            while (running.get()) {
                Frame frame = readFrame(input);
                if (frame == null || frame.opcode == 8) break;
                if (frame.opcode == 9) { synchronized (writeLock) { sendFrame(output, 10, frame.payload); } continue; }
                if (frame.opcode != 1) continue;
                JSONObject command = new JSONObject(new String(frame.payload, StandardCharsets.UTF_8));
                AminAction action = gateway.createAction(command.optString("action", ""), command.optJSONObject("parameters"), "websocket", command.optDouble("confidence", 1d), command.optString("requestId", ""));
                ExecutionResult result = gateway.executeBlocking(action, 6000L);
                synchronized (writeLock) { sendText(output, result.toJson()); }
            }
        } finally {
            events.removeListener(eventListener);
        }
    }

    private static Request readRequest(InputStream input, InetAddress remote) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int state = 0;
        while (bytes.size() < MAX_HEADER) {
            int value = input.read();
            if (value < 0) return null;
            bytes.write(value);
            if ((state == 0 || state == 2) && value == '\r') state++;
            else if ((state == 1 || state == 3) && value == '\n') state++;
            else state = value == '\r' ? 1 : 0;
            if (state == 4) break;
        }
        if (state != 4) throw new IOException("HTTP headers too large");
        String[] lines = new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1).split("\\r\\n");
        String[] first = lines[0].split(" ", 3);
        if (first.length < 2) throw new IOException("Invalid request line");
        Map<String, String> headers = new HashMap<>();
        for (int index = 1; index < lines.length; index++) {
            int colon = lines[index].indexOf(':');
            if (colon > 0) headers.put(lines[index].substring(0, colon).trim().toLowerCase(Locale.ROOT), lines[index].substring(colon + 1).trim());
        }
        byte[] body = exactly(input, integer(headers.get("content-length"), 0, 0, MAX_BODY));
        String target = first[1];
        int question = target.indexOf('?');
        String path = question < 0 ? target : target.substring(0, question);
        Map<String, String> query = question < 0 ? Collections.emptyMap() : query(target.substring(question + 1));
        return new Request(first[0].toUpperCase(Locale.ROOT), path, query, headers, body, remote);
    }

    private static byte[] exactly(InputStream input, int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int count = input.read(data, offset, length - offset);
            if (count < 0) throw new EOFException("Unexpected end of input");
            offset += count;
        }
        return data;
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> result = new HashMap<>();
        for (String pair : raw.split("&")) {
            if (pair.isBlank()) continue;
            String[] parts = pair.split("=", 2);
            result.put(decode(parts[0]), parts.length == 2 ? decode(parts[1]) : "");
        }
        return result;
    }

    private static String decode(String value) {
        try { return URLDecoder.decode(value, StandardCharsets.UTF_8.name()); }
        catch (Exception error) { return value; }
    }

    private static void json(OutputStream output, int status, JSONObject body) throws IOException {
        byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + status + " " + statusText(status) + "\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: " + data.length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        output.write(head.getBytes(StandardCharsets.ISO_8859_1));
        output.write(data);
        output.flush();
    }

    private static JSONObject error(String code, String message) {
        JSONObject body = new JSONObject();
        try { body.put("success", false); body.put("code", code); body.put("message", message); }
        catch (Exception ignored) { }
        return body;
    }

    private static void sendText(OutputStream output, String text) throws IOException { sendFrame(output, 1, text.getBytes(StandardCharsets.UTF_8)); }

    private static void sendFrame(OutputStream output, int opcode, byte[] payload) throws IOException {
        output.write(0x80 | opcode);
        if (payload.length <= 125) output.write(payload.length);
        else if (payload.length <= 65535) { output.write(126); output.write((payload.length >>> 8) & 255); output.write(payload.length & 255); }
        else { output.write(127); output.write(new byte[] {0,0,0,0}); output.write(ByteBuffer.allocate(4).putInt(payload.length).array()); }
        output.write(payload);
        output.flush();
    }

    private static Frame readFrame(InputStream input) throws IOException {
        int first = input.read();
        if (first < 0) return null;
        int second = input.read();
        if (second < 0) throw new EOFException("Incomplete WebSocket frame");
        int opcode = first & 15;
        boolean masked = (second & 128) != 0;
        long length = second & 127;
        if (length == 126) { int a = input.read(), b = input.read(); if (a < 0 || b < 0) throw new EOFException(); length = (a << 8) | b; }
        else if (length == 127) { length = 0; for (int i = 0; i < 8; i++) { int value = input.read(); if (value < 0) throw new EOFException(); length = (length << 8) | value; } }
        if (length > MAX_BODY) throw new IOException("WebSocket frame too large");
        byte[] mask = masked ? exactly(input, 4) : null;
        byte[] payload = exactly(input, (int) length);
        if (mask != null) for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
        return new Frame(opcode, payload);
    }

    private static boolean constantEquals(String expected, String actual) {
        return expected != null && actual != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private static int integer(String raw, int fallback, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(raw == null ? "" : raw.trim()))); }
        catch (NumberFormatException error) { return fallback; }
    }

    private static String statusText(int status) {
        switch (status) {
            case 200: return "OK";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 422: return "Unprocessable Entity";
            case 429: return "Too Many Requests";
            default: return "Internal Server Error";
        }
    }

    private static String message(Throwable error) {
        String value = error == null ? null : error.getMessage();
        return value == null || value.isBlank() ? (error == null ? "Unknown error" : error.getClass().getSimpleName()) : value;
    }

    private static void close(ServerSocket socket) { if (socket != null) try { socket.close(); } catch (IOException ignored) { } }

    private static final class Request {
        final String method, path;
        final Map<String, String> query, headers;
        final byte[] body;
        final InetAddress remote;
        Request(String method, String path, Map<String, String> query, Map<String, String> headers, byte[] body, InetAddress remote) {
            this.method = method; this.path = path; this.query = query; this.headers = headers; this.body = body; this.remote = remote;
        }
    }
    private static final class Decision {
        final boolean allowed; final int status; final String code, message;
        Decision(boolean allowed, int status, String code, String message) { this.allowed = allowed; this.status = status; this.code = code; this.message = message; }
        static Decision allow() { return new Decision(true, 200, "OK", "Allowed"); }
        static Decision reject(int status, String code, String message) { return new Decision(false, status, code, message); }
    }
    private static final class RateWindow { final long minute; int count; RateWindow(long minute) { this.minute = minute; } }
    private static final class Frame { final int opcode; final byte[] payload; Frame(int opcode, byte[] payload) { this.opcode = opcode; this.payload = payload; } }
}
