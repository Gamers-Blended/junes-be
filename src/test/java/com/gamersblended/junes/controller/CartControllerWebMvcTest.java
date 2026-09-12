package com.gamersblended.junes.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.WebMvcTestApplication;
import com.gamersblended.junes.dto.CartItemDTO;
import com.gamersblended.junes.dto.ProductInCartDTO;
import com.gamersblended.junes.service.auth.AccessTokenService;
import com.gamersblended.junes.service.cart.CartService;
import com.gamersblended.junes.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice test for {@link CartController} — see {@link AuthControllerWebMvcTest} for the
 * pattern rationale. {@code /junes/api/v1/cart/**} is permitAll in
 * {@link com.gamersblended.junes.config.SecurityConfig}, consistent with {@code addFilters = false} here.
 */
@WebMvcTest(CartController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = WebMvcTestApplication.class)
class CartControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CartService cartService;

    @MockBean
    private AccessTokenService accessTokenService;

    // JwtAuthenticationFilter is a @Component Filter, so @WebMvcTest instantiates it even with
    // addFilters = false (that flag only skips running filters, not building the bean); it needs
    // a JwtUtils bean to construct, which this slice otherwise wouldn't provide.
    @MockBean
    private JwtUtils jwtUtils;

    @Test
    void getCartProducts_shouldExtractUserIDFromHeaderAndReturnPagedProducts() throws Exception {
        String authHeader = "Bearer jwt-token";
        UUID sessionID = UUID.randomUUID();
        UUID userID = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        ProductInCartDTO productInCartDTO = new ProductInCartDTO("id1", "name", "slug", BigDecimal.TEN, "PS5", "US",
                "Standard", "image.png", 2, LocalDateTime.now());
        Page<ProductInCartDTO> page = new PageImpl<>(List.of(productInCartDTO));

        when(accessTokenService.extractUserIDFromToken(authHeader)).thenReturn(userID);
        when(cartService.getCartProducts(userID, sessionID, pageable)).thenReturn(page);

        mockMvc.perform(get("/junes/api/v1/cart/products")
                        .header("Authorization", authHeader)
                        .header("X-Session-Id", sessionID.toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("id1"));

        verify(cartService).getCartProducts(userID, sessionID, pageable);
    }

    @Test
    void getCartProducts_withNullAuthHeader_shouldPassNullUserIDThrough() throws Exception {
        UUID sessionID = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Page<ProductInCartDTO> page = new PageImpl<>(List.of());

        when(cartService.getCartProducts(null, sessionID, pageable)).thenReturn(page);

        mockMvc.perform(get("/junes/api/v1/cart/products")
                        .header("X-Session-Id", sessionID.toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(cartService).getCartProducts(null, sessionID, pageable);
    }

    @Test
    void addToCart_shouldExtractUserIDAndDelegateToCartService() throws Exception {
        String authHeader = "Bearer jwt-token";
        UUID sessionID = UUID.randomUUID();
        UUID userID = UUID.randomUUID();
        CartItemDTO cartItemDTO = new CartItemDTO("product-1", BigDecimal.TEN, 1, LocalDateTime.now());

        when(accessTokenService.extractUserIDFromToken(authHeader)).thenReturn(userID);

        mockMvc.perform(post("/junes/api/v1/cart/add")
                        .header("Authorization", authHeader)
                        .header("X-Session-Id", sessionID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cartItemDTO)))
                .andExpect(status().isOk())
                .andExpect(content().string("Product added to cart"));

        ArgumentCaptor<CartItemDTO> captor = ArgumentCaptor.forClass(CartItemDTO.class);
        verify(cartService).addItemToCart(eq(userID), eq(sessionID), captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(cartItemDTO);
    }

    @Test
    void removeFromCart_shouldExtractUserIDAndDelegateToCartService() throws Exception {
        String authHeader = "Bearer jwt-token";
        UUID sessionID = UUID.randomUUID();
        UUID userID = UUID.randomUUID();
        String productID = "product-1";

        when(accessTokenService.extractUserIDFromToken(authHeader)).thenReturn(userID);

        mockMvc.perform(delete("/junes/api/v1/cart/remove/{productID}", productID)
                        .header("Authorization", authHeader)
                        .header("X-Session-Id", sessionID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("Product removed from cart"));

        verify(cartService).removeItemFromCart(userID, sessionID, productID);
    }

    @Test
    void updateQuantity_shouldExtractUserIDAndDelegateToCartService() throws Exception {
        String authHeader = "Bearer jwt-token";
        UUID sessionID = UUID.randomUUID();
        UUID userID = UUID.randomUUID();
        String productID = "product-1";
        int quantity = 5;

        when(accessTokenService.extractUserIDFromToken(authHeader)).thenReturn(userID);

        mockMvc.perform(put("/junes/api/v1/cart/{productID}/quantity", productID)
                        .header("Authorization", authHeader)
                        .header("X-Session-Id", sessionID.toString())
                        .param("quantity", String.valueOf(quantity)))
                .andExpect(status().isOk())
                .andExpect(content().string("Quantity updated successfully"));

        verify(cartService).updateItemQuantity(userID, sessionID, productID, quantity);
    }

    @Test
    void clearCart_shouldExtractUserIDAndDelegateToCartService() throws Exception {
        String authHeader = "Bearer jwt-token";
        UUID sessionID = UUID.randomUUID();
        UUID userID = UUID.randomUUID();

        when(accessTokenService.extractUserIDFromToken(authHeader)).thenReturn(userID);

        mockMvc.perform(delete("/junes/api/v1/cart/items")
                        .header("Authorization", authHeader)
                        .header("X-Session-Id", sessionID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("Cart cleared successfully"));

        verify(cartService).clearCart(userID, sessionID);
    }
}
