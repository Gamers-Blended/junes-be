package com.gamersblended.junes.controller;

import com.gamersblended.junes.annotation.RateLimit;
import com.gamersblended.junes.dto.ProductDetailsDTO;
import com.gamersblended.junes.dto.ProductSliderItemDTO;
import com.gamersblended.junes.dto.response.ErrorResponseDTO;
import com.gamersblended.junes.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("junes/api/v1/product")
@RateLimit(requests = 100, duration = 1, timeUnit = TimeUnit.HOURS)
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "Get products under a given platform")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved paginated product listings.",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ProductSliderItemDTO.class))}),
            @ApiResponse(responseCode = "400", description = "Invalid search parameters or malformed query filters provided.",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Internal server or database error occurred while fetching products.",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
    })
    @GetMapping("/products/{platform}")
    public ResponseEntity<Page<ProductSliderItemDTO>> getProductListing(
            @PathVariable String platform,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) List<String> availability,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) List<String> genres,
            @RequestParam(required = false) List<String> regions,
            @RequestParam(required = false) List<String> publishers,
            @RequestParam(required = false) List<String> editions,
            @RequestParam(required = false) List<String> languages,
            @RequestParam(required = false) List<String> startingLetters,
            @RequestParam(required = false) List<String> releaseDates,
            @RequestParam(required = false) String currentDate,
            Pageable pageable) {
        log.info("Calling get product listings API for platform: {}, page = {}, sort by = {}!", platform, pageable.getPageNumber(), pageable.getSort());
        return ResponseEntity.ok(productService.getProductListings(
                platform,
                name,
                availability,
                minPrice,
                maxPrice,
                genres,
                regions,
                publishers,
                editions,
                languages,
                startingLetters,
                releaseDates,
                currentDate,
                pageable));
    }

    @Operation(summary = "Get product details by slug")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200", description = "Successfully retrieved product details and variants.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ProductDetailsDTO.class))),
            @ApiResponse(
                    responseCode = "404", description = "No product found matching the provided slug.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "500", description = "Internal server or database error occurred while fetching product details.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class)))})
    @GetMapping("/details/{productSlug}")
    public ResponseEntity<ProductDetailsDTO> getProductDetails(@PathVariable String productSlug) {
        log.info("Calling get product details API for title: {}!", productSlug);
        return ResponseEntity.ok(productService.getProductDetails(productSlug));
    }
}
