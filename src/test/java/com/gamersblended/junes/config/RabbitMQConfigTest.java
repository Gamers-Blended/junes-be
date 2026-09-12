package com.gamersblended.junes.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RabbitMQConfigTest {

    private static final String QUEUE_NAME = "email.queue";
    private static final String EXCHANGE = "email.exchange";
    private static final String ROUTING_KEY = "email.routing-key";
    private static final String DLQ_NAME = "email.dlq";
    private static final String DLQ_EXCHANGE = "email.dlq.exchange";

    @Mock
    private ConnectionFactory connectionFactory;

    private RabbitMQConfig rabbitMQConfig;

    @BeforeEach
    void setUp() {
        rabbitMQConfig = new RabbitMQConfig();
        ReflectionTestUtils.setField(rabbitMQConfig, "queueName", QUEUE_NAME);
        ReflectionTestUtils.setField(rabbitMQConfig, "exchange", EXCHANGE);
        ReflectionTestUtils.setField(rabbitMQConfig, "routingKey", ROUTING_KEY);
        ReflectionTestUtils.setField(rabbitMQConfig, "deadLetterQueueName", DLQ_NAME);
        ReflectionTestUtils.setField(rabbitMQConfig, "deadLetterQueueExchange", DLQ_EXCHANGE);
    }

    @Test
    void emailQueue_isDurable_withDeadLetterArguments() {
        Queue queue = rabbitMQConfig.emailQueue();

        assertThat(queue.getName()).isEqualTo(QUEUE_NAME);
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", DLQ_EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", DLQ_NAME);
    }

    @Test
    void emailExchange_isTopicExchangeWithConfiguredName() {
        TopicExchange topicExchange = rabbitMQConfig.emailExchange();

        assertThat(topicExchange.getName()).isEqualTo(EXCHANGE);
    }

    @Test
    void binding_bindsEmailQueueToEmailExchange_withRoutingKey() {
        Queue queue = rabbitMQConfig.emailQueue();
        TopicExchange topicExchange = rabbitMQConfig.emailExchange();

        Binding binding = rabbitMQConfig.binding(queue, topicExchange);

        assertThat(binding.getDestination()).isEqualTo(QUEUE_NAME);
        assertThat(binding.getExchange()).isEqualTo(EXCHANGE);
        assertThat(binding.getRoutingKey()).isEqualTo(ROUTING_KEY);
    }

    @Test
    void emailDLQ_isDurableWithConfiguredName() {
        Queue dlq = rabbitMQConfig.emailDLQ();

        assertThat(dlq.getName()).isEqualTo(DLQ_NAME);
        assertThat(dlq.isDurable()).isTrue();
    }

    @Test
    void deadLetterExchange_isDirectExchangeWithConfiguredName() {
        DirectExchange exchange = rabbitMQConfig.deadLetterExchange();

        assertThat(exchange.getName()).isEqualTo(DLQ_EXCHANGE);
    }

    @Test
    void dlqBinding_bindsDeadLetterQueueToDeadLetterExchange() {
        Binding binding = rabbitMQConfig.dlqBinding();

        assertThat(binding.getDestination()).isEqualTo(DLQ_NAME);
        assertThat(binding.getExchange()).isEqualTo(DLQ_EXCHANGE);
        assertThat(binding.getRoutingKey()).isEqualTo(DLQ_NAME);
    }

    @Test
    void jsonMessageConverter_returnsJackson2JsonMessageConverter() {
        MessageConverter converter = rabbitMQConfig.jsonMessageConverter();

        assertThat(converter).isInstanceOf(Jackson2JsonMessageConverter.class);
    }

    @Test
    void rabbitTemplate_usesJsonMessageConverter() {
        RabbitTemplate template = rabbitMQConfig.rabbitTemplate(connectionFactory);

        assertThat(template.getMessageConverter()).isInstanceOf(Jackson2JsonMessageConverter.class);
        assertThat(template.getConnectionFactory()).isSameAs(connectionFactory);
    }
}
