package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.PaymentResult;
import com.gamersblended.junes.dto.request.ChargeRequest;

/**
 * Abstraction over payment provider so PaymentEventConsumer never touches Stripe SDK directly
 * Can swap providers later and substitute fake implementation in tests
 */
public interface PaymentGatewayService {

    /**
     * Charges given payment method for given amount
     */
    PaymentResult charge(String idempotencyKey, ChargeRequest request);
}
