package com.gamersblended.junes.controller;

import com.gamersblended.junes.annotation.RateLimit;
import com.gamersblended.junes.dto.AddressDTO;
import com.gamersblended.junes.dto.PaymentMethodDTO;
import com.gamersblended.junes.dto.reponse.ResponseMessage;
import com.gamersblended.junes.exception.*;
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
                            schema = @Schema(implementation = InvalidTokenException.class))}),
            @ApiResponse(responseCode = "404", description = "Address not found",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = SavedItemNotFoundException.class))})
    })
    @GetMapping("/address/{savedItemID}")
    public ResponseEntity<AddressDTO> getSavedAddress(@RequestHeader("Authorization") String authHeader, @PathVariable UUID savedItemID) {
        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);

        log.info("Retrieving details for saved address {} for userID: {}...", savedItemID, userID);
        AddressDTO address = savedItemsService.getSavedAddressForUser(savedItemID, userID);
        return ResponseEntity.ok(address);
    }

    @Operation(summary = "Add a new Address to user's saved address list")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Address successfully added",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ResponseMessage.class))}),
            @ApiResponse(responseCode = "400", description = "Token is invalid",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InvalidTokenException.class))}),
            @ApiResponse(responseCode = "422", description = "Number of saved Addresses exceeded",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = SavedItemLimitExceededException.class))}),
            @ApiResponse(responseCode = "400", description = "Address already exists",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = DuplicateAddressException.class))})
    })
    @PostMapping("/address")
    public ResponseEntity<ResponseMessage> addAddress(@RequestHeader("Authorization") String authHeader, @RequestBody AddressDTO addressDTO) {
        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);

        log.info("Adding a new address for userID: {}...", userID);
        savedItemsService.addAddress(userID, addressDTO);
        return ResponseEntity.ok(new ResponseMessage("Address successfully added"));
    }

    @Operation(summary = "Edit an existing Address from user's saved address list")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Address successfully edited",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ResponseMessage.class))}),
            @ApiResponse(responseCode = "400", description = "Token is invalid",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InvalidTokenException.class))}),
            @ApiResponse(responseCode = "400", description = "Address ID not given",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InputValidationException.class))}),
            @ApiResponse(responseCode = "404", description = "Address not found",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = SavedItemNotFoundException.class))}),
            @ApiResponse(responseCode = "400", description = "Address already exists",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = DuplicateAddressException.class))})
    })
    @PutMapping("address/{addressID}")
    public ResponseEntity<ResponseMessage> editAddress(@RequestHeader("Authorization") String authHeader, @PathVariable UUID addressID, @RequestBody AddressDTO addressDTO) {
        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);

        log.info("Editing address {} for userID: {}...", addressID, userID);
        savedItemsService.editAddress(userID, addressID, addressDTO);
        return ResponseEntity.ok(new ResponseMessage("Address successfully edited"));
    }

    @Operation(summary = "Soft delete an existing Address from user's saved address list")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Address successfully deleted",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ResponseMessage.class))}),
            @ApiResponse(responseCode = "400", description = "Token is invalid",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InvalidTokenException.class))}),
            @ApiResponse(responseCode = "400", description = "Address ID not given",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InputValidationException.class))}),
            @ApiResponse(responseCode = "404", description = "Address not found",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = SavedItemNotFoundException.class))})
    })
    @DeleteMapping("address/{addressID}")
    public ResponseEntity<ResponseMessage> deleteAddress(@RequestHeader("Authorization") String authHeader, @PathVariable UUID addressID) {
        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);

        log.info("Deleting address {} for userID: {}...", addressID, userID);
        savedItemsService.deleteAddress(userID, addressID);
        return ResponseEntity.ok(new ResponseMessage("Address successfully deleted"));
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
                            schema = @Schema(implementation = InvalidTokenException.class))}),
            @ApiResponse(responseCode = "404", description = "Payment method not found",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = SavedItemNotFoundException.class))})
    })
    @GetMapping("/payment-method/{savedItemID}")
    public ResponseEntity<PaymentMethodDTO> getSavedPaymentMethod(@RequestHeader("Authorization") String authHeader, @PathVariable UUID savedItemID) {
        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);

        log.info("Retrieving details for saved payment method {} for userID: {}...", savedItemID, userID);
        PaymentMethodDTO address = savedItemsService.getSavedPaymentMethodForUser(savedItemID, userID);
        return ResponseEntity.ok(address);
    }

    @Operation(summary = "Add a new Payment method to user's saved payment method list")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment method successfully added",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ResponseMessage.class))}),
            @ApiResponse(responseCode = "400", description = "Token is invalid",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InvalidTokenException.class))}),
            @ApiResponse(responseCode = "400", description = "Invalid payment method input value(s)",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InputValidationException.class))}),
            @ApiResponse(responseCode = "404", description = "Billing address not found",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = SavedItemNotFoundException.class))}),
            @ApiResponse(responseCode = "422", description = "Number of saved Payment methods exceeded",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = SavedItemLimitExceededException.class))}),
            @ApiResponse(responseCode = "400", description = "Payment method already exists",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = DuplicatePaymentMethodException.class))})
    })
    @PostMapping("/payment-method")
    public ResponseEntity<ResponseMessage> addPaymentMethod(@RequestHeader("Authorization") String authHeader, @RequestBody PaymentMethodDTO paymentMethodDTO) {
        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);

        log.info("Adding a new payment method for userID: {}...", userID);
        savedItemsService.addPaymentMethod(userID, paymentMethodDTO);
        return ResponseEntity.ok(new ResponseMessage("Payment method successfully added"));
    }

    @Operation(summary = "Edit an existing Payment method from user's saved payment method list")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment method successfully edited",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ResponseMessage.class))}),
            @ApiResponse(responseCode = "400", description = "Token is invalid",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InvalidTokenException.class))}),
            @ApiResponse(responseCode = "400", description = "Invalid payment method input value(s)",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InputValidationException.class))}),
            @ApiResponse(responseCode = "404", description = "Billing address and/or payment method not found",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = SavedItemNotFoundException.class))}),
            @ApiResponse(responseCode = "400", description = "Payment method already exists",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = DuplicatePaymentMethodException.class))})
    })
    @PutMapping("payment-method/{paymentMethodID}")
    public ResponseEntity<ResponseMessage> editPaymentMethod(@RequestHeader("Authorization") String authHeader, @PathVariable UUID paymentMethodID, @RequestBody PaymentMethodDTO paymentMethodDTO) {
        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);

        log.info("Editing payment method {} for userID: {}...", paymentMethodID, userID);
        savedItemsService.editPaymentMethod(userID, paymentMethodID, paymentMethodDTO);
        return ResponseEntity.ok(new ResponseMessage("Payment method successfully edited"));
    }

    @Operation(summary = "Hard delete an existing Payment method from user's saved payment method list")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment method successfully deleted",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ResponseMessage.class))}),
            @ApiResponse(responseCode = "400", description = "Token is invalid",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InvalidTokenException.class))}),
            @ApiResponse(responseCode = "400", description = "Payment method ID not given",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InputValidationException.class))}),
            @ApiResponse(responseCode = "404", description = "Payment method not found",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = SavedItemNotFoundException.class))})
    })
    @DeleteMapping("payment-method/{paymentMethodID}")
    public ResponseEntity<ResponseMessage> deletePaymentMethod(@RequestHeader("Authorization") String authHeader, @PathVariable UUID paymentMethodID) {
        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);

        log.info("Deleting payment method {} for userID: {}...", paymentMethodID, userID);
        savedItemsService.deletePaymentMethod(userID, paymentMethodID);
        return ResponseEntity.ok(new ResponseMessage("Payment method successfully deleted"));
    }
}
