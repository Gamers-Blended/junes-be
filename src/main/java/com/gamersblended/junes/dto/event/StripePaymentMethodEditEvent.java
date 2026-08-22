package com.gamersblended.junes.dto.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

import static com.gamersblended.junes.constant.KafkaConstants.PAYMENT_METHOD_EDITED;

@Data
@EqualsAndHashCode(callSuper = true)
public class StripePaymentMethodEditEvent extends BaseEvent {

    private String eventID;
    private Integer schemaVersion; // For updates on payload shape
    private UUID userID;
    private UUID paymentMethodID;
    private String stripePaymentMethodID;
    private String cardHolderName;
    private String expirationMonth;
    private String expirationYear;

    public StripePaymentMethodEditEvent() {
        this.setEventType(PAYMENT_METHOD_EDITED);
    }
}