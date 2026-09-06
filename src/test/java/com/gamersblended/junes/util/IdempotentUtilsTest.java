package com.gamersblended.junes.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.exception.DuplicateRequestInProgressException;
import com.gamersblended.junes.model.IdempotencyKey;
import com.gamersblended.junes.repository.jpa.IdempotencyKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotentUtilsTest {

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private IdempotentUtils idempotentUtils;

    private final UUID userID = UUID.randomUUID();
    private static final String EVENT_TYPE = "ORDER_CREATION";
    private static final String IDEMPOTENCY_KEY = "idem-key-123";

    @BeforeEach
    void setUp() {
        idempotentUtils = new IdempotentUtils(idempotencyKeyRepository, objectMapper);
    }

    @SuppressWarnings("unchecked")
    private <T> Supplier<T> mockSupplier() {
        return mock(Supplier.class);
    }

    private IdempotencyKey existingKeyWithStatus(String status) {
        IdempotencyKey key = new IdempotencyKey();
        key.setUserID(userID);
        key.setEventType(EVENT_TYPE);
        key.setKeyValue(IDEMPOTENCY_KEY);
        key.setStatus(status);
        return key;
    }

    @Test
    void executeIdempotent_runsActionAndMarksCompletedOnFirstAttempt() {
        Supplier<String> action = () -> "result-payload";

        String result = idempotentUtils.executeIdempotent(userID, EVENT_TYPE, IDEMPOTENCY_KEY, String.class, action);

        assertThat(result).isEqualTo("result-payload");
        verify(idempotencyKeyRepository).insertInProgress(any(UUID.class), eq(userID), eq(EVENT_TYPE), eq(IDEMPOTENCY_KEY));
        verify(idempotencyKeyRepository).markCompleted("\"result-payload\"", userID, EVENT_TYPE, IDEMPOTENCY_KEY);
        verify(idempotencyKeyRepository, never()).markFailed(any(), any(), any());
        verify(idempotencyKeyRepository, never()).retryInProgress(any(), any(), any());
    }

    @Test
    void executeIdempotent_marksFailedAndRethrowsWhenActionThrows() {
        RuntimeException actionFailure = new RuntimeException("boom");
        Supplier<String> action = () -> {
            throw actionFailure;
        };

        assertThatThrownBy(() -> idempotentUtils.executeIdempotent(userID, EVENT_TYPE, IDEMPOTENCY_KEY, String.class, action))
                .isSameAs(actionFailure);

        verify(idempotencyKeyRepository).markFailed(userID, EVENT_TYPE, IDEMPOTENCY_KEY);
        verify(idempotencyKeyRepository, never()).markCompleted(any(), any(), any(), any());
    }

    @Test
    void executeIdempotent_returnsCachedResponseWhenExistingKeyIsCompleted() {
        DataIntegrityViolationException conflict = new DataIntegrityViolationException("duplicate key");
        doThrow(conflict).when(idempotencyKeyRepository)
                .insertInProgress(any(UUID.class), eq(userID), eq(EVENT_TYPE), eq(IDEMPOTENCY_KEY));

        IdempotencyKey existing = existingKeyWithStatus("COMPLETED");
        existing.setResponsePayload("\"cached-result\"");
        when(idempotencyKeyRepository.findByUserIDAndEventTypeAndKeyValue(userID, EVENT_TYPE, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existing));

        Supplier<String> action = mockSupplier();

        String result = idempotentUtils.executeIdempotent(userID, EVENT_TYPE, IDEMPOTENCY_KEY, String.class, action);

        assertThat(result).isEqualTo("cached-result");
        verifyNoMoreInteractions(action);
        verify(idempotencyKeyRepository, never()).retryInProgress(any(), any(), any());
    }

    @Test
    void executeIdempotent_throwsDuplicateRequestInProgressWhenExistingKeyIsInProgress() {
        DataIntegrityViolationException conflict = new DataIntegrityViolationException("duplicate key");
        doThrow(conflict).when(idempotencyKeyRepository)
                .insertInProgress(any(UUID.class), eq(userID), eq(EVENT_TYPE), eq(IDEMPOTENCY_KEY));

        IdempotencyKey existing = existingKeyWithStatus("IN_PROGRESS");
        when(idempotencyKeyRepository.findByUserIDAndEventTypeAndKeyValue(userID, EVENT_TYPE, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existing));

        Supplier<String> action = mockSupplier();

        assertThatThrownBy(() -> idempotentUtils.executeIdempotent(userID, EVENT_TYPE, IDEMPOTENCY_KEY, String.class, action))
                .isInstanceOf(DuplicateRequestInProgressException.class)
                .hasMessage("Current process is still running");

        verifyNoMoreInteractions(action);
        verify(idempotencyKeyRepository, never()).retryInProgress(any(), any(), any());
    }

    @Test
    void executeIdempotent_retriesAndRunsActionWhenExistingKeyIsFailed() {
        DataIntegrityViolationException conflict = new DataIntegrityViolationException("duplicate key");
        doThrow(conflict).when(idempotencyKeyRepository)
                .insertInProgress(any(UUID.class), eq(userID), eq(EVENT_TYPE), eq(IDEMPOTENCY_KEY));

        IdempotencyKey existing = existingKeyWithStatus("FAILED");
        when(idempotencyKeyRepository.findByUserIDAndEventTypeAndKeyValue(userID, EVENT_TYPE, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existing));

        Supplier<String> action = () -> "retried-result";

        String result = idempotentUtils.executeIdempotent(userID, EVENT_TYPE, IDEMPOTENCY_KEY, String.class, action);

        assertThat(result).isEqualTo("retried-result");
        verify(idempotencyKeyRepository).retryInProgress(userID, EVENT_TYPE, IDEMPOTENCY_KEY);
        verify(idempotencyKeyRepository).markCompleted("\"retried-result\"", userID, EVENT_TYPE, IDEMPOTENCY_KEY);
    }

    @Test
    void executeIdempotent_rethrowsOriginalConflictWhenExistingKeyLookupIsEmpty() {
        DataIntegrityViolationException conflict = new DataIntegrityViolationException("duplicate key");
        doThrow(conflict).when(idempotencyKeyRepository)
                .insertInProgress(any(UUID.class), eq(userID), eq(EVENT_TYPE), eq(IDEMPOTENCY_KEY));
        when(idempotencyKeyRepository.findByUserIDAndEventTypeAndKeyValue(userID, EVENT_TYPE, IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());

        Supplier<String> action = mockSupplier();

        assertThatThrownBy(() -> idempotentUtils.executeIdempotent(userID, EVENT_TYPE, IDEMPOTENCY_KEY, String.class, action))
                .isSameAs(conflict);

        verifyNoMoreInteractions(action);
    }

    @Test
    void executeIdempotent_returnsNullWhenSerializationOfResultFails() {
        Object unserializable = new Object() {
            @SuppressWarnings("unused")
            public String getValue() {
                throw new RuntimeException("cannot read value");
            }
        };
        Supplier<Object> action = () -> unserializable;

        Object result = idempotentUtils.executeIdempotent(userID, EVENT_TYPE, IDEMPOTENCY_KEY, Object.class, action);

        assertThat(result).isSameAs(unserializable);
        verify(idempotencyKeyRepository).markCompleted(isNull(), eq(userID), eq(EVENT_TYPE), eq(IDEMPOTENCY_KEY));
    }

    @Test
    void executeIdempotent_throwsIllegalStateExceptionWhenCachedPayloadIsCorrupt() {
        DataIntegrityViolationException conflict = new DataIntegrityViolationException("duplicate key");
        doThrow(conflict).when(idempotencyKeyRepository)
                .insertInProgress(any(UUID.class), eq(userID), eq(EVENT_TYPE), eq(IDEMPOTENCY_KEY));

        IdempotencyKey existing = existingKeyWithStatus("COMPLETED");
        existing.setResponsePayload("not-valid-json{{{");
        when(idempotencyKeyRepository.findByUserIDAndEventTypeAndKeyValue(userID, EVENT_TYPE, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existing));

        Supplier<String> action = mockSupplier();

        assertThatThrownBy(() -> idempotentUtils.executeIdempotent(userID, EVENT_TYPE, IDEMPOTENCY_KEY, String.class, action))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Corrupt idempotency payload");

        verifyNoMoreInteractions(action);
    }
}
