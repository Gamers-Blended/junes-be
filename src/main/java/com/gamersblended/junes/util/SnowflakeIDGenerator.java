package com.gamersblended.junes.util;

import com.gamersblended.junes.exception.ClockSkewException;

public class SnowflakeIDGenerator {

    // Epoch start time (1st January 2024 00:00:00 UTC)
    private static final long EPOCH = 1704067200000L; // 2024-01-01 in milliseconds

    // Max values
    private static final int MAX_MACHINE_ID = 15;
    private static final int MAX_SEQUENCE = 1 << 10; // 1 * 2^10 = 1024
    private static final int MAX_REDUCED_TIME = 1 << 25; // 2^25

    private final int machineID;
    private long lastTimestamp = -1L;
    private int sequence = 0;

    /**
     * Constructor
     *
     * @param machineID Unique ID for this machine/instance (0 - 1023)
     */
    public SnowflakeIDGenerator(int machineID) {
        if (machineID < 0 || machineID > MAX_MACHINE_ID) {
            throw new IllegalArgumentException("Machine ID must be between 0 and " + MAX_MACHINE_ID);
        }
        this.machineID = machineID;
    }

    /**
     * Generates 9 decimal digits
     * Format: 25 bits timestamp + 4 bits machine + 10 bits sequence
     */
    public synchronized String generateOrderID() {
        long timestamp = System.currentTimeMillis();

        // Clock moved backwards - wait until it catches up
        if (timestamp < lastTimestamp) {
            throw new ClockSkewException(lastTimestamp - timestamp);
        }

        // Same millisecond - increment sequence
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) % MAX_SEQUENCE; // 10 bits = 1024 max

            // Sequence overflow - wait for next millisecond
            if (sequence == 0) {
                timestamp = waitForNextMillis(lastTimestamp);
            }

        } else {
            // New millisecond - reset sequence
            sequence = 0;
        }

        lastTimestamp = timestamp;

        // Reduce timestamp to fit in smaller space
        long reducedTime = (timestamp - EPOCH) / 1000; // seconds since epoch
        reducedTime = reducedTime % MAX_REDUCED_TIME; // 25 bits max

        // Combine: timestamp(25) + machine(4) + sequence(10) = 39 bits
        long id = (reducedTime << 14) | ((long) machineID << 10) | sequence;

        return String.format("%09d", (int) (id % 1000000000));
    }

    private long waitForNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
