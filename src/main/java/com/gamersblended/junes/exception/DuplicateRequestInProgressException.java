package com.gamersblended.junes.exception;

public class DuplicateRequestInProgressException extends RuntimeException {
    public DuplicateRequestInProgressException(String message) {
        super(message);
    }
}
