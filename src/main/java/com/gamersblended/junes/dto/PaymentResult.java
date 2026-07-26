package com.gamersblended.junes.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentResult {
    private boolean success;
    private String paymentIntentID;
    private String status;
    private String failureReason; // populated when success = false
}
