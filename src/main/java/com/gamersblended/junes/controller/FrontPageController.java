package com.gamersblended.junes.controller;

import com.gamersblended.junes.dto.ProductDTO;
import com.gamersblended.junes.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("junes/api/v1/frontpage")
public class FrontPageController {

    @Autowired
    private ProductService productService;

    // User logged in
    @GetMapping("/recommended")
    public List<ProductDTO> getRecommendedProductsLoggedIn(@RequestParam Integer userID) {
        log.info("Calling get recommended products while userID {} is logged in API!", userID);
        return productService.getRecommendedProductsWithID(userID);
    }

    // User not logged in
    @PostMapping("/recommended/no-user")
    public List<ProductDTO> getRecommendedProductsNotLoggedIn(@RequestBody List<String> historyCache) {
        log.info("Calling get recommended products while user is NOT logged in API! Size of historyCache: {}", historyCache.size());
        return productService.getRecommendedProductsWithoutID(historyCache);
    }

}
