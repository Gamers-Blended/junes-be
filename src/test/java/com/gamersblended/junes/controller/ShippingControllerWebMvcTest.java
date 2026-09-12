package com.gamersblended.junes.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.WebMvcTestApplication;
import com.gamersblended.junes.dto.OrderItemDTO;
import com.gamersblended.junes.dto.request.CalculateShippingRequest;
import com.gamersblended.junes.service.cart.ShippingService;
import com.gamersblended.junes.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest + MockMvc slice test for {@link ShippingController}, replacing the previous plain-Mockito
 * style — see {@link AuthControllerWebMvcTest} for the pattern rationale.
 * <p>
 * Security filters are bypassed via {@code addFilters = false} since {@code /junes/api/v1/shipping/**}
 * falls under the {@code permitAll} catch-all in {@link com.gamersblended.junes.config.SecurityConfig}.
 */
@WebMvcTest(ShippingController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = WebMvcTestApplication.class)
class ShippingControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ShippingService shippingService;

    // JwtAuthenticationFilter is a @Component Filter, so @WebMvcTest instantiates it even with
    // addFilters = false; it needs a JwtUtils bean to construct, which this slice otherwise wouldn't provide.
    @MockBean
    private JwtUtils jwtUtils;

    @Test
    void getShippingFee_returnsCalculatedFee_onSuccess() throws Exception {
        List<OrderItemDTO> items = List.of(new OrderItemDTO(2, "product-1"));
        CalculateShippingRequest request = new CalculateShippingRequest(items);
        when(shippingService.getShippingFee(items)).thenReturn(BigDecimal.valueOf(9.99));

        mockMvc.perform(post("/junes/api/v1/shipping/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingCost", closeTo(9.99, 0.001)));

        verify(shippingService).getShippingFee(items);
    }

    @Test
    void getShippingFee_passesOrderItemListThrough_unchanged() throws Exception {
        List<OrderItemDTO> items = List.of(new OrderItemDTO(1, "product-1"), new OrderItemDTO(3, "product-2"));
        CalculateShippingRequest request = new CalculateShippingRequest(items);
        when(shippingService.getShippingFee(items)).thenReturn(BigDecimal.ZERO);

        mockMvc.perform(post("/junes/api/v1/shipping/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // JSON deserialization produces a new List instance, so equality (not identity) is what's
        // observable at this layer — unlike the direct-method-call Mockito test this replaces.
        ArgumentCaptor<List<OrderItemDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(shippingService).getShippingFee(captor.capture());
        assertThat(captor.getValue()).isEqualTo(items);
    }
}
