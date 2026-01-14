package com.gamersblended.junes.repository.jpa;

import com.gamersblended.junes.model.Transaction;
import com.gamersblended.junes.model.TransactionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionItemRepository extends JpaRepository<Transaction, UUID> {

    @Query("SELECT ti FROM TransactionItem ti WHERE ti.transaction.transactionID = :transactionID")
    List<TransactionItem> findByTransactionID(@Param("transactionID") UUID transactionID);

    @Query("SELECT ti FROM TransactionItem ti WHERE ti.transaction.transactionID IN :transactionIDList")
    List<TransactionItem> findByTransactionIDs(@Param("transactionIDList") List<UUID> transactionIDList);

}
