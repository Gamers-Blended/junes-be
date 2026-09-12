package com.gamersblended.junes.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaProducerConfigTest {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";

    private final KafkaProducerConfig kafkaProducerConfig = new KafkaProducerConfig();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(kafkaProducerConfig, "bootstrapServers", BOOTSTRAP_SERVERS);
    }

    @Test
    void producerFactory_configuresJsonValueSerializer_withReliabilitySettings() {
        ProducerFactory<String, Object> factory = kafkaProducerConfig.producerFactory();

        assertThat(factory).isInstanceOf(DefaultKafkaProducerFactory.class);
        var config = (factory).getConfigurationProperties();
        assertThat(config)
                .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS)
                .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
                .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class)
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                .containsEntry(ProducerConfig.RETRIES_CONFIG, 3)
                .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    }

    @Test
    void outboxProducerFactory_configuresStringValueSerializer_withReliabilitySettings() {
        ProducerFactory<String, String> factory = kafkaProducerConfig.outboxProducerFactory();

        assertThat(factory).isInstanceOf(DefaultKafkaProducerFactory.class);
        var config = (factory).getConfigurationProperties();
        assertThat(config)
                .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS)
                .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
                .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                .containsEntry(ProducerConfig.RETRIES_CONFIG, 3)
                .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    }

    @Test
    void kafkaTemplate_wrapsJsonValueProducerFactory() {
        KafkaTemplate<String, Object> template = kafkaProducerConfig.kafkaTemplate();

        assertThat(template).isNotNull();
        var config = (template.getProducerFactory()).getConfigurationProperties();
        assertThat(config).containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    }

    @Test
    void outboxKafkaTemplate_wrapsStringValueProducerFactory() {
        KafkaTemplate<String, String> template = kafkaProducerConfig.outboxKafkaTemplate();

        assertThat(template).isNotNull();
        var config = (template.getProducerFactory()).getConfigurationProperties();
        assertThat(config).containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    }
}
