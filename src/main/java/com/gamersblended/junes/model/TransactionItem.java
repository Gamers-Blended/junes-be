package com.gamersblended.junes.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_items", schema = "junes_rel")
@Getter
@Setter
public class TransactionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_item_id")
    private Long transactionItemID;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionID;

    @Column(name = "product_id", nullable = false)
    private String productID;

    @Min(1)
    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "price_at_time_of_sale", nullable = false)
    private BigDecimal priceAtTimeOfSale;

    @Column(name = "created_on", nullable = false)
    @CreationTimestamp // Hibernate will automatically set this on insert
    private LocalDateTime createdOn;

    @Column(name = "updated_on")
    @UpdateTimestamp // Hibernate will automatically update this on modify
    private LocalDateTime updatedOn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", insertable = false, updatable = false)
    private Transaction transaction;
}
