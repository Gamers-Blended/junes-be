package com.gamersblended.junes.controller;

import com.gamersblended.junes.annotation.RateLimit;
import com.gamersblended.junes.dto.reponse.ResponseMessage;
import com.gamersblended.junes.dto.request.PlaceOrderRequest;
import com.gamersblended.junes.exception.CreateOrderException;
import com.gamersblended.junes.exception.InvalidTokenException;
import com.gamersblended.junes.exception.SavedItemNotFoundException;
import com.gamersblended.junes.service.AccessTokenService;
import com.gamersblended.junes.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("junes/api/v1/order")
@RateLimit(requests = 10, duration = 1, timeUnit = TimeUnit.MINUTES)
public class OrderController {

    private final OrderService orderService;
    private final AccessTokenService accessTokenService;

    public OrderController(OrderService orderService, AccessTokenService accessTokenService) {
        this.orderService = orderService;
        this.accessTokenService = accessTokenService;
    }

    @Operation(summary = "Place an order for a logged user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order successfully placed",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = ResponseMessage.class))}),
            @ApiResponse(responseCode = "400", description = "Token is invalid",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = InvalidTokenException.class))}),
            @ApiResponse(responseCode = "404", description = "Address and/or payment method not found",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = SavedItemNotFoundException.class))}),
            @ApiResponse(responseCode = "500", description = "Error placing order",
                    content = {@Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreateOrderException.class))}),
    })
    @PostMapping("/place")
    public ResponseEntity<ResponseMessage> orderPlace(@RequestHeader("Authorization") String authHeader, @RequestBody PlaceOrderRequest placeOrderRequest) {
        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);

        log.info("Placing order for userID: {}", userID);
        String orderNumber = orderService.placeOrder(userID, placeOrderRequest);
        return ResponseEntity.ok(new ResponseMessage("Order placed, order number: " + orderNumber));
    }
}
