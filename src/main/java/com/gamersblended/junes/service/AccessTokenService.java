package com.gamersblended.junes.service;

import com.gamersblended.junes.util.JwtUtils;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;

import static com.gamersblended.junes.constant.ConfigSettingsConstants.IAT_TIMESTAMP;

@Slf4j
@Service
public class AccessTokenService {

    @Value("${jwt.verification.access.secret}")
    private String accessSecretKey;

    @Value("${jwt.verification.access.expiration:604800000}") // 7 days default
    private long expirationTime;

    private final JwtUtils jwtUtils;

    public AccessTokenService(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    public String generateAccessToken(String email) {
        long issuedAtTime = System.currentTimeMillis();

        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date(issuedAtTime))
                .expiration(new Date(issuedAtTime + expirationTime))
                .claim(IAT_TIMESTAMP, issuedAtTime)
                .signWith(jwtUtils.getSigningKey(accessSecretKey))
                .compact();
    }
}
