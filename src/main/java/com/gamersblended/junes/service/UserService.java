package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.CreateUserRequest;
import com.gamersblended.junes.model.User;
import com.gamersblended.junes.repository.jpa.UserRepository;
import com.gamersblended.junes.util.EmailValidatorService;
import com.gamersblended.junes.util.ValidationResult;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.gamersblended.junes.util.PasswordValidator.validatePassword;

@Slf4j
@Service
public class UserService {

    @Autowired
    @Qualifier("jpaUsersRepository")
    private UserRepository userRepository;

    @Autowired
    private EmailValidatorService emailValidator;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public List<User> getAllUsers() {
        List<User> res = userRepository.getAllUsers();
        log.info("Total number of users returned from db: {}", res.size());
        return res;

    }

    @Transactional
    public String addUser(CreateUserRequest createUserRequest) {
        // Validate inputs
        ValidationResult emailValidation = emailValidator.validateEmail(createUserRequest.getEmail());
        ValidationResult passwordValidation = validatePassword(createUserRequest.getPassword());
        boolean isValidEmail = emailValidation.isValid();
        boolean isValidPassword = passwordValidation.isValid();

        if (!(isValidEmail && isValidPassword)) {
            return "Inputs are not valid: (Email: " + emailValidation.getErrorMessage() + ", Password: " + passwordValidation.getErrorMessage() + ")";
        }

        // Delete unverified attempts of same email
        userRepository.deleteAllUnverifiedRecordsForEmail(createUserRequest.getEmail());

        // Encode password
        String hashedPassword = passwordEncoder.encode(createUserRequest.getPassword());

        User user = new User();
        user.setPasswordHash(hashedPassword);
        user.setEmail(createUserRequest.getEmail());
        user.setIsActive(true);
        user.setIsEmailVerified(false);

        userRepository.save(user);
        return "done";
    }
}
