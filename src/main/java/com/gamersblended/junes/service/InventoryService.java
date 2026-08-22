package com.gamersblended.junes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamersblended.junes.dto.event.InventoryChangedEvent;
import com.gamersblended.junes.exception.OutboxEventCreationException;
import com.gamersblended.junes.model.OutboxEvent;
import com.gamersblended.junes.model.Product;
import com.gamersblended.junes.repository.jpa.OutboxEventRepository;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static com.gamersblended.junes.constant.KafkaConstants.INVENTORY_EVENTS;
import static com.gamersblended.junes.constant.KafkaConstants.ORDER_PLACED;
import static com.gamersblended.junes.constant.KafkaConstants.PENDING;
import static com.gamersblended.junes.constant.KafkaConstants.STOCK_RELEASED;

@Slf4j
@Service
public class InventoryService {

    private final MongoTemplate mongoTemplate;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public InventoryService(MongoTemplate mongoTemplate, OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.mongoTemplate = mongoTemplate;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public boolean reserveStock(String productID, int quantity) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(new ObjectId(productID))
                .and("stock").gte(quantity));

        Update update = new Update();
        update.inc("stock", -quantity);

        // Atomic operation: only update if stock >= quantity
        UpdateResult result = mongoTemplate.updateFirst(
                query,
                update,
                Product.class
        );

        if (result.getModifiedCount() > 0) {
            // Successfully reserved, write outbox event for relay to publish
            Product product = mongoTemplate.findById(new ObjectId(productID), Product.class);
            writeOutboxEvent(
                    productID,
                    product.getStock() + quantity,
                    product.getStock(),
                    ORDER_PLACED
            );
            return true;
        }

        // Insufficient stock
        return false;
    }

    public void restoreStock(String productID, int quantity) {
        Query query = new Query(Criteria.where("_id").is(new ObjectId(productID)));
        Update update = new Update().inc("stock", quantity);

        UpdateResult result = mongoTemplate.updateFirst(query, update, Product.class);

        if (result.getModifiedCount() > 0) {
            // Successfully restored, write outbox event for relay to publish
            Product product = mongoTemplate.findById(new ObjectId(productID), Product.class);
            writeOutboxEvent(
                    productID,
                    product.getStock() - quantity,
                    product.getStock(),
                    STOCK_RELEASED
            );
        }
    }

    private void writeOutboxEvent(String productID, Integer previousStock, Integer currentStock, String reason) {
        InventoryChangedEvent event = new InventoryChangedEvent();
        event.setProductID(productID);
        event.setPreviousStock(previousStock);
        event.setCurrentStock(currentStock);
        event.setQuantityChanged(currentStock - previousStock);
        event.setReason(reason);

        try {
            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setAggregateID(productID); // Kafka partition key
            outboxEvent.setEventType(event.getEventType());
            outboxEvent.setTopic(INVENTORY_EVENTS);
            outboxEvent.setPayload(objectMapper.writeValueAsString(event));
            outboxEvent.setStatus(PENDING);
            outboxEvent.setCreatedOn(LocalDateTime.now(ZoneId.of("Asia/Singapore")));
            outboxEvent.setPublished(false);
            outboxEvent.setRetryCount(0);

            outboxEventRepository.save(outboxEvent);
        } catch (Exception ex) {
            log.error("Failed to write outbox event for product {}", productID, ex);
            throw new OutboxEventCreationException("Failed to write outbox event: " + ex.getMessage());
        }
    }
}
