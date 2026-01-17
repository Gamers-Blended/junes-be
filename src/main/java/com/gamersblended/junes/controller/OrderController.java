package com.gamersblended.junes.controller;

import com.gamersblended.junes.annotation.RateLimit;
import com.gamersblended.junes.dto.reponse.ResponseMessage;
import com.gamersblended.junes.dto.request.PlaceOrderRequest;
import com.gamersblended.junes.service.AccessTokenService;
import com.gamersblended.junes.service.OrderService;
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

    @PostMapping("/place")
    public ResponseEntity<ResponseMessage> orderPlace(@RequestHeader("Authorization") String authHeader, @RequestBody PlaceOrderRequest placeOrderRequest) {
        UUID userID = accessTokenService.extractUserIDFromToken(authHeader);

        log.info("Placing order for userID: {}", userID);
        String orderNumber = orderService.placeOrder(userID, placeOrderRequest);
        return ResponseEntity.ok(new ResponseMessage("Order placed, order number: " + orderNumber));
    }
}
