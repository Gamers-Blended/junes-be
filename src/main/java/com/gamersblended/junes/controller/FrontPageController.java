package com.gamersblended.junes.controller;

import com.gamersblended.junes.annotation.RateLimit;
import com.gamersblended.junes.dto.ProductDTO;
import com.gamersblended.junes.dto.ProductSliderItemDTO;
import com.gamersblended.junes.dto.request.RecommendedProductRequestDTO;
import com.gamersblended.junes.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Slf4j
@RestController
@RequestMapping("junes/api/v1/frontpage")
@RateLimit(requests = 100, duration = 1, timeUnit = TimeUnit.HOURS)
public class FrontPageController {

    private final ProductService productService;

    public FrontPageController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "Get a list of recommended products")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recommended products shown",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ProductSliderItemDTO.class))}),
            @ApiResponse(responseCode = "400", description = "Invalid userID given",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Products not found",
                    content = @Content)
    })
    @PostMapping("/recommended")
    public ResponseEntity<Page<ProductSliderItemDTO>> getRecommendedProductsLoggedIn(@Valid @RequestBody RecommendedProductRequestDTO requestDTO,
                                                                                     @RequestHeader(value = "X-Session-Id", required = false) UUID sessionID, Pageable pageable) {
        log.info("Calling get recommended products API, page {}!", pageable.getPageNumber());
        return ResponseEntity.ok(productService.getRecommendedProducts(requestDTO, pageable, sessionID));
    }

    @Operation(summary = "Get a list of preorder products")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Available preorder products shown",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ProductSliderItemDTO.class))}),
            @ApiResponse(responseCode = "404", description = "Preorder products not found",
                    content = @Content)
    })
    @GetMapping("/preorders")
    public ResponseEntity<Page<ProductSliderItemDTO>> getPreOrderProducts(@RequestParam(required = false) LocalDate currentDate, Pageable pageable) {
        log.info("Calling get preorder products API, page {}!", pageable.getPageNumber());
        return ResponseEntity.ok(productService.getPreOrderProducts(currentDate, pageable.getPageNumber()));
    }

    @Operation(summary = "Get a list of best-selling products in terms of units_sold")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Best-selling products shown",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ProductSliderItemDTO.class))}),
            @ApiResponse(responseCode = "404", description = "Products not found",
                    content = @Content)
    })
    @GetMapping("/best-sellers")
    public ResponseEntity<Page<ProductSliderItemDTO>> getBestSellingProducts(@RequestParam(required = false) LocalDate currentDate, Pageable pageable) {
        log.info("Calling get best sellers API, page {}!", pageable.getPageNumber());
        return ResponseEntity.ok(productService.getBestSellers(currentDate, pageable.getPageNumber()));
    }

    @Operation(summary = "Get product details for given product ID for quick shop window")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product details shown",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ProductSliderItemDTO.class))}),
            @ApiResponse(responseCode = "400", description = "Invalid productID given",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Product details not found",
                    content = @Content)
    })
    @GetMapping("/quick-shop/{productID}")
    public ResponseEntity<ProductDTO> getQuickShopDetailsByID(@PathVariable String productID) {
        log.info("Calling get quick shop details API for productID: {}!", productID);
        return ResponseEntity.ok(productService.getQuickShopDetails(productID));
    }
}
