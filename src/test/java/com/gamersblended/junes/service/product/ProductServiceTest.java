package com.gamersblended.junes.service.product;

import com.gamersblended.junes.dto.ProductDTO;
import com.gamersblended.junes.dto.ProductDetailsDTO;
import com.gamersblended.junes.dto.ProductSliderItemDTO;
import com.gamersblended.junes.dto.recommender.ProductRecommendationDTO;
import com.gamersblended.junes.dto.recommender.ProductSignalDTO;
import com.gamersblended.junes.dto.recommender.RecommendationRequestDTO;
import com.gamersblended.junes.dto.recommender.RecommendationResponseDTO;
import com.gamersblended.junes.dto.request.RecommendedProductRequestDTO;
import com.gamersblended.junes.exception.InvalidProductIdException;
import com.gamersblended.junes.exception.InvalidProductQueryException;
import com.gamersblended.junes.exception.ProductFetchException;
import com.gamersblended.junes.exception.ProductNotFoundException;
import com.gamersblended.junes.mapper.ProductMapper;
import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.repository.mongodb.ProductRepository;
import com.gamersblended.junes.service.cache.RecommendationCacheService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductRecommendationRequestBuilder productRecommendationRequestBuilder;
    @Mock
    private RecommendationService recommendationService;
    @Mock
    private RecommendationCacheService recommendationCacheService;
    @Mock
    private ProductMapper productMapper;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, productRecommendationRequestBuilder, recommendationService, recommendationCacheService, productMapper);
    }

    private static Product product(ObjectId id, String name, BigDecimal price, int stock) {
        Product product = new Product(name, "slug-" + name, "description", price, "PS5", "US", "Standard",
                "publisher", LocalDate.now(), Set.of(), Set.of(), Set.of(), Set.of(), BigDecimal.ONE, 0, stock,
                "image.png", List.of(), LocalDate.now());
        product.setId(id);
        return product;
    }

    private static ProductSliderItemDTO sliderItemDTOFor(Product product) {
        ProductSliderItemDTO dto = new ProductSliderItemDTO();
        dto.setProductID(product.getId().toHexString());
        dto.setName(product.getName());
        return dto;
    }

    private void stubSliderMapping() {
        when(productMapper.toSliderItemDTO(any(Product.class))).thenAnswer(inv -> sliderItemDTOFor(inv.getArgument(0)));
    }

    private static ProductRecommendationDTO recommendationDTO(String productID) {
        ProductRecommendationDTO dto = new ProductRecommendationDTO();
        dto.setProductID(productID);
        dto.setName("Recommended " + productID);
        return dto;
    }

    private static ProductSignalDTO signal(String productID) {
        return new ProductSignalDTO(productID, "BROWSE", null);
    }

    // ================= getRecommendedProducts =================

    @Test
    void getRecommendedProducts_returnsCachedRecommendations_whenCacheHit() {
        RecommendedProductRequestDTO requestDTO = new RecommendedProductRequestDTO();
        Pageable pageable = PageRequest.of(0, 5);
        List<ProductSignalDTO> signals = List.of(signal("p1"));
        RecommendationResponseDTO cached = new RecommendationResponseDTO();
        cached.setProducts(List.of(recommendationDTO("p1")));
        RecommendationRequestDTO recRequestDTO = new RecommendationRequestDTO();
        recRequestDTO.setSignalList(signals);
        when(productRecommendationRequestBuilder.getRecommendationInputDTOList(any(), any(), any())).thenReturn(signals);
        when(productRecommendationRequestBuilder.getRecommendationRequestDTO(signals)).thenReturn(recRequestDTO);
        when(recommendationCacheService.get(signals)).thenReturn(Optional.of(cached));
        when(productMapper.recommendationToSliderItemDTO(any())).thenAnswer(inv -> {
            ProductRecommendationDTO dto = inv.getArgument(0);
            ProductSliderItemDTO sliderDTO = new ProductSliderItemDTO();
            sliderDTO.setProductID(dto.getProductID());
            return sliderDTO;
        });

        Page<ProductSliderItemDTO> result = productService.getRecommendedProducts(requestDTO, pageable, UUID.randomUUID(), UUID.randomUUID());

        assertThat(result.getContent()).extracting(ProductSliderItemDTO::getProductID).containsExactly("p1");
        verifyNoInteractions(recommendationService);
    }

    @Test
    void getRecommendedProducts_callsRecommenderAndCaches_whenCacheMiss() {
        RecommendedProductRequestDTO requestDTO = new RecommendedProductRequestDTO();
        Pageable pageable = PageRequest.of(0, 5);
        List<ProductSignalDTO> signals = List.of(signal("p2"));
        RecommendationRequestDTO recRequestDTO = new RecommendationRequestDTO();
        recRequestDTO.setSignalList(signals);
        RecommendationResponseDTO fresh = new RecommendationResponseDTO();
        fresh.setProducts(List.of(recommendationDTO("p2")));
        when(productRecommendationRequestBuilder.getRecommendationInputDTOList(any(), any(), any())).thenReturn(signals);
        when(productRecommendationRequestBuilder.getRecommendationRequestDTO(signals)).thenReturn(recRequestDTO);
        when(recommendationCacheService.get(signals)).thenReturn(Optional.empty());
        when(recommendationService.getRecommendations(recRequestDTO)).thenReturn(Mono.just(fresh));
        when(productMapper.recommendationToSliderItemDTO(any())).thenAnswer(inv -> {
            ProductRecommendationDTO dto = inv.getArgument(0);
            ProductSliderItemDTO sliderDTO = new ProductSliderItemDTO();
            sliderDTO.setProductID(dto.getProductID());
            return sliderDTO;
        });

        Page<ProductSliderItemDTO> result = productService.getRecommendedProducts(requestDTO, pageable, UUID.randomUUID(), UUID.randomUUID());

        assertThat(result.getContent()).extracting(ProductSliderItemDTO::getProductID).containsExactly("p2");
        verify(recommendationCacheService).put(signals, fresh);
    }

    @Test
    void getRecommendedProducts_fallsBackToBestSellers_whenRecommenderReturnsEmptyProducts() {
        RecommendedProductRequestDTO requestDTO = new RecommendedProductRequestDTO();
        Pageable pageable = PageRequest.of(0, 5);
        List<ProductSignalDTO> signals = List.of(signal("p3"));
        RecommendationResponseDTO empty = new RecommendationResponseDTO();
        empty.setProducts(List.of());
        RecommendationRequestDTO recRequestDTO = new RecommendationRequestDTO();
        recRequestDTO.setSignalList(signals);
        when(productRecommendationRequestBuilder.getRecommendationInputDTOList(any(), any(), any())).thenReturn(signals);
        when(productRecommendationRequestBuilder.getRecommendationRequestDTO(signals)).thenReturn(recRequestDTO);
        when(recommendationCacheService.get(signals)).thenReturn(Optional.empty());
        when(recommendationService.getRecommendations(any())).thenReturn(Mono.just(empty));
        Product bestSeller = product(new ObjectId(), "Best Seller", BigDecimal.TEN, 5);
        when(productRepository.findBestSellersBeforeDateWithPagination(any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(bestSeller)));
        stubSliderMapping();

        Page<ProductSliderItemDTO> result = productService.getRecommendedProducts(requestDTO, pageable, UUID.randomUUID(), UUID.randomUUID());

        assertThat(result.getContent()).extracting(ProductSliderItemDTO::getName).containsExactly("Best Seller");
        verify(recommendationCacheService, never()).put(any(), any());
    }

    @Test
    void getRecommendedProducts_fallsBackToBestSellers_whenExceptionThrown() {
        RecommendedProductRequestDTO requestDTO = new RecommendedProductRequestDTO();
        Pageable pageable = PageRequest.of(0, 5);
        when(productRecommendationRequestBuilder.getRecommendationInputDTOList(any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));
        Product bestSeller = product(new ObjectId(), "Best Seller", BigDecimal.TEN, 5);
        when(productRepository.findBestSellersBeforeDateWithPagination(any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(bestSeller)));
        stubSliderMapping();

        Page<ProductSliderItemDTO> result = productService.getRecommendedProducts(requestDTO, pageable, UUID.randomUUID(), UUID.randomUUID());

        assertThat(result.getContent()).extracting(ProductSliderItemDTO::getName).containsExactly("Best Seller");
    }

    @Test
    void getRecommendedProducts_trimsHistoryCache_toMostRecent30_whenExceedingLimit() {
        RecommendedProductRequestDTO requestDTO = new RecommendedProductRequestDTO();
        List<RecommendedProductRequestDTO.HistoryItem> items = new ArrayList<>();
        for (int i = 0; i < 35; i++) {
            RecommendedProductRequestDTO.HistoryItem item = new RecommendedProductRequestDTO.HistoryItem();
            item.setProductID("p" + i);
            item.setViewAt(java.time.LocalDateTime.now().plusMinutes(i));
            items.add(item);
        }
        requestDTO.setHistoryCache(items);
        Pageable pageable = PageRequest.of(0, 5);
        RecommendationRequestDTO recRequestDTO = new RecommendationRequestDTO();
        recRequestDTO.setSignalList(List.of());
        when(productRecommendationRequestBuilder.getRecommendationInputDTOList(any(), any(), any())).thenReturn(List.of());
        when(productRecommendationRequestBuilder.getRecommendationRequestDTO(any())).thenReturn(recRequestDTO);
        when(recommendationCacheService.get(any())).thenReturn(Optional.empty());
        when(recommendationService.getRecommendations(any())).thenReturn(Mono.empty());
        when(productRepository.findBestSellersBeforeDateWithPagination(any(LocalDate.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        productService.getRecommendedProducts(requestDTO, pageable, UUID.randomUUID(), UUID.randomUUID());

        ArgumentCaptor<RecommendedProductRequestDTO> captor = ArgumentCaptor.forClass(RecommendedProductRequestDTO.class);
        verify(productRecommendationRequestBuilder).getRecommendationInputDTOList(captor.capture(), any(), any());
        assertThat(captor.getValue().getHistoryCache()).hasSize(30);
    }

    // ================= getPreOrderProducts =================

    @Test
    void getPreOrderProducts_returnsMappedPage_onSuccess() {
        Product preorder = product(new ObjectId(), "Preorder Game", BigDecimal.TEN, 0);
        when(productRepository.findPreOrderProductsAfterDateWithPagination(any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(preorder)));
        stubSliderMapping();

        Page<ProductSliderItemDTO> result = productService.getPreOrderProducts(LocalDate.of(2026, Month.JANUARY, 1), 0);

        assertThat(result.getContent()).extracting(ProductSliderItemDTO::getName).containsExactly("Preorder Game");
    }

    @Test
    void getPreOrderProducts_usesToday_whenCurrentDateIsNull() {
        when(productRepository.findPreOrderProductsAfterDateWithPagination(any(LocalDate.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        productService.getPreOrderProducts(null, 0);

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(productRepository).findPreOrderProductsAfterDateWithPagination(dateCaptor.capture(), any(Pageable.class));
        assertThat(dateCaptor.getValue()).isEqualTo(LocalDate.now());
    }

    @Test
    void getPreOrderProducts_returnsEmptyPage_onException() {
        when(productRepository.findPreOrderProductsAfterDateWithPagination(any(LocalDate.class), any(Pageable.class)))
                .thenThrow(new RuntimeException("db down"));

        Page<ProductSliderItemDTO> result = productService.getPreOrderProducts(LocalDate.now(), 0);

        assertThat(result).isEmpty();
    }

    // ================= getBestSellers =================

    @Test
    void getBestSellers_returnsMappedPage_onSuccess() {
        Product bestSeller = product(new ObjectId(), "Best Seller", BigDecimal.TEN, 5);
        when(productRepository.findBestSellersBeforeDateWithPagination(any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(bestSeller)));
        stubSliderMapping();

        Page<ProductSliderItemDTO> result = productService.getBestSellers(LocalDate.now(), 0);

        assertThat(result.getContent()).extracting(ProductSliderItemDTO::getName).containsExactly("Best Seller");
    }

    @Test
    void getBestSellers_returnsEmptyPage_onException() {
        when(productRepository.findBestSellersBeforeDateWithPagination(any(LocalDate.class), any(Pageable.class)))
                .thenThrow(new RuntimeException("db down"));

        Page<ProductSliderItemDTO> result = productService.getBestSellers(LocalDate.now(), 0);

        assertThat(result).isEmpty();
    }

    // ================= getQuickShopDetails =================

    @Test
    void getQuickShopDetails_throwsInvalidProductIdException_whenIDIsNull() {
        assertThatThrownBy(() -> productService.getQuickShopDetails(null))
                .isInstanceOf(InvalidProductIdException.class);

        verifyNoInteractions(productRepository);
    }

    @Test
    void getQuickShopDetails_throwsInvalidProductIdException_whenIDIsBlank() {
        assertThatThrownBy(() -> productService.getQuickShopDetails("   "))
                .isInstanceOf(InvalidProductIdException.class);
    }

    @Test
    void getQuickShopDetails_throwsProductNotFoundException_whenNotFound() {
        when(productRepository.findById("abc123")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getQuickShopDetails("abc123"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void getQuickShopDetails_returnsMappedDTO_whenFound() {
        Product product = product(new ObjectId(), "Game", BigDecimal.TEN, 5);
        ProductDTO dto = new ProductDTO();
        when(productRepository.findById("abc123")).thenReturn(Optional.of(product));
        when(productMapper.toDTO(product)).thenReturn(dto);

        ProductDTO result = productService.getQuickShopDetails("abc123");

        assertThat(result).isSameAs(dto);
    }

    @Test
    void getQuickShopDetails_wrapsUnexpectedException_inRuntimeException() {
        when(productRepository.findById("abc123")).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> productService.getQuickShopDetails("abc123"))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(InvalidProductIdException.class)
                .isNotInstanceOf(ProductNotFoundException.class);
    }

    // ================= getProductListings =================

    @Test
    void getProductListings_returnsMappedPage_onSuccess() {
        Product product = product(new ObjectId(), "Game", BigDecimal.TEN, 5);
        Pageable pageable = PageRequest.of(0, 5);
        when(productRepository.findProductsWithFilters(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(product)));
        stubSliderMapping();

        Page<ProductSliderItemDTO> result = productService.getProductListings("PS5", null, null, null, null, null, null, null, null, null, null, null, null, pageable);

        assertThat(result.getContent()).extracting(ProductSliderItemDTO::getName).containsExactly("Game");
    }

    @Test
    void getProductListings_throwsInvalidProductQueryException_whenReleaseDateFormatIsInvalid() {
        Pageable pageable = PageRequest.of(0, 5);
        List<String> invalidReleaseDates = List.of("not-a-valid-date");

        assertThatThrownBy(() -> productService.getProductListings("PS5", null, null, null, null, null, null, null, null, null, null,
                invalidReleaseDates, null, pageable))
                .isInstanceOf(InvalidProductQueryException.class);

        verifyNoInteractions(productRepository);
    }

    @Test
    void getProductListings_rethrowsInvalidProductQueryException_fromRepository() {
        Pageable pageable = PageRequest.of(0, 5);
        when(productRepository.findProductsWithFilters(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(pageable)))
                .thenThrow(new InvalidProductQueryException("bad filter"));

        assertThatThrownBy(() -> productService.getProductListings("PS5", null, null, null, null, null, null, null, null, null, null, null, null, pageable))
                .isInstanceOf(InvalidProductQueryException.class)
                .hasMessage("bad filter");
    }

    @Test
    void getProductListings_throwsProductFetchException_onUnexpectedError() {
        Pageable pageable = PageRequest.of(0, 5);
        when(productRepository.findProductsWithFilters(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(pageable)))
                .thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> productService.getProductListings("PS5", null, null, null, null, null, null, null, null, null, null, null, null, pageable))
                .isInstanceOf(ProductFetchException.class);
    }

    // ================= searchProducts =================

    @Test
    void searchProducts_usesDefaultLimit_whenLimitIsNull() {
        when(productRepository.searchProducts("mario", 10)).thenReturn(List.of());

        productService.searchProducts("mario", null);

        verify(productRepository).searchProducts("mario", 10);
    }

    @Test
    void searchProducts_usesGivenLimit() {
        Product product = product(new ObjectId(), "Mario Kart", BigDecimal.TEN, 5);
        when(productRepository.searchProducts("mario", 3)).thenReturn(List.of(product));
        stubSliderMapping();

        List<ProductSliderItemDTO> result = productService.searchProducts("mario", 3);

        assertThat(result).extracting(ProductSliderItemDTO::getName).containsExactly("Mario Kart");
    }

    @Test
    void searchProducts_rethrowsInvalidProductQueryException() {
        when(productRepository.searchProducts(anyString(), anyInt())).thenThrow(new InvalidProductQueryException("bad query"));

        assertThatThrownBy(() -> productService.searchProducts("bad", null))
                .isInstanceOf(InvalidProductQueryException.class);
    }

    @Test
    void searchProducts_throwsProductFetchException_onUnexpectedError() {
        when(productRepository.searchProducts(anyString(), anyInt())).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> productService.searchProducts("mario", null))
                .isInstanceOf(ProductFetchException.class);
    }

    // ================= getProductDetails =================

    @Test
    void getProductDetails_throwsProductNotFoundException_whenNoVariantsFound() {
        when(productRepository.findBySlug("missing-slug")).thenReturn(List.of());

        assertThatThrownBy(() -> productService.getProductDetails("missing-slug"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void getProductDetails_returnsDetailsWithAllVariants_onSuccess() {
        Product variant1 = product(new ObjectId(), "Game", BigDecimal.TEN, 5);
        Product variant2 = product(new ObjectId(), "Game", BigDecimal.valueOf(15), 3);
        ProductDTO productDTO = new ProductDTO();
        when(productRepository.findBySlug("game-slug")).thenReturn(List.of(variant1, variant2));
        when(productMapper.toDTO(variant1)).thenReturn(productDTO);

        ProductDetailsDTO result = productService.getProductDetails("game-slug");

        assertThat(result.getProductDTO()).isSameAs(productDTO);
        assertThat(result.getProductVariantDTOList()).hasSize(2);
        assertThat(result.getProductVariantDTOList().get(0).getProductID()).isEqualTo(variant1.getId().toHexString());
        assertThat(result.getProductVariantDTOList().get(1).getProductID()).isEqualTo(variant2.getId().toHexString());
    }

    @Test
    void getProductDetails_throwsProductFetchException_onUnexpectedError() {
        when(productRepository.findBySlug("game-slug")).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> productService.getProductDetails("game-slug"))
                .isInstanceOf(ProductFetchException.class);
    }
}
