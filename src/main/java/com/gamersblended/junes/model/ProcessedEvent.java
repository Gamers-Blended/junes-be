package com.gamersblended.junes.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_events", schema = "junes_rel")
@Data
@NoArgsConstructor
public class ProcessedEvent {

    @Id
    @Column(name = "event_id")
    private String eventID;

    @Column(name = "processed_on", nullable = false)
    private LocalDateTime processedOn;

    public ProcessedEvent(String eventID, LocalDateTime processedOn) {
        this.eventID = eventID;
        this.processedOn = processedOn;
    }
}
