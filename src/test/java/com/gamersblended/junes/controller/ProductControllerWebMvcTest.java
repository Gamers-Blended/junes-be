package com.gamersblended.junes.controller;

import com.gamersblended.junes.WebMvcTestApplication;
import com.gamersblended.junes.dto.ProductDTO;
import com.gamersblended.junes.dto.ProductDetailsDTO;
import com.gamersblended.junes.dto.ProductSliderItemDTO;
import com.gamersblended.junes.service.product.ProductService;
import com.gamersblended.junes.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice test for {@link ProductController} — see {@link AuthControllerWebMvcTest} for the
 * pattern rationale. {@code /junes/api/v1/product/**} is permitAll in
 * {@link com.gamersblended.junes.config.SecurityConfig}, consistent with {@code addFilters = false} here.
 */
@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = WebMvcTestApplication.class)
class ProductControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    // JwtAuthenticationFilter is a @Component Filter, so @WebMvcTest instantiates it even with
    // addFilters = false (that flag only skips running filters, not building the bean); it needs
    // a JwtUtils bean to construct, which this slice otherwise wouldn't provide.
    @MockBean
    private JwtUtils jwtUtils;

    @Test
    void getProductListing_withNoOptionalFilters_shouldPassNullsThroughToService() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        ProductSliderItemDTO item = new ProductSliderItemDTO();
        item.setProductID("id1");
        Page<ProductSliderItemDTO> page = new PageImpl<>(List.of(item));

        when(productService.getProductListings(eq("PS5"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(page);

        mockMvc.perform(get("/junes/api/v1/product/products/{platform}", "PS5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productID").value("id1"));

        verify(productService).getProductListings("PS5", null, null, null, null, null, null, null, null, null, null, null, null, pageable);
    }

    @Test
    void getProductListing_withFiltersApplied_shouldPassThemThroughToService() throws Exception {
        Pageable pageable = PageRequest.of(1, 10);
        Page<ProductSliderItemDTO> page = new PageImpl<>(List.of());
        List<String> availability = List.of("IN_STOCK");
        List<String> genres = List.of("RPG", "Action");
        BigDecimal minPrice = BigDecimal.valueOf(10);
        BigDecimal maxPrice = BigDecimal.valueOf(100);

        when(productService.getProductListings(eq("Switch"), eq("Mario"), eq(availability), eq(minPrice), eq(maxPrice),
                eq(genres), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq("2026-01-01"), eq(pageable)))
                .thenReturn(page);

        mockMvc.perform(get("/junes/api/v1/product/products/{platform}", "Switch")
                        .param("name", "Mario")
                        .param("availability", "IN_STOCK")
                        .param("minPrice", "10")
                        .param("maxPrice", "100")
                        .param("genres", "RPG", "Action")
                        .param("currentDate", "2026-01-01")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(productService).getProductListings("Switch", "Mario", availability, minPrice, maxPrice, genres, null, null, null, null, null, null, "2026-01-01", pageable);
    }

    @Test
    void searchProducts_shouldReturnMatchesFromService() throws Exception {
        ProductSliderItemDTO item = new ProductSliderItemDTO();
        item.setProductID("zelda-1");
        List<ProductSliderItemDTO> results = List.of(item);
        when(productService.searchProducts("zelda", 5)).thenReturn(results);

        mockMvc.perform(get("/junes/api/v1/product/search").param("q", "zelda").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productID").value("zelda-1"));

        verify(productService).searchProducts("zelda", 5);
    }

    @Test
    void searchProducts_withNoLimit_shouldPassNullLimitThrough() throws Exception {
        List<ProductSliderItemDTO> results = List.of();
        when(productService.searchProducts("mario", null)).thenReturn(results);

        mockMvc.perform(get("/junes/api/v1/product/search").param("q", "mario"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(productService).searchProducts("mario", null);
    }

    @Test
    void getProductDetails_shouldReturnDetailsForGivenSlug() throws Exception {
        ProductDetailsDTO detailsDTO = new ProductDetailsDTO();
        ProductDTO productDTO = new ProductDTO();
        detailsDTO.setProductDTO(productDTO);
        detailsDTO.setProductVariantDTOList(List.of());
        when(productService.getProductDetails("elden-ring-ps5")).thenReturn(detailsDTO);

        mockMvc.perform(get("/junes/api/v1/product/details/{productSlug}", "elden-ring-ps5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productVariantDTOList").isArray());

        verify(productService).getProductDetails("elden-ring-ps5");
    }
}
