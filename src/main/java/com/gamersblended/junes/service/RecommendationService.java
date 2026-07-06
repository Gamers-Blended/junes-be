package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.recommender.RecommendationRequestDTO;
import com.gamersblended.junes.dto.recommender.RecommendationResponseDTO;
import com.gamersblended.junes.exception.RecommendationClientException;
import com.gamersblended.junes.exception.RecommendationServerException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class RecommendationService {

    private final WebClient recommenderClient;
    private final CircuitBreaker circuitBreaker;
    private static final String RECOMMENDATIONS_ENDPOINT = "/api/v1/products/";
    private static final String CB_NAME = "recommendation-engine";

    public RecommendationService(WebClient recommenderClient, CircuitBreakerRegistry registry) {
        this.recommenderClient = recommenderClient;
        this.circuitBreaker = registry.circuitBreaker(CB_NAME);

        this.circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.warn("[RecommendationService] Circuit breaker '{}' state transition: {} -> {}",
                        CB_NAME, event.getStateTransition().getFromState(), event.getStateTransition().getToState()));
    }

    /**
     * Fetches recommendations from recommendation engine
     * Falls back gracefully on timeout, 5xx errors, or network failures
     *
     * @param requestDTO signals and max result count
     * @return a Mono of RecommendationResponseDTO, or empty on recoverable failure
     */
    public Mono<RecommendationResponseDTO> getRecommendations(RecommendationRequestDTO requestDTO) {
        log.info("Calling Recommender API with {} input ID(s)...", requestDTO.getSignalList().size());
        return recommenderClient
                .post()
                .uri(RECOMMENDATIONS_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestDTO)
                .retrieve()
                .onStatus(
                        HttpStatusCode::is4xxClientError,
                        response -> response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("[RecommendationService] 4xx from recommendation engine: status={}, body={}",
                                            response.statusCode(), body);
                                    // 4xx = bad request; don't retry, propagate
                                    return Mono.error(new RecommendationClientException(
                                            "Recommendation engine rejected request: " + response.statusCode()));
                                })
                )
                .onStatus(
                        HttpStatusCode::is5xxServerError,
                        response -> response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("[RecommendationService] 5xx from recommendation engine: status={}, body={}",
                                            response.statusCode(), body);
                                    // 5xx = upstream failure; circuit breaker will catch this
                                    return Mono.error(new RecommendationServerException(
                                            "Recommendation engine server error: " + response.statusCode()));
                                })
                )
                .bodyToMono(RecommendationResponseDTO.class)
                .timeout(Duration.ofSeconds(3))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .doOnSuccess(result -> {
                    if (null != result) {
                        log.info("[RecommendationService] Received {} recommendations", result.getProducts().size());
                    }
                })
                .onErrorResume(CallNotPermittedException.class, ex -> {
                    log.warn("[RecommendationService] Circuit breaker OPEN — short-circuiting, returning empty");
                    return Mono.empty();
                })
                .onErrorResume(RecommendationServerException.class, ex -> {
                    // Circuit breaker re-throws or returns empty
                    log.warn("[RecommendationService] Server error, falling back: {}", ex.getMessage());
                    return Mono.error(ex);
                })
                .onErrorResume(RecommendationClientException.class, ex -> {
                    log.error("[RecommendationService] Client error — not retrying: {}", ex.getMessage());
                    return Mono.empty();
                })
                .onErrorResume(WebClientRequestException.class, ex -> {
                    // Network-level failure (DNS, connection refused, etc.)
                    log.error("[RecommendationService] Network error reaching recommendation engine: {}", ex.getMessage());
                    return Mono.empty();
                })
                .onErrorResume(TimeoutException.class, ex -> {
                    log.warn("[RecommendationService] Request timed out — returning empty for fallback");
                    return Mono.empty();
                });
    }

}
