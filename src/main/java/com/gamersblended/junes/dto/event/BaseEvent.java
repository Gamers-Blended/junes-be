package com.gamersblended.junes.dto.event;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Data
public class BaseEvent {

    private String eventID = UUID.randomUUID().toString();
    private LocalDateTime timestamp = LocalDateTime.now(ZoneId.of("Asia/Singapore"));
    private String eventType;
}
