package com.gamersblended.junes.controller;

import com.gamersblended.junes.annotation.RateLimit;
import com.gamersblended.junes.dto.ProductInWishlistDTO;
import com.gamersblended.junes.dto.WishlistItemDTO;
import com.gamersblended.junes.dto.response.ErrorResponseDTO;
import com.gamersblended.junes.service.AccessTokenService;
import com.gamersblended.junes.service.WishlistService;
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

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("junes/api/v1/wishlist")
@RateLimit(requests = 10, duration = 1, timeUnit = TimeUnit.MINUTES, perUser = true)
public class WishlistController {

    private final WishlistService wishlistService;
    private final AccessTokenService accessTokenService;

    public WishlistController(WishlistService wishlistService, AccessTokenService accessTokenService) {
        this.wishlistService = wishlistService;
        this.accessTokenService = accessTokenService;
    }

    @Operation(summary = "Get products in user's wishlist")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Paged list of products in wishlist"),
            @ApiResponse(responseCode = "400", description = "User ID or Session ID required",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Corrupt wishlist data",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Failed to serialise wishlist",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))})
    })
    @GetMapping("/products")
    public ResponseEntity<Page<ProductInWishlistDTO>> getWishlistProducts(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                                                          @RequestHeader(value = "X-Session-Id", required = false) UUID sessionID, Pageable pageable) {
        log.info("Calling get wishlist product(s) API, page {}", pageable.getPageNumber());

        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);
        return ResponseEntity.ok(wishlistService.getWishlistProducts(userID, sessionID, pageable));
    }

    @Operation(summary = "Add product to user's wishlist")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product added to user's wishlist",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = String.class))}),
            @ApiResponse(responseCode = "400", description = "User ID or Session ID required",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "404", description = "Product ID not found",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "409", description = "Wishlist was modified concurrently, retry the request",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Corrupt wishlist data",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Failed to serialise wishlist",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Error persisting wishlist to database",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))})
    })
    @PostMapping("/add")
    public ResponseEntity<String> addToWishlist(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                                @RequestHeader(value = "X-Session-Id", required = false) UUID sessionID, @RequestBody WishlistItemDTO wishlistItemDTO) {
        log.info("Calling add to wishlist API");

        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);
        wishlistService.addItemToWishlist(userID, sessionID, wishlistItemDTO);
        return ResponseEntity.ok("Product added to wishlist");
    }

    @Operation(summary = "Remove product from user's wishlist")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product removed from wishlist",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = String.class))}),
            @ApiResponse(responseCode = "400", description = "User ID or Session ID required",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "409", description = "Wishlist was modified concurrently, retry the request",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Corrupt wishlist data",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Failed to serialise wishlist",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))})
    })
    @DeleteMapping("/remove/{productID}")
    public ResponseEntity<String> removeFromWishlist(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                                     @RequestHeader(value = "X-Session-Id", required = false) UUID sessionID, @PathVariable String productID) {
        log.info("Calling remove from wishlist API");

        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);
        wishlistService.removeItemFromWishlist(userID, sessionID, productID);
        return ResponseEntity.ok("Product removed from wishlist");
    }

    @Operation(summary = "Clear user's wishlist")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Wishlist cleared successfully",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = String.class))}),
            @ApiResponse(responseCode = "400", description = "User ID or Session ID required",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "409", description = "Wishlist was modified concurrently, retry the request",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Corrupt wishlist data",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Failed to serialise wishlist",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))})
    })
    @DeleteMapping("/items")
    public ResponseEntity<String> clearWishlist(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Session-Id", required = false) UUID sessionID
    ) {
        log.info("Calling clear wishlist API");

        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);
        wishlistService.clearWishlist(userID, sessionID);
        return ResponseEntity.ok("Wishlist cleared successfully");
    }
}
