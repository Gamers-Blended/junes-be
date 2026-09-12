package com.gamersblended.junes.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.WebMvcTestApplication;
import com.gamersblended.junes.dto.ProductInWishlistDTO;
import com.gamersblended.junes.dto.WishlistItemDTO;
import com.gamersblended.junes.service.auth.AccessTokenService;
import com.gamersblended.junes.service.cart.WishlistService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice test for {@link WishlistController} — see {@link AuthControllerWebMvcTest} for the
 * pattern rationale. {@code /junes/api/v1/wishlist/**} is permitAll in
 * {@link com.gamersblended.junes.config.SecurityConfig}, consistent with {@code addFilters = false} here.
 */
@WebMvcTest(WishlistController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = WebMvcTestApplication.class)
class WishlistControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WishlistService wishlistService;

    @MockBean
    private AccessTokenService accessTokenService;

    // JwtAuthenticationFilter is a @Component Filter, so @WebMvcTest instantiates it even with
    // addFilters = false (that flag only skips running filters, not building the bean); it needs
    // a JwtUtils bean to construct, which this slice otherwise wouldn't provide.
    @MockBean
    private JwtUtils jwtUtils;

    @Test
    void getWishlistProducts_shouldReturnPagedProducts() throws Exception {
        String authHeader = "Bearer token123";
        UUID sessionID = UUID.randomUUID();
        UUID userID = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        ProductInWishlistDTO item = new ProductInWishlistDTO("id1", "Game", "slug", BigDecimal.TEN, "PS5", "US", "Standard", "image.png", LocalDateTime.now());
        Page<ProductInWishlistDTO> page = new PageImpl<>(List.of(item));

        when(accessTokenService.extractUserIDFromToken(authHeader)).thenReturn(userID);
        when(wishlistService.getWishlistProducts(userID, sessionID, pageable)).thenReturn(page);

        mockMvc.perform(get("/junes/api/v1/wishlist/products")
                        .header("Authorization", authHeader)
                        .header("X-Session-Id", sessionID.toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("id1"));

        verify(wishlistService).getWishlistProducts(userID, sessionID, pageable);
    }

    @Test
    void addToWishlist_shouldAddItemAndReturnConfirmation() throws Exception {
        String authHeader = "Bearer token123";
        UUID sessionID = UUID.randomUUID();
        UUID userID = UUID.randomUUID();
        WishlistItemDTO wishlistItemDTO = new WishlistItemDTO("product-1", LocalDateTime.now());

        when(accessTokenService.extractUserIDFromToken(authHeader)).thenReturn(userID);

        mockMvc.perform(post("/junes/api/v1/wishlist/add")
                        .header("Authorization", authHeader)
                        .header("X-Session-Id", sessionID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wishlistItemDTO)))
                .andExpect(status().isOk())
                .andExpect(content().string("Product added to wishlist"));

        ArgumentCaptor<WishlistItemDTO> captor = ArgumentCaptor.forClass(WishlistItemDTO.class);
        verify(wishlistService).addItemToWishlist(eq(userID), eq(sessionID), captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(wishlistItemDTO);
    }

    @Test
    void removeFromWishlist_shouldRemoveItemAndReturnConfirmation() throws Exception {
        String authHeader = "Bearer token123";
        UUID sessionID = UUID.randomUUID();
        UUID userID = UUID.randomUUID();
        String productID = "product-1";

        when(accessTokenService.extractUserIDFromToken(authHeader)).thenReturn(userID);

        mockMvc.perform(delete("/junes/api/v1/wishlist/remove/{productID}", productID)
                        .header("Authorization", authHeader)
                        .header("X-Session-Id", sessionID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("Product removed from wishlist"));

        verify(wishlistService).removeItemFromWishlist(userID, sessionID, productID);
    }

    @Test
    void clearWishlist_shouldClearAndReturnConfirmation() throws Exception {
        String authHeader = "Bearer token123";
        UUID sessionID = UUID.randomUUID();
        UUID userID = UUID.randomUUID();

        when(accessTokenService.extractUserIDFromToken(authHeader)).thenReturn(userID);

        mockMvc.perform(delete("/junes/api/v1/wishlist/items")
                        .header("Authorization", authHeader)
                        .header("X-Session-Id", sessionID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("Wishlist cleared successfully"));

        verify(wishlistService).clearWishlist(userID, sessionID);
    }
}
