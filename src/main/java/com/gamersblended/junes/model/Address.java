package com.gamersblended.junes.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "addresses", schema = "junes_rel")
@Getter
@Setter
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "address_id")
    private UUID addressID;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "address_line", nullable = false)
    private String addressLine;

    @Column(name = "unit_number")
    private String unitNumber;

    @Column(name = "country", nullable = false)
    private String country;

    @Column(name = "zip_code", nullable = false)
    private String zipCode;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "is_default")
    private Boolean isDefault;

    @Column(name = "user_id", nullable = false)
    private UUID userID;

    @Column(name = "created_on", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdOn;

    @Column(name = "updated_on")
    @UpdateTimestamp
    private LocalDateTime updatedOn;
}
