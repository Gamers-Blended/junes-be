package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.request.LoginRequest;
import com.gamersblended.junes.dto.reponse.LoginResponse;
import com.gamersblended.junes.dto.reponse.LogoutResponse;
import com.gamersblended.junes.exception.*;
import com.gamersblended.junes.model.User;
import com.gamersblended.junes.repository.jpa.UserRepository;
import com.gamersblended.junes.util.EmailValidatorService;
import com.gamersblended.junes.util.ValidationResult;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

import static com.gamersblended.junes.util.PasswordValidator.validatePassword;

@Slf4j
@Service
public class AuthService {

    @Value("${baseURL:}")
    private String baseURL;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailProducerService emailProducerService;
    private final EmailValidatorService emailValidator;
    private final EmailVerificationTokenService emailTokenService;
    private final AccessTokenService accessTokenService;
    public static final String VERIFY_EMAIL_ENDPOINT = "/junes/api/v1/auth/verify?token=";

    public AuthService(
            @Qualifier("jpaUsersRepository") UserRepository userRepository, PasswordEncoder passwordEncoder,
            EmailProducerService emailProducerService,
            EmailValidatorService emailValidator,
            EmailVerificationTokenService emailTokenService,
            AccessTokenService accessTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailProducerService = emailProducerService;
        this.emailValidator = emailValidator;
        this.emailTokenService = emailTokenService;
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
        user.setIsActive(true);

        try {
            sendVerificationEmail(email, user);
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
            sendVerificationEmail(email, user);
        } catch (Exception ex) {
            log.error("Exception in resending verification email to {}: ", email, ex);
            throw new EmailDeliveryException("Unable to resent verification email");
        }

    }

    public void sendVerificationEmail(String email, User user) throws NoSuchAlgorithmException {
        String token = emailTokenService.generateVerificationToken(email, user);
        userRepository.save(user);

        String verificationLink = baseURL + VERIFY_EMAIL_ENDPOINT + token;

        emailProducerService.sendVerificationEmail(email, verificationLink);

    }

    @Transactional
    public void verifyEmail(String token) {
        if (!emailTokenService.isTokenValid(token)) {
            log.error("Invalid or expired token");
            throw new InvalidTokenException("Invalid or expired token");
        }

        String email = emailTokenService.extractEmail(token);

        emailTokenService.markAsVerified(email);

        emailProducerService.sendWelcomeEmail(email);

    }

    @Transactional
    public LoginResponse login(LoginRequest loginRequest) {
        String email = loginRequest.getEmail();
        User user = userRepository.getUserByEmail(email)
                .orElseThrow(() -> {
                    log.error("User not found with email: {}", email);
                    return new UserNotFoundException("User not found with email: " + email);
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

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String token = accessTokenService.generateAccessToken(user.getUserID(), user.getEmail());

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
