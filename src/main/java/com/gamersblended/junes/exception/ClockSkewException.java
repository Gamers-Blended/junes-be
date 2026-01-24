package com.gamersblended.junes.exception;

public class ClockSkewException extends RuntimeException {
    public ClockSkewException(long skewMilliseconds) {
        super("Clock moved backwards by " + skewMilliseconds + " milliseconds. " +
                "Cannot generate ID until clock catches up");
    }
}
