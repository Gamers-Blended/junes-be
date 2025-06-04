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
            List<String> genre,
            List<String> region,
            List<String> publisher,
            List<String> edition,
            List<String> rating,
            List<String> language,
            String startingLetter,
            YearMonth releaseDate,
            Pageable pageable);
}
