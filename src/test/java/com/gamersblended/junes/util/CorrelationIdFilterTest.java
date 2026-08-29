package com.gamersblended.junes.util;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.gamersblended.junes.constant.LoggingConstants.CORRELATION_ID_HEADER;
import static com.gamersblended.junes.constant.LoggingConstants.MDC_CORRELATION_ID_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesCorrelationIdWhenHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcDuringChain.set(MDC.get(MDC_CORRELATION_ID_KEY));

        filter.doFilterInternal(request, response, chain);

        String generated = mdcDuringChain.get();
        assertThat(generated).isNotBlank();
        assertThat(UUID.fromString(generated)).isNotNull();
        assertThat(response.getHeader(CORRELATION_ID_HEADER)).isEqualTo(generated);
    }

    @Test
    void generatesCorrelationIdWhenHeaderBlank() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CORRELATION_ID_HEADER, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcDuringChain.set(MDC.get(MDC_CORRELATION_ID_KEY));

        filter.doFilterInternal(request, response, chain);

        assertThat(mdcDuringChain.get()).isNotBlank();
        assertThat(response.getHeader(CORRELATION_ID_HEADER)).isEqualTo(mdcDuringChain.get());
    }

    @Test
    void reusesIncomingCorrelationId() throws Exception {
        String incoming = "test-correlation-id-123";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CORRELATION_ID_HEADER, incoming);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcDuringChain.set(MDC.get(MDC_CORRELATION_ID_KEY));

        filter.doFilterInternal(request, response, chain);

        assertThat(mdcDuringChain.get()).isEqualTo(incoming);
        assertThat(response.getHeader(CORRELATION_ID_HEADER)).isEqualTo(incoming);
    }

    @Test
    void clearsMdcAfterChainCompletes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { };

        filter.doFilterInternal(request, response, chain);

        assertThat(MDC.get(MDC_CORRELATION_ID_KEY)).isNull();
    }

    @Test
    void clearsMdcEvenWhenChainThrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doThrow(new RuntimeException("downstream failure")).when(chain).doFilter(request, response);

        try {
            filter.doFilterInternal(request, response, chain);
        } catch (RuntimeException expected) {
            // propagation is expected; MDC cleanup is what we're verifying
        }

        assertThat(MDC.get(MDC_CORRELATION_ID_KEY)).isNull();
        verify(chain).doFilter(request, response);
    }
}
