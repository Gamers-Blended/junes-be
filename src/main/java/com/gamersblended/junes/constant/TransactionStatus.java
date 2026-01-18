package com.gamersblended.junes.constant;

public enum TransactionStatus {
    PAYMENT_PENDING("Payment Pending"),
    AWAITING_FULFILLMENT("Awaiting Fulfillment"),
    AWAITING_SHIPMENT("Awaiting Shipment"),
    SHIPPED("Shipped"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    ON_HOLD("On Hold"),
    REFUNDED("Refunded");

    private final String transactionStatusValue;

    TransactionStatus(String transactionStatusValue) {
        this.transactionStatusValue = transactionStatusValue;
    }

    public String getTransactionStatusValue() {
        return transactionStatusValue;
    }
}
