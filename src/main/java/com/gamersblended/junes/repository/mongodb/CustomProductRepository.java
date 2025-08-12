package com.gamersblended.junes.repository.mongodb;

import com.gamersblended.junes.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public interface CustomProductRepository {
    Page<Product> findProductsWithFilters(
            String platform,
            String name,
            List<String> availability,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            List<String> genres,
            List<String> regions,
            List<String> publishers,
            List<String> editions,
            List<String> languages,
            List<String> startingLetters,
            List<YearMonth> releaseDates,
            String currentDate,
            Pageable pageable);
}
