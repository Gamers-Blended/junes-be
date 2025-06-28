package com.gamersblended.junes.controller;

import com.gamersblended.junes.dto.ProductDetailsDTO;
import com.gamersblended.junes.dto.ProductSliderItemDTO;
import com.gamersblended.junes.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("junes/api/v1/product")
public class ProductController {

    private ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

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
            @RequestParam(required = false) String startingLetter,
            @RequestParam(required = false) String releaseDate,
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
                startingLetter,
                releaseDate,
                currentDate,
                pageable));
    }

    @GetMapping("/details/{productSlug}")
    public ResponseEntity<ProductDetailsDTO> getProductDetails(@PathVariable String productSlug) {
        log.info("Calling get product details API for title: {}!", productSlug);
        return ResponseEntity.ok(productService.getProductDetails(productSlug));
    }
}
