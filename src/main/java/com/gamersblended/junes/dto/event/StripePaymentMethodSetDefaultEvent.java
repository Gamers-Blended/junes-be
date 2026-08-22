package com.gamersblended.junes.dto.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

import static com.gamersblended.junes.constant.KafkaConstants.PAYMENT_METHOD_SET_DEFAULT;

@Data
@EqualsAndHashCode(callSuper = true)
public class StripePaymentMethodSetDefaultEvent extends BaseEvent {

    private String eventID;
    private Integer schemaVersion; // For updates on payload shape
    private UUID userID;
    private String stripeCustomerID;
    private String stripePaymentMethodID;

    public StripePaymentMethodSetDefaultEvent() {
        this.setEventType(PAYMENT_METHOD_SET_DEFAULT);
    }
}