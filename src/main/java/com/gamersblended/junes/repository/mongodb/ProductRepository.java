package com.gamersblended.junes.repository.mongodb;

import com.gamersblended.junes.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("mongoProductsRepository") //  MongoDB repository bean
public interface ProductRepository extends MongoRepository<Product, String> {

    List<Product> findAll();

    List<Product> findTop20ByOrderByUnitsSoldDesc();

    List<Product> findTop10ByOrderByUnitsSoldAsc();

    List<Product> findTop20ByOrderByReleaseDateAsc();

    // Specific query, MongoDB query syntax:
    // @Query("{ }") // Select all documents (similar to findAll)
}
