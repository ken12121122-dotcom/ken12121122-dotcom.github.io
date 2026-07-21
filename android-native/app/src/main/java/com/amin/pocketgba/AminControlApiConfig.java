package com.amin.pocketgba;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public final class AminControlApiConfig {
    private static final String PREFS = "amin_control_api_v1";
    private static final String KEY_API_ENABLED = "api_enabled";
    private static final String KEY_LAN_ENABLED = "lan_enabled";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_ALLOWLIST = "allowlist";
    private static final String KEY_PORT = "port";
    private static final String KEY_RATE_LIMIT = "rate_limit";
    private static final String KEY_AUTOMATION_ENABLED = "automation_enabled";

    public static final int DEFAULT_PORT = 8765;
    public static final int DEFAULT_RATE_LIMIT = 60;
    public static final String DEFAULT_ALLOWLIST = "192.168.0.0/16,10.0.0.0/8,172.16.0.0/12";

    private final SharedPreferences preferences;

    public AminControlApiConfig(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isApiEnabled() { return preferences.getBoolean(KEY_API_ENABLED, false); }
    public boolean isLanEnabled() { return preferences.getBoolean(KEY_LAN_ENABLED, false); }
    public boolean isAutomationEnabled() { return preferences.getBoolean(KEY_AUTOMATION_ENABLED, false); }
    public int getPort() { return clampPort(preferences.getInt(KEY_PORT, DEFAULT_PORT)); }
    public int getRateLimitPerMinute() {
        return Math.max(1, Math.min(600, preferences.getInt(KEY_RATE_LIMIT, DEFAULT_RATE_LIMIT)));
    }
    public String getToken() { return preferences.getString(KEY_TOKEN, ""); }
    public String getAllowlist() { return preferences.getString(KEY_ALLOWLIST, DEFAULT_ALLOWLIST); }

    public void save(
            boolean apiEnabled, boolean lanEnabled, boolean automationEnabled, int port,
            int rateLimit, String token, String allowlist
    ) {
        preferences.edit()
                .putBoolean(KEY_API_ENABLED, apiEnabled)
                .putBoolean(KEY_LAN_ENABLED, lanEnabled)
                .putBoolean(KEY_AUTOMATION_ENABLED, automationEnabled)
                .putInt(KEY_PORT, clampPort(port))
                .putInt(KEY_RATE_LIMIT, Math.max(1, Math.min(600, rateLimit)))
                .putString(KEY_TOKEN, token == null ? "" : token.trim())
                .putString(KEY_ALLOWLIST,
                        allowlist == null || allowlist.isBlank() ? DEFAULT_ALLOWLIST : allowlist.trim())
                .apply();
    }

    public String ensureToken() {
        String current = getToken();
        if (current != null && current.length() >= 24) return current;
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String generated = Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        preferences.edit().putString(KEY_TOKEN, generated).apply();
        return generated;
    }

    public boolean isRemoteAllowed(InetAddress address) {
        if (address == null) return false;
        if (!isLanEnabled()) return address.isLoopbackAddress();
        if (address.isLoopbackAddress()) return true;
        byte[] candidate = address.getAddress();
        if (candidate.length != 4) return false;
        for (Cidr cidr : parseAllowlist(getAllowlist())) {
            if (cidr.matches(candidate)) return true;
        }
        return false;
    }

    public String getBindHost() { return isLanEnabled() ? "0.0.0.0" : "127.0.0.1"; }

    private static int clampPort(int port) { return Math.max(1024, Math.min(65535, port)); }

    private static List<Cidr> parseAllowlist(String raw) {
        List<Cidr> result = new ArrayList<>();
        if (raw == null) return result;
        for (String token : raw.split("[,\\n\\s]+")) {
            String value = token.trim();
            if (value.isEmpty()) continue;
            try { result.add(Cidr.parse(value)); }
            catch (IllegalArgumentException ignored) { }
        }
        return result;
    }

    private static final class Cidr {
        private final int network;
        private final int mask;

        private Cidr(int network, int mask) { this.network = network; this.mask = mask; }

        static Cidr parse(String raw) {
            String[] parts = raw.split("/", 2);
            byte[] address;
            try { address = InetAddress.getByName(parts[0]).getAddress(); }
            catch (Exception error) { throw new IllegalArgumentException(error); }
            if (address.length != 4) throw new IllegalArgumentException("IPv4 only");
            int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : 32;
            if (prefix < 0 || prefix > 32) throw new IllegalArgumentException("Invalid prefix");
            int mask = prefix == 0 ? 0 : (int) (0xffffffffL << (32 - prefix));
            return new Cidr(toInt(address) & mask, mask);
        }

        boolean matches(byte[] address) { return (toInt(address) & mask) == network; }

        private static int toInt(byte[] address) {
            return ((address[0] & 0xff) << 24) | ((address[1] & 0xff) << 16)
                    | ((address[2] & 0xff) << 8) | (address[3] & 0xff);
        }
    }
}
