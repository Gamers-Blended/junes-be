package com.gamersblended.junes.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "cart_items",
        schema = "junes_rel",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "product_id"})
        })
@Getter
@Setter
public class CartItems {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_item_id")
    private Long cartItemID;

    @Column(name = "user_id", nullable = false)
    private Long userID;

    @Column(name = "product_id", nullable = false)
    private String productID;

    @Min(1)
    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "created_on", nullable = false)
    @CreationTimestamp // Hibernate will automatically set this on insert
    private LocalDateTime createdOn;

    @Column(name = "updated_on")
    @UpdateTimestamp // Hibernate will automatically update this on modify
    private LocalDateTime updatedOn;

     @ManyToOne(fetch = FetchType.LAZY)
     @JoinColumn(name = "user_id", insertable = false, updatable = false)
     private User user;
}
