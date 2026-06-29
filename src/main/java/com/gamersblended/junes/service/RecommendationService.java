package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.ProductSliderItemDTO;
import com.gamersblended.junes.dto.recommender.ProductRecommendationDTO;
import com.gamersblended.junes.dto.recommender.RecommendationRequestDTO;
import com.gamersblended.junes.dto.recommender.RecommendationResponseDTO;
import com.gamersblended.junes.exception.RecommendationClientException;
import com.gamersblended.junes.exception.RecommendationServerException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class RecommendationService {

    private final WebClient recommenderClient;
    private static final String RECOMMENDATIONS_ENDPOINT = "/api/v1/products/";

    public RecommendationService(WebClient recommenderClient) {
        this.recommenderClient = recommenderClient;
    }

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
                                    return Mono.error(new RecommendationServerException(
                                            "Recommendation engine server error: " + response.statusCode()));
                                })
                )
                .bodyToMono(RecommendationResponseDTO.class)
                .timeout(Duration.ofSeconds(3), Mono.defer(() -> {
                    log.warn("[RecommendationService] Request timed out — returning empty for fallback");
                    return Mono.empty();
                }))
                .onErrorResume(RecommendationServerException.class, ex -> {
                    log.warn("[RecommendationService] Server error propagated for circuit breaker: {}", ex.getMessage());
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
                .doOnSuccess(result -> {
                    if (result != null) {
                        log.info("[RecommendationService] Received {} recommendations", result.getTotal());
                    }
                });
    }

}
