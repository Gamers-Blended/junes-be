package com.gamersblended.junes.service;

import com.gamersblended.junes.model.User;
import com.gamersblended.junes.repository.jpa.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JunesUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public JunesUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.getUserByEmail(email)
                .orElseThrow(() -> {
                    log.error("User not found with email: {}", email);
                    return new UsernameNotFoundException("User not found with email: " + email);
                });

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUserID().toString())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .build();
    }
}
