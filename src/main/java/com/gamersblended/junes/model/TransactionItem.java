package com.gamersblended.junes.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transaction_items", schema = "junes_rel")
@Getter
@Setter
public class TransactionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "transaction_item_id")
    private UUID transactionItemID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @Column(name = "product_id", nullable = false)
    private String productID;

    @Min(1)
    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "created_on", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdOn;

    @Column(name = "updated_on")
    @UpdateTimestamp
    private LocalDateTime updatedOn;
}
