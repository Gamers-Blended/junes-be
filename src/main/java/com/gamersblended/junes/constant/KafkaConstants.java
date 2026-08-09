package com.gamersblended.junes.constant;

public class KafkaConstants {

    private KafkaConstants() {
    }

    // Event types
    public static final String EMAIL_UPDATED = "EMAIL_UPDATED";
    public static final String INVENTORY_CHANGED = "INVENTORY_CHANGED";
    public static final String ORDER_CREATED = "ORDER_CREATED";
    public static final String ORDER_PLACED = "ORDER_PLACED";
    public static final String PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";

    // Topics
    public static final String STRIPE_SYNC_EVENTS = "stripe-sync-events";
}
