package com.gamersblended.junes.service;

import com.gamersblended.junes.model.DeadLetterEvent;
import com.gamersblended.junes.model.OutboxEvent;
import com.gamersblended.junes.repository.jpa.DeadLetterEventRepository;
import com.gamersblended.junes.repository.jpa.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static com.gamersblended.junes.constant.KafkaConstants.FAILED_PERMANENTLY;
import static com.gamersblended.junes.constant.KafkaConstants.UNRESOLVED;

@Slf4j
@Service
public class ReconciliationService {

    private final OutboxEventRepository outboxEventRepository;
    private final DeadLetterEventRepository deadLetterEventRepository;

    public ReconciliationService(OutboxEventRepository outboxEventRepository, DeadLetterEventRepository deadLetterEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
        this.deadLetterEventRepository = deadLetterEventRepository;
    }

    public List<OutboxEvent> getPermanentlyFailedOutboxEvents() {
        return outboxEventRepository.findByStatus(FAILED_PERMANENTLY);
    }

    public List<DeadLetterEvent> getUnresolvedDeadLetterEvents() {
        return deadLetterEventRepository.findByStatus(UNRESOLVED);
    }

    public void logUnresolvedFailures() {
        List<OutboxEvent> failedOutboxEvents = getPermanentlyFailedOutboxEvents();
        List<DeadLetterEvent> unresolvedDeadLetterEvents = getUnresolvedDeadLetterEvents();

        if (failedOutboxEvents.isEmpty() && unresolvedDeadLetterEvents.isEmpty()) {
            return;
        }

        if (!failedOutboxEvents.isEmpty()) {
            log.warn("[ReconciliationService] {} outbox event(s) permanently failed to publish: {}",
                    failedOutboxEvents.size(),
                    failedOutboxEvents.stream().map(OutboxEvent::getId).map(String::valueOf).collect(Collectors.joining(", ")));
        }

        if (!unresolvedDeadLetterEvents.isEmpty()) {
            log.warn("[ReconciliationService] {} dead-lettered event(s) awaiting manual resolution: {}",
                    unresolvedDeadLetterEvents.size(),
                    unresolvedDeadLetterEvents.stream().map(DeadLetterEvent::getId).map(String::valueOf).collect(Collectors.joining(", ")));
        }
    }
}