package com.gamersblended.junes.controller;

import com.gamersblended.junes.dto.ProductSliderItemDTO;
import com.gamersblended.junes.dto.RecommendedProductNotLoggedRequestDTO;
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
    public List<ProductSliderItemDTO> getRecommendedProductsLoggedIn(@RequestParam Integer userID, @RequestParam Integer pageNumber) {
        log.info("Calling get recommended products API while userID {} is logged in, page {}!", userID, pageNumber);
        return productService.getRecommendedProductsWithID(userID, pageNumber);
    }

    // User not logged in
    @PostMapping("/recommended/no-user")
    public List<ProductSliderItemDTO> getRecommendedProductsNotLoggedIn(@RequestBody RecommendedProductNotLoggedRequestDTO requestDTO) {
        log.info("Calling get recommended products API while user is NOT logged in, page {}! Size of historyCache: {}", requestDTO.getPageNumber(), requestDTO.getHistoryCache().size());
        return productService.getRecommendedProductsWithoutID(requestDTO);
    }

}
