package com.gamersblended.junes.controller;

import com.gamersblended.junes.model.DeadLetterEvent;
import com.gamersblended.junes.model.OutboxEvent;
import com.gamersblended.junes.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/junes/api/v1/housekeep/reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Operation(summary = "List outbox events that permanently failed to publish to Kafka")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Permanently failed outbox events retrieved",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = OutboxEvent.class))})
    })
    @GetMapping("/failed-outbox-events")
    public ResponseEntity<List<OutboxEvent>> getPermanentlyFailedOutboxEvents() {

        log.info("Fetching permanently failed outbox events...");
        return ResponseEntity.ok(reconciliationService.getPermanentlyFailedOutboxEvents());
    }

    @Operation(summary = "List dead-lettered events awaiting manual resolution")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Unresolved dead-letter events retrieved",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = DeadLetterEvent.class))})
    })
    @GetMapping("/dead-letter-events")
    public ResponseEntity<List<DeadLetterEvent>> getUnresolvedDeadLetterEvents() {

        log.info("Fetching unresolved dead-letter events...");
        return ResponseEntity.ok(reconciliationService.getUnresolvedDeadLetterEvents());
    }
}