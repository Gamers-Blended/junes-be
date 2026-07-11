package com.gamersblended.junes.controller;

import com.gamersblended.junes.annotation.RateLimit;
import com.gamersblended.junes.dto.CartItemDTO;
import com.gamersblended.junes.dto.ProductInCartDTO;
import com.gamersblended.junes.dto.response.ErrorResponseDTO;
import com.gamersblended.junes.service.AccessTokenService;
import com.gamersblended.junes.service.CartService;
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
@RequestMapping("junes/api/v1/cart")
@RateLimit(requests = 10, duration = 1, timeUnit = TimeUnit.MINUTES)
public class CartController {

    private final CartService cartService;
    private final AccessTokenService accessTokenService;

    public CartController(CartService cartService, AccessTokenService accessTokenService) {
        this.cartService = cartService;
        this.accessTokenService = accessTokenService;
    }

    @Operation(summary = "Get products in user's cart")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Paged list of products in cart"),
            @ApiResponse(responseCode = "400", description = "User ID or Session ID required",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Corrupt cart data",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Failed to serialise cart",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))})
    })
    @GetMapping("/products")
    public ResponseEntity<Page<ProductInCartDTO>> getCartProducts(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                                                  @RequestHeader(value = "X-Session-Id", required = false) UUID sessionID, Pageable pageable) {
        log.info("Calling get shopping cart product(s) API, page {}", pageable.getPageNumber());

        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);
        return ResponseEntity.ok(cartService.getCartProducts(userID, sessionID, pageable));
    }

    @Operation(summary = "Add product to user's cart")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product added to user's cart",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = String.class))}),
            @ApiResponse(responseCode = "400", description = "User ID or Session ID required",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "400", description = "Invalid quantity given",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "404", description = "Product ID not found",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Corrupt cart data",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Failed to serialise cart",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Error persisting cart to database",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))})
    })
    @PostMapping("/add")
    public ResponseEntity<String> addToCart(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                            @RequestHeader(value = "X-Session-Id", required = false) UUID sessionID, @RequestBody CartItemDTO cartItemDTO) {
        log.info("Calling add to cart API");

        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);
        cartService.addItemToCart(userID, sessionID, cartItemDTO);
        return ResponseEntity.ok("Product added to cart");
    }

    @Operation(summary = "Remove product from user's cart")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product removed from cart",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = String.class))}),
            @ApiResponse(responseCode = "400", description = "User ID or Session ID required",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Corrupt cart data",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Failed to serialise cart",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Error inserting token into database",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))})
    })
    @DeleteMapping("/remove/{productID}")
    public ResponseEntity<String> removeFromCart(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                                 @RequestHeader(value = "X-Session-Id", required = false) UUID sessionID, @PathVariable String productID) {
        log.info("Calling remove from cart API");

        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);
        cartService.removeItemFromCart(userID, sessionID, productID);
        return ResponseEntity.ok("Product removed from cart");
    }

    @Operation(summary = "Change the quantity of a product in user's cart")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Quantity updated successfully",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = String.class))}),
            @ApiResponse(responseCode = "400", description = "User ID or Session ID required",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "400", description = "Invalid quantity given",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Corrupt cart data",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Failed to serialise cart",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Error inserting token into database",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))})
    })
    @PutMapping("/{productID}/quantity")
    public ResponseEntity<String> updateQuantity(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Session-Id", required = false) UUID sessionID,
            @PathVariable String productID,
            @RequestParam int quantity
    ) {
        log.info("Calling update item quantity API");

        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);
        cartService.updateItemQuantity(userID, sessionID, productID, quantity);
        return ResponseEntity.ok("Quantity updated successfully");
    }

    @Operation(summary = "Clear user's cart")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cart cleared successfully",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = String.class))}),
            @ApiResponse(responseCode = "400", description = "User ID or Session ID required",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Corrupt cart data",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Failed to serialise cart",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Error inserting token into database",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))})
    })
    @DeleteMapping("/items")
    public ResponseEntity<String> clearCart(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Session-Id", required = false) UUID sessionID
    ) {
        log.info("Calling clear cart API");

        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);
        cartService.clearCart(userID, sessionID);
        return ResponseEntity.ok("Cart cleared successfully");
    }
}
