package com.gamersblended.junes.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class RecommenderSystemClientConfigTest {

    private final RecommenderSystemClientConfig config = new RecommenderSystemClientConfig();

    @Test
    void recommenderWebClient_buildsWebClientForConfiguredBaseUrl() {
        ReflectionTestUtils.setField(config, "recommenderBaseUrl", "http://recommendation-engine:8000");
        ReflectionTestUtils.setField(config, "timeoutDurationSeconds", 3);

        WebClient webClient = config.recommenderWebClient();

        assertThat(webClient).isNotNull();
    }

    @Test
    void recommenderWebClient_returnsIndependentInstance_onEachCall() {
        ReflectionTestUtils.setField(config, "recommenderBaseUrl", "http://recommendation-engine:8000");
        ReflectionTestUtils.setField(config, "timeoutDurationSeconds", 3);

        WebClient first = config.recommenderWebClient();
        WebClient second = config.recommenderWebClient();

        assertThat(first).isNotSameAs(second);
    }
}
