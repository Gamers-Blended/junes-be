package com.gamersblended.junes.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "transactions", schema = "junes_rel")
@Getter
@Setter
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "transaction_id")
    private UUID transactionID;

    @OneToMany(mappedBy = "transaction", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransactionItem> items;

    @Column(name = "order_number", nullable = false)
    private String orderNumber;

    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    @Column(nullable = false)
    private String status;

    @Min(0)
    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Min(0)
    @Column(name = "shipping_cost", nullable = false)
    private BigDecimal shippingCost;

    @Column(name = "shipped_date")
    private LocalDateTime shippedDate;

    @Min(0)
    @Column(name = "shipping_weight", nullable = false)
    private BigDecimal shippingWeight;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "shipping_address_id", nullable = false)
    private UUID shippingAddressID;

    @Column(name = "user_id", nullable = false)
    private UUID userID;

    @Column(name = "created_on", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdOn;

    @Column(name = "updated_on")
    @UpdateTimestamp
    private LocalDateTime updatedOn;
}
