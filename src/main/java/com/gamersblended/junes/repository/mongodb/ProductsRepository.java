package com.gamersblended.junes.repository.mongodb;

import com.gamersblended.junes.model.Products;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("mongoProductsRepository") //  MongoDB repository bean
public interface ProductsRepository extends MongoRepository<Products, String> {

    // Spring Data MongoDB's method name convention
    List<Products> findAll();

    // Specific query, MongoDB query syntax:
    // @Query("{ }") // Select all documents (similar to findAll)
    // List<Products> getAllProducts();
}
