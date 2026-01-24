package com.gamersblended.junes.util;

public class SnowflakeIDGenerator {

    // Epoch start time (1st January 2024 00:00:00 UTC)
    private static final long EPOCH = 1704067200000L; // 2024-01-01 in milliseconds

    // Bit allocation
    private static final long MACHINE_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;

    // Max values
    private static final long MAX_MACHINE_ID = ~(-1L << MACHINE_ID_BITS); // 1023
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS); // 4095

    // Bit shifts
    private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS;

    private final long machineID;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    /**
     * Constructor
     * @param machineID Unique ID for this machine/instance (0 - 1023)
     */
    public SnowflakeIDGenerator(long machineID) {
        if (machineID < 0 || machineID > MAX_MACHINE_ID) {
            throw new IllegalArgumentException("Machine ID must be between 0 and " + MAX_MACHINE_ID);
        }
        this.machineID = machineID;
    }

    /**
     * Generates a unique 64-bit numeric ID
     * Format: [timestamp: 41 bits][machine ID: 10 bits][sequence: 12 bits]
     */
    public synchronized long generateID() {
        long timestamp = getCurrentTimestamp();

        // Clock moved backwards - wait until it catches up
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate ID for " + (lastTimestamp - timestamp) + " milliseconds");
        }

        // Same millisecond - increment sequence
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;

            // Sequence overflow - wait for next millisecond
            if (sequence == 0) {
                timestamp = waitForNextMillis(lastTimestamp);
            }

        } else {
            // New millisecond - reset sequence
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        // Combine all parts into 64-bit ID
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (machineID << MACHINE_ID_SHIFT)
                | sequence;
    }

    /**
     * Generates ID as string
     * @return String representation of ID
     */
    public String generateOrderID() {
        return String.valueOf(generateID());
    }

    private long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }

    private long waitForNextMillis(long lastTimestamp) {
        long timestamp = getCurrentTimestamp();
        while (timestamp <= lastTimestamp) {
            timestamp = getCurrentTimestamp();
        }
        return timestamp;
    }

    // Utility methods to extract information from a Snowflake ID
    public static long extractTimestamp(long snowflakeID) {
        return (snowflakeID >> TIMESTAMP_SHIFT) + EPOCH;
    }

    public static long extractMachineID(long snowflakeID) {
        return (snowflakeID >> MACHINE_ID_SHIFT) & MAX_MACHINE_ID;
    }

    public static long extractSequence(long snowflakeID) {
        return snowflakeID & MAX_SEQUENCE;
    }
}
