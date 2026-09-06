package com.gamersblended.junes.util;

import com.gamersblended.junes.repository.jpa.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailValidatorServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private EmailValidatorService emailValidatorService;

    @Test
    void validateEmail_acceptsValidUnverifiedEmail() {
        when(userRepository.isEmailVerified("john.doe@example.com")).thenReturn(false);

        ValidationResult result = emailValidatorService.validateEmail("john.doe@example.com");

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrorList()).isEmpty();
    }

    @Test
    void validateEmail_trimsAndLowercasesBeforeChecking() {
        when(userRepository.isEmailVerified("john.doe@example.com")).thenReturn(false);

        ValidationResult result = emailValidatorService.validateEmail("  John.Doe@Example.com  ");

        assertThat(result.isValid()).isTrue();
        verify(userRepository).isEmailVerified("john.doe@example.com");
    }

    @Test
    void validateEmail_rejectsNullEmail() {
        ValidationResult result = emailValidatorService.validateEmail(null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorList()).containsExactly("Email cannot be empty");
        verify(userRepository, never()).isEmailVerified(anyString());
    }

    @Test
    void validateEmail_rejectsBlankEmail() {
        ValidationResult result = emailValidatorService.validateEmail("   ");

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorList()).containsExactly("Email cannot be empty");
        verify(userRepository, never()).isEmailVerified(anyString());
    }

    @Test
    void validateEmail_rejectsEmailExceedingMaxLength() {
        String tooLongEmail = "a".repeat(245) + "@example.com";
        when(userRepository.isEmailVerified(tooLongEmail)).thenReturn(false);

        ValidationResult result = emailValidatorService.validateEmail(tooLongEmail);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorList()).contains("Email must not exceed 254 characters");
    }

    @Test
    void validateEmail_rejectsInvalidFormat() {
        when(userRepository.isEmailVerified("not-an-email")).thenReturn(false);

        ValidationResult result = emailValidatorService.validateEmail("not-an-email");

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorList()).containsExactly("Invalid email format");
    }

    @Test
    void validateEmail_rejectsAlreadyVerifiedEmail() {
        when(userRepository.isEmailVerified("john.doe@example.com")).thenReturn(true);

        ValidationResult result = emailValidatorService.validateEmail("john.doe@example.com");

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorList()).containsExactly("Email is already verified");
    }

    @Test
    void validateEmail_accumulatesMultipleErrors() {
        String tooLongInvalidEmail = "a".repeat(300);
        when(userRepository.isEmailVerified(tooLongInvalidEmail)).thenReturn(true);

        ValidationResult result = emailValidatorService.validateEmail(tooLongInvalidEmail);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorList()).containsExactly(
                "Email must not exceed 254 characters",
                "Invalid email format",
                "Email is already verified");
    }

    @Test
    void validateEmail_treatsNullVerificationLookupAsUnverified() {
        when(userRepository.isEmailVerified("john.doe@example.com")).thenReturn(null);

        ValidationResult result = emailValidatorService.validateEmail("john.doe@example.com");

        assertThat(result.isValid()).isTrue();
    }
}
