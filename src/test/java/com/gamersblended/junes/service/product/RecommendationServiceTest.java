package com.gamersblended.junes.service.product;

import com.gamersblended.junes.dto.recommender.ProductSignalDTO;
import com.gamersblended.junes.dto.recommender.RecommendationRequestDTO;
import com.gamersblended.junes.dto.recommender.RecommendationResponseDTO;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationServiceTest {

    private static final String SUCCESS_BODY =
            "{\"products\":[{\"product_id\":\"p1\",\"name\":\"Game 1\",\"slug\":\"game-1\"," +
                    "\"platform\":\"PS5\",\"region\":\"US\",\"edition\":\"Standard\",\"price\":59.99,\"product_image_url\":\"img.png\"}]}";

    private static RecommendationRequestDTO requestDTO() {
        RecommendationRequestDTO dto = new RecommendationRequestDTO();
        dto.setSignalList(List.of(new ProductSignalDTO("p1", "BROWSE", LocalDateTime.now())));
        dto.setMaxResult(20);
        return dto;
    }

    private static RecommendationService buildService(ExchangeFunction exchangeFunction) {
        return buildService(exchangeFunction, 3);
    }

    private static RecommendationService buildService(ExchangeFunction exchangeFunction, int timeoutSeconds) {
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        RecommendationService service = new RecommendationService(webClient, registry);
        ReflectionTestUtils.setField(service, "timeoutDurationSeconds", timeoutSeconds);
        return service;
    }

    private static ExchangeFunction respondWith(HttpStatus status, String body) {
        return request -> Mono.just(ClientResponse.create(status)
                .header("Content-Type", "application/json")
                .body(body)
                .build());
    }

    // ---- getRecommendations: success ----

    @Test
    void getRecommendations_returnsMappedResponse_onSuccessfulCall() {
        RecommendationService service = buildService(respondWith(HttpStatus.OK, SUCCESS_BODY));

        RecommendationResponseDTO result = service.getRecommendations(requestDTO()).block();

        assertThat(result).isNotNull();
        assertThat(result.getProducts()).hasSize(1);
        assertThat(result.getProducts().get(0).getProductID()).isEqualTo("p1");
        assertThat(result.getProducts().get(0).getName()).isEqualTo("Game 1");
    }

    @Test
    void getRecommendations_sendsPostRequest_withJsonContentType() {
        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        ExchangeFunction exchangeFunction = request -> {
            capturedRequest.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(SUCCESS_BODY)
                    .build());
        };
        RecommendationService service = buildService(exchangeFunction);

        service.getRecommendations(requestDTO()).block();

        assertThat(capturedRequest.get().method()).isEqualTo(HttpMethod.POST);
        assertThat(capturedRequest.get().url().getPath()).isEqualTo("/api/v1/products/");
        assertThat(Objects.requireNonNull(capturedRequest.get().headers().getContentType()).toString()).contains("application/json");
    }

    // ---- getRecommendations: 4xx client error ----

    @Test
    void getRecommendations_returnsEmpty_on4xxClientError() {
        RecommendationService service = buildService(respondWith(HttpStatus.BAD_REQUEST, "{\"error\":\"bad request\"}"));

        RecommendationResponseDTO result = service.getRecommendations(requestDTO()).block();

        assertThat(result).isNull();
    }

    // ---- getRecommendations: 5xx server error ----

    @Test
    void getRecommendations_returnsEmpty_on5xxServerError() {
        RecommendationService service = buildService(respondWith(HttpStatus.INTERNAL_SERVER_ERROR, "{\"error\":\"boom\"}"));

        RecommendationResponseDTO result = service.getRecommendations(requestDTO()).block();

        assertThat(result).isNull();
    }

    // ---- getRecommendations: network failure ----

    @Test
    void getRecommendations_returnsEmpty_onNetworkError() {
        ExchangeFunction exchangeFunction = request -> Mono.error(new WebClientRequestException(
                new IOException("Connection refused"), HttpMethod.POST, URI.create("http://localhost/api/v1/products/"), new HttpHeaders()));
        RecommendationService service = buildService(exchangeFunction);

        RecommendationResponseDTO result = service.getRecommendations(requestDTO()).block();

        assertThat(result).isNull();
    }

    // ---- getRecommendations: timeout ----

    @Test
    void getRecommendations_returnsEmpty_onTimeout() {
        ExchangeFunction exchangeFunction = request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body(SUCCESS_BODY)
                        .build())
                .delayElement(Duration.ofSeconds(2));
        RecommendationService service = buildService(exchangeFunction, 1);

        RecommendationResponseDTO result = service.getRecommendations(requestDTO()).block();

        assertThat(result).isNull();
    }

    // ---- getRecommendations: circuit breaker open ----

    @Test
    void getRecommendations_returnsEmpty_whenCircuitBreakerIsOpen() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(respondWith(HttpStatus.OK, SUCCESS_BODY))
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        RecommendationService service = new RecommendationService(webClient, registry);
        ReflectionTestUtils.setField(service, "timeoutDurationSeconds", 3);

        CircuitBreaker circuitBreaker = registry.circuitBreaker("recommendation-engine");
        circuitBreaker.transitionToOpenState();

        RecommendationResponseDTO result = service.getRecommendations(requestDTO()).block();

        assertThat(result).isNull();
    }
}
