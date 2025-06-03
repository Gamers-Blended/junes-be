package com.gamersblended.junes.controller;

import com.gamersblended.junes.dto.CartProductDTO;
import com.gamersblended.junes.dto.ProductSliderItemDTO;
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

import java.util.List;

@Slf4j
@RestController
@RequestMapping("junes/api/v1/cart")
public class CartController {

    private CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @Operation(summary = "Get products in user's cart")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Products inside user's cart shown",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = CartProductDTO.class))})
    })
    @PostMapping("/products")
    public ResponseEntity<Page<CartProductDTO>> getCartProducts(@RequestParam(required = false) Integer userID, @RequestBody(required = false) List<CartProductDTO> cartProductDTOList, Pageable pageable) {
        if (null == cartProductDTOList) {
            log.info("Calling get shopping cart product(s) API for logged user with userID = {}, page = {}, sort by = {}!", userID, pageable.getPageNumber(), pageable.getSort());
        } else {
            log.info("Calling get shopping cart product(s) API for guest user, page {}!", pageable.getPageNumber());
        }
        return ResponseEntity.ok(cartService.getCartProducts(userID, cartProductDTOList, pageable));
    }

    @Operation(summary = "Add product to user's cart")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product added to user's cart",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ProductSliderItemDTO.class))}),
            @ApiResponse(responseCode = "400", description = "Invalid quantity given",
                    content = @Content)
    })
    @PostMapping("/add")
    public ResponseEntity<String> addToCart(@RequestParam Integer userID, @RequestBody CartProductDTO cartProductDTO) {
        log.info("Calling add to cart API for userID = {}, product = {}!", userID, cartProductDTO);
        return ResponseEntity.ok(cartService.addToCart(userID, cartProductDTO));
    }

    @Operation(summary = "Remove product from user's cart")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product removed from cart",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ProductSliderItemDTO.class))}),
            @ApiResponse(responseCode = "204", description = "Users cart does not exist in database",
                    content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid quantity given",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Product not found in cart",
                    content = @Content),
    })
    @PostMapping("/remove")
    public ResponseEntity<String> removeFromCart(@RequestParam Integer userID, @RequestBody CartProductDTO cartProductDTO) {
        log.info("Calling remove from cart API for userID = {}, productID = {}, quantity = {}!", userID, cartProductDTO.getProductID(), cartProductDTO.getQuantity());
        return ResponseEntity.ok(cartService.removeFromCart(userID, cartProductDTO));
    }
}
