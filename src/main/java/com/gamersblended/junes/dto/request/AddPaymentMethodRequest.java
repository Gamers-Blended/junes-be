package com.gamersblended.junes.dto.request;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AddPaymentMethodRequest {
    private String stripePaymentMethodID;
    private Boolean isDefault;
}
