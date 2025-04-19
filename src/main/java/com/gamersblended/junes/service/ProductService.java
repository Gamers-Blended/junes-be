package com.gamersblended.junes.service;

import com.gamersblended.junes.model.Products;
import com.gamersblended.junes.repository.mongodb.ProductsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ProductService {

    @Autowired
    @Qualifier("mongoProductsRepository") // Inject MongoDB repository
    private ProductsRepository productsRepository;

    public List<Products> getAllProducts() {
        List<Products> res = productsRepository.findAll();
        log.info("Total number of products returned from db: {}", res.size());
        return res;

    }
}
