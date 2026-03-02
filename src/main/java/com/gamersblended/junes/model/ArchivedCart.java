package com.gamersblended.junes.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "archived_carts",
        schema = "junes_rel")
@Getter
@Setter
public class ArchivedCart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "cart_item_id")
    private UUID archivedCartId;

    @Column(name = "order_number", nullable = false, unique = true)
    private String orderNumber;

    @Column(name ="user_id", nullable = false)
    private UUID userID;

    @Column(name = "cart_data", nullable = false, columnDefinition = "jsonb")
    private String cartData; // JSON string of cart

    @Column(name = "total_items", nullable = false)
    private Integer totalItems;

    @Column(name = "archived_on", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime archivedOn;
}
