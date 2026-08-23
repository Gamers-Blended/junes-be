package com.gamersblended.junes.exception;

public class CartUpdateConflictException extends RuntimeException {
    public CartUpdateConflictException(String message) {
        super(message);
    }
}
