package com.gamersblended.junes.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.annotation.RateLimit;
import com.gamersblended.junes.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    @Autowired
    public RateLimitInterceptor(RateLimiterService rateLimiterService, ObjectMapper objectMapper) {
        this.rateLimiterService = rateLimiterService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        // Skip rate limiting for non-handler methods (e.g., static resources)
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RateLimit rateLimit = getRateLimitAnnotation(handlerMethod);

        // Skip if no rate limit annotation found
        if (rateLimit == null) {
            return true;
        }

        String clientKey = getClientKey(request, rateLimit, handlerMethod);

        if (!rateLimiterService.isAllowed(clientKey, rateLimit)) {
            handleRateLimitExceeded(response, clientKey, rateLimit);
            return false;
        }

        // Add rate limit headers to response
        addRateLimitHeaders(response, clientKey, rateLimit);
        return true;
    }

    private RateLimit getRateLimitAnnotation(HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();

        // Check method-level annotation first (highest priority)
        RateLimit methodRateLimit = AnnotationUtils.findAnnotation(method, RateLimit.class);
        if (methodRateLimit != null) {
            return methodRateLimit;
        }

        // Check class-level annotation
        return AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), RateLimit.class);
    }

    private String getClientKey(HttpServletRequest request, RateLimit rateLimit, HandlerMethod handlerMethod) {
        String baseKey;

        if (!rateLimit.key().isEmpty()) {
            // Use custom key if specified
            baseKey = rateLimit.key();
        } else {
            // Default to endpoint-specific key
            String controllerName = handlerMethod.getBeanType().getSimpleName();
            String methodName = handlerMethod.getMethod().getName();
            baseKey = "rate_limit:" + controllerName + ":" + methodName;
        }

        // Use IP address as key
        String clientIdentifier = getClientIpAddress(request);
        return baseKey + ":" + clientIdentifier;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (null != xForwardedFor && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xReadIp = request.getHeader("X-Read-IP");
        if (null != xReadIp && !xReadIp.isEmpty()) {
            return xReadIp;
        }

        return request.getRemoteAddr();
    }

    private void addRateLimitHeaders(HttpServletResponse response, String clientKey, RateLimit rateLimit) {
        long availableTokens = rateLimiterService.getAvailableTokens(clientKey, rateLimit);
        long resetTimeSeconds = rateLimiterService.getRemainingTimeInSeconds(clientKey, rateLimit);

        response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimit.requests()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(availableTokens));
        response.setHeader("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() / 1000 + resetTimeSeconds));
        response.setHeader("X-RateLimit-Window", rateLimit.duration() + " " + rateLimit.timeUnit().name().toLowerCase());
    }

    private void handleRateLimitExceeded(HttpServletResponse response, String clientKey, RateLimit rateLimit) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        long resetTimeSeconds = rateLimiterService.getRemainingTimeInSeconds(clientKey, rateLimit);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Rate limit exceeded");
        errorResponse.put("message", String.format("Too many requests. Limit: %d requests per %d %s",
                rateLimit.requests(), rateLimit.duration(), rateLimit.timeUnit().name().toLowerCase()));
        errorResponse.put("retryAfterSeconds", resetTimeSeconds);
        errorResponse.put("timestamp", System.currentTimeMillis());

        response.setHeader("Retry-After", String.valueOf(resetTimeSeconds));
        addRateLimitHeaders(response, clientKey, rateLimit);

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
