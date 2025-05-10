package com.gamersblended.junes.controller;

import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("junes/api/v1/frontpage")
public class FrontPageController {

    @Autowired
    private ProductService productService;

    @GetMapping("/recommended")
    public List<Product> getRecommendedItems(@RequestParam(required = false) Integer optionalUserID) {
        return productService.getRecommendedProducts(optionalUserID);
    }

}
