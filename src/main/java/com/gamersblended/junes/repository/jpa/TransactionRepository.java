package com.gamersblended.junes.repository.jpa;

import com.gamersblended.junes.dto.OrderEvent;
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

    @Query("""
                SELECT new com.gamersblended.junes.dto.OrderEvent(ti.productID, t.createdOn)
                FROM TransactionItem ti
                JOIN ti.transaction t
                WHERE t.userID = :userID
                ORDER BY t.createdOn DESC
            """)
    List<OrderEvent> findRecentItemsByUserID(@Param("userID") UUID userID, Pageable pageable);
}
