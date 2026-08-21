package com.gamersblended.junes.dto.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

import static com.gamersblended.junes.constant.KafkaConstants.PAYMENT_METHOD_DETACHED;

@Data
@EqualsAndHashCode(callSuper = true)
public class StripePaymentMethodDetachEvent extends BaseEvent {

    private String eventID;
    private Integer schemaVersion; // For updates on payload shape
    private UUID userID;
    private String stripePaymentMethodID;
    private UUID paymentMethodID;

    public StripePaymentMethodDetachEvent() {
        this.setEventType(PAYMENT_METHOD_DETACHED);
    }
}
