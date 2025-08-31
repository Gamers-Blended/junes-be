package com.gamersblended.junes.controller;

import com.gamersblended.junes.dto.ProductDTO;
import com.gamersblended.junes.dto.ProductSliderItemDTO;
import com.gamersblended.junes.dto.RecommendedProductNotLoggedRequestDTO;
import com.gamersblended.junes.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;


@Slf4j
@RestController
@RequestMapping("junes/api/v1/frontpage")
public class FrontPageController {

    private final ProductService productService;

    public FrontPageController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "Get a list of recommended products for logged user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recommended products shown",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ProductSliderItemDTO.class))}),
            @ApiResponse(responseCode = "400", description = "Invalid userID given",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Products not found",
                    content = @Content)
    })
    @GetMapping("/recommended")
    public ResponseEntity<Page<ProductSliderItemDTO>> getRecommendedProductsLoggedIn(@RequestParam Integer userID, Pageable pageable) {
        log.info("Calling get recommended products API while userID {} is logged in, page {}!", userID, pageable.getPageNumber());
        return ResponseEntity.ok(productService.getRecommendedProductsWithID(userID, pageable));
    }

    @Operation(summary = "Get a list of recommended products for not logged user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recommended products shown",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ProductSliderItemDTO.class))}),
            @ApiResponse(responseCode = "400", description = "Invalid userID given",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Products not found",
                    content = @Content)
    })
    @PostMapping("/recommended/no-user")
    public ResponseEntity<Page<ProductSliderItemDTO>> getRecommendedProductsNotLoggedIn(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "List of up to 20 productIDs and pageNumber", required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RecommendedProductNotLoggedRequestDTO.class),
                    examples = @ExampleObject(value = "{ \"pageNumber\": 0, \"historyCache\": [\"681a55f2cb20535492b5e695\", \"681a55f2cb20535492b5e691\"] }")))
                                                                        @RequestBody RecommendedProductNotLoggedRequestDTO requestDTO, Pageable pageable) {
        log.info("Calling get recommended products API while user is NOT logged in, page {}! Size of historyCache: {}", pageable.getPageNumber(), requestDTO.getHistoryCache().size());
        return ResponseEntity.ok(productService.getRecommendedProductsWithoutID(requestDTO, pageable));
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
