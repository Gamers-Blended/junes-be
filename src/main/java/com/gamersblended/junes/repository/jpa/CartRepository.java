package com.gamersblended.junes.repository.jpa;

import com.gamersblended.junes.model.Cart;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<Cart, Integer> {

    @Query(value = "SELECT * FROM customer_data.carts WHERE user_id = :user_id", nativeQuery = true)
    Page<Cart> getUserCart(@Param("user_id") Integer userID, Pageable pageable);
}
