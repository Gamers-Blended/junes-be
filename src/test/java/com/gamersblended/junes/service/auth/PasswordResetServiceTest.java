package com.gamersblended.junes.service.auth;

import com.gamersblended.junes.exception.DatabaseDeletionException;
import com.gamersblended.junes.exception.InvalidTokenException;
import com.gamersblended.junes.model.PasswordResetToken;
import com.gamersblended.junes.model.User;
import com.gamersblended.junes.repository.jpa.PasswordResetTokenRepository;
import com.gamersblended.junes.repository.jpa.UserRepository;
import com.gamersblended.junes.service.email.EmailProducerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final String APP_URL = "https://junes.example.com";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetTokenRepository tokenRepository;
    @Mock
    private EmailProducerService emailProducerService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(userRepository, tokenRepository, emailProducerService, passwordEncoder);
        ReflectionTestUtils.setField(passwordResetService, "appUrl", APP_URL);
    }

    private static User user(UUID userID) {
        User user = new User();
        user.setUserID(userID);
        return user;
    }

    // ---- initiatePasswordReset ----

    @Test
    void initiatePasswordReset_doesNothing_whenUserNotFound() {
        when(userRepository.getUserByEmail("missing@example.com")).thenReturn(Optional.empty());

        passwordResetService.initiatePasswordReset("missing@example.com");

        verify(tokenRepository, never()).deleteByUserID(any());
        verify(emailProducerService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void initiatePasswordReset_deletesExistingTokens_andSendsResetEmail_whenUserFound() {
        UUID userID = UUID.randomUUID();
        User user = user(userID);
        when(userRepository.getUserByEmail("john.doe@example.com")).thenReturn(Optional.of(user));

        passwordResetService.initiatePasswordReset("john.doe@example.com");

        verify(tokenRepository).deleteByUserID(userID);
        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).saveAndFlush(captor.capture());
        PasswordResetToken saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getToken()).isNotBlank();
        assertThat(saved.getExpiryDate()).isAfter(LocalDateTime.now());

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailProducerService).sendPasswordResetEmail(eq("john.doe@example.com"), linkCaptor.capture());
        assertThat(linkCaptor.getValue()).startsWith(APP_URL + "/resetpassword/");
    }

    // ---- resetPassword ----

    @Test
    void resetPassword_throwsInvalidTokenException_whenTokenNotFound() {
        when(tokenRepository.getTokenEntityByToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword("bad-token", "NewP@ssw0rd"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Invalid or expired token");
    }

    @Test
    void resetPassword_throwsInvalidTokenException_whenTokenExpired() {
        PasswordResetToken token = new PasswordResetToken();
        token.setExpiryDate(LocalDateTime.now().minusHours(1));
        when(tokenRepository.getTokenEntityByToken("expired-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordResetService.resetPassword("expired-token", "NewP@ssw0rd"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Token has expired");
    }

    @Test
    void resetPassword_throwsInvalidTokenException_whenTokenAlreadyUsed() {
        PasswordResetToken token = new PasswordResetToken();
        token.setExpiryDate(LocalDateTime.now().plusHours(1));
        token.setUsed(true);
        when(tokenRepository.getTokenEntityByToken("used-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordResetService.resetPassword("used-token", "NewP@ssw0rd"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Token has already been used");
    }

    @Test
    void resetPassword_updatesPasswordHash_andMarksTokenUsed_whenValid() {
        User user = user(UUID.randomUUID());
        PasswordResetToken token = new PasswordResetToken();
        token.setExpiryDate(LocalDateTime.now().plusHours(1));
        token.setUser(user);
        when(tokenRepository.getTokenEntityByToken("valid-token")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewP@ssw0rd")).thenReturn("new-hashed-password");

        passwordResetService.resetPassword("valid-token", "NewP@ssw0rd");

        assertThat(user.getPasswordHash()).isEqualTo("new-hashed-password");
        verify(userRepository).save(user);
        assertThat(token.isUsed()).isTrue();
        verify(tokenRepository).save(token);
    }

    // ---- cleanupExpiredTokens ----

    @Test
    void cleanupExpiredTokens_deletesExpiredTokens() {
        when(tokenRepository.deleteByExpiryDateBefore(any())).thenReturn(2);

        passwordResetService.cleanupExpiredTokens();

        verify(tokenRepository).deleteByExpiryDateBefore(any());
    }

    @Test
    void cleanupExpiredTokens_throwsDatabaseDeletionException_whenDeletionFails() {
        when(tokenRepository.deleteByExpiryDateBefore(any())).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> passwordResetService.cleanupExpiredTokens())
                .isInstanceOf(DatabaseDeletionException.class);
    }
}
