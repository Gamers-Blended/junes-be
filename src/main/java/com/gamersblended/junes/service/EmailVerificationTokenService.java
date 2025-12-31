package com.gamersblended.junes.service;

import com.gamersblended.junes.exception.UserNotFoundException;
import com.gamersblended.junes.exception.VerificationException;
import com.gamersblended.junes.model.User;
import com.gamersblended.junes.repository.jpa.UserRepository;
import com.gamersblended.junes.util.JwtUtils;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;

import static com.gamersblended.junes.constant.ConfigSettingsConstants.IAT_TIMESTAMP;

@Slf4j
@Service
public class EmailVerificationTokenService {

    @Value("${jwt.verification.email.secret}")
    private String emailSecretKey;

    @Value("${jwt.verification.email.expiration:86400000}") // 24 hours default
    private long expirationTime;

    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;

    public EmailVerificationTokenService(UserRepository userRepository, JwtUtils jwtUtils) {
        this.userRepository = userRepository;
        this.jwtUtils = jwtUtils;
    }

    public String generateVerificationToken(String email, User user) throws NoSuchAlgorithmException {
        long issuedAtTime = System.currentTimeMillis();

        String token = Jwts.builder()
                .subject(email)
                .issuedAt(new Date(issuedAtTime))
                .expiration(new Date(issuedAtTime + expirationTime))
                .claim(IAT_TIMESTAMP, issuedAtTime)
                .signWith(jwtUtils.getSigningKey(emailSecretKey))
                .compact();

        String tokenHash = hashToken(token);
        user.setVerificationTokenHash(tokenHash);
        user.setVerificationTokenIssuedAt(issuedAtTime);

        log.info("Email verification token generated for {}", email);
        return token;
    }

    public String extractEmail(String token) {
        return Jwts.parser()
                .verifyWith(jwtUtils.getSigningKey(emailSecretKey))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            // Verify JWT signature and expiration
            var claims = Jwts.parser()
                    .verifyWith(jwtUtils.getSigningKey(emailSecretKey))
                    .build()
                    .parseSignedClaims(token);

            String email = claims.getPayload().getSubject();
            Long tokenIssuedAt = claims.getPayload().get(IAT_TIMESTAMP, Long.class);

            // Retrieve user and check if latest token
            User user = userRepository.getUserByEmail(email)
                    .orElseThrow(() -> {
                        log.error("User not found with email: {}", email);
                        return new UserNotFoundException("User not found with email: " + email);
                    });

            // Check if user is already verified
            if (Boolean.TRUE.equals(user.getIsEmailVerified())) {
                log.error("{} is already verified", email);
                return false;
            }

            // Check if token is the latest one
            if (null == tokenIssuedAt || !tokenIssuedAt.equals(user.getVerificationTokenIssuedAt())) {
                log.error("Token is not the latest one issued to user: {}", user.getUserID());
                return false;
            }

            // Check token hash
            String tokenHash = hashToken(token);
            if (!tokenHash.equals(user.getVerificationTokenHash())) {
                log.error("Token hash do not match");
                return false;
            }

            return true;
        } catch (JwtException | IllegalArgumentException | NoSuchAlgorithmException ex) {
            log.error("Exception in verifying email token: ", ex);
            return false;
        }
    }

    private String hashToken(String token) throws NoSuchAlgorithmException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            log.error("Exception in hashing token: ", ex);
            throw new NoSuchAlgorithmException("SHA-256 algorithm not available", ex);
        }
    }

    public void markAsVerified(String email) {
        User user = userRepository.getUserByEmail(email)
                .orElseThrow(() -> {
                    log.error("User not found with email: {}", email);
                    return new UserNotFoundException("User not found with email: " + email);
                });

        try {
            user.setIsEmailVerified(true);
            user.setVerificationTokenHash(null); // Clear token after verification
            user.setVerificationTokenIssuedAt(null);
            userRepository.save(user);

            log.info("The email: {} has been verified, userID: {}", email, user.getUserID());
        } catch (Exception ex) {
            log.error("Exception in verifying token in markAsVerified: ", ex);
            throw new VerificationException("Error in trying to verify : " + user.getEmail());
        }

    }
}
