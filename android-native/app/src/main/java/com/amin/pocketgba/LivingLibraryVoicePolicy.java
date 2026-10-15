package com.amin.pocketgba;

/** Only the exact trusted, foreground library document can own a voice session. */
final class LivingLibraryVoicePolicy {
    static boolean allowed(String committedUrl, String currentUrl, String origin, boolean mainFrame, boolean foreground) {
        return committedUrl != null && currentUrl != null && foreground && mainFrame && LivingLibraryPolicy.isHome(committedUrl)
                && LivingLibraryPolicy.isHome(currentUrl)
                && committedUrl.equals(currentUrl)
                && ("https://ken12121122-dotcom.github.io".equals(origin)
                    || "https://appassets.androidplatform.net".equals(origin));
    }
    static boolean validSession(String session) {
        return session != null && session.matches("[a-zA-Z0-9._-]{1,80}");
    }
}
