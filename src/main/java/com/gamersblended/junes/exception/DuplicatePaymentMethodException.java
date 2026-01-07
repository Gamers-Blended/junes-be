package com.gamersblended.junes.exception;

public class DuplicatePaymentMethodException extends RuntimeException {
    public DuplicatePaymentMethodException(String message) {
        super(message);
    }
}
