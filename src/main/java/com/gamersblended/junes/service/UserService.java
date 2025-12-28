package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.reponse.UserDetailsResponse;
import com.gamersblended.junes.exception.UserNotFoundException;
import com.gamersblended.junes.repository.jpa.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserDetailsResponse getUserDetails(UUID userID) {

        String email = userRepository.getUserEmail(userID)
                .orElseThrow(() -> {
                    log.error("User not found with ID: {}", userID);
                    return new UserNotFoundException("User not found with ID: " + userID);
                });

        return new UserDetailsResponse(email);
    }
}
