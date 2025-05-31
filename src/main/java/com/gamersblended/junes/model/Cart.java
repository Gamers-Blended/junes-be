package com.gamersblended.junes.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "carts", schema = "customer_data")
@Getter
@Setter
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userID;

    @Column(name = "product_id", nullable = false)
    private String productID;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "created_on", nullable = false)
    private LocalDate createdOn;

    @Column(name = "updated_on")
    private LocalDate updatedOn;
}
