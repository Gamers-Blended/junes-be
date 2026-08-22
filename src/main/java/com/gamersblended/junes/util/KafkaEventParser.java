package com.gamersblended.junes.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.constant.KafkaConstants;
import com.gamersblended.junes.dto.event.*;
import com.gamersblended.junes.exception.UnknownEventTypeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class KafkaEventParser {

    private final ObjectMapper objectMapper;

    // eventType String -> BaseEvent subclasses to deserialise into
    private final Map<String, Class<? extends BaseEvent>> eventTypeRegistry = Map.of(
            KafkaConstants.EMAIL_UPDATED, StripeEmailUpdateEvent.class,
            KafkaConstants.PAYMENT_METHOD_DETACHED, StripePaymentMethodDetachEvent.class,
            KafkaConstants.PAYMENT_METHOD_EDITED, StripePaymentMethodEditEvent.class,
            KafkaConstants.PAYMENT_METHOD_ADDRESS_ATTACHED, StripePaymentMethodAddressAttachedEvent.class,
            KafkaConstants.PAYMENT_METHOD_SET_DEFAULT, StripePaymentMethodSetDefaultEvent.class,
            KafkaConstants.ORDER_CREATED, OrderCreatedEvent.class,
            KafkaConstants.PAYMENT_SUCCEEDED, PaymentSucceededEvent.class,
            KafkaConstants.PAYMENT_FAILED, PaymentFailedEvent.class
    );

    public KafkaEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Parses eventType field out of raw JSON and deserialises into matching BaseEvent subclass
    public BaseEvent parse(String rawJson) {
        String eventType = extractEventType(rawJson);

        Class<? extends BaseEvent> targetClass = eventTypeRegistry.get(eventType);
        if (null == targetClass) {
            throw new UnknownEventTypeException("No registered event class for eventType: " + eventType);
        }

        try {
            return objectMapper.readValue(rawJson, targetClass);
        } catch (Exception ex) {
            log.error("Failed to deserialise event of type {} from raw JSON", eventType, ex);
            throw new UnknownEventTypeException(
                    "Failed to deserialize event of type " + eventType + ": " + ex.getMessage());
        }
    }

    private String extractEventType(String rawJson) {
        try {
            JsonNode node = objectMapper.readTree(rawJson);
            JsonNode eventTypeNode = node.get("eventType");
            if (eventTypeNode == null || eventTypeNode.isNull()) {
                throw new UnknownEventTypeException("Event JSON has no eventType field: " + rawJson);
            }
            return eventTypeNode.asText();
        } catch (UnknownEventTypeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new UnknownEventTypeException("Failed to parse eventType from raw JSON: " + ex.getMessage());
        }

    }
}
