package com.gamersblended.junes.repository.jpa;

import com.gamersblended.junes.model.CartItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CartRepository extends JpaRepository<CartItem, Integer> {

    @Query(value = "SELECT * FROM junes_rel.cart_items WHERE user_id = :user_id", nativeQuery = true)
    Page<CartItem> getUserCart(@Param("user_id") Long userID, Pageable pageable);

    @Query(value = "SELECT * FROM junes_rel.cart_items WHERE user_id = :user_id", nativeQuery = true)
    List<CartItem> getUserCart(@Param("user_id") Long userID);
}
