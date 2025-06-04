package com.gamersblended.junes.controller;

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
            @RequestParam(required = false) List<String> genre,
            @RequestParam(required = false) List<String> region,
            @RequestParam(required = false) List<String> publisher,
            @RequestParam(required = false) List<String> edition,
            @RequestParam(required = false) List<String> rating,
            @RequestParam(required = false) List<String> language,
            @RequestParam(required = false) String startingLetter,
            @RequestParam(required = false) String releaseDate,
            Pageable pageable) {
        log.info("Calling get product listings API for platform: {}, page = {}, sort by = {}!", platform, pageable.getPageNumber(), pageable.getSort());
        return ResponseEntity.ok(productService.getProductListings(
                platform,
                name,
                availability,
                minPrice,
                maxPrice,
                genre,
                region,
                publisher,
                edition,
                rating,
                language,
                startingLetter,
                releaseDate,
                pageable));
    }
}
