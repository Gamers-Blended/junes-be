package com.gamersblended.junes.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.exception.DuplicateRequestInProgressException;
import com.gamersblended.junes.model.IdempotencyKey;
import com.gamersblended.junes.repository.jpa.IdempotencyKeyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;


@Slf4j
@Component
public class IdempotentUtils {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;
    private static final String COMPLETED = "COMPLETED";
    private static final String FAILED = "FAILED";

    public IdempotentUtils(IdempotencyKeyRepository idempotencyKeyRepository, ObjectMapper objectMapper) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.objectMapper = objectMapper;
    }

    public <T> T executeIdempotent(UUID userID, String eventType, String idempotencyKey, Class<T> responseType, Supplier<T> action) {
        try {
            idempotencyKeyRepository.insertInProgress(UUID.randomUUID(), userID, eventType, idempotencyKey);
        } catch (DataIntegrityViolationException ex) {
            // Current process was triggered before
            IdempotencyKey existing = idempotencyKeyRepository
                    .findByUserIDAndEventTypeAndKeyValue(userID, eventType, idempotencyKey)
                    .orElseThrow(() -> ex);

            if (COMPLETED.equals(existing.getStatus())) {
                return deserialize(existing.getResponsePayload(), responseType);
            }
            if (!FAILED.equals(existing.getStatus())) {
                // Concurrent duplicate, still running
                throw new DuplicateRequestInProgressException("Current process is still running");
            }
            // Previous attempt failed - retry under the same idempotency key
            idempotencyKeyRepository.retryInProgress(userID, eventType, idempotencyKey);
        }

        try {
            T result = action.get();
            idempotencyKeyRepository.markCompleted(serialize(result), userID, eventType, idempotencyKey);
            return result;
        } catch (Exception ex) {
            idempotencyKeyRepository.markFailed(userID, eventType, idempotencyKey);
            throw ex;
        }
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize idempotency response payload", ex);
            return null;
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            log.error("Failed to deserialize idempotency response payload", ex);
            throw new IllegalStateException("Corrupt idempotency payload", ex);
        }
    }
}
