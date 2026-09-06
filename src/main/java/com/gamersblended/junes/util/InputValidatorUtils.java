package com.gamersblended.junes.util;

public final class InputValidatorUtils {

    private InputValidatorUtils() {
    }

    public static String sanitizeString(String input) {
        if (null == input) {
            return null;
        }

        // Trim whitespace
        String sanitized = input.trim();

        // Remove any null bytes
        sanitized = sanitized.replace("\0", "");

        // Normalize whitespace (replace multiple spaces with single space)
        sanitized = sanitized.replaceAll("\\s+", " ");

        return sanitized.isBlank() ? null : sanitized;
    }
}
