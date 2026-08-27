package com.gamersblended.junes.controller;

import com.gamersblended.junes.dto.response.ErrorResponseDTO;
import com.gamersblended.junes.dto.response.ResponseMessage;
import com.gamersblended.junes.service.CartService;
import com.gamersblended.junes.service.EmailVerificationTokenService;
import com.gamersblended.junes.service.PasswordResetService;
import com.gamersblended.junes.service.WishlistService;
import com.gamersblended.junes.service.order.OrderShipmentService;
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
    private final EmailVerificationTokenService emailVerificationTokenService;
    private final CartService cartService;
    private final WishlistService wishlistService;
    private final OrderShipmentService orderShipmentService;

    public HouseKeepController(PasswordResetService passwordResetService, EmailVerificationTokenService emailVerificationTokenService, CartService cartService, WishlistService wishlistService, OrderShipmentService orderShipmentService) {
        this.passwordResetService = passwordResetService;
        this.emailVerificationTokenService = emailVerificationTokenService;
        this.cartService = cartService;
        this.wishlistService = wishlistService;
        this.orderShipmentService = orderShipmentService;
    }

    @Operation(summary = "Manually trigger housekeeping of expired tokens")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Blacklisted tokens cleared",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ResponseMessage.class))}),
            @ApiResponse(responseCode = "500", description = "Error in deleting expired tokens",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))})
    })
    @PostMapping("/tokens")
    public ResponseEntity<ResponseMessage> houseKeepExpiredTokens() {

        log.info("Starting house keeping for blacklisted tokens...");
        passwordResetService.cleanupExpiredTokens();
        return ResponseEntity.ok(new ResponseMessage("Blacklisted tokens cleared"));
    }

    @Operation(summary = "Manually trigger housekeeping of unverified emails")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Unverified emails cleared",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ResponseMessage.class))}),
            @ApiResponse(responseCode = "500", description = "Error in deleting unverified emails",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))})
    })
    @PostMapping("/unverified-emails")
    public ResponseEntity<ResponseMessage> houseKeepUnverifiedEmails() {

        log.info("Starting house keeping for unverified emails...");
        emailVerificationTokenService.cleanupUnverifiedEmails();
        return ResponseEntity.ok(new ResponseMessage("Unverified emails cleared"));
    }

    @Operation(summary = "Manually trigger housekeeping of inactive carts")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Inactive carts cleared",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ResponseMessage.class))}),
            @ApiResponse(responseCode = "500", description = "Error in deleting inactive carts",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))})
    })
    @PostMapping("/carts")
    public ResponseEntity<ResponseMessage> houseKeepInactiveCarts() {

        log.info("Starting house keeping for inactive carts...");
        cartService.cleanupInactiveCarts();
        return ResponseEntity.ok(new ResponseMessage("Inactive carts cleared"));
    }

    @Operation(summary = "Manually trigger housekeeping of inactive wishlists")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Inactive wishlists cleared",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ResponseMessage.class))}),
            @ApiResponse(responseCode = "500", description = "Error in deleting inactive wishlists",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))})
    })
    @PostMapping("/wishlists")
    public ResponseEntity<ResponseMessage> houseKeepInactiveWishlists() {

        log.info("Starting house keeping for inactive wishlists...");
        wishlistService.cleanupInactiveWishlists();
        return ResponseEntity.ok(new ResponseMessage("Inactive wishlists cleared"));
    }

    @Operation(summary = "Manually trigger simulated shipment of Awaiting Shipment orders")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Awaiting-shipment orders transitioned to Shipped",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ResponseMessage.class))}),
            @ApiResponse(responseCode = "500", description = "Error in simulating order shipment",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class))})
    })
    @PostMapping("/shipments")
    public ResponseEntity<ResponseMessage> houseKeepSimulateShipment() {

        log.info("Starting house keeping for order shipment simulation...");
        orderShipmentService.simulateShipment();
        return ResponseEntity.ok(new ResponseMessage("Awaiting-shipment orders transitioned to Shipped"));
    }
}
