package com.gamersblended.junes.exception;

public class StripeOperationException extends RuntimeException {
    public StripeOperationException(String message) {
        super(message);
    }
}
