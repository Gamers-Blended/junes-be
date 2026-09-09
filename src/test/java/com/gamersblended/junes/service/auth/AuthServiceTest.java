package com.gamersblended.junes.service.auth;

import com.gamersblended.junes.constant.Role;
import com.gamersblended.junes.dto.request.LoginRequest;
import com.gamersblended.junes.dto.response.LoginResponse;
import com.gamersblended.junes.dto.response.LogoutResponse;
import com.gamersblended.junes.dto.response.ValidationResult;
import com.gamersblended.junes.exception.*;
import com.gamersblended.junes.model.EmailVerificationToken;
import com.gamersblended.junes.model.User;
import com.gamersblended.junes.repository.jpa.EmailVerificationTokenRepository;
import com.gamersblended.junes.repository.jpa.UserRepository;
import com.gamersblended.junes.service.cart.CartService;
import com.gamersblended.junes.service.cart.WishlistService;
import com.gamersblended.junes.service.email.EmailProducerService;
import com.gamersblended.junes.util.EmailValidatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.gamersblended.junes.constant.TokenPurpose.CHANGE_EMAIL;
import static com.gamersblended.junes.constant.TokenPurpose.SIGNUP_EMAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String APP_URL = "https://junes.example.com";
    private static final String STRONG_PASSWORD = "StrongP@ss1";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailProducerService emailProducerService;
    @Mock
    private EmailValidatorService emailValidator;
    @Mock
    private EmailVerificationTokenService emailTokenService;
    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock
    private AccessTokenService accessTokenService;
    @Mock
    private CartService cartService;
    @Mock
    private WishlistService wishlistService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, emailProducerService, emailValidator,
                emailTokenService, emailVerificationTokenRepository, accessTokenService, cartService, wishlistService);
        ReflectionTestUtils.setField(authService, "appURL", APP_URL);
    }

    private static User activeVerifiedUser() {
        User user = new User();
        user.setUserID(UUID.randomUUID());
        user.setEmail("john.doe@example.com");
        user.setPasswordHash("hash");
        user.setRole(Role.USER);
        user.setIsActive(true);
        user.setIsEmailVerified(true);
        return user;
    }

    private static ValidationResult valid() {
        return new ValidationResult("email", true, List.of());
    }

    private static ValidationResult invalid() {
        return new ValidationResult("email", false, List.of("Invalid email format"));
    }

    // ---- addUser ----

    @Test
    void addUser_throwsInputValidationException_whenEmailInvalid() {
        when(emailValidator.validateEmail("bad-email")).thenReturn(invalid());

        assertThatThrownBy(() -> authService.addUser("bad-email", STRONG_PASSWORD))
                .isInstanceOf(InputValidationException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void addUser_throwsInputValidationException_whenPasswordInvalid() {
        when(emailValidator.validateEmail("john.doe@example.com")).thenReturn(valid());

        assertThatThrownBy(() -> authService.addUser("john.doe@example.com", "weak"))
                .isInstanceOf(InputValidationException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void addUser_createsUser_andSendsVerificationEmail_whenInputsValid() throws NoSuchAlgorithmException {
        when(emailValidator.validateEmail("john.doe@example.com")).thenReturn(valid());
        when(passwordEncoder.encode(STRONG_PASSWORD)).thenReturn("hashed-password");
        when(emailTokenService.generateVerificationToken(any(), eq("john.doe@example.com"), eq(SIGNUP_EMAIL)))
                .thenReturn("raw-token");

        authService.addUser("john.doe@example.com", STRONG_PASSWORD);

        verify(userRepository).deleteAllUnverifiedRecordsForEmail("john.doe@example.com");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getIsActive()).isTrue();
        verify(emailProducerService).sendVerificationEmail("john.doe@example.com",
                APP_URL + "/verify?token=raw-token", SIGNUP_EMAIL);
    }

    @Test
    void addUser_throwsEmailDeliveryException_whenVerificationEmailFailsToSend() throws NoSuchAlgorithmException {
        when(emailValidator.validateEmail("john.doe@example.com")).thenReturn(valid());
        when(passwordEncoder.encode(STRONG_PASSWORD)).thenReturn("hashed-password");
        when(emailTokenService.generateVerificationToken(any(), eq("john.doe@example.com"), eq(SIGNUP_EMAIL)))
                .thenThrow(new NoSuchAlgorithmException("boom"));

        assertThatThrownBy(() -> authService.addUser("john.doe@example.com", STRONG_PASSWORD))
                .isInstanceOf(EmailDeliveryException.class);

        verify(userRepository).save(any());
    }

    // ---- resendVerificationEmail ----

    @Test
    void resendVerificationEmail_throwsUserNotFoundException_whenUserDoesNotExist() {
        when(userRepository.getUserByEmail("john.doe@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resendVerificationEmail("john.doe@example.com"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void resendVerificationEmail_throwsEmailAlreadyVerifiedException_whenAlreadyVerified() {
        User user = activeVerifiedUser();
        when(userRepository.getUserByEmail("john.doe@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.resendVerificationEmail("john.doe@example.com"))
                .isInstanceOf(EmailAlreadyVerifiedException.class);
    }

    @Test
    void resendVerificationEmail_sendsVerificationEmail_whenUnverified() throws NoSuchAlgorithmException {
        User user = activeVerifiedUser();
        user.setIsEmailVerified(false);
        when(userRepository.getUserByEmail("john.doe@example.com")).thenReturn(Optional.of(user));
        when(emailTokenService.generateVerificationToken(user.getUserID(), "john.doe@example.com", SIGNUP_EMAIL))
                .thenReturn("raw-token");

        authService.resendVerificationEmail("john.doe@example.com");

        verify(emailProducerService).sendVerificationEmail("john.doe@example.com",
                APP_URL + "/verify?token=raw-token", SIGNUP_EMAIL);
    }

    @Test
    void resendVerificationEmail_throwsEmailDeliveryException_whenSendingFails() throws NoSuchAlgorithmException {
        User user = activeVerifiedUser();
        user.setIsEmailVerified(false);
        when(userRepository.getUserByEmail("john.doe@example.com")).thenReturn(Optional.of(user));
        when(emailTokenService.generateVerificationToken(any(), anyString(), eq(SIGNUP_EMAIL)))
                .thenThrow(new NoSuchAlgorithmException("boom"));

        assertThatThrownBy(() -> authService.resendVerificationEmail("john.doe@example.com"))
                .isInstanceOf(EmailDeliveryException.class);
    }

    // ---- verifyEmail ----

    @Test
    void verifyEmail_throwsInvalidTokenException_whenTokenNotFound() {
        when(emailVerificationTokenRepository.getTokenEntityByToken(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail("raw-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void verifyEmail_throwsInvalidTokenException_whenTokenExpired() {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setExpiryDate(LocalDateTime.now(ZoneId.of("Asia/Singapore")).minusHours(1));
        token.setPurpose(SIGNUP_EMAIL);
        when(emailVerificationTokenRepository.getTokenEntityByToken(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.verifyEmail("raw-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void verifyEmail_marksSignupVerified_forSignupPurpose() throws NoSuchAlgorithmException {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setEmail("john.doe@example.com");
        token.setPurpose(SIGNUP_EMAIL);
        token.setExpiryDate(LocalDateTime.now(ZoneId.of("Asia/Singapore")).plusHours(1));
        when(emailVerificationTokenRepository.getTokenEntityByToken(anyString())).thenReturn(Optional.of(token));

        authService.verifyEmail("raw-token");

        verify(emailTokenService).markSignupVerified(token);
        assertThat(token.isUsed()).isTrue();
        verify(emailVerificationTokenRepository).save(token);
        verify(emailProducerService).sendWelcomeEmail("john.doe@example.com");
    }

    @Test
    void verifyEmail_marksEmailChangeVerified_forChangeEmailPurpose() throws NoSuchAlgorithmException {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setEmail("new.email@example.com");
        token.setPurpose(CHANGE_EMAIL);
        token.setExpiryDate(LocalDateTime.now(ZoneId.of("Asia/Singapore")).plusHours(1));
        when(emailVerificationTokenRepository.getTokenEntityByToken(anyString())).thenReturn(Optional.of(token));

        authService.verifyEmail("raw-token");

        verify(emailTokenService).markEmailChangeVerified(token);
        assertThat(token.isUsed()).isTrue();
        verify(emailVerificationTokenRepository).save(token);
        verify(emailProducerService).sendWelcomeEmail("new.email@example.com");
    }

    // ---- login ----

    private static LoginRequest loginRequest(String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail("john.doe@example.com");
        request.setPassword(password);
        return request;
    }

    @Test
    void login_throwsUserNotFoundException_whenUserDoesNotExist() {
        when(userRepository.getUserByEmail("john.doe@example.com")).thenReturn(Optional.empty());
        LoginRequest request = loginRequest(STRONG_PASSWORD);

        assertThatThrownBy(() -> authService.login(request, null))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void login_throwsInputValidationException_whenPasswordDoesNotMatch() {
        User user = activeVerifiedUser();
        when(userRepository.getUserByEmail("john.doe@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hash")).thenReturn(false);
        LoginRequest request = loginRequest("wrong-password");

        assertThatThrownBy(() -> authService.login(request, null))
                .isInstanceOf(InputValidationException.class);
    }

    @Test
    void login_throwsUserDisabledException_whenAccountDisabled() {
        User user = activeVerifiedUser();
        user.setIsActive(false);
        when(userRepository.getUserByEmail("john.doe@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(STRONG_PASSWORD, "hash")).thenReturn(true);
        LoginRequest request = loginRequest(STRONG_PASSWORD);

        assertThatThrownBy(() -> authService.login(request, null))
                .isInstanceOf(UserDisabledException.class);
    }

    @Test
    void login_throwsUserNotVerifiedException_whenEmailNotVerified() {
        User user = activeVerifiedUser();
        user.setIsEmailVerified(false);
        when(userRepository.getUserByEmail("john.doe@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(STRONG_PASSWORD, "hash")).thenReturn(true);
        LoginRequest request = loginRequest(STRONG_PASSWORD);

        assertThatThrownBy(() -> authService.login(request, null))
                .isInstanceOf(UserNotVerifiedException.class);
    }

    @Test
    void login_returnsLoginResponse_andSkipsCartWishlistMerge_whenSessionIDNull() {
        User user = activeVerifiedUser();
        when(userRepository.getUserByEmail("john.doe@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(STRONG_PASSWORD, "hash")).thenReturn(true);
        when(accessTokenService.generateAccessToken(user, "john.doe@example.com")).thenReturn("access-token");

        LoginResponse response = authService.login(loginRequest(STRONG_PASSWORD), null);

        assertThat(response.getToken()).isEqualTo("access-token");
        assertThat(response.getUserID()).isEqualTo(user.getUserID());
        assertThat(response.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(user.getLastLoginAt()).isNotNull();
        verify(userRepository).save(user);
        verify(cartService, never()).mergeGuestCartIntoUserCart(any(), any());
        verify(wishlistService, never()).mergeGuestWishlistIntoUserWishlist(any(), any());
    }

    @Test
    void login_mergesGuestCartAndWishlist_whenSessionIDPresent() {
        User user = activeVerifiedUser();
        UUID sessionID = UUID.randomUUID();
        when(userRepository.getUserByEmail("john.doe@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(STRONG_PASSWORD, "hash")).thenReturn(true);
        when(accessTokenService.generateAccessToken(user, "john.doe@example.com")).thenReturn("access-token");

        authService.login(loginRequest(STRONG_PASSWORD), sessionID);

        verify(cartService).mergeGuestCartIntoUserCart(user.getUserID(), sessionID);
        verify(wishlistService).mergeGuestWishlistIntoUserWishlist(user.getUserID(), sessionID);
    }

    @Test
    void login_succeeds_whenCartMergeThrows() {
        User user = activeVerifiedUser();
        UUID sessionID = UUID.randomUUID();
        when(userRepository.getUserByEmail("john.doe@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(STRONG_PASSWORD, "hash")).thenReturn(true);
        when(accessTokenService.generateAccessToken(user, "john.doe@example.com")).thenReturn("access-token");
        doThrow(new RuntimeException("cart merge failed")).when(cartService)
                .mergeGuestCartIntoUserCart(any(), any());

        LoginResponse response = authService.login(loginRequest(STRONG_PASSWORD), sessionID);

        assertThat(response.getToken()).isEqualTo("access-token");
        verify(wishlistService).mergeGuestWishlistIntoUserWishlist(user.getUserID(), sessionID);
    }

    @Test
    void login_succeeds_whenWishlistMergeThrows() {
        User user = activeVerifiedUser();
        UUID sessionID = UUID.randomUUID();
        when(userRepository.getUserByEmail("john.doe@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(STRONG_PASSWORD, "hash")).thenReturn(true);
        when(accessTokenService.generateAccessToken(user, "john.doe@example.com")).thenReturn("access-token");
        doThrow(new RuntimeException("wishlist merge failed")).when(wishlistService)
                .mergeGuestWishlistIntoUserWishlist(any(), any());

        LoginResponse response = authService.login(loginRequest(STRONG_PASSWORD), sessionID);

        assertThat(response.getToken()).isEqualTo("access-token");
        verify(cartService).mergeGuestCartIntoUserCart(user.getUserID(), sessionID);
    }

    // ---- logout ----

    @Test
    void logout_throwsMissingTokenException_whenAuthHeaderIsNull() {
        assertThatThrownBy(() -> authService.logout(null))
                .isInstanceOf(MissingTokenException.class);
    }

    @Test
    void logout_throwsMissingTokenException_whenAuthHeaderMissingBearerPrefix() {
        assertThatThrownBy(() -> authService.logout("Basic abc123"))
                .isInstanceOf(MissingTokenException.class);
    }

    @Test
    void logout_throwsMissingTokenException_whenTokenIsEmpty() {
        assertThatThrownBy(() -> authService.logout("Bearer "))
                .isInstanceOf(MissingTokenException.class);
    }

    @Test
    void logout_blacklistsToken_andReturnsSuccessMessage_whenTokenValid() {
        when(accessTokenService.validateAccessToken("valid-token")).thenReturn(true);

        LogoutResponse response = authService.logout("Bearer valid-token");

        assertThat(response.getMessage()).isEqualTo("Logout successful");
        verify(accessTokenService).blacklistToken("valid-token");
    }

    @Test
    void logout_throwsInvalidTokenException_whenTokenInvalid() {
        when(accessTokenService.validateAccessToken("invalid-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.logout("Bearer invalid-token"))
                .isInstanceOf(InvalidTokenException.class);

        verify(accessTokenService, never()).blacklistToken(anyString());
    }
}
