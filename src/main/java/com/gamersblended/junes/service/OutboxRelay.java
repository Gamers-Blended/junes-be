package com.gamersblended.junes.service;

import com.gamersblended.junes.model.OutboxEvent;
import com.gamersblended.junes.repository.jpa.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final int MAX_RETRY_COUNT = 5;
    private static final long KAFKA_SEND_TIMEOUT_SECONDS = 5;

    // Relay needs String-valued template
    // Payload is already serialised JSON
    public OutboxRelay(OutboxEventRepository outboxEventRepository,
                       @Qualifier("outboxKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @SchedulerLock(name = "RelayOutboxTask", lockAtMostFor = "${relay.outbox-task.lock-at-most}", lockAtLeastFor = "${relay.outbox-task.lock-at-least}")
    @Scheduled(fixedDelay = 500)
    public void relayOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findTop100PendingEvents();

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("[OutboxRelay] Found {} unpublished event(s) to relay", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            relaySingleEvent(event);
        }
    }

    private void relaySingleEvent(OutboxEvent event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getAggregateID(), event.getPayload())
                    .get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            outboxEventRepository.markPublished(event.getId(), LocalDateTime.now(ZoneId.of("Asia/Singapore")));

            log.info("[OutboxRelay] Published event {} ({}) for aggregate {} to topic {}",
                    event.getId(), event.getEventType(), event.getAggregateID(), event.getTopic());
        } catch (Exception ex) {
            int attemptNumber = event.getRetryCount() + 1;

            if (attemptNumber >= MAX_RETRY_COUNT) {
                outboxEventRepository.markFailedPermanently(event.getId(), LocalDateTime.now(ZoneId.of("Asia/Singapore")));

                log.error("[OutboxRelay] Event {} has exceeded max retry count ({}) - needs manual attention",
                        event.getId(), MAX_RETRY_COUNT);
            } else {
                outboxEventRepository.incrementRetryCount(event.getId());
                log.error("[OutboxRelay] Failed to publish event {} for aggregate {} (attempt {})",
                        event.getId(), event.getAggregateID(), attemptNumber, ex);
            }
        }
    }
}
