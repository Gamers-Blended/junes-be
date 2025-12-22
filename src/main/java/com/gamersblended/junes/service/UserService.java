package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.CreateUserRequest;
import com.gamersblended.junes.exception.EmailAlreadyVerifiedException;
import com.gamersblended.junes.exception.EmailDeliveryException;
import com.gamersblended.junes.exception.InputValidationException;
import com.gamersblended.junes.exception.UserNotFoundException;
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

import java.util.List;

import static com.gamersblended.junes.util.PasswordValidator.validatePassword;

@Slf4j
@Service
public class UserService {

    @Value("${baseURL:}")
    private String baseURL;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailProducerService emailProducerService;
    private final EmailValidatorService emailValidator;
    private final EmailVerificationTokenService tokenService;
    public static final String VERIFY_EMAIL_ENDPOINT = "junes/api/v1/user/verify?token=";

    public UserService(
            @Qualifier("jpaUsersRepository") UserRepository userRepository, PasswordEncoder passwordEncoder,
            EmailProducerService emailProducerService,
            EmailValidatorService emailValidator,
            EmailVerificationTokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailProducerService = emailProducerService;
        this.emailValidator = emailValidator;
        this.tokenService = tokenService;
    }

    public List<User> getAllUsers() {
        List<User> res = userRepository.getAllUsers();
        log.info("Total number of users returned from db: {}", res.size());
        return res;

    }

    @Transactional
    public void addUser(CreateUserRequest createUserRequest) {
        String userEmail = createUserRequest.getEmail();
        ValidationResult emailValidation = emailValidator.validateEmail(userEmail);
        ValidationResult passwordValidation = validatePassword(createUserRequest.getPassword());
        boolean isValidEmail = emailValidation.isValid();
        boolean isValidPassword = passwordValidation.isValid();

        if (!(isValidEmail && isValidPassword)) {
            log.error("Inputs are not valid: (Email: {}, Password: {})", emailValidation.getErrorMessage(), passwordValidation.getErrorMessage());
            throw new InputValidationException("Inputs are not valid: (Email: " + emailValidation.getErrorMessage() + ", Password: " + passwordValidation.getErrorMessage() + ")");
        }

        // Delete unverified attempts of same email
        userRepository.deleteAllUnverifiedRecordsForEmail(userEmail);

        String hashedPassword = passwordEncoder.encode(createUserRequest.getPassword());

        User user = new User();
        user.setPasswordHash(hashedPassword);
        user.setEmail(userEmail);
        user.setIsActive(true);

        try {
            sendVerificationEmail(userEmail, user);
        } catch (Exception ex) {
            log.error("Exception in creating new user with email: {}: {}", userEmail, ex.getMessage());
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
            log.error("Exception in resending verification email to {}: {}", email, ex.getMessage());
            throw new EmailDeliveryException("Unable to resent verification email");
        }

    }

    private void sendVerificationEmail(String email, User user) {
        String token = tokenService.generateVerificationToken(email, user);
        userRepository.save(user);

        String verificationLink = baseURL + VERIFY_EMAIL_ENDPOINT + token;

        emailProducerService.sendVerificationEmail(email, verificationLink);
    }

    @Transactional
    public boolean verifyEmail(String token) {
        try {
            if (!tokenService.isTokenValid(token)) {
                return false;
            }
            tokenService.markAsVerified(token);
        } catch (Exception ex) {
            log.error("Exception in verifying token: ", ex);
            return false;
        }

        return true;
    }
}
