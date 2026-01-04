package com.gamersblended.junes.exception;

public class SavedItemLimitExceededException extends RuntimeException {
    public SavedItemLimitExceededException(String message) {
        super(message);
    }
}
