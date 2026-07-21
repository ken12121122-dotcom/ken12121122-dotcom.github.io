package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class AminControlApiInstrumentedTest {
    private static final int PORT = 18765;

    private Context context;
    private AminControlApiConfig config;
    private AminControlHttpServer server;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        config = new AminControlApiConfig(context);
        config.save(
                true,
                false,
                true,
                PORT,
                120,
                "",
                AminControlApiConfig.DEFAULT_ALLOWLIST
        );
    }

    @After
    public void tearDown() {
        if (server != null) server.stop();
        config.save(
                false,
                false,
                false,
                AminControlApiConfig.DEFAULT_PORT,
                AminControlApiConfig.DEFAULT_RATE_LIMIT,
                "",
                AminControlApiConfig.DEFAULT_ALLOWLIST
        );
    }

    @Test
    public void localhostRestAndWebSocketExecuteThroughGateway() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<String> startupError = new AtomicReference<>();
        server = new AminControlHttpServer(context, new AminControlHttpServer.StateListener() {
            @Override public void onStarted(String endpoint) { started.countDown(); }
            @Override public void onStopped() { }
            @Override public void onError(String message) {
                startupError.set(message);
                started.countDown();
            }
        });
        server.start();
        assertTrue("API server did not start", started.await(5, TimeUnit.SECONDS));
        assertEquals(null, startupError.get());

        JSONObject status = requestJson("GET", "/v1/status", null);
        assertEquals("amin-control-api-v1", status.getString("service"));
        assertTrue(status.getBoolean("running"));
        assertEquals("127.0.0.1", status.getString("bindHost"));
        assertEquals(PORT, status.getInt("port"));
        assertTrue(!status.getBoolean("lanMode"));

        JSONObject actions = requestJson("GET", "/v1/actions", null);
        assertEquals(17, actions.getInt("count"));
        assertEquals(17, actions.getJSONArray("actions").length());

        JSONObject restBody = new JSONObject();
        restBody.put("requestId", "rest-instrumented-001");
        JSONObject restResult = requestJson(
                "POST",
                "/v1/actions/SYSTEM_HOME",
                restBody.toString()
        );
        assertTrue(restResult.getBoolean("success"));
        assertEquals("rest-instrumented-001", restResult.getString("requestId"));
        assertEquals("rest", restResult.getString("source"));

        JSONObject recent = requestJson("GET", "/v1/events?limit=100", null);
        assertTrue(containsRequestId(recent.getJSONArray("events"), "rest-instrumented-001"));

        try (Socket socket = new Socket("127.0.0.1", PORT)) {
            socket.setSoTimeout(5000);
            OutputStream output = socket.getOutputStream();
            InputStream input = socket.getInputStream();
            String request = "GET /v1/events HTTP/1.1\r\n"
                    + "Host: 127.0.0.1:" + PORT + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n\r\n";
            output.write(request.getBytes(StandardCharsets.ISO_8859_1));
            output.flush();
            String responseHeaders = readHeaders(input);
            assertTrue(responseHeaders.startsWith("HTTP/1.1 101"));

            JSONObject connected = new JSONObject(readServerTextFrame(input));
            assertEquals("connected", connected.getString("type"));

            JSONObject command = new JSONObject();
            command.put("action", "SYSTEM_HOME");
            command.put("requestId", "ws-instrumented-001");
            command.put("parameters", new JSONObject());
            sendMaskedTextFrame(output, command.toString());

            boolean received = false;
            for (int attempt = 0; attempt < 6; attempt += 1) {
                String frame = readServerTextFrame(input);
                if (frame.contains("ws-instrumented-001") && frame.contains("\"success\":true")) {
                    received = true;
                    break;
                }
            }
            assertTrue("WebSocket did not return the execution result", received);
        }
    }

    @Test
    public void orderedBroadcastReturnsStructuredExecutionResult() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<String> resultJson = new AtomicReference<>();
        AtomicReference<String> resultMessage = new AtomicReference<>();

        Intent command = new Intent(context, AminAutomationReceiver.class)
                .setAction(AminAutomationReceiver.ACTION_EXECUTE)
                .putExtra(AminAutomationReceiver.EXTRA_ACTION, "SYSTEM_HOME")
                .putExtra(AminAutomationReceiver.EXTRA_REQUEST_ID, "broadcast-instrumented-001");

        context.sendOrderedBroadcast(
                command,
                null,
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context receiverContext, Intent intent) {
                        Bundle extras = getResultExtras(false);
                        resultJson.set(extras == null
                                ? null
                                : extras.getString(AminAutomationReceiver.EXTRA_RESULT_JSON));
                        resultMessage.set(getResultData());
                        completed.countDown();
                    }
                },
                new Handler(Looper.getMainLooper()),
                0,
                null,
                null
        );

        assertTrue("Broadcast result timed out", completed.await(8, TimeUnit.SECONDS));
        assertNotNull(resultJson.get());
        JSONObject result = new JSONObject(resultJson.get());
        assertTrue(result.getBoolean("success"));
        assertEquals("broadcast-instrumented-001", result.getString("requestId"));
        assertEquals("broadcast", result.getString("source"));
        assertNotNull(resultMessage.get());
    }

    private JSONObject requestJson(String method, String path, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://127.0.0.1:" + PORT + path
        ).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(7000);
        connection.setRequestProperty("Accept", "application/json");
        if (body != null) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String response = readAll(stream);
        connection.disconnect();
        assertTrue("Unexpected HTTP status " + status + ": " + response, status >= 200 && status < 300);
        return new JSONObject(response);
    }

    private static boolean containsRequestId(JSONArray events, String requestId) {
        for (int index = 0; index < events.length(); index += 1) {
            JSONObject event = events.optJSONObject(index);
            if (event == null) continue;
            JSONObject payload = event.optJSONObject("payload");
            if (payload != null && requestId.equals(payload.optString("requestId"))) return true;
        }
        return false;
    }

    private static String readHeaders(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int state = 0;
        while (output.size() < 32768) {
            int value = input.read();
            if (value < 0) throw new EOFException("WebSocket handshake ended early");
            output.write(value);
            if ((state == 0 || state == 2) && value == '\r') state += 1;
            else if ((state == 1 || state == 3) && value == '\n') state += 1;
            else state = value == '\r' ? 1 : 0;
            if (state == 4) break;
        }
        return output.toString(StandardCharsets.ISO_8859_1.name());
    }

    private static String readServerTextFrame(InputStream input) throws Exception {
        int first = input.read();
        int second = input.read();
        if (first < 0 || second < 0) throw new EOFException("Incomplete WebSocket frame");
        int opcode = first & 0x0f;
        assertEquals(1, opcode);
        int length = second & 0x7f;
        if (length == 126) {
            length = (readByte(input) << 8) | readByte(input);
        } else if (length == 127) {
            long longLength = 0L;
            for (int index = 0; index < 8; index += 1) {
                longLength = (longLength << 8) | readByte(input);
            }
            assertTrue(longLength <= 65536L);
            length = (int) longLength;
        }
        byte[] payload = readExactly(input, length);
        return new String(payload, StandardCharsets.UTF_8);
    }

    private static void sendMaskedTextFrame(OutputStream output, String text) throws Exception {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        byte[] mask = new byte[] { 0x12, 0x34, 0x56, 0x78 };
        output.write(0x81);
        if (payload.length <= 125) {
            output.write(0x80 | payload.length);
        } else {
            output.write(0x80 | 126);
            output.write((payload.length >>> 8) & 0xff);
            output.write(payload.length & 0xff);
        }
        output.write(mask);
        for (int index = 0; index < payload.length; index += 1) {
            output.write(payload[index] ^ mask[index % mask.length]);
        }
        output.flush();
    }

    private static int readByte(InputStream input) throws Exception {
        int value = input.read();
        if (value < 0) throw new EOFException("Unexpected WebSocket EOF");
        return value;
    }

    private static byte[] readExactly(InputStream input, int length) throws Exception {
        byte[] result = new byte[length];
        int offset = 0;
        while (offset < length) {
            int count = input.read(result, offset, length - offset);
            if (count < 0) throw new EOFException("Unexpected WebSocket EOF");
            offset += count;
        }
        return result;
    }

    private static String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
