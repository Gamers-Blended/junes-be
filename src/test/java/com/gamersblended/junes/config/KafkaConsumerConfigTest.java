package com.gamersblended.junes.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConsumerConfigTest {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";

    private final KafkaConsumerConfig kafkaConsumerConfig = new KafkaConsumerConfig(new SimpleMeterRegistry());

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(kafkaConsumerConfig, "bootstrapServers", BOOTSTRAP_SERVERS);
    }

    @Test
    void consumerFactory_configuresStringDeserializers_withManualCommit() {
        ConsumerFactory<String, String> factory = kafkaConsumerConfig.consumerFactory();

        assertThat(factory).isInstanceOf(DefaultKafkaConsumerFactory.class);
        var config = (factory).getConfigurationProperties();
        assertThat(config)
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS)
                .containsEntry(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class)
                .containsEntry(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class)
                .containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    }

    @Test
    void kafkaListenerContainerFactory_setsManualAckModeAndMicrometer() {
        KafkaTemplate<String, String> outboxKafkaTemplate = new KafkaTemplate<>(
                new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(java.util.Map.of()));

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                kafkaConsumerConfig.kafkaListenerContainerFactory(outboxKafkaTemplate);

        assertThat(factory.getContainerProperties().getAckMode()).isEqualTo(ContainerProperties.AckMode.MANUAL);
        assertThat(factory.getContainerProperties().isMicrometerEnabled()).isTrue();
    }

    @Test
    void kafkaListenerContainerFactory_setsConcurrencyOfThree() {
        KafkaTemplate<String, String> outboxKafkaTemplate = new KafkaTemplate<>(
                new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(java.util.Map.of()));

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                kafkaConsumerConfig.kafkaListenerContainerFactory(outboxKafkaTemplate);

        Integer concurrency = (Integer) ReflectionTestUtils.getField(factory, "concurrency");
        assertThat(concurrency).isEqualTo(3);
    }

    @Test
    void kafkaListenerContainerFactory_setsDeadLetterErrorHandler() {
        KafkaTemplate<String, String> outboxKafkaTemplate = new KafkaTemplate<>(
                new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(java.util.Map.of()));

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                kafkaConsumerConfig.kafkaListenerContainerFactory(outboxKafkaTemplate);

        Object errorHandler = ReflectionTestUtils.getField(factory, "commonErrorHandler");
        assertThat(errorHandler).isInstanceOf(DefaultErrorHandler.class);
    }
}
