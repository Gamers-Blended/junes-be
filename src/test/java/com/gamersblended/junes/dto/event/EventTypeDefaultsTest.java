package com.gamersblended.junes.dto.event;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.gamersblended.junes.constant.KafkaConstants.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each {@link BaseEvent} subclass hardcodes its {@code eventType} in its no-arg constructor
 * A copy-paste mistake here (wrong {@link com.gamersblended.junes.constant.KafkaConstants} value
 * on a new event class) would silently mis-route Kafka consumers, so each mapping is locked in here
 */
class EventTypeDefaultsTest {

    private static Stream<Arguments> eventSuppliers() {
        return Stream.of(
                Arguments.of((Supplier<BaseEvent>) StripeEmailUpdateEvent::new, EMAIL_UPDATED),
                Arguments.of((Supplier<BaseEvent>) StripePaymentMethodDetachEvent::new, PAYMENT_METHOD_DETACHED),
                Arguments.of((Supplier<BaseEvent>) StripePaymentMethodEditEvent::new, PAYMENT_METHOD_EDITED),
                Arguments.of((Supplier<BaseEvent>) StripePaymentMethodAddressAttachedEvent::new, PAYMENT_METHOD_ADDRESS_ATTACHED),
                Arguments.of((Supplier<BaseEvent>) StripePaymentMethodSetDefaultEvent::new, PAYMENT_METHOD_SET_DEFAULT),
                Arguments.of((Supplier<BaseEvent>) InventoryChangedEvent::new, INVENTORY_CHANGED),
                Arguments.of((Supplier<BaseEvent>) OrderCreatedEvent::new, ORDER_CREATED),
                Arguments.of((Supplier<BaseEvent>) PaymentSucceededEvent::new, PAYMENT_SUCCEEDED),
                Arguments.of((Supplier<BaseEvent>) PaymentFailedEvent::new, PAYMENT_FAILED)
        );
    }

    @ParameterizedTest
    @MethodSource("eventSuppliers")
    void noArgConstructor_setsExpectedEventTypeAndBaseDefaults(Supplier<BaseEvent> supplier, String expectedEventType) {
        BaseEvent event = supplier.get();

        assertThat(event.getEventType()).isEqualTo(expectedEventType);
        assertThat(event.getEventID()).isNotBlank();
        assertThat(event.getTimestamp()).isNotNull();
    }
}
