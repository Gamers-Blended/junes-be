package com.gamersblended.junes.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys", schema = "junes_rel")
@Getter
@Setter
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userID;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "key_value", nullable = false)
    private String keyValue;

    @Column(nullable = false, length = 100)
    private String status;

    @Column(name = "response_payload")
    private String responsePayload;

    @Column(name = "created_on", nullable = false)
    private LocalDateTime createdOn;

    @Column(name = "updated_on")
    private LocalDateTime updatedOn;
}
