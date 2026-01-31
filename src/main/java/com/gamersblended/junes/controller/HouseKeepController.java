package com.gamersblended.junes.controller;

import com.gamersblended.junes.dto.TransactionHistoryDTO;
import com.gamersblended.junes.dto.reponse.ResponseMessage;
import com.gamersblended.junes.exception.DatabaseDeletionException;
import com.gamersblended.junes.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/junes/api/v1/housekeep")
public class HouseKeepController {

    private final PasswordResetService passwordResetService;

    public HouseKeepController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @Operation(summary = "Manually trigger housekeeping of expired tokens")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Blacklisted tokens cleared",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = TransactionHistoryDTO.class))}),
            @ApiResponse(responseCode = "500", description = "Error in deleting expired tokens",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = DatabaseDeletionException.class))})
    })
    @PostMapping("/tokens")
    public ResponseEntity<ResponseMessage> houseKeepExpiredTokens() {

        log.info("Starting house keeping for blacklisted tokens...");
        passwordResetService.cleanupExpiredTokens();
        return ResponseEntity.ok(new ResponseMessage("Blacklisted tokens cleared"));
    }
}
