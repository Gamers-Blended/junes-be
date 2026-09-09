package com.gamersblended.junes.service.auth;

import com.gamersblended.junes.constant.Role;
import com.gamersblended.junes.model.User;
import com.gamersblended.junes.repository.jpa.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JunesUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private JunesUserDetailsService junesUserDetailsService;

    @BeforeEach
    void setUp() {
        junesUserDetailsService = new JunesUserDetailsService(userRepository);
    }

    // ---- loadUserByUsername ----

    @Test
    void loadUserByUsername_returnsUserDetails_withUserIDAsUsername() {
        UUID userID = UUID.randomUUID();
        User user = new User();
        user.setUserID(userID);
        user.setPasswordHash("hashed-password");
        user.setRole(Role.ADMIN);
        when(userRepository.getUserByEmail("john.doe@example.com")).thenReturn(Optional.of(user));

        UserDetails userDetails = junesUserDetailsService.loadUserByUsername("john.doe@example.com");

        assertThat(userDetails.getUsername()).isEqualTo(userID.toString());
        assertThat(userDetails.getPassword()).isEqualTo("hashed-password");
        assertThat(userDetails.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void loadUserByUsername_throwsUsernameNotFoundException_whenUserDoesNotExist() {
        when(userRepository.getUserByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> junesUserDetailsService.loadUserByUsername("missing@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
