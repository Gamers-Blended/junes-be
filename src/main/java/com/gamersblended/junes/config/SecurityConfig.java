package com.gamersblended.junes.config;

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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authz -> authz
                                .requestMatchers("/junes/api/v1/frontpage/**", "/junes/api/v1/product/**").permitAll() // Public APIs
//                        .requestMatchers("/junes/api/v1/cart/**").hasAnyRole("READER", "ADMIN") // Read-only
                                .requestMatchers("/junes/api/v1/cart/**").permitAll()
                                .requestMatchers("/actuator/health").permitAll() // Health check
                                .requestMatchers("/junes/api/v1/**").permitAll() // TODO temp
                                .anyRequest().authenticated() // All other requests require authentication
                )
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
}
