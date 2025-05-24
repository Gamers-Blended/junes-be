package com.gamersblended.junes.controller;

import com.gamersblended.junes.dto.ProductDTO;
import com.gamersblended.junes.dto.ProductSliderItemDTO;
import com.gamersblended.junes.dto.RecommendedProductNotLoggedRequestDTO;
import com.gamersblended.junes.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;


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

    @GetMapping("/preorders")
    public List<ProductSliderItemDTO> getPreOrderProducts(@RequestParam(required = false) LocalDate currentDate, @RequestParam Integer pageNumber) {
        log.info("Calling get preorder products API, page {}!", pageNumber);
        return productService.getPreOrderProducts(currentDate, pageNumber);
    }

    @GetMapping("/best-sellers")
    public List<ProductSliderItemDTO> getBestSellingProducts(@RequestParam(required = false) LocalDate currentDate, @RequestParam Integer pageNumber) {
        log.info("Calling get best sellers API, page {}!", pageNumber);
        return productService.getBestSellers(currentDate, pageNumber);
    }

    @GetMapping("quick-shop/{productID}")
    public ProductDTO getQuickShopDetailsByID(@PathVariable String productID) {
        log.info("Calling get quick shop details API for productID: {}!", productID);
        return productService.getQuickShopDetails(productID);
    }
}
