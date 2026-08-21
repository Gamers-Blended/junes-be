package com.gamersblended.junes.repository.jpa;

import com.gamersblended.junes.model.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(value = "INSERT INTO junes_rel.idempotency_keys (id, user_id, event_type, key_value, status, created_on) VALUES (:id, :userID, :eventType, :keyValue, 'IN_PROGRESS', NOW())", nativeQuery = true)
    void insertInProgress(@Param("id") UUID id, @Param("userID") UUID userID, @Param("eventType") String eventType, @Param("keyValue") String keyValue);

    @Query(value = "SELECT * FROM junes_rel.idempotency_keys WHERE user_id = :userID AND event_type = :eventType AND key_value = :keyValue", nativeQuery = true)
    Optional<IdempotencyKey> findByUserIDAndEventTypeAndKeyValue(@Param("userID") UUID userID, @Param("eventType") String eventType, @Param("keyValue") String keyValue);

    @Modifying
    @Query(value = "UPDATE junes_rel.idempotency_keys SET status = 'IN_PROGRESS', response_payload = NULL, updated_on = NOW() WHERE user_id = :userID AND event_type = :eventType AND key_value = :keyValue", nativeQuery = true)
    void retryInProgress(@Param("userID") UUID userID, @Param("eventType") String eventType, @Param("keyValue") String keyValue);

    @Modifying
    @Query(value = "UPDATE junes_rel.idempotency_keys SET status = 'COMPLETED', response_payload = :responsePayload, updated_on = NOW() WHERE user_id = :userID AND event_type = :eventType AND key_value = :keyValue", nativeQuery = true)
    void markCompleted(@Param("responsePayload") String responsePayload, @Param("userID") UUID userID, @Param("eventType") String eventType, @Param("keyValue") String keyValue);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(value = "UPDATE junes_rel.idempotency_keys SET status = 'FAILED', updated_on = NOW() WHERE user_id = :userID AND event_type = :eventType AND key_value = :keyValue", nativeQuery = true)
    void markFailed(@Param("userID") UUID userID, @Param("eventType") String eventType, @Param("keyValue") String keyValue);

    @Modifying
    @Query(value = "DELETE FROM junes_rel.idempotency_keys WHERE created_on < :cutoff", nativeQuery = true)
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
