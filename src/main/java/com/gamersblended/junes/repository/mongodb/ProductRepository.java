package com.gamersblended.junes.repository.mongodb;

import com.gamersblended.junes.model.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("mongoProductsRepository") //  MongoDB repository bean
public interface ProductRepository extends MongoRepository<Product, String> {

    List<Product> findAll();

    @Query(value = "{}", sort = "{ 'units_sold': -1 }")
    List<Product> findTopProductsWithPagination(Pageable pageable);

    // Specific query, MongoDB query syntax:
    // @Query("{ }") // Select all documents (similar to findAll)
}
