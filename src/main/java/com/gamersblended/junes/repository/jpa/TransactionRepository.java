package com.gamersblended.junes.repository.jpa;

import com.gamersblended.junes.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    // Derived query
    Page<Transaction> findByUserID(UUID userID, Pageable pageable);

    Optional<Transaction> findByUserIDAndTransactionID(UUID userID, UUID transactionID);
}
