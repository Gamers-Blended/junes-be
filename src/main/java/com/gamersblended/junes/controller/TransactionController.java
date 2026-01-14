package com.gamersblended.junes.controller;

import com.gamersblended.junes.annotation.RateLimit;
import com.gamersblended.junes.dto.TransactionDetailsDTO;
import com.gamersblended.junes.dto.TransactionHistoryDTO;
import com.gamersblended.junes.exception.InvalidTokenException;
import com.gamersblended.junes.exception.TransactionNotFoundException;
import com.gamersblended.junes.service.AccessTokenService;
import com.gamersblended.junes.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("junes/api/v1/transaction")
@RateLimit(requests = 10, duration = 1, timeUnit = TimeUnit.MINUTES)
public class TransactionController {

    private final TransactionService transactionService;
    private final AccessTokenService accessTokenService;

    public TransactionController(TransactionService transactionService, AccessTokenService accessTokenService) {
        this.transactionService = transactionService;
        this.accessTokenService = accessTokenService;
    }

    @Operation(summary = "Get paginated user's transaction history")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User's transaction history successfully retrieved",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = TransactionHistoryDTO.class))}),
            @ApiResponse(responseCode = "400", description = "Token is invalid",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InvalidTokenException.class))})
    })
    @GetMapping("/history")
    public ResponseEntity<Page<TransactionHistoryDTO>> getUserTransactionHistory(@RequestHeader("Authorization") String authHeader, Pageable pageable) {
        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);

        log.info("Retrieving user's transaction history for page = {}, size = {}, sort = {}, userID: {}...", pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort(), userID);
        Page<TransactionHistoryDTO> transactionHistory = transactionService.getTransactionHistory(userID, pageable);
        return ResponseEntity.ok(transactionHistory);
    }

    @Operation(summary = "Get transaction details")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction details successfully retrieved",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = TransactionDetailsDTO.class))}),
            @ApiResponse(responseCode = "400", description = "Token is invalid",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InvalidTokenException.class))}),
            @ApiResponse(responseCode = "404", description = "Transaction not found",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = TransactionNotFoundException.class))})
    })
    @GetMapping("/{transactionID}/details")
    public ResponseEntity<TransactionDetailsDTO> getTransactionDetails(@RequestHeader("Authorization") String authHeader, @PathVariable UUID transactionID) {
        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);

        log.info("Retrieving transaction details for transactionID: {}, userID: {}...", transactionID, userID);
        TransactionDetailsDTO transactionDetails = transactionService.getTransactionDetails(userID, transactionID);
        return ResponseEntity.ok(transactionDetails);
    }
}
