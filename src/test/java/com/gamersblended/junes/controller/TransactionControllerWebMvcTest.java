package com.gamersblended.junes.controller;

import com.gamersblended.junes.WebMvcTestApplication;
import com.gamersblended.junes.dto.TransactionDetailsDTO;
import com.gamersblended.junes.dto.TransactionHistoryDTO;
import com.gamersblended.junes.service.auth.AccessTokenService;
import com.gamersblended.junes.service.order.TransactionService;
import com.gamersblended.junes.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest + MockMvc slice test for {@link TransactionController}, replacing the previous
 * plain-Mockito style — see {@link AuthControllerWebMvcTest} for the pattern rationale.
 * <p>
 * Security filters are bypassed via {@code addFilters = false} since {@code /junes/api/v1/transaction/**}
 * falls under the {@code permitAll} catch-all in {@link com.gamersblended.junes.config.SecurityConfig}.
 * {@code getTransactionDetails}'s 401 handling for a missing/malformed {@code Authorization} header is
 * controller logic (not a filter or a mapped exception), so it's still reachable and tested here.
 */
@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = WebMvcTestApplication.class)
class TransactionControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    @MockBean
    private AccessTokenService accessTokenService;

    // JwtAuthenticationFilter is a @Component Filter, so @WebMvcTest instantiates it even with
    // addFilters = false; it needs a JwtUtils bean to construct, which this slice otherwise wouldn't provide.
    @MockBean
    private JwtUtils jwtUtils;

    @Test
    void getUserTransactionHistory_shouldReturnPagedHistory() throws Exception {
        String authHeader = "Bearer token123";
        UUID userID = UUID.randomUUID();
        TransactionHistoryDTO historyDTO = new TransactionHistoryDTO(
                "ORD-1", LocalDateTime.now(), "Delivered", BigDecimal.TEN, List.of());
        Page<TransactionHistoryDTO> page = new PageImpl<>(List.of(historyDTO));

        when(accessTokenService.extractUserIDFromToken(authHeader)).thenReturn(userID);
        when(transactionService.getTransactionHistory(eq(userID), any())).thenReturn(page);

        mockMvc.perform(get("/junes/api/v1/transaction/history")
                        .header("Authorization", authHeader)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderNumber").value("ORD-1"))
                .andExpect(jsonPath("$.content[0].status").value("Delivered"));

        verify(transactionService).getTransactionHistory(eq(userID), any());
    }

    @Test
    void getTransactionDetails_shouldReturnUnauthorized_whenAuthHeaderIsMissing() throws Exception {
        mockMvc.perform(get("/junes/api/v1/transaction/{orderNumber}/details", "ORD-1"))
                .andExpect(status().isUnauthorized());

        verify(accessTokenService, never()).extractUserIDFromToken(anyString());
        verify(transactionService, never()).getTransactionDetails(any(), anyString());
    }

    @Test
    void getTransactionDetails_shouldReturnUnauthorized_whenAuthHeaderDoesNotStartWithBearer() throws Exception {
        mockMvc.perform(get("/junes/api/v1/transaction/{orderNumber}/details", "ORD-1")
                        .header("Authorization", "Basic abc123"))
                .andExpect(status().isUnauthorized());

        verify(accessTokenService, never()).extractUserIDFromToken(anyString());
        verify(transactionService, never()).getTransactionDetails(any(), anyString());
    }

    @Test
    void getTransactionDetails_shouldReturnDetails_whenAuthHeaderIsValid() throws Exception {
        String authHeader = "Bearer token123";
        String orderNumber = "ORD-1";
        UUID userID = UUID.randomUUID();
        TransactionDetailsDTO detailsDTO = new TransactionDetailsDTO(
                orderNumber, LocalDateTime.now(), null, BigDecimal.TEN, List.of(), null,
                BigDecimal.ONE, BigDecimal.ONE, "TRACK-1");

        when(accessTokenService.extractUserIDFromToken(authHeader)).thenReturn(userID);
        when(transactionService.getTransactionDetails(userID, orderNumber)).thenReturn(detailsDTO);

        mockMvc.perform(get("/junes/api/v1/transaction/{orderNumber}/details", orderNumber)
                        .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.trackingNumber").value("TRACK-1"));

        verify(transactionService).getTransactionDetails(userID, orderNumber);
    }
}
