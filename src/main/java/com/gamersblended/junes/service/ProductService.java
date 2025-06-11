package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.ProductDTO;
import com.gamersblended.junes.dto.ProductSliderItemDTO;
import com.gamersblended.junes.dto.RecommendedProductNotLoggedRequestDTO;
import com.gamersblended.junes.exception.InvalidProductIdException;
import com.gamersblended.junes.exception.ProductNotFoundException;
import com.gamersblended.junes.mapper.ProductMapper;
import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.repository.jpa.UserRepository;
import com.gamersblended.junes.repository.mongodb.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ProductService {

    private static final int MAX_CACHE_SIZE = 20;
    private static final int PAGE_SIZE = 5;
    private static final String UNITS_SOLD = "units_sold";
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

    /**
     * For get recommended products API
     * Case 1: logged users
     *
     * @param userID     ID of logged user calling the API
     * @param pageNumber Starts from 0, last page calculated from front end
     * @return List of up to 5 recommended products
     */
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

    /**
     * For get recommended products API
     * Case 2: user not logged in
     *
     * @param requestDTO Contains historyCache & pageNumber
     * @return List of up to 5 recommended products
     */
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

    /**
     * Calls external recommender system
     *
     * @param inputProductIDList List of up to 20 unique product IDs
     * @param pageNumber         Starts from 0, last page calculated from front end
     * @return List of up to 5 recommended products
     */
    public List<ProductSliderItemDTO> callRecommenderSystem(List<String> inputProductIDList, Integer pageNumber) {
        log.info("Recommender API called with {} product(s) for page {}!", inputProductIDList.size(), pageNumber);
        PageRequest pageRequest = PageRequest.of(pageNumber, PAGE_SIZE, Sort.by(Sort.Direction.DESC, UNITS_SOLD));
        // Mocked result
        return productMapper.toSliderItemDTOList(productRepository.findTopProductsWithPagination(pageRequest));
    }


    /**
     * By default, gives products with most units sold, descending
     *
     * @param pageNumber Starts from 0, last page calculated from front end
     * @return List of up to 5 products with most units sold as default
     */
    public List<ProductSliderItemDTO> returnDefaultRecommendedProducts(Integer pageNumber) {
        log.info("Returning default products with most units sold for page {}!", pageNumber);
        PageRequest pageRequest = PageRequest.of(pageNumber, PAGE_SIZE, Sort.by(Sort.Direction.DESC, UNITS_SOLD));

        return productMapper.toSliderItemDTOList(productRepository.findTopProductsWithPagination(pageRequest));
    }

    /**
     * For get preorder products API
     *
     * @param currentDate Products must have release_date on or after this date (triggered date if not given)
     * @param pageNumber  Starts from 0, last page calculated from front end
     * @return List of up to 5 preorder products in ascending release_date order
     */
    public List<ProductSliderItemDTO> getPreOrderProducts(LocalDate currentDate, Integer pageNumber) {
        try {
            if (null == currentDate) {
                currentDate = LocalDate.now();
            }
            log.info("Searching for preorder products on and after the date: {}, page: {}", currentDate, pageNumber);
            PageRequest pageRequest = PageRequest.of(pageNumber, PAGE_SIZE, Sort.by(Sort.Direction.ASC, "release_date"));
            return productMapper.toSliderItemDTOList(productRepository.findPreOrderProductsAfterDateWithPagination(currentDate, pageRequest));
        } catch (Exception ex) {
            log.error("Exception in getPreOrderProducts: ", ex);
            return new ArrayList<>();
        }
    }

    /**
     * For get bestseller API
     *
     * @param currentDate Products must have created_on on or before this date (triggered date if not given)
     * @param pageNumber  Starts from 0, last page calculated from front end
     * @return List of up to 5 bestsellers in terms of units_sold in descending order
     */
    public List<ProductSliderItemDTO> getBestSellers(LocalDate currentDate, Integer pageNumber) {
        try {
            if (null == currentDate) {
                currentDate = LocalDate.now();
            }
            log.info("Searching for best selling products for date: {}, page: {}", currentDate, pageNumber);
            PageRequest pageRequest = PageRequest.of(pageNumber, PAGE_SIZE, Sort.by(Sort.Direction.DESC, UNITS_SOLD));
            return productMapper.toSliderItemDTOList(productRepository.findBestSellersBeforeDateWithPagination(currentDate, pageRequest));
        } catch (Exception ex) {
            log.error("Exception in getBestSellers: ", ex);
            return new ArrayList<>();
        }
    }

    /**
     * For get product details for quick shop API
     *
     * @param productID _id value of product
     * @return Product details used in quick shop window
     */
    public ProductDTO getQuickShopDetails(String productID) {
        try {
            if (null == productID || productID.trim().isEmpty()) {
                log.error("productID cannot be null or empty.");
                throw new InvalidProductIdException("Product ID cannot be null or empty.");
            }

            log.info("Searching product details for productID: {}", productID);
            Optional<Product> product = productRepository.findById(productID);
            if (product.isPresent()) {
                return productMapper.toDTO(product.get());
            } else {
                log.error("productID {} not found.", productID);
                throw new ProductNotFoundException("productID " + productID + " not found.");
            }

        } catch (InvalidProductIdException | ProductNotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Exception in getQuickShopDetails for ID {}: {}", productID, ex.getMessage());
            throw new RuntimeException("Service error in getQuickShopDetails: Could not retrieve product details.", ex);
        }
    }

    public Page<ProductSliderItemDTO> getProductListings(String platform, String name, List<String> availability, BigDecimal minPrice, BigDecimal maxPrice, List<String> genres, List<String> regions, List<String> publishers, List<String> editions, List<String> languages, String startingLetter, String releaseDate, LocalDate currentDate, Pageable pageable) {
        try {
            // Optional month-year filter
            YearMonth releaseYearMonth = null;
            if (null != releaseDate) {
                releaseYearMonth = YearMonth.parse(releaseDate);
                log.info("releaseYearMonth filter provided: {}", releaseYearMonth);
            }

            // Process currentDate
            if (null == currentDate) {
                currentDate = LocalDate.now();
            }
            log.info("Reference date: {}", currentDate);

            Page<Product> productsPage = productRepository.findProductsWithFilters(
                    platform, name, availability, minPrice, maxPrice, genres,
                    regions, publishers, editions, languages, startingLetter,
                    releaseYearMonth, currentDate, pageable);

            return productsPage.map(productMapper::toSliderItemDTO);
        } catch (Exception ex) {
            log.error("Exception in getProductListings for platform = {}: {}", platform, ex.getMessage());
            throw new RuntimeException("Service error in getProductListings: Could not retrieve product listings for " + platform + ".", ex);
        }
    }
}
