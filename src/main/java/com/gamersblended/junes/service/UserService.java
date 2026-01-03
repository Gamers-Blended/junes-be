package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.reponse.UserDetailsResponse;
import com.gamersblended.junes.dto.request.UpdateEmailRequest;
import com.gamersblended.junes.dto.request.UpdatePasswordRequest;
import com.gamersblended.junes.exception.EmailDeliveryException;
import com.gamersblended.junes.exception.InputValidationException;
import com.gamersblended.junes.exception.UserNotFoundException;
import com.gamersblended.junes.model.User;
import com.gamersblended.junes.repository.jpa.UserRepository;
import com.gamersblended.junes.util.EmailValidatorService;
import com.gamersblended.junes.util.ValidationResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.gamersblended.junes.util.PasswordValidator.validatePassword;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailValidatorService emailValidator;
    private final EmailProducerService emailProducerService;
    private final AuthService authService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       EmailValidatorService emailValidator, EmailProducerService emailProducerService, AuthService authService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailValidator = emailValidator;
        this.emailProducerService = emailProducerService;
        this.authService = authService;
    }

    public UserDetailsResponse getUserDetails(UUID userID) {

        String email = userRepository.getUserEmail(userID)
                .orElseThrow(() -> {
                    log.error("User not found with ID: {}", userID);
                    return new UserNotFoundException("User not found with ID: " + userID);
                });

        return new UserDetailsResponse(email);
    }

    public void updateEmail(UUID userID, UpdateEmailRequest updateEmailRequest) {

        String currentEmail = updateEmailRequest.getCurrentEmail();
        String newEmail = updateEmailRequest.getNewEmail();

        if (currentEmail.equals(newEmail)) {
            log.error("New email is the same as current one: {}", currentEmail);
            throw new InputValidationException("New email is the same as current one: " + currentEmail);
        }

        ValidationResult emailValidation = emailValidator.validateEmail(newEmail);

        if (!emailValidation.isValid()) {
            log.error("Validation error(s) for email: {}", emailValidation.getErrorMessage());
            throw new InputValidationException("New email input is not valid: (" + emailValidation.getErrorMessage() + ")");
        }

        User user = userRepository.getUserByUserIDAndEmail(userID, currentEmail)
                .orElseThrow(() -> {
                    log.error("User {} not found with current email: {}", userID, currentEmail);
                    return new UserNotFoundException("User not found with current email: " + currentEmail);
                });

        if (Boolean.FALSE.equals(user.getIsEmailVerified())) {
            log.error("Current email {} is not verified", currentEmail);
            throw new InputValidationException("Current email " + currentEmail + " is not verified");
        }

        if (Boolean.TRUE.equals(userRepository.isEmailVerified(newEmail))) {
            log.error("New email: {} is already in use, use another email", newEmail);
            throw new InputValidationException("New email " + newEmail + " is already in use, please use another email");
        }

        try {
            authService.sendVerificationEmail(newEmail, user);
        } catch (Exception ex) {
            log.error("Exception in updating email: {} for userID: {}: ", newEmail, userID, ex);
            throw new EmailDeliveryException("Unable to send verification email for update email");
        }
    }

    @Transactional
    public void updatePassword(UUID userID, UpdatePasswordRequest updatePasswordRequest, HttpServletRequest request) {

        String currentPassword = updatePasswordRequest.getCurrentPassword();
        String newPassword = updatePasswordRequest.getNewPassword();

        if (currentPassword.equals(newPassword)) {
            log.info("New password is the same as current one");
            throw new InputValidationException("New password is the same as current one");
        }

        ValidationResult passwordValidation = validatePassword(newPassword);
        boolean isValidPassword = passwordValidation.isValid();

        if (!isValidPassword) {
            log.error("Validation error(s) for password: {}", passwordValidation.getErrorMessage());
            throw new InputValidationException("New password is not valid: (" + passwordValidation.getErrorMessage() + ")");
        }

        User user = userRepository.getUserByID(userID)
                .orElseThrow(() -> {
                    log.error("User not found with ID: {}", userID);
                    return new UserNotFoundException("User not found");
                });

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            log.error("Current password does not match user's in database, userID: {}", userID);
            throw new InputValidationException("Current password does not match user's in database, userID: " + userID);
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        emailProducerService.sendPasswordChangedEmail(user.getEmail(), request);
    }
}
