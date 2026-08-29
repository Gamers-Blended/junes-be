package com.gamersblended.junes.dto.event;

import lombok.Data;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static com.gamersblended.junes.constant.LoggingConstants.MDC_CORRELATION_ID_KEY;

@Data
public class BaseEvent {

    private String eventID = UUID.randomUUID().toString();
    private LocalDateTime timestamp = LocalDateTime.now(ZoneId.of("Asia/Singapore"));
    private String eventType;
    private String idempotencyKey;
    private String correlationId = MDC.get(MDC_CORRELATION_ID_KEY);
}
