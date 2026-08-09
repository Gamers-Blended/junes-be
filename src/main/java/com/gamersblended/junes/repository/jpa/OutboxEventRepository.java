package com.gamersblended.junes.repository.jpa;

import com.gamersblended.junes.model.OutboxEvent;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Used by outbox relay's polling loop - oldest unpublished events first
     * Capped so 1 poll can't drain unbounded backlog
     */
    @Query(value = "SELECT * FROM junes_rel.outbox_events WHERE published = false AND status = 'PENDING' ORDER BY created_on LIMIT 100", nativeQuery = true)
    List<OutboxEvent> findTop100PendingEvents();

    /**
     * Called after a successful Kafka send
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE junes_rel.outbox_events SET published = true, published_on = :publishedOn WHERE id = :id", nativeQuery = true)
    void markPublished(@Param("id") UUID id, @Param("publishedOn") LocalDateTime publishedOn);

    /**
     * Called whenever a publish attempt fails
     * Relay's error handler decide whether to retry or flag it for dead-letter path once retryCount crosses threshold
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = "UPDATE junes_rel.outbox_events SET retry_count = retry_count + 1 WHERE id = :id", nativeQuery = true)
    void incrementRetryCount(@Param("id") UUID id);

    /**
     * Called whenever a publish attempt fails
     * Ensures failed events don't get picked up by relayer
     */
    @Modifying
    @Query(value = "UPDATE junes_rel.outbox_events SET status = 'FAILED_PERMANENTLY', published_on = :publishedOn WHERE id = :id", nativeQuery = true)
    void markFailedPermanently(@Param("id") UUID id, @Param("publishedOn") LocalDateTime publishedOn);
}
