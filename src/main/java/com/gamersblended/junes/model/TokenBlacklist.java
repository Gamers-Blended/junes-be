package com.gamersblended.junes.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static com.gamersblended.junes.constant.ConfigSettingsConstants.ASIA_SINGAPORE;

@Entity
@Table(name = "token_blacklist", schema = "junes_rel")
@Getter
@Setter
public class TokenBlacklist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "token_id")
    private UUID tokenID;

    @Column(nullable = false, unique = true, length = 500)
    private String token;

    @Column(name = "blacklisted_at", nullable = false)
    private LocalDateTime blacklistedAt = LocalDateTime.now(ZoneId.of(ASIA_SINGAPORE));

    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;
}
