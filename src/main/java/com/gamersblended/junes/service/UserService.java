package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.reponse.UserDetailsResponse;
import com.gamersblended.junes.dto.request.UpdateEmailRequest;
import com.gamersblended.junes.exception.EmailDeliveryException;
import com.gamersblended.junes.exception.InputValidationException;
import com.gamersblended.junes.exception.UserNotFoundException;
import com.gamersblended.junes.model.User;
import com.gamersblended.junes.repository.jpa.UserRepository;
import com.gamersblended.junes.util.EmailValidatorService;
import com.gamersblended.junes.util.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;
    private final EmailValidatorService emailValidator;
    private final AuthService authService;

    public UserService(UserRepository userRepository, EmailValidatorService emailValidator, AuthService authService) {
        this.userRepository = userRepository;
        this.emailValidator = emailValidator;
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
            throw new InputValidationException("New email inputs are not valid: (" + emailValidation.getErrorMessage() + ")");
        }

        User user = userRepository.getUserByEmail(currentEmail)
                .orElseThrow(() -> {
                    log.error("User not found with current email: {}", currentEmail);
                    return new UserNotFoundException("User not found with current email: " + currentEmail);
                });

        if (!user.getUserID().equals(userID)) {
            log.error("Mismatched userID {} for email: {}", userID, currentEmail);
            throw new InputValidationException("Mismatched userID " + userID + " for email: " + currentEmail);
        }

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
}
