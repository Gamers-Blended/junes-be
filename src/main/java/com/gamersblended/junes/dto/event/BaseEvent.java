package com.gamersblended.junes.dto.event;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class BaseEvent {

    private String eventID = UUID.randomUUID().toString();
    private LocalDateTime timestamp = LocalDateTime.now();
    private String eventType;
}
