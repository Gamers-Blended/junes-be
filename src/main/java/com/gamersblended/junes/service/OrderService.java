package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.request.PlaceOrderRequest;
import com.gamersblended.junes.service.order.OrderProcessingService;
import com.gamersblended.junes.util.IdempotentUtils;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.gamersblended.junes.constant.KafkaConstants.ORDER_CREATED;

@Slf4j
@Service
@Transactional
public class OrderService {

    private final OrderProcessingService orderProcessingService;
    private final IdempotentUtils idempotentUtils;

    public OrderService(OrderProcessingService orderProcessingService, IdempotentUtils idempotentUtils) {
        this.orderProcessingService = orderProcessingService;
        this.idempotentUtils = idempotentUtils;
    }

    public String placeOrder(UUID userID, PlaceOrderRequest placeOrderRequest, String idempotencyKey) {

        return idempotentUtils.executeIdempotent(userID, ORDER_CREATED, idempotencyKey, String.class,
                () -> orderProcessingService.processOrder(userID, placeOrderRequest, idempotencyKey));
    }

}
