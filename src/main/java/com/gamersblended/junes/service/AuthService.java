package com.gamersblended.junes.service;

import com.gamersblended.junes.constant.Role;
import com.gamersblended.junes.constant.TokenPurpose;
import com.gamersblended.junes.dto.request.LoginRequest;
import com.gamersblended.junes.dto.response.LoginResponse;
import com.gamersblended.junes.dto.response.LogoutResponse;
import com.gamersblended.junes.exception.*;
import com.gamersblended.junes.model.EmailVerificationToken;
import com.gamersblended.junes.model.User;
import com.gamersblended.junes.repository.jpa.EmailVerificationTokenRepository;
import com.gamersblended.junes.repository.jpa.UserRepository;
import com.gamersblended.junes.util.EmailValidatorService;
import com.gamersblended.junes.util.ValidationResult;
import com.stripe.exception.StripeException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static com.gamersblended.junes.constant.TokenPurpose.SIGNUP_EMAIL;
import static com.gamersblended.junes.util.PasswordValidator.validatePassword;
import static com.gamersblended.junes.util.TokenUtils.hashToken;

@Slf4j
@Service
public class AuthService {

    @Value("${app.url:}")
    private String appURL;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailProducerService emailProducerService;
    private final EmailValidatorService emailValidator;
    private final EmailVerificationTokenService emailTokenService;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final AccessTokenService accessTokenService;
    public static final String VERIFY_EMAIL_ROUTE = "/verify?token=";

    public AuthService(
            @Qualifier("jpaUsersRepository") UserRepository userRepository, PasswordEncoder passwordEncoder,
            EmailProducerService emailProducerService,
            EmailValidatorService emailValidator,
            EmailVerificationTokenService emailTokenService,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            AccessTokenService accessTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailProducerService = emailProducerService;
        this.emailValidator = emailValidator;
        this.emailTokenService = emailTokenService;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.accessTokenService = accessTokenService;
    }

    @Transactional
    public void addUser(String email, String password) {
        ValidationResult emailValidation = emailValidator.validateEmail(email);
        ValidationResult passwordValidation = validatePassword(password);
        boolean isValidEmail = emailValidation.isValid();
        boolean isValidPassword = passwordValidation.isValid();

        if (!(isValidEmail && isValidPassword)) {
            log.error("Inputs are not valid: (Email: {}, Password: {})", emailValidation.getErrorMessage(), passwordValidation.getErrorMessage());
            throw new InputValidationException("Inputs are not valid: (Email: " + emailValidation.getErrorMessage() + ", Password: " + passwordValidation.getErrorMessage() + ")");
        }

        // Delete unverified attempts of same email
        userRepository.deleteAllUnverifiedRecordsForEmail(email);

        String hashedPassword = passwordEncoder.encode(password);

        User user = new User();
        user.setPasswordHash(hashedPassword);
        user.setEmail(email);
        user.setRole(Role.USER);
        user.setIsActive(true);
        userRepository.save(user);

        try {
            sendVerificationEmail(email, user, SIGNUP_EMAIL);
        } catch (Exception ex) {
            log.error("Exception in creating new user with email: {}: ", email, ex);
            throw new EmailDeliveryException("Unable to send verification email");
        }

    }

    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.getUserByEmail(email)
                .orElseThrow(() -> {
                    log.error("User not found with email: {}", email);
                    return new UserNotFoundException("User not found with email: " + email);
                });

        if (Boolean.TRUE.equals(user.getIsEmailVerified())) {
            log.error("{} is already verified", email);
            throw new EmailAlreadyVerifiedException(email + " is already verified");
        }

        try {
            sendVerificationEmail(email, user, SIGNUP_EMAIL);
        } catch (Exception ex) {
            log.error("Exception in resending verification email to {}: ", email, ex);
            throw new EmailDeliveryException("Unable to resent verification email");
        }

    }

    public void sendVerificationEmail(String email, User user, TokenPurpose purpose) throws NoSuchAlgorithmException {
        String token = emailTokenService.generateVerificationToken(user.getUserID(), email, purpose);

        String verificationLink = appURL + VERIFY_EMAIL_ROUTE + token;

        emailProducerService.sendVerificationEmail(email, verificationLink, purpose);

    }

    @Transactional
    public void verifyEmail(String token) throws NoSuchAlgorithmException, StripeException {
        String tokenHash = hashToken(token);

        EmailVerificationToken emailVerificationToken = emailVerificationTokenRepository
                .getTokenEntityByToken(tokenHash)
                .filter(t -> t.getExpiryDate().isAfter(LocalDateTime.now(ZoneId.of("Asia/Singapore"))))
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired token"));

        switch (emailVerificationToken.getPurpose()) {
            case SIGNUP_EMAIL -> emailTokenService.markSignupVerified(emailVerificationToken);
            case CHANGE_EMAIL -> emailTokenService.markEmailChangeVerified(emailVerificationToken);
            default ->
                    throw new InvalidTokenException("Unsupported token purpose: " + emailVerificationToken.getPurpose());
        }

        emailVerificationToken.setUsed(true);
        emailVerificationTokenRepository.save(emailVerificationToken);

        emailProducerService.sendWelcomeEmail(emailVerificationToken.getEmail());

    }

    @Transactional
    public LoginResponse login(LoginRequest loginRequest) {
        String email = loginRequest.getEmail();
        User user = userRepository.getUserByEmail(email)
                .orElseThrow(() -> {
                    log.error("User not found with email: {}", email);
                    return new UserNotFoundException("Invalid email or password");
                });

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash())) {
            log.error("Invalid password");
            throw new InputValidationException("Invalid email or password");
        }

        if (Boolean.FALSE.equals(user.getIsActive())) {
            log.error("Account is disabled, userID: {}", user.getUserID());
            throw new UserDisabledException("Account is disabled");
        }

        if (Boolean.FALSE.equals(user.getIsEmailVerified())) {
            log.error("User's email is not verified, email: {}", user.getEmail());
            throw new UserNotVerifiedException("User's email is not verified");
        }

        user.setLastLoginAt(LocalDateTime.now(ZoneId.of("Asia/Singapore")));
        userRepository.save(user);

        String token = accessTokenService.generateAccessToken(user, email);

        LoginResponse loginResponse = new LoginResponse();
        loginResponse.setToken(token);
        loginResponse.setUserID(user.getUserID());
        loginResponse.setEmail(user.getEmail());

        log.info("User successfully logged in, userID: {}", user.getUserID());
        return loginResponse;

    }

    @Transactional
    public LogoutResponse logout(String authHeader) {
        String token = null;
        if (null != authHeader && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        if (null == token || token.isEmpty()) {
            log.error("No token provided for logout");
            throw new MissingTokenException("No token provided for logout");
        }

        if (accessTokenService.validateAccessToken(token)) {
            accessTokenService.blacklistToken(token);
            log.info("Token is blacklisted from logout");

            LogoutResponse logoutResponse = new LogoutResponse();
            logoutResponse.setMessage("Logout successful");
            return logoutResponse;
        } else {
            log.error("Token used for logout is invalid");
            throw new InvalidTokenException("Invalid access token used for logout");
        }
    }
}
