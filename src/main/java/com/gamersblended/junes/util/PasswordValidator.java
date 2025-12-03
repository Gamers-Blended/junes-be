package com.gamersblended.junes.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class PasswordValidator {

    private static final String PASSWORD = "password";
    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_LENGTH = 50;
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]");

    public static ValidationResult validatePassword(String password) {
        List<String> errorList = new ArrayList<>();

        if (null == password || password.trim().isEmpty()) {
            errorList.add("Password cannot be empty");
            return new ValidationResult(PASSWORD, false, errorList);
        }

        if (password.length() < PASSWORD_MIN_LENGTH) {
            errorList.add("Password must be at least " + PASSWORD_MIN_LENGTH + " characters long");
        }

        if (password.length() > PASSWORD_MAX_LENGTH) {
            errorList.add("Password must not exceed " + PASSWORD_MAX_LENGTH + " characters");
        }

        if (!UPPERCASE_PATTERN.matcher(password).find()) {
            errorList.add("Password must contain at least 1 uppercase letter");
        }

        if (!LOWERCASE_PATTERN.matcher(password).find()) {
            errorList.add("Password must contain at least 1 lowercase letter");
        }

        if (!DIGIT_PATTERN.matcher(password).find()) {
            errorList.add("Password must contain at least 1 digit");
        }

        if (!SPECIAL_CHAR_PATTERN.matcher(password).find()) {
            errorList.add("Password must contain at least 1 special character");
        }

        if (password.contains(" ")) {
            errorList.add("Password cannot contain spaces");
        }

        return new ValidationResult(PASSWORD, errorList.isEmpty(), errorList);
    }
}
