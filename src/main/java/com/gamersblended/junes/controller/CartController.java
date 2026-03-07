package com.gamersblended.junes.controller;

import com.gamersblended.junes.annotation.RateLimit;
import com.gamersblended.junes.dto.CartItemDTO;
import com.gamersblended.junes.dto.ProductInCartDTO;
import com.gamersblended.junes.exception.InvalidQuantityException;
import com.gamersblended.junes.exception.MissingIdentifierException;
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

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @Operation(summary = "Get products in user's cart")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Paged list of products in cart")
    })
    @GetMapping("/products")
    public ResponseEntity<Page<ProductInCartDTO>> getCartProducts(@RequestHeader(value = "X-User-Id", required = false) UUID userID,
                                                                  @RequestHeader(value = "X-Session-Id", required = false) UUID sessionID, Pageable pageable) {
        log.info("Calling get shopping cart product(s) API, page {}", pageable.getPageNumber());

        return ResponseEntity.ok(cartService.getCartProducts(userID, sessionID, pageable));
    }

    @Operation(summary = "Add product to user's cart")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product added to user's cart",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = String.class))})
    })
    @PostMapping("/add")
    public ResponseEntity<String> addToCart(@RequestHeader(value = "X-User-Id", required = false) UUID userID,
                                            @RequestHeader(value = "X-Session-Id", required = false) UUID sessionID, @RequestBody CartItemDTO cartItemDTO) {
        log.info("Calling add to cart API for userID = {}", userID);

        cartService.addItemToCart(userID, sessionID, cartItemDTO);
        return ResponseEntity.ok("Product added to cart");
    }

    @Operation(summary = "Remove product from user's cart")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product removed from cart",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = String.class))}),
            @ApiResponse(responseCode = "400", description = "Invalid quantity given",
                    content = @Content),
    })
    @DeleteMapping("/remove/{productID}")
    public ResponseEntity<String> removeFromCart(@RequestHeader(value = "X-User-Id", required = false) UUID userID,
                                                 @RequestHeader(value = "X-Session-Id", required = false) UUID sessionID, @PathVariable String productID) {
        log.info("Calling remove from cart API for userID = {}, productID = {}", userID, productID);

        cartService.removeItemFromCart(userID, sessionID, productID);
        return ResponseEntity.ok("Product removed from cart");
    }

    @Operation(summary = "Remove product from user's cart")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product removed from cart",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = String.class))}),
            @ApiResponse(responseCode = "400", description = "Invalid quantity given",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InvalidQuantityException.class))}),
            @ApiResponse(responseCode = "400", description = "User ID or Session ID required",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = MissingIdentifierException.class))})
    })
    @PutMapping("/{productID}/quantity")
    public ResponseEntity<String> updateQuantity(
            @RequestHeader(value = "X-User-Id", required = false) UUID userID,
            @RequestHeader(value = "X-Session-Id", required = false) UUID sessionID,
            @PathVariable String productID,
            @RequestParam int quantity
    ) {
        log.info("Calling update item quantity API for userID = {}, productID = {}", userID, productID);
        cartService.updateItemQuantity(userID, sessionID, productID, quantity);

        return ResponseEntity.ok("Quantity updated successfully");
    }
}
