package com.gamersblended.junes.dto.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.UUID;

import static com.gamersblended.junes.constant.KafkaConstants.PAYMENT_SUCCEEDED;

@Data
@EqualsAndHashCode(callSuper = true)
public class PaymentSucceededEvent extends BaseEvent {

    private UUID transactionID;
    private String orderNumber;
    private UUID userID;
    private BigDecimal amountCharged;
    private String stripePaymentIntentID;

    public PaymentSucceededEvent() {
        this.setEventType(PAYMENT_SUCCEEDED);
    }
}
