package com.gamersblended.junes.service.consumer;

import com.gamersblended.junes.dto.event.OrderCreatedEvent;
import com.gamersblended.junes.exception.UnknownEventTypeException;
import com.gamersblended.junes.model.DeadLetterEvent;
import com.gamersblended.junes.repository.jpa.DeadLetterEventRepository;
import com.gamersblended.junes.util.KafkaEventParser;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;

import java.nio.charset.StandardCharsets;

import static com.gamersblended.junes.constant.KafkaConstants.ORDER_EVENTS;
import static com.gamersblended.junes.constant.KafkaConstants.UNRESOLVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeadLetterEventConsumerTest {

    @Mock
    private DeadLetterEventRepository deadLetterEventRepository;
    @Mock
    private KafkaEventParser kafkaEventParser;
    @Mock
    private Acknowledgment ack;

    private DeadLetterEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new DeadLetterEventConsumer(deadLetterEventRepository, kafkaEventParser);
    }

    private static ConsumerRecord<String, String> recordWithoutHeaders(String rawJson) {
        return new ConsumerRecord<>(ORDER_EVENTS + ".DLT", 0, 0L, "key", rawJson);
    }

    // ---- onDeadLetteredRecord: header-derived fields ----

    @Test
    void onDeadLetteredRecord_populatesOriginalTopicAndExceptionMessage_fromHeaders() {
        ConsumerRecord<String, String> consumerRecord = recordWithoutHeaders("{\"eventType\":\"ORDER_CREATED\"}");
        consumerRecord.headers().add(new RecordHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC, "order-events".getBytes(StandardCharsets.UTF_8)));
        consumerRecord.headers().add(new RecordHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE, "boom".getBytes(StandardCharsets.UTF_8)));
        OrderCreatedEvent parsed = new OrderCreatedEvent();
        parsed.setEventID("evt-1");
        when(kafkaEventParser.parse(consumerRecord.value())).thenReturn(parsed);

        consumer.onDeadLetteredRecord(consumerRecord, ack);

        ArgumentCaptor<DeadLetterEvent> captor = ArgumentCaptor.forClass(DeadLetterEvent.class);
        verify(deadLetterEventRepository).save(captor.capture());
        DeadLetterEvent saved = captor.getValue();
        assertThat(saved.getOriginalTopic()).isEqualTo("order-events");
        assertThat(saved.getExceptionMessage()).isEqualTo("boom");
        assertThat(saved.getEventID()).isEqualTo("evt-1");
        assertThat(saved.getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(saved.getPayload()).isEqualTo(consumerRecord.value());
        assertThat(saved.getStatus()).isEqualTo(UNRESOLVED);
        assertThat(saved.getFailedOn()).isNotNull();

        verify(ack).acknowledge();
    }

    @Test
    void onDeadLetteredRecord_fallsBackToRecordTopic_andNullExceptionMessage_whenHeadersAbsent() {
        ConsumerRecord<String, String> consumerRecord = recordWithoutHeaders("{\"eventType\":\"ORDER_CREATED\"}");
        OrderCreatedEvent parsed = new OrderCreatedEvent();
        parsed.setEventID("evt-2");
        when(kafkaEventParser.parse(consumerRecord.value())).thenReturn(parsed);

        consumer.onDeadLetteredRecord(consumerRecord, ack);

        ArgumentCaptor<DeadLetterEvent> captor = ArgumentCaptor.forClass(DeadLetterEvent.class);
        verify(deadLetterEventRepository).save(captor.capture());
        DeadLetterEvent saved = captor.getValue();
        assertThat(saved.getOriginalTopic()).isEqualTo(consumerRecord.topic());
        assertThat(saved.getExceptionMessage()).isNull();

        verify(ack).acknowledge();
    }

    // ---- onDeadLetteredRecord: unparsable payload ----

    @Test
    void onDeadLetteredRecord_storesRawPayload_withNullEventFields_whenParsingFails() {
        ConsumerRecord<String, String> consumerRecord = recordWithoutHeaders("{not valid json");
        when(kafkaEventParser.parse(consumerRecord.value())).thenThrow(new UnknownEventTypeException("bad payload"));

        consumer.onDeadLetteredRecord(consumerRecord, ack);

        ArgumentCaptor<DeadLetterEvent> captor = ArgumentCaptor.forClass(DeadLetterEvent.class);
        verify(deadLetterEventRepository).save(captor.capture());
        DeadLetterEvent saved = captor.getValue();
        assertThat(saved.getEventID()).isNull();
        assertThat(saved.getEventType()).isNull();
        assertThat(saved.getPayload()).isEqualTo(consumerRecord.value());
        assertThat(saved.getStatus()).isEqualTo(UNRESOLVED);

        verify(ack).acknowledge();
    }

    @Test
    void onDeadLetteredRecord_alwaysAcknowledges_evenWhenParsingFails() {
        ConsumerRecord<String, String> consumerRecord = recordWithoutHeaders("garbage");
        when(kafkaEventParser.parse(consumerRecord.value())).thenThrow(new RuntimeException("unexpected"));

        consumer.onDeadLetteredRecord(consumerRecord, ack);

        verify(deadLetterEventRepository).save(any(DeadLetterEvent.class));
        verify(ack).acknowledge();
    }
}
