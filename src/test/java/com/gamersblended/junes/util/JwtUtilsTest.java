package com.gamersblended.junes.util;

import io.jsonwebtoken.io.Decoders;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilsTest {

    private final JwtUtils jwtUtils = new JwtUtils();

    private static String base64Secret(String raw) {
        return Base64.getEncoder().encodeToString(raw.repeat(32).substring(0, 32).getBytes());
    }

    @Test
    void getSigningKey_returnsHmacKeyMatchingDecodedSecretBytes() {
        String secret = base64Secret("a");

        SecretKey key = jwtUtils.getSigningKey(secret);

        assertThat(key.getAlgorithm()).startsWith("HmacSHA");
        assertThat(key.getEncoded()).isEqualTo(Decoders.BASE64.decode(secret));
    }

    @Test
    void getSigningKey_isDeterministicForSameSecret() {
        String secret = base64Secret("a");

        SecretKey first = jwtUtils.getSigningKey(secret);
        SecretKey second = jwtUtils.getSigningKey(secret);

        assertThat(first.getEncoded()).isEqualTo(second.getEncoded());
    }

    @Test
    void getSigningKey_returnsDifferentKeysForDifferentSecrets() {
        SecretKey first = jwtUtils.getSigningKey(base64Secret("a"));
        SecretKey second = jwtUtils.getSigningKey(base64Secret("b"));

        assertThat(first.getEncoded()).isNotEqualTo(second.getEncoded());
    }

    @Test
    void getSigningKey_throwsForNonBase64Secret() {
        assertThatThrownBy(() -> jwtUtils.getSigningKey("not-valid-base64!!"))
                .isInstanceOf(io.jsonwebtoken.io.DecodingException.class);
    }

    @Test
    void getSigningKey_throwsForNullSecret() {
        assertThatThrownBy(() -> jwtUtils.getSigningKey(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getSigningKey_throwsForSecretShorterThanHmacShaMinimumKeyLength() {
        String shortSecret = Base64.getEncoder().encodeToString("short".getBytes());

        assertThatThrownBy(() -> jwtUtils.getSigningKey(shortSecret))
                .isInstanceOf(io.jsonwebtoken.security.WeakKeyException.class);
    }
}
