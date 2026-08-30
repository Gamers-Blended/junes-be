package com.gamersblended.junes.config;

import com.gamersblended.junes.util.CorrelationIdFilter;
import com.gamersblended.junes.util.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.security.SecureRandom;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorrelationIdFilter correlationIdFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, CorrelationIdFilter correlationIdFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.correlationIdFilter = correlationIdFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authz -> authz
                                .requestMatchers("/junes/api/v1/frontpage/**", "/junes/api/v1/product/**").permitAll() // Public APIs
//                        .requestMatchers("/junes/api/v1/cart/**").hasAnyRole("READER", "ADMIN") // Read-only
                                .requestMatchers("/junes/api/v1/cart/**").permitAll()
                                .requestMatchers("/junes/api/v1/wishlist/**").permitAll()
                                // /actuator/** is served on separate management port (see management.server.port in application*.properties)
                                // Never reaches this filter chain — kept off the main JWT-secured API surface deliberately
                                // Isolated via Docker network rather than app-level auth
                                .requestMatchers("/junes/api/v1/housekeep/**").hasRole("ADMIN")
                                .requestMatchers("/junes/api/v1/**").permitAll() // TODO temp
                                .requestMatchers("/junes/api/v1/auth/**").permitAll()
                                .anyRequest().authenticated() // All other requests require authentication
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(correlationIdFilter, JwtAuthenticationFilter.class)
                .httpBasic(httpBasic -> httpBasic.realmName("ReadOnlyAPI"))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .csrf(AbstractHttpConfigurer::disable) // Disable CSRF for API (stateless)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }
}
