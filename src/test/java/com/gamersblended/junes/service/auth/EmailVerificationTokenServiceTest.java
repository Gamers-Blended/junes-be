package com.gamersblended.junes.service.auth;

import com.gamersblended.junes.constant.TokenPurpose;
import com.gamersblended.junes.exception.*;
import com.gamersblended.junes.model.EmailVerificationToken;
import com.gamersblended.junes.model.User;
import com.gamersblended.junes.repository.jpa.EmailVerificationTokenRepository;
import com.gamersblended.junes.repository.jpa.UserRepository;
import com.gamersblended.junes.service.payment.StripeService;
import com.gamersblended.junes.util.TokenUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationTokenServiceTest {

    private static final ZoneId SGT = ZoneId.of("Asia/Singapore");

    @Mock
    private StripeService stripeService;
    @Mock
    private UserVerificationWriter userVerificationWriter;
    @Mock
    private SecureRandom secureRandom;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    private EmailVerificationTokenService emailVerificationTokenService;

    @BeforeEach
    void setUp() {
        emailVerificationTokenService = new EmailVerificationTokenService(
                stripeService, userVerificationWriter, secureRandom, userRepository, emailVerificationTokenRepository);
    }

    private static User user(UUID userID, String email, boolean emailVerified) {
        User user = new User();
        user.setUserID(userID);
        user.setEmail(email);
        user.setIsEmailVerified(emailVerified);
        return user;
    }

    // ---- generateVerificationToken ----

    @Test
    void generateVerificationToken_invalidatesOldTokens_andSavesNewOne_forSignupPurpose() throws Exception {
        UUID userID = UUID.randomUUID();

        String token = emailVerificationTokenService.generateVerificationToken(
                userID, "john.doe@example.com", TokenPurpose.SIGNUP_EMAIL);

        verify(emailVerificationTokenRepository).invalidateActiveTokens(userID, TokenPurpose.SIGNUP_EMAIL);
        ArgumentCaptor<EmailVerificationToken> captor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(emailVerificationTokenRepository).save(captor.capture());
        EmailVerificationToken saved = captor.getValue();
        assertThat(saved.getUserID()).isEqualTo(userID);
        assertThat(saved.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(saved.getPurpose()).isEqualTo(TokenPurpose.SIGNUP_EMAIL);
        assertThat(saved.isUsed()).isFalse();
        assertThat(saved.getTokenHash()).isEqualTo(TokenUtils.hashToken(token));
        assertThat(saved.getExpiryDate())
                .isCloseTo(LocalDateTime.now(SGT).plusHours(24), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void generateVerificationToken_setsShorterExpiry_forChangeEmailPurpose() throws Exception {
        UUID userID = UUID.randomUUID();

        emailVerificationTokenService.generateVerificationToken(userID, "john.doe@example.com", TokenPurpose.CHANGE_EMAIL);

        ArgumentCaptor<EmailVerificationToken> captor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(emailVerificationTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getExpiryDate())
                .isCloseTo(LocalDateTime.now(SGT).plusHours(5), within(1, ChronoUnit.MINUTES));
    }

    // ---- markSignupVerified ----

    @Test
    void markSignupVerified_throwsUserNotFoundException_whenUserMissing() {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserID(UUID.randomUUID());
        when(userRepository.getUserByID(token.getUserID())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationTokenService.markSignupVerified(token))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void markSignupVerified_throwsEmailAlreadyVerifiedException_whenUserAlreadyVerified() {
        UUID userID = UUID.randomUUID();
        User user = user(userID, "john.doe@example.com", true);
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserID(userID);
        token.setEmail("john.doe@example.com");
        when(userRepository.getUserByID(userID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> emailVerificationTokenService.markSignupVerified(token))
                .isInstanceOf(EmailAlreadyVerifiedException.class);
    }

    @Test
    void markSignupVerified_throwsEmailAlreadyInUseException_whenTokenEmailAlreadyVerifiedInDb() {
        UUID userID = UUID.randomUUID();
        User user = user(userID, "john.doe@example.com", false);
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserID(userID);
        token.setEmail("john.doe@example.com");
        when(userRepository.getUserByID(userID)).thenReturn(Optional.of(user));
        when(userRepository.isEmailVerified("john.doe@example.com")).thenReturn(true);

        assertThatThrownBy(() -> emailVerificationTokenService.markSignupVerified(token))
                .isInstanceOf(EmailAlreadyInUseException.class);
    }

    @Test
    void markSignupVerified_throwsInvalidTokenException_whenUserEmailChangedSinceTokenIssued() {
        UUID userID = UUID.randomUUID();
        User user = user(userID, "current.email@example.com", false);
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserID(userID);
        token.setEmail("stale.email@example.com");
        when(userRepository.getUserByID(userID)).thenReturn(Optional.of(user));
        when(userRepository.isEmailVerified("stale.email@example.com")).thenReturn(false);

        assertThatThrownBy(() -> emailVerificationTokenService.markSignupVerified(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void markSignupVerified_createsStripeCustomer_andCompletesSignup_whenValid() {
        UUID userID = UUID.randomUUID();
        User user = user(userID, "john.doe@example.com", false);
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserID(userID);
        token.setEmail("john.doe@example.com");
        when(userRepository.getUserByID(userID)).thenReturn(Optional.of(user));
        when(userRepository.isEmailVerified("john.doe@example.com")).thenReturn(false);
        when(stripeService.createCustomer(userID, "john.doe@example.com")).thenReturn("cus_123");

        emailVerificationTokenService.markSignupVerified(token);

        verify(userVerificationWriter).completeSignupVerification(user, "cus_123");
    }

    // ---- markEmailChangeVerified ----

    @Test
    void markEmailChangeVerified_throwsUserNotFoundException_whenUserMissing() {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserID(UUID.randomUUID());
        when(userRepository.getUserByID(token.getUserID())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationTokenService.markEmailChangeVerified(token))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void markEmailChangeVerified_throwsEmailAlreadyInUseException_whenNewEmailAlreadyVerified() {
        UUID userID = UUID.randomUUID();
        User user = user(userID, "old.email@example.com", true);
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserID(userID);
        token.setEmail("new.email@example.com");
        when(userRepository.getUserByID(userID)).thenReturn(Optional.of(user));
        when(userRepository.isEmailVerified("new.email@example.com")).thenReturn(true);

        assertThatThrownBy(() -> emailVerificationTokenService.markEmailChangeVerified(token))
                .isInstanceOf(EmailAlreadyInUseException.class);
    }

    @Test
    void markEmailChangeVerified_completesEmailChange_whenValid() {
        UUID userID = UUID.randomUUID();
        User user = user(userID, "old.email@example.com", true);
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserID(userID);
        token.setEmail("new.email@example.com");
        when(userRepository.getUserByID(userID)).thenReturn(Optional.of(user));
        when(userRepository.isEmailVerified("new.email@example.com")).thenReturn(false);

        emailVerificationTokenService.markEmailChangeVerified(token);

        verify(userVerificationWriter).completeEmailChange(user, "new.email@example.com");
    }

    // ---- cleanupUnverifiedEmails ----

    @Test
    void cleanupUnverifiedEmails_deletesUnverifiedRecords() {
        when(userRepository.deleteAllUnverifiedRecords()).thenReturn(3);

        emailVerificationTokenService.cleanupUnverifiedEmails();

        verify(userRepository).deleteAllUnverifiedRecords();
    }

    @Test
    void cleanupUnverifiedEmails_throwsDatabaseDeletionException_whenDeletionFails() {
        when(userRepository.deleteAllUnverifiedRecords()).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> emailVerificationTokenService.cleanupUnverifiedEmails())
                .isInstanceOf(DatabaseDeletionException.class);
    }
}
