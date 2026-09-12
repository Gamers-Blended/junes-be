package com.gamersblended.junes.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.WebMvcTestApplication;
import com.gamersblended.junes.dto.AddressDTO;
import com.gamersblended.junes.dto.OrderItemDTO;
import com.gamersblended.junes.dto.request.PlaceOrderRequest;
import com.gamersblended.junes.service.auth.AccessTokenService;
import com.gamersblended.junes.service.order.OrderService;
import com.gamersblended.junes.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest + MockMvc slice test for {@link OrderController}, replacing the previous plain-Mockito
 * style — see {@link AuthControllerWebMvcTest} for the pattern rationale.
 * <p>
 * Security filters are bypassed via {@code addFilters = false} since {@code /junes/api/v1/order/**}
 * falls under the {@code permitAll} catch-all in {@link com.gamersblended.junes.config.SecurityConfig}.
 */
@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = WebMvcTestApplication.class)
class OrderControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private AccessTokenService accessTokenService;

    // JwtAuthenticationFilter is a @Component Filter, so @WebMvcTest instantiates it even with
    // addFilters = false; it needs a JwtUtils bean to construct, which this slice otherwise wouldn't provide.
    @MockBean
    private JwtUtils jwtUtils;

    @Test
    void orderPlace_validRequest_extractsUserIDAndReturnsOrderNumber() throws Exception {
        String authHeader = "Bearer some-token";
        String idempotencyKey = "idem-key-123";
        UUID userID = UUID.randomUUID();
        PlaceOrderRequest placeOrderRequest = new PlaceOrderRequest(
                new AddressDTO(), UUID.randomUUID(), List.of(new OrderItemDTO(1, "product-1")), BigDecimal.TEN);
        String orderNumber = "ORD-000123";

        when(accessTokenService.extractUserIDFromToken(authHeader)).thenReturn(userID);
        when(orderService.placeOrder(userID, placeOrderRequest, idempotencyKey)).thenReturn(orderNumber);

        mockMvc.perform(post("/junes/api/v1/order/place")
                        .header("Authorization", authHeader)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(placeOrderRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(orderNumber));

        verify(accessTokenService).extractUserIDFromToken(authHeader);
        verify(orderService).placeOrder(userID, placeOrderRequest, idempotencyKey);
    }
}
