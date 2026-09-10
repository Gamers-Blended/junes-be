package com.gamersblended.junes.service.outbox;

import com.gamersblended.junes.model.DeadLetterEvent;
import com.gamersblended.junes.model.OutboxEvent;
import com.gamersblended.junes.repository.jpa.DeadLetterEventRepository;
import com.gamersblended.junes.repository.jpa.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static com.gamersblended.junes.constant.KafkaConstants.FAILED_PERMANENTLY;
import static com.gamersblended.junes.constant.KafkaConstants.UNRESOLVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private DeadLetterEventRepository deadLetterEventRepository;

    private ReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService = new ReconciliationService(outboxEventRepository, deadLetterEventRepository);
    }

    private static OutboxEvent outboxEvent(UUID id) {
        OutboxEvent event = new OutboxEvent();
        event.setId(id);
        return event;
    }

    private static DeadLetterEvent deadLetterEvent(UUID id) {
        DeadLetterEvent event = new DeadLetterEvent();
        event.setId(id);
        return event;
    }

    // ---- getPermanentlyFailedOutboxEvents ----

    @Test
    void getPermanentlyFailedOutboxEvents_queriesByFailedPermanentlyStatus() {
        OutboxEvent event = outboxEvent(UUID.randomUUID());
        when(outboxEventRepository.findByStatus(FAILED_PERMANENTLY)).thenReturn(List.of(event));

        List<OutboxEvent> result = reconciliationService.getPermanentlyFailedOutboxEvents();

        assertThat(result).containsExactly(event);
    }

    // ---- getUnresolvedDeadLetterEvents ----

    @Test
    void getUnresolvedDeadLetterEvents_queriesByUnresolvedStatus() {
        DeadLetterEvent event = deadLetterEvent(UUID.randomUUID());
        when(deadLetterEventRepository.findByStatus(UNRESOLVED)).thenReturn(List.of(event));

        List<DeadLetterEvent> result = reconciliationService.getUnresolvedDeadLetterEvents();

        assertThat(result).containsExactly(event);
    }

    // ---- logUnresolvedFailures ----

    @Test
    void logUnresolvedFailures_doesNotThrow_whenNoFailuresExist() {
        when(outboxEventRepository.findByStatus(FAILED_PERMANENTLY)).thenReturn(List.of());
        when(deadLetterEventRepository.findByStatus(UNRESOLVED)).thenReturn(List.of());

        reconciliationService.logUnresolvedFailures();

        verify(outboxEventRepository).findByStatus(FAILED_PERMANENTLY);
        verify(deadLetterEventRepository).findByStatus(UNRESOLVED);
    }

    @Test
    void logUnresolvedFailures_queriesBothRepositories_whenOnlyOutboxEventsFailed() {
        when(outboxEventRepository.findByStatus(FAILED_PERMANENTLY)).thenReturn(List.of(outboxEvent(UUID.randomUUID())));
        when(deadLetterEventRepository.findByStatus(UNRESOLVED)).thenReturn(List.of());

        reconciliationService.logUnresolvedFailures();

        verify(outboxEventRepository).findByStatus(FAILED_PERMANENTLY);
        verify(deadLetterEventRepository).findByStatus(UNRESOLVED);
    }

    @Test
    void logUnresolvedFailures_queriesBothRepositories_whenOnlyDeadLetterEventsUnresolved() {
        when(outboxEventRepository.findByStatus(FAILED_PERMANENTLY)).thenReturn(List.of());
        when(deadLetterEventRepository.findByStatus(UNRESOLVED)).thenReturn(List.of(deadLetterEvent(UUID.randomUUID())));

        reconciliationService.logUnresolvedFailures();

        verify(outboxEventRepository).findByStatus(FAILED_PERMANENTLY);
        verify(deadLetterEventRepository).findByStatus(UNRESOLVED);
    }

    @Test
    void logUnresolvedFailures_handlesBothOutboxAndDeadLetterFailures() {
        when(outboxEventRepository.findByStatus(FAILED_PERMANENTLY))
                .thenReturn(List.of(outboxEvent(UUID.randomUUID()), outboxEvent(UUID.randomUUID())));
        when(deadLetterEventRepository.findByStatus(UNRESOLVED)).thenReturn(List.of(deadLetterEvent(UUID.randomUUID())));

        reconciliationService.logUnresolvedFailures();

        verify(outboxEventRepository).findByStatus(FAILED_PERMANENTLY);
        verify(deadLetterEventRepository).findByStatus(UNRESOLVED);
    }
}
