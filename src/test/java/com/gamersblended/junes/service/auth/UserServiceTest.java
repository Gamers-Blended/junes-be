package com.gamersblended.junes.service.auth;

import com.gamersblended.junes.constant.TokenPurpose;
import com.gamersblended.junes.dto.request.UpdateEmailRequest;
import com.gamersblended.junes.dto.request.UpdatePasswordRequest;
import com.gamersblended.junes.dto.response.UserDetailsResponse;
import com.gamersblended.junes.dto.response.ValidationResult;
import com.gamersblended.junes.exception.EmailDeliveryException;
import com.gamersblended.junes.exception.EmailNotFoundException;
import com.gamersblended.junes.exception.InputValidationException;
import com.gamersblended.junes.exception.UserNotFoundException;
import com.gamersblended.junes.model.User;
import com.gamersblended.junes.repository.jpa.UserRepository;
import com.gamersblended.junes.service.email.EmailProducerService;
import com.gamersblended.junes.util.EmailValidatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String STRONG_PASSWORD = "StrongP@ss1";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailValidatorService emailValidator;
    @Mock
    private EmailProducerService emailProducerService;
    @Mock
    private AuthService authService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, emailValidator, emailProducerService, authService);
    }

    private static ValidationResult valid() {
        return new ValidationResult("email", true, List.of());
    }

    private static ValidationResult invalid() {
        return new ValidationResult("email", false, List.of("Invalid email format"));
    }

    private static UpdateEmailRequest updateEmailRequest(String currentEmail, String newEmail) {
        UpdateEmailRequest request = new UpdateEmailRequest();
        request.setCurrentEmail(currentEmail);
        request.setNewEmail(newEmail);
        return request;
    }

    private static UpdatePasswordRequest updatePasswordRequest(String currentPassword, String newPassword) {
        UpdatePasswordRequest request = new UpdatePasswordRequest();
        request.setCurrentPassword(currentPassword);
        request.setNewPassword(newPassword);
        return request;
    }

    // ---- getUserDetails ----

    @Test
    void getUserDetails_returnsEmail_whenUserExists() {
        UUID userID = UUID.randomUUID();
        when(userRepository.getUserEmail(userID)).thenReturn(Optional.of("john.doe@example.com"));

        UserDetailsResponse response = userService.getUserDetails(userID);

        assertThat(response.getEmail()).isEqualTo("john.doe@example.com");
    }

    @Test
    void getUserDetails_throwsEmailNotFoundException_whenUserMissing() {
        UUID userID = UUID.randomUUID();
        when(userRepository.getUserEmail(userID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserDetails(userID))
                .isInstanceOf(EmailNotFoundException.class);
    }

    // ---- updateEmail ----

    @Test
    void updateEmail_throwsInputValidationException_whenNewEmailSameAsCurrent() {
        UUID userID = UUID.randomUUID();
        UpdateEmailRequest request = updateEmailRequest("john.doe@example.com", "john.doe@example.com");

        assertThatThrownBy(() -> userService.updateEmail(userID, request))
                .isInstanceOf(InputValidationException.class);
    }

    @Test
    void updateEmail_throwsInputValidationException_whenNewEmailInvalid() {
        UUID userID = UUID.randomUUID();
        when(emailValidator.validateEmail("new@example.com")).thenReturn(invalid());
        UpdateEmailRequest request = updateEmailRequest("old@example.com", "new@example.com");

        assertThatThrownBy(() -> userService.updateEmail(userID, request))
                .isInstanceOf(InputValidationException.class);
    }

    @Test
    void updateEmail_throwsUserNotFoundException_whenUserDoesNotMatchCurrentEmail() {
        UUID userID = UUID.randomUUID();
        when(emailValidator.validateEmail("new@example.com")).thenReturn(valid());
        when(userRepository.getUserByUserIDAndEmail(userID, "old@example.com")).thenReturn(Optional.empty());
        UpdateEmailRequest request = updateEmailRequest("old@example.com", "new@example.com");

        assertThatThrownBy(() -> userService.updateEmail(userID, request))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateEmail_throwsInputValidationException_whenCurrentEmailNotVerified() {
        UUID userID = UUID.randomUUID();
        User user = new User();
        user.setIsEmailVerified(false);
        when(emailValidator.validateEmail("new@example.com")).thenReturn(valid());
        when(userRepository.getUserByUserIDAndEmail(userID, "old@example.com")).thenReturn(Optional.of(user));
        UpdateEmailRequest request = updateEmailRequest("old@example.com", "new@example.com");

        assertThatThrownBy(() -> userService.updateEmail(userID, request))
                .isInstanceOf(InputValidationException.class);
    }

    @Test
    void updateEmail_throwsInputValidationException_whenNewEmailAlreadyInUse() {
        UUID userID = UUID.randomUUID();
        User user = new User();
        user.setIsEmailVerified(true);
        when(emailValidator.validateEmail("new@example.com")).thenReturn(valid());
        when(userRepository.getUserByUserIDAndEmail(userID, "old@example.com")).thenReturn(Optional.of(user));
        when(userRepository.isEmailVerified("new@example.com")).thenReturn(true);
        UpdateEmailRequest request = updateEmailRequest("old@example.com", "new@example.com");

        assertThatThrownBy(() -> userService.updateEmail(userID, request))
                .isInstanceOf(InputValidationException.class);
    }

    @Test
    void updateEmail_sendsVerificationEmail_whenValid() throws NoSuchAlgorithmException {
        UUID userID = UUID.randomUUID();
        User user = new User();
        user.setIsEmailVerified(true);
        when(emailValidator.validateEmail("new@example.com")).thenReturn(valid());
        when(userRepository.getUserByUserIDAndEmail(userID, "old@example.com")).thenReturn(Optional.of(user));
        when(userRepository.isEmailVerified("new@example.com")).thenReturn(false);

        userService.updateEmail(userID, updateEmailRequest("old@example.com", "new@example.com"));

        verify(authService).sendVerificationEmail("new@example.com", user, TokenPurpose.CHANGE_EMAIL);
    }

    @Test
    void updateEmail_throwsEmailDeliveryException_whenSendingFails() throws NoSuchAlgorithmException {
        UUID userID = UUID.randomUUID();
        User user = new User();
        user.setIsEmailVerified(true);
        when(emailValidator.validateEmail("new@example.com")).thenReturn(valid());
        when(userRepository.getUserByUserIDAndEmail(userID, "old@example.com")).thenReturn(Optional.of(user));
        when(userRepository.isEmailVerified("new@example.com")).thenReturn(false);
        doThrow(new NoSuchAlgorithmException("boom")).when(authService)
                .sendVerificationEmail("new@example.com", user, TokenPurpose.CHANGE_EMAIL);
        UpdateEmailRequest request = updateEmailRequest("old@example.com", "new@example.com");

        assertThatThrownBy(() -> userService.updateEmail(userID, request))
                .isInstanceOf(EmailDeliveryException.class);
    }

    // ---- updatePassword ----

    @Test
    void updatePassword_throwsInputValidationException_whenNewPasswordSameAsCurrent() {
        UUID userID = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        UpdatePasswordRequest passwordRequest = updatePasswordRequest(STRONG_PASSWORD, STRONG_PASSWORD);

        assertThatThrownBy(() -> userService.updatePassword(userID, passwordRequest, request))
                .isInstanceOf(InputValidationException.class);
    }

    @Test
    void updatePassword_throwsInputValidationException_whenNewPasswordWeak() {
        UUID userID = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        UpdatePasswordRequest passwordRequest = updatePasswordRequest(STRONG_PASSWORD, "weak");

        assertThatThrownBy(() -> userService.updatePassword(userID, passwordRequest, request))
                .isInstanceOf(InputValidationException.class);
    }

    @Test
    void updatePassword_throwsUserNotFoundException_whenUserMissing() {
        UUID userID = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(userRepository.getUserByID(userID)).thenReturn(Optional.empty());
        UpdatePasswordRequest passwordRequest = updatePasswordRequest("OldP@ssw0rd", STRONG_PASSWORD);

        assertThatThrownBy(() -> userService.updatePassword(userID, passwordRequest, request))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updatePassword_throwsInputValidationException_whenCurrentPasswordIncorrect() {
        UUID userID = UUID.randomUUID();
        User user = new User();
        user.setPasswordHash("hash");
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(userRepository.getUserByID(userID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldP@ssw0rd", "hash")).thenReturn(false);
        UpdatePasswordRequest passwordRequest = updatePasswordRequest("OldP@ssw0rd", STRONG_PASSWORD);

        assertThatThrownBy(() -> userService.updatePassword(userID, passwordRequest, request))
                .isInstanceOf(InputValidationException.class);
    }

    @Test
    void updatePassword_updatesPasswordHash_andSendsNotificationEmail_whenValid() {
        UUID userID = UUID.randomUUID();
        User user = new User();
        user.setEmail("john.doe@example.com");
        user.setPasswordHash("hash");
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(userRepository.getUserByID(userID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldP@ssw0rd", "hash")).thenReturn(true);
        when(passwordEncoder.encode(STRONG_PASSWORD)).thenReturn("new-hashed-password");

        userService.updatePassword(userID, updatePasswordRequest("OldP@ssw0rd", STRONG_PASSWORD), request);

        assertThat(user.getPasswordHash()).isEqualTo("new-hashed-password");
        verify(userRepository).save(user);
        verify(emailProducerService).sendPasswordChangedEmail("john.doe@example.com", request);
    }
}
