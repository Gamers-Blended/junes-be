package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.ProductSliderItemDTO;
import com.gamersblended.junes.dto.RecommendedProductNotLoggedRequestDTO;
import com.gamersblended.junes.mapper.ProductMapper;
import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.repository.jpa.UserRepository;
import com.gamersblended.junes.repository.mongodb.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ProductService {

    private static final int MAX_CACHE_SIZE = 20;
    private static final int PAGE_SIZE = 5;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductMapper productMapper;

    @Autowired
    public ProductService(ProductRepository productRepository, UserRepository userRepository, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.productMapper = productMapper;
    }

    public List<Product> getAllProducts() {
        List<Product> res = productRepository.findAll();
        log.info("Total number of products returned from db: {}", res.size());
        return res;
    }

    // Case for logged users
    public List<ProductSliderItemDTO> getRecommendedProductsWithID(Integer userID, Integer pageNumber) {
        try {
            if (null != userID) {
                // Get user's browsing history
                List<String> userHistoryList = userRepository.getUserHistory(userID);
                log.info("Retrieved userHistoryList: {}", userHistoryList);

                // Call Recommender System API if browsing history non-empty
                if (!userHistoryList.isEmpty() && !userHistoryList.contains("")) {
                    return callRecommenderSystem(userHistoryList, pageNumber);
                }
                log.info("UserID {} doesn't have any browsing history", userID);
            }
        } catch (Exception ex) {
            log.error("Exception in getRecommendedProductsWithID for userID {}: ", userID, ex);
        }
        return returnDefaultRecommendedProducts(pageNumber);
    }

    // Case for user not logged in
    public List<ProductSliderItemDTO> getRecommendedProductsWithoutID(RecommendedProductNotLoggedRequestDTO requestDTO) {
        List<String> browsingCache = requestDTO.getHistoryCache();
        Integer pageNumber = requestDTO.getPageNumber();
        try {
            // Keep only the most recent 20 products in browsingCache
            if (browsingCache.size() > MAX_CACHE_SIZE) {
                log.info("browsingCache exceeded max capacity of {}, keeping only the most recent {} products...", MAX_CACHE_SIZE, MAX_CACHE_SIZE);
                browsingCache = browsingCache.subList(browsingCache.size() - MAX_CACHE_SIZE, browsingCache.size());
            }
            if (!browsingCache.contains("") && !browsingCache.isEmpty()) {
                return callRecommenderSystem(browsingCache, pageNumber);
            }
        } catch (Exception ex) {
            log.error("Exception in getRecommendedProductsWithoutID for browsingCache {}: ", browsingCache, ex);
        }
        log.info("browsingCache is empty, returning default products...");
        return returnDefaultRecommendedProducts(pageNumber);
    }

    public List<ProductSliderItemDTO> callRecommenderSystem(List<String> inputProductIDList, Integer pageNumber) {
        log.info("Recommender API called with {} product(s) for page {}!", inputProductIDList.size(), pageNumber);
        PageRequest pageRequest = PageRequest.of(pageNumber, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "units_sold"));
        // Mocked result
        return productMapper.toSliderItemDTOList(productRepository.findTopProductsWithPagination(pageRequest));
    }

    // Default return products with most units sold, descending
    public List<ProductSliderItemDTO> returnDefaultRecommendedProducts(Integer pageNumber) {
        log.info("Returning default products with most units sold for page {}!", pageNumber);
        PageRequest pageRequest = PageRequest.of(pageNumber, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "units_sold"));

        return productMapper.toSliderItemDTOList(productRepository.findTopProductsWithPagination(pageRequest));
    }

    public List<ProductSliderItemDTO> getPreOrderProducts(LocalDate currentDate, Integer pageNumber) {
        try {
            if (null == currentDate) {
                currentDate = LocalDate.now();
            }
            log.info("Searching for preorder products after date: {}", currentDate);
            PageRequest pageRequest = PageRequest.of(pageNumber, PAGE_SIZE, Sort.by(Sort.Direction.ASC, "release_date"));
            return productMapper.toSliderItemDTOList(productRepository.findPreOrderProductsAfterDateWithPagination(currentDate, pageRequest));
        } catch (Exception ex) {
            log.error("Exception in getPreOrderProducts: ", ex);
            return new ArrayList<>();
        }
    }
}
