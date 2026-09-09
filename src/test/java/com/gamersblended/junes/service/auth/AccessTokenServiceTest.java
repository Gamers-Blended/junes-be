package com.gamersblended.junes.service.auth;

import com.gamersblended.junes.constant.Role;
import com.gamersblended.junes.exception.DatabaseInsertionException;
import com.gamersblended.junes.exception.InvalidTokenException;
import com.gamersblended.junes.model.TokenBlacklist;
import com.gamersblended.junes.model.User;
import com.gamersblended.junes.repository.jpa.TokenBlacklistRepository;
import com.gamersblended.junes.util.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.gamersblended.junes.constant.ConfigSettingsConstants.IAT_TIMESTAMP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessTokenServiceTest {

    private static final String SECRET = Base64.getEncoder().encodeToString("a".repeat(32).getBytes());
    private static final long EXPIRATION_MS = 604_800_000L;

    @Mock
    private TokenBlacklistRepository tokenBlacklistRepository;

    private AccessTokenService accessTokenService;

    @BeforeEach
    void setUp() {
        accessTokenService = new AccessTokenService(new JwtUtils(), tokenBlacklistRepository);
        ReflectionTestUtils.setField(accessTokenService, "accessSecretKey", SECRET);
        ReflectionTestUtils.setField(accessTokenService, "expirationTime", EXPIRATION_MS);
    }

    private static User user() {
        User user = new User();
        user.setUserID(UUID.randomUUID());
        user.setRole(Role.USER);
        return user;
    }

    private static Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(new JwtUtils().getSigningKey(SECRET))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ---- generateAccessToken ----

    @Test
    void generateAccessToken_includesUserClaims_andSubject() {
        User user = user();

        String token = accessTokenService.generateAccessToken(user, "john.doe@example.com");

        Claims claims = parse(token);
        assertThat(claims.getSubject()).isEqualTo(user.getUserID().toString());
        assertThat(claims)
                .containsEntry("userID", user.getUserID().toString())
                .containsEntry("email", "john.doe@example.com");
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) claims.get("roles", List.class);
        assertThat(roles).containsExactly("USER");
        assertThat(claims.get(IAT_TIMESTAMP)).isNotNull();
    }

    @Test
    void generateAccessToken_setsExpiration_afterConfiguredDuration() {
        String token = accessTokenService.generateAccessToken(user(), "john.doe@example.com");

        Claims claims = parse(token);
        Date expiration = accessTokenService.getExpirationFromToken(token);

        assertThat(expiration.getTime() - claims.getIssuedAt().getTime()).isEqualTo(EXPIRATION_MS);
    }

    // ---- getExpirationFromToken ----

    @Test
    void getExpirationFromToken_returnsExpirationFromValidToken() {
        String token = accessTokenService.generateAccessToken(user(), "john.doe@example.com");

        Date expiration = accessTokenService.getExpirationFromToken(token);

        assertThat(expiration).isAfter(new Date());
    }

    // ---- extractUserIDFromToken ----

    @Test
    void extractUserIDFromToken_returnsNull_whenAuthHeaderIsNull() {
        assertThat(accessTokenService.extractUserIDFromToken(null)).isNull();
    }

    @Test
    void extractUserIDFromToken_returnsNull_whenAuthHeaderMissingBearerPrefix() {
        assertThat(accessTokenService.extractUserIDFromToken("some-token")).isNull();
    }

    @Test
    void extractUserIDFromToken_returnsUserID_forValidToken() {
        User user = user();
        String token = accessTokenService.generateAccessToken(user, "john.doe@example.com");
        when(tokenBlacklistRepository.isTokenExist(token)).thenReturn(false);

        UUID extracted = accessTokenService.extractUserIDFromToken("Bearer " + token);

        assertThat(extracted).isEqualTo(user.getUserID());
    }

    @Test
    void extractUserIDFromToken_throwsInvalidTokenException_whenTokenIsBlacklisted() {
        String token = accessTokenService.generateAccessToken(user(), "john.doe@example.com");
        when(tokenBlacklistRepository.isTokenExist(token)).thenReturn(true);

        assertThatThrownBy(() -> accessTokenService.extractUserIDFromToken("Bearer " + token))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Token has been revoked");
    }

    @Test
    void extractUserIDFromToken_throwsInvalidTokenException_forMalformedToken() {
        when(tokenBlacklistRepository.isTokenExist("garbage")).thenReturn(false);

        assertThatThrownBy(() -> accessTokenService.extractUserIDFromToken("Bearer garbage"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Invalid or expired token");
    }

    // ---- validateAccessToken ----

    @Test
    void validateAccessToken_returnsTrue_forValidNonBlacklistedToken() {
        String token = accessTokenService.generateAccessToken(user(), "john.doe@example.com");
        when(tokenBlacklistRepository.isTokenExist(token)).thenReturn(false);

        assertThat(accessTokenService.validateAccessToken(token)).isTrue();
    }

    @Test
    void validateAccessToken_returnsFalse_whenTokenIsBlacklisted() {
        String token = accessTokenService.generateAccessToken(user(), "john.doe@example.com");
        when(tokenBlacklistRepository.isTokenExist(token)).thenReturn(true);

        assertThat(accessTokenService.validateAccessToken(token)).isFalse();
    }

    @Test
    void validateAccessToken_returnsFalse_forMalformedToken() {
        when(tokenBlacklistRepository.isTokenExist("garbage")).thenReturn(false);

        assertThat(accessTokenService.validateAccessToken("garbage")).isFalse();
    }

    // ---- blacklistToken ----

    @Test
    void blacklistToken_savesTokenWithExpiryFromClaims() {
        String token = accessTokenService.generateAccessToken(user(), "john.doe@example.com");

        accessTokenService.blacklistToken(token);

        ArgumentCaptor<TokenBlacklist> captor = ArgumentCaptor.forClass(TokenBlacklist.class);
        verify(tokenBlacklistRepository).save(captor.capture());
        assertThat(captor.getValue().getToken()).isEqualTo(token);
        assertThat(captor.getValue().getExpiryDate()).isNotNull();
    }

    @Test
    void blacklistToken_throwsDatabaseInsertionException_whenSaveFails() {
        String token = accessTokenService.generateAccessToken(user(), "john.doe@example.com");
        when(tokenBlacklistRepository.save(any())).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> accessTokenService.blacklistToken(token))
                .isInstanceOf(DatabaseInsertionException.class);
    }
}
