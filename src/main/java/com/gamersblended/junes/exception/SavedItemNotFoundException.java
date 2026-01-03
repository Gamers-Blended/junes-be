package com.gamersblended.junes.exception;

public class SavedItemNotFoundException extends RuntimeException {
    public SavedItemNotFoundException(String message) {
        super(message);
    }
}
