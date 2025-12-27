package com.gamersblended.junes.config;

import com.gamersblended.junes.annotation.RateLimit;
import com.gamersblended.junes.dto.CreateUserRequest;
import com.gamersblended.junes.service.RateLimiterService;
import com.gamersblended.junes.util.JwtUtils;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    @Value("${jwt.verification.access.secret}")
    private String accessSecretKey;

    private final RateLimiterService rateLimiterService;
    private final JwtUtils jwtUtils;

    public RateLimitAspect(RateLimiterService rateLimiterService, JwtUtils jwtUtils) {
        this.rateLimiterService = rateLimiterService;
        this.jwtUtils = jwtUtils;
    }

    @Around("@annotation(com.gamersblended.junes.annotation.RateLimit) || " +
            "@within(com.gamersblended.junes.annotation.RateLimit)")
    public Object rateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // Get RateLimit annotation (method level takes precedence)
        RateLimit rateLimit = AnnotationUtils.findAnnotation(method, RateLimit.class);
        if (null == rateLimit) {
            rateLimit = AnnotationUtils.findAnnotation(method.getDeclaringClass(), RateLimit.class);
        }

        if (null == rateLimit) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = getCurrentRequest();
        String clientKey = getClientKey(request, rateLimit, joinPoint, method);

        if (!rateLimiterService.isAllowed(clientKey, rateLimit)) {
            return handleRateLimitExceeded(clientKey, rateLimit);
        }

        return joinPoint.proceed();
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attributes.getRequest();
    }

    private String getClientKey(HttpServletRequest request, RateLimit rateLimit,
                                ProceedingJoinPoint joinPoint, Method method) {
        StringBuilder keyBuilder = new StringBuilder();

        // Start with base key
        if (!rateLimit.key().isEmpty()) {
            keyBuilder.append(rateLimit.key());
        } else {
            // Default to method-based key
            String className = method.getDeclaringClass().getSimpleName();
            String methodName = method.getName();
            keyBuilder.append("rate_limit:").append(className).append(":").append(methodName);
        }

        // Add request body field value if specified
        if (!rateLimit.keyFromRequestBody().isEmpty()) {
            String fieldValue = extractFieldFromRequestBody(joinPoint, rateLimit.keyFromRequestBody());
            if (null != fieldValue) {
                keyBuilder.append(":").append(fieldValue);
                return keyBuilder.toString();
            }
        }

        // Add per-user identifier if specified
        if (rateLimit.perUser()) {
            String userID = getUserIdentifier(request);
            if (null != userID) {
                keyBuilder.append(":user:").append(userID);
                return keyBuilder.toString();
            }
        }

        // Default to IP address
        String ipAddress = getClientIpAddress(request);
        keyBuilder.append(":").append(ipAddress);

        return keyBuilder.toString();
    }

    private ResponseEntity<?> handleRateLimitExceeded(String clientKey, RateLimit rateLimit) {
        long resetTimeSeconds = rateLimiterService.getRemainingTimeInSeconds(clientKey, rateLimit);
        long timestamp = System.currentTimeMillis();
        Instant instant = Instant.ofEpochMilli(timestamp);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Rate limit exceeded");
        errorResponse.put("message", String.format("Too many requests. Limit: %d requests per %d %s",
                rateLimit.requests(), rateLimit.duration(), rateLimit.timeUnit().name().toLowerCase()));
        errorResponse.put("retryAfterSeconds", resetTimeSeconds);
        errorResponse.put("timestamp", instant.atZone(ZoneId.of("Asia/Singapore")).toString());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(resetTimeSeconds))
                .header("X-RateLimit-Limit", String.valueOf(rateLimit.requests()))
                .header("X-RateLimit-Remaining", "0")
                .header("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() / 1000 + resetTimeSeconds))
                .body(errorResponse);
    }

    private String extractFieldFromRequestBody(ProceedingJoinPoint joinPoint, String fieldName) {
        Object[] args = joinPoint.getArgs();

        for (Object arg : args) {
            if (arg instanceof CreateUserRequest request && "email".equals(fieldName)) {
                return request.getEmail();
            }

        }

        return null;
    }

    private String getUserIdentifier(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (null != authHeader && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                // Extract email (subject) from JWT
                return Jwts.parser()
                        .verifyWith(jwtUtils.getSigningKey(accessSecretKey))
                        .build()
                        .parseSignedClaims(token)
                        .getPayload()
                        .getSubject();
            } catch (Exception ex) {
                log.error("Invalid token, fall back to IP-based limiting");
                return null;
            }
        }
        return null;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (null != xForwardedFor && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (null != xRealIp && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}
