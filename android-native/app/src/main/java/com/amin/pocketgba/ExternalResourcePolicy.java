package com.amin.pocketgba;

import java.net.URI;

final class ExternalResourcePolicy {
    private ExternalResourcePolicy() {}

    static boolean isAllowedHttps(String raw) {
        if (raw == null) return false;
        String value = raw.trim();
        if (value.isEmpty()) return false;
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().trim().isEmpty();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
