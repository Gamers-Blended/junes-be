package com.gamersblended.junes.controller;

import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.service.ProductService;
import com.gamersblended.junes.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("junes/api/v1")
public class TestController {

    @Autowired
    private AuthService authService;

    @Autowired
    private ProductService productService;

    @GetMapping("/all-products")
    public List<Product> getAllProducts() {
        return productService.getAllProducts();
    }
}
