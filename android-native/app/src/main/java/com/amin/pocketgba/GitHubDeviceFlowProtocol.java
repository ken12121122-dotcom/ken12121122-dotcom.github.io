package com.amin.pocketgba;

import org.json.JSONObject;

final class GitHubDeviceFlowProtocol {
    enum PollStatus { PENDING, SUCCESS, DENIED, EXPIRED, ERROR }

    private GitHubDeviceFlowProtocol() { }

    static DeviceCode parseDeviceCode(JSONObject response, long receivedAtMillis) {
        if (response == null || receivedAtMillis <= 0L) throw new IllegalArgumentException("Device Flow 回應無效。 ");
        String deviceCode = required(response, "device_code");
        String userCode = required(response, "user_code");
        String verificationUri = required(response, "verification_uri");
        if (!"https://github.com/login/device".equals(verificationUri)) {
            throw new SecurityException("Device Flow 驗證網址不受信任。 ");
        }
        int expiresIn = response.optInt("expires_in", 0);
        int interval = response.optInt("interval", 5);
        if (expiresIn < 60 || expiresIn > 1800 || interval < 1 || interval > 60) {
            throw new IllegalArgumentException("Device Flow 時效資料無效。 ");
        }
        return new DeviceCode(deviceCode, userCode, verificationUri, interval,
                receivedAtMillis + expiresIn * 1000L);
    }

    static Poll parsePoll(JSONObject response, int currentIntervalSeconds) {
        if (response == null || currentIntervalSeconds < 1 || currentIntervalSeconds > 120) {
            throw new IllegalArgumentException("Device Flow polling 狀態無效。 ");
        }
        String accessToken = response.optString("access_token", "").trim();
        if (!accessToken.isEmpty()) {
            if (!"bearer".equalsIgnoreCase(response.optString("token_type", ""))) {
                return new Poll(PollStatus.ERROR, currentIntervalSeconds, null, "unexpected_token_type");
            }
            long now = System.currentTimeMillis();
            int expiresIn = response.optInt("expires_in", 0);
            int refreshExpiresIn = response.optInt("refresh_token_expires_in", 0);
            if (expiresIn <= 0) return new Poll(PollStatus.ERROR, currentIntervalSeconds, null, "missing_expiry");
            Token token = new Token(accessToken, response.optString("refresh_token", ""),
                    now + expiresIn * 1000L,
                    refreshExpiresIn > 0 ? now + refreshExpiresIn * 1000L : 0L);
            return new Poll(PollStatus.SUCCESS, currentIntervalSeconds, token, "");
        }
        String error = response.optString("error", "");
        if ("authorization_pending".equals(error)) {
            return new Poll(PollStatus.PENDING, currentIntervalSeconds, null, error);
        }
        if ("slow_down".equals(error)) {
            return new Poll(PollStatus.PENDING, Math.min(120, currentIntervalSeconds + 5), null, error);
        }
        if ("access_denied".equals(error)) {
            return new Poll(PollStatus.DENIED, currentIntervalSeconds, null, error);
        }
        if ("expired_token".equals(error)) {
            return new Poll(PollStatus.EXPIRED, currentIntervalSeconds, null, error);
        }
        return new Poll(PollStatus.ERROR, currentIntervalSeconds, null,
                error.isEmpty() ? "invalid_response" : error);
    }

    private static String required(JSONObject object, String key) {
        String value = object.optString(key, "").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Device Flow 缺少 " + key + "。 ");
        return value;
    }

    static final class DeviceCode {
        private final String deviceCode;
        private final String userCode;
        private final String verificationUri;
        private final int intervalSeconds;
        private final long expiresAtMillis;

        DeviceCode(String deviceCode, String userCode, String verificationUri,
                   int intervalSeconds, long expiresAtMillis) {
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.verificationUri = verificationUri;
            this.intervalSeconds = intervalSeconds;
            this.expiresAtMillis = expiresAtMillis;
        }
        String deviceCode() { return deviceCode; }
        String userCode() { return userCode; }
        String verificationUri() { return verificationUri; }
        int intervalSeconds() { return intervalSeconds; }
        long expiresAtMillis() { return expiresAtMillis; }
        JSONObject json() {
            try {
                return new JSONObject().put("user_code", userCode).put("verification_uri", verificationUri)
                        .put("interval", intervalSeconds).put("expires_at", expiresAtMillis);
            } catch (Exception error) {
                throw new IllegalStateException("Unable to create Device Flow status JSON", error);
            }
        }
    }

    static final class Token {
        private final String accessToken;
        private final String refreshToken;
        private final long expiresAtMillis;
        private final long refreshExpiresAtMillis;

        Token(String accessToken, String refreshToken, long expiresAtMillis, long refreshExpiresAtMillis) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken == null ? "" : refreshToken;
            this.expiresAtMillis = expiresAtMillis;
            this.refreshExpiresAtMillis = refreshExpiresAtMillis;
        }
        String accessToken() { return accessToken; }
        String refreshToken() { return refreshToken; }
        long expiresAtMillis() { return expiresAtMillis; }
        long refreshExpiresAtMillis() { return refreshExpiresAtMillis; }
        boolean accessUsable(long now) { return expiresAtMillis - now > 60_000L; }
        boolean refreshUsable(long now) { return !refreshToken.isEmpty() && refreshExpiresAtMillis - now > 60_000L; }
    }

    static final class Poll {
        private final PollStatus status;
        private final int nextIntervalSeconds;
        private final Token token;
        private final String error;

        Poll(PollStatus status, int nextIntervalSeconds, Token token, String error) {
            this.status = status;
            this.nextIntervalSeconds = nextIntervalSeconds;
            this.token = token;
            this.error = error;
        }
        PollStatus status() { return status; }
        int nextIntervalSeconds() { return nextIntervalSeconds; }
        Token token() { return token; }
        String error() { return error; }
    }
}
