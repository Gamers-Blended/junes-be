package com.gamersblended.junes.dto.response;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationResultTest {

    @Test
    void getErrorMessage_joinsAllErrorsWithCommaAndSpace_whenMultipleErrorsPresent() {
        ValidationResult result = new ValidationResult(
                "email", false, List.of("Email is required", "Invalid email format"));

        assertThat(result.getErrorMessage()).isEqualTo("Email is required, Invalid email format");
    }

    @Test
    void getErrorMessage_returnsSingleError_whenOnlyOneErrorPresent() {
        ValidationResult result = new ValidationResult("password", false, List.of("Password is required"));

        assertThat(result.getErrorMessage()).isEqualTo("Password is required");
    }

    @Test
    void getErrorMessage_returnsEmptyString_whenErrorListIsEmpty() {
        ValidationResult result = new ValidationResult("email", true, List.of());

        assertThat(result.getErrorMessage()).isEmpty();
    }
}
