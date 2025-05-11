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

    private static final int MAX_CACHE_SIZE = 20;

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

    // Case for logged users
    public List<Product> getRecommendedProductsWithID(Integer userID) {
        try {
            if (null != userID) {
                // Get user's browsing history
                List<String> userHistoryList = usersRepository.getUserHistory(userID);
                log.info("Retrieved userHistoryList: {}", userHistoryList);

                // Call Recommender System API if browsing history non-empty
                if (!userHistoryList.isEmpty() && !userHistoryList.contains("")) {
                    return callRecommenderSystem(userHistoryList);
                }
                log.info("UserID {} doesn't have any browsing history", userID);
            }
        } catch (Exception ex) {
            log.error("Exception in getRecommendedProductsWithID for userID {}: ", userID, ex);
        }
        return returnDefaultRecommendedProducts();
    }

    // Case for user not logged in
    public List<Product> getRecommendedProductsWithoutID(List<String> browsingCache) {
        try {
            // Keep only the most recent 20 products in browsingCache
            if (null != browsingCache && browsingCache.size() > MAX_CACHE_SIZE) {
                log.info("browsingCache exceeded max capacity of {}, keeping only the most recent {} products...", MAX_CACHE_SIZE, MAX_CACHE_SIZE);
                browsingCache = browsingCache.subList(browsingCache.size() - MAX_CACHE_SIZE, browsingCache.size());
            }
            if (null != browsingCache && !browsingCache.contains("")) {
                return callRecommenderSystem(browsingCache);
            }
        } catch (Exception ex) {
            log.error("Exception in getRecommendedProductsWithoutID for browsingCache {}: ", browsingCache, ex);
        }
        log.info("browsingCache is empty, returning default products...");
        return returnDefaultRecommendedProducts();
    }

    public List<Product> callRecommenderSystem(List<String> inputProductIDList) {
        log.info("Recommender API called with {} product(s)!", inputProductIDList.size());

        // Mocked result
        return productsRepository.findTop10ByOrderByUnitsSoldAsc();
    }

    // Default return top 20 products in terms of most units sold
    public List<Product> returnDefaultRecommendedProducts() {
        return productsRepository.findTop20ByOrderByUnitsSoldDesc();
    }
}
