package com.gamersblended.junes.controller;

import com.gamersblended.junes.annotation.RateLimit;
import com.gamersblended.junes.dto.AddressDTO;
import com.gamersblended.junes.dto.PaymentMethodDTO;
import com.gamersblended.junes.exception.InvalidTokenException;
import com.gamersblended.junes.service.AccessTokenService;
import com.gamersblended.junes.service.SavedItemsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("junes/api/v1/saved-items")
@RateLimit(requests = 10, duration = 1, timeUnit = TimeUnit.MINUTES)
public class SavedItemsController {

    private final SavedItemsService savedItemsService;
    private final AccessTokenService accessTokenService;

    public SavedItemsController(SavedItemsService savedItemsService, AccessTokenService accessTokenService) {
        this.savedItemsService = savedItemsService;
        this.accessTokenService = accessTokenService;
    }

    @Operation(summary = "Get user's saved address(es)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User's saved address(es) successfully retrieved",
                    content = {@Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AddressDTO.class)))}),
            @ApiResponse(responseCode = "400", description = "Token is invalid",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InvalidTokenException.class))})
    })
    @GetMapping("/addresses/user")
    public ResponseEntity<List<AddressDTO>> getAllSavedAddresses(@RequestHeader("Authorization") String authHeader) {
        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);

        log.info("Retrieving all saved addresses for userID: {}...", userID);
        List<AddressDTO> allSavedAddresses = savedItemsService.getAllSavedAddressesForUser(userID);
        return ResponseEntity.ok(allSavedAddresses);
    }

    @Operation(summary = "Get a user's saved address")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User's saved address successfully retrieved",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = AddressDTO.class))}),
            @ApiResponse(responseCode = "400", description = "Token is invalid",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InvalidTokenException.class))})
    })
    @GetMapping("/address/{savedItemID}")
    public ResponseEntity<AddressDTO> getSavedAddress(@RequestHeader("Authorization") String authHeader, @PathVariable UUID savedItemID) {
        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);

        log.info("Retrieving details for saved address {} for userID: {}...", savedItemID, userID);
        AddressDTO address = savedItemsService.getSavedAddressForUser(savedItemID, userID);
        return ResponseEntity.ok(address);
    }


    @Operation(summary = "Get user's saved payment method(s)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User's saved payment method(s) successfully retrieved",
                    content = {@Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = PaymentMethodDTO.class)))}),
            @ApiResponse(responseCode = "400", description = "Token is invalid",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InvalidTokenException.class))})
    })
    @GetMapping("/payment-methods/user")
    public ResponseEntity<List<PaymentMethodDTO>> getAllSavedPaymentMethods(@RequestHeader("Authorization") String authHeader) {
        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);

        log.info("Retrieving all saved payment methods for userID: {}...", userID);
        List<PaymentMethodDTO> allSavedPaymentMethods = savedItemsService.getAllPaymentMethodsForUser(userID);
        return ResponseEntity.ok(allSavedPaymentMethods);
    }

    @Operation(summary = "Get a user's saved payment method")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User's saved payment method successfully retrieved",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = PaymentMethodDTO.class))}),
            @ApiResponse(responseCode = "400", description = "Token is invalid",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InvalidTokenException.class))})
    })
    @GetMapping("/payment-method/{savedItemID}")
    public ResponseEntity<PaymentMethodDTO> getSavedPaymentMethod(@RequestHeader("Authorization") String authHeader, @PathVariable UUID savedItemID) {
        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);

        log.info("Retrieving details for saved payment method {} for userID: {}...", savedItemID, userID);
        PaymentMethodDTO address = savedItemsService.getSavedPaymentMethodForUser(savedItemID, userID);
        return ResponseEntity.ok(address);
    }
}
