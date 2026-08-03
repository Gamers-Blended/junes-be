package com.gamersblended.junes.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Slf4j
@Component
public class TokenUtils {
    private TokenUtils() {
        /* This utility class should not be instantiated */
    }

    public static String hashToken(String token) throws NoSuchAlgorithmException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            log.error("Exception in hashing token: ", ex);
            throw new NoSuchAlgorithmException("SHA-256 algorithm not available", ex);
        }
    }
}
