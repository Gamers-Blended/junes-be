package com.gamersblended.junes.util;

import com.gamersblended.junes.dto.response.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordValidatorTest {

    private static final String VALID_PASSWORD = "Abcdef1!";

    @Test
    void validatePassword_returnsValid_whenAllRulesAreSatisfied() {
        ValidationResult result = PasswordValidator.validatePassword(VALID_PASSWORD);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrorList()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void validatePassword_returnsOnlyEmptyError_whenPasswordIsNullOrEmpty(String password) {
        ValidationResult result = PasswordValidator.validatePassword(password);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorList()).containsExactly("Password cannot be empty");
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    void validatePassword_returnsOnlyEmptyError_whenPasswordIsBlank(String password) {
        ValidationResult result = PasswordValidator.validatePassword(password);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorList()).hasSize(1);
        assertThat(result.getErrorMessage()).isEqualTo("Password cannot be empty");
    }

    @Test
    void validatePassword_acceptsLength_whenExactlyMinLength() {
        ValidationResult result = PasswordValidator.validatePassword("Abcdefg1!");

        assertThat(result.getErrorList()).isEmpty();
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validatePassword_acceptsLength_whenExactlyMaxLength() {
        String exactlyMax = "Aa1!" + "a".repeat(46); // 50 chars total

        ValidationResult result = PasswordValidator.validatePassword(exactlyMax);

        assertThat(exactlyMax).hasSize(50);
        assertThat(result.getErrorList()).isEmpty();
        assertThat(result.isValid()).isTrue();
    }

    private static Stream<Arguments> singleRuleViolations() {
        return Stream.of(
                Arguments.of("Ab1!fgh", "Password must be at least 8 characters long"),
                Arguments.of("Aa1!" + "a".repeat(48), "Password must not exceed 50 characters"), // 52 chars total
                Arguments.of("abcdefg1!", "Password must contain at least 1 uppercase letter"),
                Arguments.of("ABCDEFG1!", "Password must contain at least 1 lowercase letter"),
                Arguments.of("Abcdefgh!", "Password must contain at least 1 digit"),
                Arguments.of("Abcdefg1", "Password must contain at least 1 special character"),
                Arguments.of("Abcdef 1!", "Password cannot contain spaces")
        );
    }

    @ParameterizedTest
    @MethodSource("singleRuleViolations")
    void validatePassword_addsExpectedError_whenSingleRuleIsViolated(String password, String expectedError) {
        ValidationResult result = PasswordValidator.validatePassword(password);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorList()).contains(expectedError);
    }

    @Test
    void validatePassword_accumulatesAllApplicableErrors_whenMultipleRulesAreViolated() {
        ValidationResult result = PasswordValidator.validatePassword("abc de");

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorList()).containsExactlyInAnyOrder(
                "Password must be at least 8 characters long",
                "Password must contain at least 1 uppercase letter",
                "Password must contain at least 1 digit",
                "Password must contain at least 1 special character",
                "Password cannot contain spaces"
        );
    }

    @Test
    void getErrorMessage_joinsAllErrorsWithCommaSeparator() {
        ValidationResult result = PasswordValidator.validatePassword("abc");

        assertThat(result.getErrorMessage()).isEqualTo(String.join(", ", result.getErrorList()));
        assertThat(result.getErrorMessage()).contains(", ");
    }
}
