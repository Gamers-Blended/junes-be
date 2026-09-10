package com.gamersblended.junes.service.outbox;

import com.gamersblended.junes.model.OutboxEvent;
import com.gamersblended.junes.repository.jpa.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxRelay outboxRelay;

    @BeforeEach
    void setUp() {
        outboxRelay = new OutboxRelay(outboxEventRepository, kafkaTemplate);
    }

    @SuppressWarnings("unchecked")
    private static CompletableFuture<SendResult<String, String>> successfulSend() {
        return CompletableFuture.completedFuture(mock(SendResult.class));
    }

    private static OutboxEvent outboxEvent(UUID id, String topic, String aggregateID, String payload, int retryCount) {
        OutboxEvent event = new OutboxEvent();
        event.setId(id);
        event.setTopic(topic);
        event.setAggregateID(aggregateID);
        event.setPayload(payload);
        event.setRetryCount(retryCount);
        return event;
    }

    // ---- relayOutboxEvents ----

    @Test
    void relayOutboxEvents_doesNothing_whenNoPendingEvents() {
        when(outboxEventRepository.findTop100PendingEvents()).thenReturn(List.of());

        outboxRelay.relayOutboxEvents();

        verifyNoInteractions(kafkaTemplate);
        verify(outboxEventRepository, never()).markPublished(any(), any());
    }

    @Test
    void relayOutboxEvents_publishesEventAndMarksPublished_onSuccess() {
        UUID id = UUID.randomUUID();
        OutboxEvent event = outboxEvent(id, "order-events", "ORD-1", "{}", 0);
        when(outboxEventRepository.findTop100PendingEvents()).thenReturn(List.of(event));
        when(kafkaTemplate.send("order-events", "ORD-1", "{}")).thenReturn(successfulSend());

        outboxRelay.relayOutboxEvents();

        verify(kafkaTemplate).send("order-events", "ORD-1", "{}");
        verify(outboxEventRepository).markPublished(eq(id), any(LocalDateTime.class));
        verify(outboxEventRepository, never()).incrementRetryCount(any());
        verify(outboxEventRepository, never()).markFailedPermanently(any(), any());
    }

    @Test
    void relayOutboxEvents_publishesEachPendingEvent() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        OutboxEvent event1 = outboxEvent(id1, "order-events", "ORD-1", "{}", 0);
        OutboxEvent event2 = outboxEvent(id2, "inventory-events", "p1", "{}", 0);
        when(outboxEventRepository.findTop100PendingEvents()).thenReturn(List.of(event1, event2));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(successfulSend());

        outboxRelay.relayOutboxEvents();

        verify(kafkaTemplate).send("order-events", "ORD-1", "{}");
        verify(kafkaTemplate).send("inventory-events", "p1", "{}");
        verify(outboxEventRepository).markPublished(eq(id1), any());
        verify(outboxEventRepository).markPublished(eq(id2), any());
    }

    @Test
    void relayOutboxEvents_incrementsRetryCount_whenPublishFailsBelowMaxRetry() {
        UUID id = UUID.randomUUID();
        OutboxEvent event = outboxEvent(id, "order-events", "ORD-1", "{}", 2);
        when(outboxEventRepository.findTop100PendingEvents()).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("kafka down"));

        outboxRelay.relayOutboxEvents();

        verify(outboxEventRepository).incrementRetryCount(id);
        verify(outboxEventRepository, never()).markFailedPermanently(any(), any());
        verify(outboxEventRepository, never()).markPublished(any(), any());
    }

    @Test
    void relayOutboxEvents_marksFailedPermanently_whenRetryCountReachesMax() {
        UUID id = UUID.randomUUID();
        // retryCount = 4 -> attemptNumber = 5 = MAX_RETRY_COUNT
        OutboxEvent event = outboxEvent(id, "order-events", "ORD-1", "{}", 4);
        when(outboxEventRepository.findTop100PendingEvents()).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("kafka down"));

        outboxRelay.relayOutboxEvents();

        verify(outboxEventRepository).markFailedPermanently(eq(id), any(LocalDateTime.class));
        verify(outboxEventRepository, never()).incrementRetryCount(any());
    }

    @Test
    void relayOutboxEvents_handlesFailedFuture_asPublishFailure() {
        UUID id = UUID.randomUUID();
        OutboxEvent event = outboxEvent(id, "order-events", "ORD-1", "{}", 0);
        when(outboxEventRepository.findTop100PendingEvents()).thenReturn(List.of(event));
        when(kafkaTemplate.send("order-events", "ORD-1", "{}"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unreachable")));

        outboxRelay.relayOutboxEvents();

        verify(outboxEventRepository).incrementRetryCount(id);
        verify(outboxEventRepository, never()).markPublished(any(), any());
    }

    @Test
    void relayOutboxEvents_continuesRelayingRemainingEvents_whenOnePublishThrows() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        OutboxEvent event1 = outboxEvent(id1, "order-events", "ORD-1", "{}", 0);
        OutboxEvent event2 = outboxEvent(id2, "order-events", "ORD-2", "{}", 0);
        when(outboxEventRepository.findTop100PendingEvents()).thenReturn(List.of(event1, event2));
        when(kafkaTemplate.send("order-events", "ORD-1", "{}")).thenThrow(new RuntimeException("kafka down"));
        when(kafkaTemplate.send("order-events", "ORD-2", "{}")).thenReturn(successfulSend());

        outboxRelay.relayOutboxEvents();

        verify(outboxEventRepository).incrementRetryCount(id1);
        verify(outboxEventRepository).markPublished(eq(id2), any());
    }

    @Test
    void relayOutboxEvents_sendsPayloadWithAggregateIDAsKey() {
        UUID id = UUID.randomUUID();
        OutboxEvent event = outboxEvent(id, "stripe-sync-events", "cus_123", "{\"a\":1}", 0);
        when(outboxEventRepository.findTop100PendingEvents()).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(successfulSend());

        outboxRelay.relayOutboxEvents();

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());
        assertThat(topicCaptor.getValue()).isEqualTo("stripe-sync-events");
        assertThat(keyCaptor.getValue()).isEqualTo("cus_123");
        assertThat(payloadCaptor.getValue()).isEqualTo("{\"a\":1}");
    }
}
