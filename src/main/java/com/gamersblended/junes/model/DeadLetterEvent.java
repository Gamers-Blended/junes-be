package com.gamersblended.junes.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "dead_letter_events", schema = "junes_rel")
@Getter
@Setter
public class DeadLetterEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "original_topic", nullable = false, length = 100)
    private String originalTopic;

    @Column(name = "event_id")
    private String eventID;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "exception_message")
    private String exceptionMessage;

    @Column(nullable = false, length = 100)
    private String status;

    @Column(name = "failed_on", nullable = false)
    private LocalDateTime failedOn;

    @Column(name = "resolved_on")
    private LocalDateTime resolvedOn;
}