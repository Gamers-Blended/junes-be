package com.gamersblended.junes.dto.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

import static com.gamersblended.junes.constant.KafkaConstants.PAYMENT_FAILED;

@Data
@EqualsAndHashCode(callSuper = true)
public class PaymentFailedEvent extends BaseEvent {

    private UUID transactionID;
    private String orderNumber;
    private UUID userID;
    private String failureReason;

    public PaymentFailedEvent() {
        this.setEventType(PAYMENT_FAILED);
    }
}
