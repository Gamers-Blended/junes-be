package com.gamersblended.junes.dto.request;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChargeRequest {
    private String stripeCustomerID;
    private String stripePaymentMethodID;
    private long amountInCents;
    private String currency;
    private String orderNumber;
}
