package com.gamersblended.junes.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${jwt.verification.access.secret}")
    private String accessSecretKey;

    private final JwtUtils jwtUtils;

    public JwtAuthenticationFilter(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (null == authHeader || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authHeader.substring(7);

            Claims claims = Jwts.parser()
                    .verifyWith(jwtUtils.getSigningKey(accessSecretKey))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userID = claims.getSubject();

            // Extract roles from claims
            List<String> roleList = claims.get("roles", List.class);

            // Convert roles to authorities with ROLE_ prefix if not already present
            List<SimpleGrantedAuthority> authorityList = roleList == null ? List.of() :
                    roleList.stream()
                            .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());

            if (null != userID && null == SecurityContextHolder.getContext().getAuthentication()) {
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        userID, // UUID as string
                        null,
                        authorityList // Contains ROLE_ADMIN, ROLE_USER
                );

                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
            // Filter chain handles 403 Forbidden based on SecurityConfig rules
        }

        filterChain.doFilter(request, response);
    }
}
