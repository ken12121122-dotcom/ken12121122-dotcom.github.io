package com.amin.pocketgba;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** User-selected raster images / backup JSON only. No file-system bridge or persistent grants. */
final class LivingLibraryFileChooserPolicy {
    static boolean allowedPage(String committed, String current) {
        return committed != null && current != null && committed.equals(current)
                && LivingLibraryPolicy.isHome(committed);
    }
    static String[] mimeTypes(String[] accept) {
        if (accept == null || accept.length == 0) return new String[0];
        Set<String> result = new LinkedHashSet<>();
        for (String group : accept) {
            if (group == null) return new String[0];
            for (String raw : group.split(",", -1)) {
                String value = raw.trim().toLowerCase(Locale.ROOT);
                if ("image/*".equals(value)) {
                    result.add("image/jpeg"); result.add("image/png"); result.add("image/webp");
                } else if ("image/jpeg".equals(value) || ".jpg".equals(value) || ".jpeg".equals(value)) result.add("image/jpeg");
                else if ("image/png".equals(value) || ".png".equals(value)) result.add("image/png");
                else if ("image/webp".equals(value) || ".webp".equals(value)) result.add("image/webp");
                else if ("application/json".equals(value) || ".json".equals(value)) result.add("application/json");
                else return new String[0];
            }
        }
        // A request must be either images or JSON, never a generic mixed-file picker.
        if (result.contains("application/json") && result.size() != 1) return new String[0];
        return result.toArray(new String[0]);
    }
}
