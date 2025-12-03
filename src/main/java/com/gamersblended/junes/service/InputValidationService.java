package com.gamersblended.junes.service;

import com.gamersblended.junes.repository.jpa.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class InputValidationService {

    public static final int EMAIL_LENGTH_LIMIT = 254;

    UserRepository userRepository;

    public InputValidationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String validateEmail(String email) {
        if (null == email || email.trim().isEmpty()) {
            return "Email not provided";
        }

        email = email.trim().toLowerCase();

        if (email.length() > EMAIL_LENGTH_LIMIT) {
            return "Email exceeds length limit";
        }

        if (!EmailValidator.getInstance().isValid(email)) {
            return "Invalid email format";
        }

        if (userRepository.doesEmailExistInDatabase(email)) {
            return "Email already exists, please use another email";
        }

        return "";
    }
}
