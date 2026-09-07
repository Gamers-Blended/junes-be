package com.gamersblended.junes.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gamersblended.junes.dto.event.*;
import com.gamersblended.junes.exception.UnknownEventTypeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.stream.Stream;

import static com.gamersblended.junes.constant.KafkaConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaEventParserTest {

    private KafkaEventParser parser;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        parser = new KafkaEventParser(objectMapper);
    }

    private static Stream<Arguments> registeredEventTypes() {
        return Stream.of(
                Arguments.of(EMAIL_UPDATED, StripeEmailUpdateEvent.class),
                Arguments.of(PAYMENT_METHOD_DETACHED, StripePaymentMethodDetachEvent.class),
                Arguments.of(PAYMENT_METHOD_EDITED, StripePaymentMethodEditEvent.class),
                Arguments.of(PAYMENT_METHOD_ADDRESS_ATTACHED, StripePaymentMethodAddressAttachedEvent.class),
                Arguments.of(PAYMENT_METHOD_SET_DEFAULT, StripePaymentMethodSetDefaultEvent.class),
                Arguments.of(ORDER_CREATED, OrderCreatedEvent.class),
                Arguments.of(PAYMENT_SUCCEEDED, PaymentSucceededEvent.class),
                Arguments.of(PAYMENT_FAILED, PaymentFailedEvent.class)
        );
    }

    @ParameterizedTest
    @MethodSource("registeredEventTypes")
    void parse_returnsInstanceOfRegisteredSubclass_forEachKnownEventType(String eventType, Class<? extends BaseEvent> expectedClass) {
        String rawJson = "{\"eventType\":\"" + eventType + "\"}";

        BaseEvent event = parser.parse(rawJson);

        assertThat(event).isInstanceOf(expectedClass);
        assertThat(event.getEventType()).isEqualTo(eventType);
    }

    @Test
    void parse_populatesDeclaredAndBaseFields_forOrderCreatedEvent() {
        String rawJson = """
                {
                  "eventType": "ORDER_CREATED",
                  "eventID": "evt-123",
                  "timestamp": "2026-01-15T10:30:00",
                  "idempotencyKey": "idem-456",
                  "correlationId": "corr-789",
                  "transactionID": "11111111-1111-1111-1111-111111111111",
                  "orderNumber": "ORD-0001",
                  "userID": "22222222-2222-2222-2222-222222222222",
                  "totalAmount": 59.99,
                  "currency": "USD",
                  "itemList": [
                    {"quantity": 2, "productID": "prod-1"}
                  ]
                }
                """;

        BaseEvent event = parser.parse(rawJson);

        assertThat(event).isInstanceOf(OrderCreatedEvent.class);
        OrderCreatedEvent orderCreatedEvent = (OrderCreatedEvent) event;
        assertThat(orderCreatedEvent.getEventID()).isEqualTo("evt-123");
        assertThat(orderCreatedEvent.getTimestamp()).isEqualTo(LocalDateTime.of(2026, Month.JANUARY, 15, 10, 30, 0));
        assertThat(orderCreatedEvent.getIdempotencyKey()).isEqualTo("idem-456");
        assertThat(orderCreatedEvent.getCorrelationId()).isEqualTo("corr-789");
        assertThat(orderCreatedEvent.getOrderNumber()).isEqualTo("ORD-0001");
        assertThat(orderCreatedEvent.getCurrency()).isEqualTo("USD");
        assertThat(orderCreatedEvent.getItemList()).hasSize(1);
        assertThat(orderCreatedEvent.getItemList().get(0).getQuantity()).isEqualTo(2);
        assertThat(orderCreatedEvent.getItemList().get(0).getProductID()).isEqualTo("prod-1");
    }

    @Test
    void parse_throwsUnknownEventTypeException_whenEventTypeIsUnregistered() {
        String rawJson = "{\"eventType\":\"SOME_UNKNOWN_TYPE\"}";

        assertThatThrownBy(() -> parser.parse(rawJson))
                .isInstanceOf(UnknownEventTypeException.class)
                .hasMessageContaining("No registered event class for eventType: SOME_UNKNOWN_TYPE");
    }

    @Test
    void parse_throwsUnknownEventTypeException_whenEventTypeFieldIsMissing() {
        String rawJson = "{\"foo\":\"bar\"}";

        assertThatThrownBy(() -> parser.parse(rawJson))
                .isInstanceOf(UnknownEventTypeException.class)
                .hasMessageContaining("Event JSON has no eventType field");
    }

    @Test
    void parse_throwsUnknownEventTypeException_whenEventTypeFieldIsExplicitJsonNull() {
        String rawJson = "{\"eventType\":null}";

        assertThatThrownBy(() -> parser.parse(rawJson))
                .isInstanceOf(UnknownEventTypeException.class)
                .hasMessageContaining("Event JSON has no eventType field");
    }

    @Test
    void parse_throwsUnknownEventTypeException_whenRawJsonIsMalformed() {
        String rawJson = "{not valid json";

        assertThatThrownBy(() -> parser.parse(rawJson))
                .isInstanceOf(UnknownEventTypeException.class)
                .hasMessageContaining("Failed to parse eventType from raw JSON");
    }

    @Test
    void parse_throwsUnknownEventTypeException_whenRawJsonIsBlank() {
        assertThatThrownBy(() -> parser.parse(""))
                .isInstanceOf(UnknownEventTypeException.class)
                .hasMessageContaining("Event JSON has no eventType field");
    }

    @Test
    void parse_throwsUnknownEventTypeException_whenFieldTypeDoesNotMatchTargetClass() {
        String rawJson = "{\"eventType\":\"ORDER_CREATED\",\"totalAmount\":\"not-a-number\"}";

        assertThatThrownBy(() -> parser.parse(rawJson))
                .isInstanceOf(UnknownEventTypeException.class)
                .hasMessageContaining("Failed to deserialize event of type ORDER_CREATED");
    }
}
