package com.gamersblended.junes.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level coverage for the plain {@code @Bean} methods on {@link SecurityConfig}.
 * <p>
 * The two {@code SecurityFilterChain} beans (route authorization rules, filter ordering) are covered
 * separately by {@link SecurityConfigWebMvcTest}, since building an {@link org.springframework.security.config.annotation.web.builders.HttpSecurity}
 * requires a Spring context rather than a plain constructor call.
 */
@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private com.gamersblended.junes.util.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Mock
    private com.gamersblended.junes.util.CorrelationIdFilter correlationIdFilter;

    private final SecurityConfig securityConfig = new SecurityConfig(jwtAuthenticationFilter, correlationIdFilter);

    @Test
    void passwordEncoder_returnsArgon2PasswordEncoder() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();

        assertThat(encoder).isInstanceOf(Argon2PasswordEncoder.class);
    }

    @Test
    void passwordEncoder_encodesAndMatchesSamePassword() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();

        String encoded = encoder.encode("correct-horse-battery-staple");

        assertThat(encoded).isNotEqualTo("correct-horse-battery-staple");
        assertThat(encoder.matches("correct-horse-battery-staple", encoded)).isTrue();
    }

    @Test
    void passwordEncoder_doesNotMatchDifferentPassword() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();

        String encoded = encoder.encode("correct-horse-battery-staple");

        assertThat(encoder.matches("wrong-password", encoded)).isFalse();
    }

    @Test
    void secureRandom_returnsUsableSecureRandomInstance() {
        SecureRandom secureRandom = securityConfig.secureRandom();

        assertThat(secureRandom).isNotNull();
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        assertThat(bytes).isNotEqualTo(new byte[16]);
    }
}
