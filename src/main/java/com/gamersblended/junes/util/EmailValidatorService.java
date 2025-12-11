package com.gamersblended.junes.util;

import com.gamersblended.junes.repository.jpa.UserRepository;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EmailValidatorService {

    private static final String EMAIL = "email";
    private static final int EMAIL_MAX_LENGTH = 254;

    private static final EmailValidator emailValidator = EmailValidator.getInstance();
    private final UserRepository userRepository;

    public EmailValidatorService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public ValidationResult validateEmail(String email) {
        List<String> errorList = new ArrayList<>();

        if (null == email || email.trim().isEmpty()) {
            errorList.add("Email cannot be empty");
            return new ValidationResult(EMAIL, false, errorList);
        }

        email = email.trim().toLowerCase();

        if (email.length() > EMAIL_MAX_LENGTH) {
            errorList.add("Email must not exceed " + EMAIL_MAX_LENGTH + " characters");
        }

        if (!emailValidator.isValid(email)) {
            errorList.add("Invalid email format");
        }

        if (Boolean.TRUE.equals(userRepository.isEmailVerified(email))) {
            errorList.add("Email is already verified");
        }

        return new ValidationResult(EMAIL, errorList.isEmpty(), errorList);
    }
}
