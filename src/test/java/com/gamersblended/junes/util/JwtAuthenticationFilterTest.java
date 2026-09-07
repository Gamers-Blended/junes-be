package com.gamersblended.junes.util;

import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final SecretKey SIGNING_KEY = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
            "a".repeat(32).getBytes(StandardCharsets.UTF_8));

    private static final SecretKey WRONG_SIGNING_KEY = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
            "b".repeat(32).getBytes(StandardCharsets.UTF_8));

    @Mock
    private JwtUtils jwtUtils;

    private JwtAuthenticationFilter filter;

    private final UUID userID = UUID.randomUUID();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private JwtAuthenticationFilter buildFilter() {
        lenient().when(jwtUtils.getSigningKey(any())).thenReturn(SIGNING_KEY);
        return new JwtAuthenticationFilter(jwtUtils);
    }

    private String buildToken(SecretKey key, String subject, List<String> roles, Date issuedAt, Date expiration) {
        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(key);
        if (roles != null) {
            builder.claim("roles", roles);
        }
        return builder.compact();
    }

    private String buildValidToken(List<String> roles) {
        Date now = new Date();
        return buildToken(SIGNING_KEY, userID.toString(), roles, now, new Date(now.getTime() + 3600_000));
    }

    private AtomicBoolean runFilter(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainInvoked.set(true);

        filter.doFilterInternal(request, response, chain);

        return chainInvoked;
    }

    @Test
    void doFilterInternal_proceedsWithoutAuthenticationWhenNoAuthorizationHeader() throws Exception {
        filter = buildFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();

        AtomicBoolean chainInvoked = runFilter(request);

        assertThat(chainInvoked.get()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_proceedsWithoutAuthenticationWhenHeaderIsNotBearer() throws Exception {
        filter = buildFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        AtomicBoolean chainInvoked = runFilter(request);

        assertThat(chainInvoked.get()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_setsAuthenticationWithPrefixedRolesForValidToken() throws Exception {
        filter = buildFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + buildValidToken(List.of("ADMIN", "USER")));

        AtomicBoolean chainInvoked = runFilter(request);

        assertThat(chainInvoked.get()).isTrue();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(authentication.getPrincipal()).isEqualTo(userID.toString());
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void doFilterInternal_doesNotDoublePrefixRolesAlreadyContainingRolePrefix() throws Exception {
        filter = buildFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + buildValidToken(List.of("ROLE_ADMIN")));

        runFilter(request);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList())
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void doFilterInternal_setsAuthenticationWithNoAuthoritiesWhenRolesClaimAbsent() throws Exception {
        filter = buildFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + buildValidToken(null));

        runFilter(request);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities()).isEmpty();
    }

    @Test
    void doFilterInternal_proceedsWithoutAuthenticationWhenTokenSignatureIsInvalid() throws Exception {
        filter = buildFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        Date now = new Date();
        String tamperedToken = buildToken(WRONG_SIGNING_KEY, userID.toString(), List.of("ADMIN"), now,
                new Date(now.getTime() + 3600_000));
        request.addHeader("Authorization", "Bearer " + tamperedToken);

        AtomicBoolean chainInvoked = runFilter(request);

        assertThat(chainInvoked.get()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_proceedsWithoutAuthenticationWhenTokenIsExpired() throws Exception {
        filter = buildFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        Date past = new Date(System.currentTimeMillis() - 7200_000);
        String expiredToken = buildToken(SIGNING_KEY, userID.toString(), List.of("ADMIN"), past,
                new Date(past.getTime() + 1000));
        request.addHeader("Authorization", "Bearer " + expiredToken);

        AtomicBoolean chainInvoked = runFilter(request);

        assertThat(chainInvoked.get()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_doesNotOverwriteExistingAuthentication() throws Exception {
        filter = buildFilter();
        Authentication existing = new UsernamePasswordAuthenticationToken("existing-user", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + buildValidToken(List.of("ADMIN")));

        runFilter(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
    }
}
