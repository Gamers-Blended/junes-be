package com.gamersblended.junes.constant;

public class KafkaConstants {

    private KafkaConstants() {
    }

    // Outbox statuses
    public static final String PENDING = "PENDING";

    // Dead-letter statuses
    public static final String UNRESOLVED = "UNRESOLVED";

    // Event types
    public static final String EMAIL_UPDATED = "EMAIL_UPDATED";
    public static final String PAYMENT_METHOD_DETACHED = "PAYMENT_METHOD_DETACHED";
    public static final String PAYMENT_METHOD_EDITED = "PAYMENT_METHOD_EDITED";
    public static final String PAYMENT_METHOD_ADDRESS_ATTACHED = "PAYMENT_METHOD_ADDRESS_ATTACHED";
    public static final String PAYMENT_METHOD_SET_DEFAULT = "PAYMENT_METHOD_SET_DEFAULT";
    public static final String INVENTORY_CHANGED = "INVENTORY_CHANGED";
    public static final String ORDER_CREATED = "ORDER_CREATED";
    public static final String ORDER_PLACED = "ORDER_PLACED";
    public static final String STOCK_RELEASED = "STOCK_RELEASED";
    public static final String PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";

    // Topics
    public static final String STRIPE_SYNC_EVENTS = "stripe-sync-events";
    public static final String STRIPE_DETACH_PM_EVENTS = "stripe-detach-payment-method-events";
    public static final String STRIPE_PM_SYNC_EVENTS = "stripe-payment-method-sync-events";
    public static final String ORDER_EVENTS = "order-events";
    public static final String INVENTORY_EVENTS = "inventory-events";
}
