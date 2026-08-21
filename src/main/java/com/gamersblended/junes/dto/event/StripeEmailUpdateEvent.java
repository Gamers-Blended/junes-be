package com.gamersblended.junes.dto.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

import static com.gamersblended.junes.constant.KafkaConstants.EMAIL_UPDATED;

@Data
@EqualsAndHashCode(callSuper = true)
public class StripeEmailUpdateEvent extends BaseEvent {

    private String eventID;
    private Integer schemaVersion; // For updates on payload shape
    private UUID userID;
    private String stripeCustomerID;
    private String newEmail;

    public StripeEmailUpdateEvent() {
        this.setEventType(EMAIL_UPDATED);
    }
}
