package com.gamersblended.junes.service;

import com.gamersblended.junes.exception.DatabaseInsertionException;
import com.gamersblended.junes.exception.InvalidTokenException;
import com.gamersblended.junes.model.TokenBlacklist;
import com.gamersblended.junes.model.User;
import com.gamersblended.junes.repository.jpa.TokenBlacklistRepository;
import com.gamersblended.junes.util.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static com.gamersblended.junes.constant.ConfigSettingsConstants.IAT_TIMESTAMP;

@Slf4j
@Service
public class AccessTokenService {

    @Value("${jwt.verification.access.secret}")
    private String accessSecretKey;

    @Value("${jwt.verification.access.expiration:604800000}") // 7 days default
    private long expirationTime;

    private final JwtUtils jwtUtils;
    private final TokenBlacklistRepository tokenBlacklistRepository;

    public AccessTokenService(JwtUtils jwtUtils, TokenBlacklistRepository tokenBlacklistRepository) {
        this.jwtUtils = jwtUtils;
        this.tokenBlacklistRepository = tokenBlacklistRepository;
    }

    public String generateAccessToken(User user, String email) {
        long issuedAtTime = System.currentTimeMillis();
        Map<String, Object> claims = new HashMap<>();
        claims.put("userID", user.getUserID().toString());
        claims.put("roles", List.of(user.getRole().name()));
        claims.put(IAT_TIMESTAMP, issuedAtTime);
        claims.put("email", email);

        return Jwts.builder()
                .subject(user.getUserID().toString())
                .claims(claims)
                .issuedAt(new Date(issuedAtTime))
                .expiration(new Date(issuedAtTime + expirationTime))
                .signWith(jwtUtils.getSigningKey(accessSecretKey))
                .compact();
    }

    public Date getExpirationFromToken(String token) {
        return Jwts.parser()
                .verifyWith(jwtUtils.getSigningKey(accessSecretKey))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();
    }

    public UUID extractUserIDFromToken(String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");

            Claims claims = Jwts.parser()
                    .verifyWith(jwtUtils.getSigningKey(accessSecretKey))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return UUID.fromString(claims.getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            log.error("Token validation failed: ", ex);
            throw new InvalidTokenException("Invalid or expired token");
        }
    }

    public boolean validateAccessToken(String token) {
        try {
            if (Boolean.TRUE.equals(tokenBlacklistRepository.isTokenExist(token))) {
                log.error("Access token is blacklisted");
                return false;
            }

            Jwts.parser()
                    .verifyWith(jwtUtils.getSigningKey(accessSecretKey))
                    .build()
                    .parseSignedClaims(token);

            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.error("Exception in verifying access token: ", ex);
            return false;
        }
    }

    public void blacklistToken(String token) {
        TokenBlacklist blacklistToken = new TokenBlacklist();
        blacklistToken.setToken(token);

        Date expirationDate = getExpirationFromToken(token);
        blacklistToken.setExpiryDate(
                LocalDateTime.ofInstant(expirationDate.toInstant(), ZoneId.systemDefault())
        );

        try {
            tokenBlacklistRepository.save(blacklistToken);
        } catch (Exception ex) {
            log.error("Exception in blacklisting token: ", ex);
            throw new DatabaseInsertionException("Exception in blacklisting token");
        }

        log.info("Token: {} successfully blacklisted with expiry: {}", blacklistToken.getToken(), blacklistToken.getExpiryDate());
    }
}
