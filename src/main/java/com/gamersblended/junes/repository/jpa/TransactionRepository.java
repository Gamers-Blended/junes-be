package com.gamersblended.junes.repository.jpa;

import com.gamersblended.junes.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByUserID(UUID userID, Pageable pageable);

    Optional<Transaction> findByUserIDAndOrderNumber(UUID userID, String orderNumber);

    @Query(value = """
            SELECT DISTINCT ON (ti.product_id)
                ti.product_id as productID,
                t.created_on as createdOn
            FROM junes_rel.transaction_items ti
            JOIN junes_rel.transactions t ON ti.transaction_id = t.transaction_id
            WHERE t.user_id = :userID
            ORDER BY ti.product_id, t.created_on DESC
            """, nativeQuery = true)
    List<Object[]> findRecentItemsByUserID(@Param("userID") UUID userID, Pageable pageable);
}
