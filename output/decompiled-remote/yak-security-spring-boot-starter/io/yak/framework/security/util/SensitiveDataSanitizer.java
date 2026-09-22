/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.security.util;

import java.util.regex.Pattern;

public final class SensitiveDataSanitizer {
    private static final Pattern JSON_SECRET = Pattern.compile("(?i)(\\\"?(?:password|passwd|pw|salt|token|secret|authorization|cookie)\\\"?\\s*[:=]\\s*\\\"?)[^\\\",}\\s]*");
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[^\\s,;}]+");

    private SensitiveDataSanitizer() {
        throw new IllegalStateException("Utility class");
    }

    public static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = BEARER.matcher(value).replaceAll("Bearer [REDACTED]");
        return JSON_SECRET.matcher(sanitized).replaceAll("$1[REDACTED]");
    }
}

