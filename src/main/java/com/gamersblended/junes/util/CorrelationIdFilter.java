package com.gamersblended.junes.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

import static com.gamersblended.junes.constant.LoggingConstants.CORRELATION_ID_HEADER;
import static com.gamersblended.junes.constant.LoggingConstants.MDC_CORRELATION_ID_KEY;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incomingCorrelationID = request.getHeader(CORRELATION_ID_HEADER);
        String correlationID = (null == incomingCorrelationID || incomingCorrelationID.isBlank())
                ? UUID.randomUUID().toString()
                : incomingCorrelationID;

        MDC.put(MDC_CORRELATION_ID_KEY, correlationID);
        response.setHeader(CORRELATION_ID_HEADER, correlationID);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID_KEY);
        }
    }
}