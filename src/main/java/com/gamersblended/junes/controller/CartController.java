package com.gamersblended.junes.controller;

import com.gamersblended.junes.dto.CartProductDTO;
import com.gamersblended.junes.service.CartService;
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

    @PostMapping("/products")
    public ResponseEntity<Page<CartProductDTO>> getCartProducts(@RequestParam(required = false) Integer userID, @RequestBody(required = false) List<CartProductDTO> cartProductDTOList, Pageable pageable) {
        if (null == cartProductDTOList) {
            log.info("Calling get shopping cart product(s) API for guest user, page {}!", pageable.getPageNumber());
        } else {
            log.info("Calling get shopping cart product(s) API for logged user with userID = {}, page {}!", userID, pageable.getPageNumber());
        }
        return ResponseEntity.ok(cartService.getCartProducts(userID, cartProductDTOList, pageable));
    }
}
