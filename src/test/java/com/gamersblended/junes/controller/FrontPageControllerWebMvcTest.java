package com.gamersblended.junes.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.WebMvcTestApplication;
import com.gamersblended.junes.dto.ProductDTO;
import com.gamersblended.junes.dto.ProductSliderItemDTO;
import com.gamersblended.junes.dto.request.RecommendedProductRequestDTO;
import com.gamersblended.junes.service.auth.AccessTokenService;
import com.gamersblended.junes.service.product.ProductService;
import com.gamersblended.junes.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} + MockMvc slice test for {@link FrontPageController}, following the pattern
 * established in {@link AuthControllerWebMvcTest}. Exercises real JSON (de)serialization, real
 * {@code @Valid} cascade validation on {@link RecommendedProductRequestDTO}'s nested history items,
 * and delegation to {@link ProductService}/{@link AccessTokenService} over a simulated HTTP request.
 * <p>
 * Security filters are bypassed via {@code addFilters = false} since {@code /junes/api/v1/frontpage/**}
 * is permitAll in {@link com.gamersblended.junes.config.SecurityConfig}.
 */
@WebMvcTest(FrontPageController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = WebMvcTestApplication.class)
class FrontPageControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @MockBean
    private AccessTokenService accessTokenService;

    // JwtAuthenticationFilter is a @Component Filter, so @WebMvcTest instantiates it even with
    // addFilters = false (that flag only skips running filters, not building the bean); it needs
    // a JwtUtils bean to construct, which this slice otherwise wouldn't provide.
    @MockBean
    private JwtUtils jwtUtils;

    @Test
    void getRecommendedProductsLoggedIn_withAuthHeader_returnsOkAndDelegatesToService() throws Exception {
        UUID userID = UUID.randomUUID();
        UUID sessionID = UUID.randomUUID();
        RecommendedProductRequestDTO requestDTO = new RecommendedProductRequestDTO();
        ProductSliderItemDTO item = new ProductSliderItemDTO();
        item.setProductID("product-1");
        Page<ProductSliderItemDTO> page = new PageImpl<>(List.of(item));

        when(accessTokenService.extractUserIDFromToken("Bearer jwt-token")).thenReturn(userID);
        when(productService.getRecommendedProducts(any(RecommendedProductRequestDTO.class), any(), eq(userID), eq(sessionID)))
                .thenReturn(page);

        mockMvc.perform(post("/junes/api/v1/frontpage/recommended")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer jwt-token")
                        .header("X-Session-Id", sessionID.toString())
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productID").value("product-1"));

        verify(productService).getRecommendedProducts(any(RecommendedProductRequestDTO.class), any(), eq(userID), eq(sessionID));
    }

    @Test
    void getRecommendedProductsLoggedIn_withNullAuthHeader_shouldPassNullUserIDThrough() throws Exception {
        RecommendedProductRequestDTO requestDTO = new RecommendedProductRequestDTO();
        Page<ProductSliderItemDTO> page = new PageImpl<>(List.of());

        when(accessTokenService.extractUserIDFromToken(null)).thenReturn(null);
        when(productService.getRecommendedProducts(any(RecommendedProductRequestDTO.class), any(), isNull(), any()))
                .thenReturn(page);

        mockMvc.perform(post("/junes/api/v1/frontpage/recommended")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(productService).getRecommendedProducts(any(RecommendedProductRequestDTO.class), any(), isNull(), any());
    }

    @Test
    void getRecommendedProductsLoggedIn_blankHistoryItemProductID_returnsBadRequestFromBeanValidation() throws Exception {
        RecommendedProductRequestDTO.HistoryItem historyItem = new RecommendedProductRequestDTO.HistoryItem();
        historyItem.setProductID("");
        RecommendedProductRequestDTO requestDTO = new RecommendedProductRequestDTO();
        requestDTO.setHistoryCache(List.of(historyItem));

        mockMvc.perform(post("/junes/api/v1/frontpage/recommended")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Product ID cannot be blank")));
    }

    @Test
    void getPreOrderProducts_returnsOkWithPagedProducts() throws Exception {
        ProductSliderItemDTO item = new ProductSliderItemDTO();
        item.setProductID("preorder-1");
        Page<ProductSliderItemDTO> page = new PageImpl<>(List.of(item));

        when(productService.getPreOrderProducts(any(), eq(1))).thenReturn(page);

        mockMvc.perform(get("/junes/api/v1/frontpage/preorders")
                        .param("currentDate", "2026-09-12")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productID").value("preorder-1"));

        verify(productService).getPreOrderProducts(any(), eq(1));
    }

    @Test
    void getBestSellingProducts_returnsOkWithPagedProducts() throws Exception {
        ProductSliderItemDTO item = new ProductSliderItemDTO();
        item.setProductID("best-seller-1");
        Page<ProductSliderItemDTO> page = new PageImpl<>(List.of(item));

        when(productService.getBestSellers(any(), eq(0))).thenReturn(page);

        mockMvc.perform(get("/junes/api/v1/frontpage/best-sellers")
                        .param("currentDate", "2026-09-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productID").value("best-seller-1"));

        verify(productService).getBestSellers(any(), eq(0));
    }

    @Test
    void getQuickShopDetailsByID_returnsOkWithProductDetails() throws Exception {
        ProductDTO productDTO = new ProductDTO();
        productDTO.setId("product-1");
        productDTO.setName("Some Game");

        when(productService.getQuickShopDetails("product-1")).thenReturn(productDTO);

        mockMvc.perform(get("/junes/api/v1/frontpage/quick-shop/{productID}", "product-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("product-1"))
                .andExpect(jsonPath("$.name").value("Some Game"));

        verify(productService).getQuickShopDetails("product-1");
    }
}
