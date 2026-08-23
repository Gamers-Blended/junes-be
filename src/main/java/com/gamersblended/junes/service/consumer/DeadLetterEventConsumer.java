package com.gamersblended.junes.service.consumer;

import com.gamersblended.junes.dto.event.BaseEvent;
import com.gamersblended.junes.model.DeadLetterEvent;
import com.gamersblended.junes.repository.jpa.DeadLetterEventRepository;
import com.gamersblended.junes.util.KafkaEventParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static com.gamersblended.junes.constant.KafkaConstants.*;

@Slf4j
@Service
public class DeadLetterEventConsumer {

    private final DeadLetterEventRepository deadLetterEventRepository;
    private final KafkaEventParser kafkaEventParser;

    public DeadLetterEventConsumer(DeadLetterEventRepository deadLetterEventRepository, KafkaEventParser kafkaEventParser) {
        this.deadLetterEventRepository = deadLetterEventRepository;
        this.kafkaEventParser = kafkaEventParser;
    }

    // Explicit list of every ".DLT" topic Spring Kafka's DeadLetterPublishingRecoverer can produce to,
    // one per topic any @KafkaListener in the app consumes from
    @KafkaListener(topics = {
            STRIPE_PM_SYNC_EVENTS + DLT_SUFFIX,
            STRIPE_DETACH_PM_EVENTS + DLT_SUFFIX,
            STRIPE_SYNC_EVENTS + DLT_SUFFIX,
            ORDER_EVENTS + DLT_SUFFIX,
            INVENTORY_EVENTS + DLT_SUFFIX
    }, groupId = "dead-letter-event-consumer")
    @Transactional
    public void onDeadLetteredRecord(ConsumerRecord<String, String> record, Acknowledgment ack) {
        DeadLetterEvent deadLetterEvent = new DeadLetterEvent();
        deadLetterEvent.setOriginalTopic(readHeader(record, KafkaHeaders.DLT_ORIGINAL_TOPIC, record.topic()));
        deadLetterEvent.setExceptionMessage(readHeader(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE, null));
        deadLetterEvent.setPayload(record.value());
        deadLetterEvent.setStatus(UNRESOLVED);
        deadLetterEvent.setFailedOn(LocalDateTime.now(ZoneId.of("Asia/Singapore")));

        // Best-effort: a payload this consumer receives already failed its own listener,
        // so it may be malformed - must not let a parse failure throw and get this record DLT'd again
        try {
            BaseEvent parsed = kafkaEventParser.parse(record.value());
            deadLetterEvent.setEventID(parsed.getEventID());
            deadLetterEvent.setEventType(parsed.getEventType());
        } catch (Exception ex) {
            log.warn("[DeadLetterEventConsumer] Could not parse dead-lettered payload from topic {}, storing raw", record.topic());
        }

        deadLetterEventRepository.save(deadLetterEvent);
        log.error("[DeadLetterEventConsumer] Recorded dead-lettered record from topic {} (eventID {})",
                deadLetterEvent.getOriginalTopic(), deadLetterEvent.getEventID());

        ack.acknowledge();
    }

    private String readHeader(ConsumerRecord<String, String> record, String headerName, String fallback) {
        Header header = record.headers().lastHeader(headerName);
        if (null == header) {
            return fallback;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
