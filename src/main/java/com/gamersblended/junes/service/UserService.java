package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.CreateUserRequest;
import com.gamersblended.junes.model.User;
import com.gamersblended.junes.repository.jpa.UserRepository;
import com.gamersblended.junes.util.InputValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private InputValidationService inputValidationService;

    public List<User> getAllUsers() {
        List<User> res = userRepository.getAllUsers();
        log.info("Total number of users returned from db: {}", res.size());
        return res;

    }

    public String addUser(CreateUserRequest createUserRequest) {
        // Validate inputs
        boolean isValidEmail = inputValidationService.validateEmail(createUserRequest.getEmail()).isEmpty();
        boolean isValidPassword = validatePassword(createUserRequest.getPassword()).isValid();

        if (isValidEmail && isValidPassword) {
            return "Inputs are valid";
        }
        return "Inputs are not valid";
    }
}
