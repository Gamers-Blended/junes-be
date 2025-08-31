package com.gamersblended.junes.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
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
        String clientKey = getClientKey(request);

        if (!rateLimiterService.isAllowed(clientKey)) {
            handleRateLimitExceeded(response, clientKey);
            return false;
        }

        // Add rate limit headers to response
        addRateLimitHeaders(response, clientKey);
        return true;
    }

    private String getClientKey(HttpServletRequest request) {
        // Use IP address as key
        String clientIp = getClientIpAddress(request);
        return "rate_limit:" + clientIp;
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

    private void addRateLimitHeaders(HttpServletResponse response, String clientKey) {
        long availableTokens = rateLimiterService.getAvailableTokens(clientKey);
        long resetTimeSeconds = rateLimiterService.getRemainingTimeInSeconds(clientKey);

        response.setHeader("X-RateLimit-Limit", "5");
        response.setHeader("X-RateLimit-Remaining", String.valueOf(availableTokens));
        response.setHeader("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() / 1000 + resetTimeSeconds));
    }

    private void handleRateLimitExceeded(HttpServletResponse response, String clientKey) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        long resetTimeSeconds = rateLimiterService.getRemainingTimeInSeconds(clientKey);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Rate limit exceeded");
        errorResponse.put("message", "Too many requests. Try again later.");
        errorResponse.put("retryAfterSeconds", resetTimeSeconds);
        errorResponse.put("timestamp", System.currentTimeMillis());

        response.setHeader("Retry-After", String.valueOf(resetTimeSeconds));
        addRateLimitHeaders(response, clientKey);

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
