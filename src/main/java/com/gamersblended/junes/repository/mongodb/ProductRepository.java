package com.gamersblended.junes.repository.mongodb;

import com.gamersblended.junes.model.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository("mongoProductsRepository") //  MongoDB repository bean
public interface ProductRepository extends MongoRepository<Product, String>, CustomProductRepository {

    List<Product> findAll();

    @Query(value = "{}", sort = "{ 'units_sold': -1 }")
    List<Product> findTopProductsWithPagination(Pageable pageable);

    @Query("{ 'release_date': { '$gte': '?0' } }")
    List<Product> findPreOrderProductsAfterDateWithPagination(LocalDate currentDate, Pageable pageable);

    /**
     * Find products with created_on before currentDate
     *
     * @param currentDate Upper bound for date product added to database (inclusive)
     * @param pageable    Page number
     * @return List of products
     */
    @Query("{ 'created_on': { '$lte': '?0' } }")
    List<Product> findBestSellersBeforeDateWithPagination(LocalDate currentDate, Pageable pageable);

    Optional<Product> findById(String id);

    List<Product> findByIdIn(List<String> idList);

    List<Product> findBySlug(String slug);

    // Specific query, MongoDB query syntax:
    // @Query("{ }") // Select all documents (similar to findAll)
}
