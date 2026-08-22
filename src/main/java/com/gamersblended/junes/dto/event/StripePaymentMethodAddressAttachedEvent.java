package com.gamersblended.junes.dto.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

import static com.gamersblended.junes.constant.KafkaConstants.PAYMENT_METHOD_ADDRESS_ATTACHED;

@Data
@EqualsAndHashCode(callSuper = true)
public class StripePaymentMethodAddressAttachedEvent extends BaseEvent {

    private String eventID;
    private Integer schemaVersion; // For updates on payload shape
    private UUID userID;
    private UUID paymentMethodID;
    private String stripePaymentMethodID;
    private String addressLine;
    private String zipCode;
    private String country;

    public StripePaymentMethodAddressAttachedEvent() {
        this.setEventType(PAYMENT_METHOD_ADDRESS_ATTACHED);
    }
}