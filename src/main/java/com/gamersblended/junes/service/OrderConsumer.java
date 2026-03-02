package com.gamersblended.junes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.dto.event.OrderPlacedEvent;
import com.gamersblended.junes.repository.RedisCartRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OrderConsumer {

    private final RedisCartRepository cartRepository;
    private final CartService cartService;
    private final ObjectMapper objectMapper;

    public OrderConsumer(RedisCartRepository cartRepository, CartService cartService, ObjectMapper objectMapper) {
        this.cartRepository = cartRepository;
        this.cartService = cartService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${kafka.topic.order-placed}",
            groupId = "${kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderPlacedEvent(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment
    ) {
        try {
            log.info("Received order placed event: offset={}, partition={}",
                    record.offset(), record.partition());

            OrderPlacedEvent event = objectMapper.readValue(
                    record.value(),
                    OrderPlacedEvent.class
            );

            log.info("Processing order placed event: orderNumber = {}, userID = {}, sessionID = {}",
                    event.getOrderNumber(), event.getUserID(), event.getSessionID());

            // Clear cart after successful order placement
            boolean deleted = cartRepository.deleteCart(event.getUserID(), event.getSessionID());

            if (deleted) {
                log.info("Successfully cleared cart for orderNumber = {}, userID = {}",
                        event.getOrderNumber(), event.getUserID());
            } else {
                log.error("Cart not found or already cleared for orderNumber = {}, userID = {}",
                        event.getOrderNumber(), event.getUserID());
            }

            cartService.archiveCartForOrder(event.getOrderNumber(), event.getUserID(), event.getSessionID());

            acknowledgment.acknowledge();
        } catch (Exception ex) {
            log.error("Error processing order placed event: offset = {}, partition = {}",
                    record.offset(), record.partition(), ex);

            // Don't acknowledge - message will be retried
            // TODO dead letter queue
            throw new RuntimeException("Failed to process order event", ex);
        }
    }

    @KafkaListener(
            topics = "${kafka.topic.order-cancelled}",
            groupId = "${kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCancelledEvent(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment
    ) {
        try {
            log.info("Received order cancelled event: offset = {}, partition= {}",
                    record.offset(), record.partition());

            OrderPlacedEvent event = objectMapper.readValue(
                    record.value(),
                    OrderPlacedEvent.class
            );

            log.info("Processing order cancelled event: orderNumber = {}, userID = {}",
                    event.getOrderNumber(), event.getUserID());

            acknowledgment.acknowledge();
        } catch (Exception ex) {
            log.error("EError processing order cancelled event: offset = {}, partition = {}",
                    record.offset(), record.partition(), ex);
            throw new RuntimeException("Failed to process order cancelled event", e);
        }
    }
}
