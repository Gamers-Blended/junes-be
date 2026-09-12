package com.gamersblended.junes.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebConfigTest {

    private static final String[] ORIGINS = {"https://junes.example.com"};
    private static final String[] METHODS = {"GET", "POST"};
    private static final String[] HEADERS = {"Authorization", "Content-Type"};
    private static final boolean CREDENTIALS = true;
    private static final int MAX_AGE = 3600;

    private final WebConfig webConfig = new WebConfig();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(webConfig, "allowedOrigins", ORIGINS);
        ReflectionTestUtils.setField(webConfig, "allowedMethods", METHODS);
        ReflectionTestUtils.setField(webConfig, "allowedHeaders", HEADERS);
        ReflectionTestUtils.setField(webConfig, "allowedCredentials", CREDENTIALS);
        ReflectionTestUtils.setField(webConfig, "maxAge", MAX_AGE);
    }

    @Test
    void addCorsMappings_registersMappingWithConfiguredValues() {
        CorsRegistry registry = mock(CorsRegistry.class);
        CorsRegistration registration = mock(CorsRegistration.class);
        when(registry.addMapping("/junes/api/v1/**")).thenReturn(registration);
        when(registration.allowedOrigins(ORIGINS)).thenReturn(registration);
        when(registration.allowedMethods(METHODS)).thenReturn(registration);
        when(registration.allowedHeaders(HEADERS)).thenReturn(registration);
        when(registration.allowCredentials(CREDENTIALS)).thenReturn(registration);

        webConfig.addCorsMappings(registry);

        verify(registry).addMapping("/junes/api/v1/**");
        verify(registration).allowedOrigins(ORIGINS);
        verify(registration).allowedMethods(METHODS);
        verify(registration).allowedHeaders(HEADERS);
        verify(registration).allowCredentials(CREDENTIALS);
        verify(registration).maxAge(MAX_AGE);
    }

    @Test
    void corsConfigurationSource_registersConfigurationForAllPaths() {
        CorsConfigurationSource source = webConfig.corsConfigurationSource();

        assertThat(source).isInstanceOf(UrlBasedCorsConfigurationSource.class);
        CorsConfiguration configuration = ((UrlBasedCorsConfigurationSource) source)
                .getCorsConfigurations().get("/**");
        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly(ORIGINS);
        assertThat(configuration.getAllowedMethods()).containsExactly(METHODS);
        assertThat(configuration.getAllowedHeaders()).containsExactly(HEADERS);
        assertThat(configuration.getAllowCredentials()).isEqualTo(CREDENTIALS);
    }
}
