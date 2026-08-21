package com.gamersblended.junes.exception;

public class OutboxEventCreationException extends RuntimeException {
    public OutboxEventCreationException(String message) {
        super(message);
    }
}
