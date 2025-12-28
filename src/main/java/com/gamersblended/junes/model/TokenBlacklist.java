package com.gamersblended.junes.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "token_blacklist", schema = "junes_rel")
@Data
public class TokenBlacklist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "token_id")
    private UUID tokenID;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "blacklisted_at", nullable = false)
    private LocalDateTime blacklistedAt = LocalDateTime.now();

    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;
}
