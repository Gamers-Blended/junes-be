package com.gamersblended.junes.util;

import com.gamersblended.junes.exception.ClockSkewException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

class SnowflakeIDGeneratorTest {

    @ParameterizedTest
    @ValueSource(ints = {-1, 16, 100})
    void constructor_throwsIllegalArgumentException_whenMachineIDIsOutOfRange(int machineID) {
        assertThatThrownBy(() -> new SnowflakeIDGenerator(machineID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Machine ID must be between 0 and 15");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 15})
    void constructor_doesNotThrow_whenMachineIDIsAtBoundary(int machineID) {
        assertThatCode(() -> new SnowflakeIDGenerator(machineID)).doesNotThrowAnyException();
    }

    @Test
    void generateOrderID_returnsNineDigitNumericString() {
        SnowflakeIDGenerator generator = new SnowflakeIDGenerator(1);

        String id = generator.generateOrderID();

        assertThat(id).hasSize(9).matches("\\d{9}");
    }

    @Test
    void generateOrderID_returnsUniqueIDs_forConsecutiveCalls() {
        SnowflakeIDGenerator generator = new SnowflakeIDGenerator(2);
        Set<String> ids = new HashSet<>();

        for (int i = 0; i < 200; i++) {
            ids.add(generator.generateOrderID());
        }

        assertThat(ids).hasSize(200);
    }

    @Test
    void generateOrderID_returnsUniqueIDs_underConcurrentAccess() throws InterruptedException {
        SnowflakeIDGenerator generator = new SnowflakeIDGenerator(3);
        int threadCount = 20;
        int idsPerThread = 50;
        int totalIDs = threadCount * idsPerThread;

        Set<String> ids = ConcurrentHashMap.newKeySet();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < idsPerThread; i++) {
                        ids.add(generator.generateOrderID());
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(ids).hasSize(totalIDs).allMatch(id -> id.matches("\\d{9}"));
    }

    @Test
    void generateOrderID_returnsUniqueIDs_forCallsInDifferentMillisecondsWithinSameSecond() {
        // Regression test: sequence resets to 0 on every new millisecond, so the encoded
        // timestamp must also carry millisecond resolution - otherwise two calls landing in the
        // same second but different milliseconds would both encode as (time, machine, 0) and collide
        SnowflakeIDGenerator generator = new SnowflakeIDGenerator(6);

        String first = generator.generateOrderID();
        long firstCallMillis = System.currentTimeMillis();
        while (System.currentTimeMillis() == firstCallMillis) {
            // Busy-wait for clock to roll over to the next millisecond
        }
        String second = generator.generateOrderID();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void generateOrderID_throwsClockSkewException_whenClockMovedBackwards() throws Exception {
        SnowflakeIDGenerator generator = new SnowflakeIDGenerator(4);
        long futureTimestamp = System.currentTimeMillis() + 60_000L;
        setLastTimestamp(generator, futureTimestamp);

        assertThatThrownBy(generator::generateOrderID)
                .isInstanceOf(ClockSkewException.class)
                .hasMessageContaining("Clock moved backwards by")
                .hasMessageContaining("Cannot generate ID until clock catches up");
    }

    private void setLastTimestamp(SnowflakeIDGenerator generator, long value) throws Exception {
        Field field = SnowflakeIDGenerator.class.getDeclaredField("lastTimestamp");
        field.setAccessible(true);
        field.set(generator, value);
    }
}
