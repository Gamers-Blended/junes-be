package com.gamersblended.junes.service;

import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.repository.jpa.UsersRepository;
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

    @Autowired
    private UsersRepository usersRepository;

    public List<Product> getAllProducts() {
        List<Product> res = productsRepository.findAll();
        log.info("Total number of products returned from db: {}", res.size());
        return res;

    }

    public List<Product> getRecommendedProducts(Integer optionalUserID) {
        if (null != optionalUserID) {
            // get user's browsing history
            List<String> userHistoryList = usersRepository.getUserHistory(optionalUserID);

            // check if history is empty
            if (userHistoryList.isEmpty()) {
                return productsRepository.findTop10ByOrderByUnitsSoldDesc();
            }
        }
        return productsRepository.findTop10ByOrderByUnitsSoldDesc();
    }
}
