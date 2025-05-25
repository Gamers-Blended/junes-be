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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;


@Slf4j
@RestController
@RequestMapping("junes/api/v1/frontpage")
public class FrontPageController {

    private ProductService productService;

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
    public List<ProductSliderItemDTO> getRecommendedProductsLoggedIn(@RequestParam Integer userID, @RequestParam Integer pageNumber) {
        log.info("Calling get recommended products API while userID {} is logged in, page {}!", userID, pageNumber);
        return productService.getRecommendedProductsWithID(userID, pageNumber);
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
    public List<ProductSliderItemDTO> getRecommendedProductsNotLoggedIn(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "List of up to 20 productIDs and pageNumber", required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = RecommendedProductNotLoggedRequestDTO.class),
                    examples = @ExampleObject(value = "{ \"pageNumber\": 0, \"historyCache\": [\"681a55f2cb20535492b5e695\", \"681a55f2cb20535492b5e691\"] }")))
                                                                        @RequestBody RecommendedProductNotLoggedRequestDTO requestDTO) {
        log.info("Calling get recommended products API while user is NOT logged in, page {}! Size of historyCache: {}", requestDTO.getPageNumber(), requestDTO.getHistoryCache().size());
        return productService.getRecommendedProductsWithoutID(requestDTO);
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
    public List<ProductSliderItemDTO> getPreOrderProducts(@RequestParam(required = false) LocalDate currentDate, @RequestParam Integer pageNumber) {
        log.info("Calling get preorder products API, page {}!", pageNumber);
        return productService.getPreOrderProducts(currentDate, pageNumber);
    }

    @Operation(summary = "Get a list of best-selling products")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Best-selling products shown",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ProductSliderItemDTO.class))}),
            @ApiResponse(responseCode = "404", description = "Products not found",
                    content = @Content)
    })
    @GetMapping("/best-sellers")
    public List<ProductSliderItemDTO> getBestSellingProducts(@RequestParam(required = false) LocalDate currentDate, @RequestParam Integer pageNumber) {
        log.info("Calling get best sellers API, page {}!", pageNumber);
        return productService.getBestSellers(currentDate, pageNumber);
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
    @GetMapping("quick-shop/{productID}")
    public ResponseEntity<ProductDTO> getQuickShopDetailsByID(@PathVariable String productID) {
        log.info("Calling get quick shop details API for productID: {}!", productID);
        return ResponseEntity.ok(productService.getQuickShopDetails(productID));
    }
}
