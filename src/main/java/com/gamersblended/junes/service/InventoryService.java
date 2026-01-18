package com.gamersblended.junes.service;

import com.gamersblended.junes.model.Product;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import static com.gamersblended.junes.constant.KafkaConstants.ORDER_PLACED;

@Slf4j
@Service
public class InventoryService {

    private final MongoTemplate mongoTemplate;
    private final EventPublisher eventPublisher;

    public InventoryService(MongoTemplate mongoTemplate, EventPublisher eventPublisher) {
        this.mongoTemplate = mongoTemplate;
        this.eventPublisher = eventPublisher;
    }

    public boolean reserveStock(String productID, int quantity) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(productID)
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
            // Successfully reserved, publish event
            Product product = mongoTemplate.findById(productID, Product.class);
            eventPublisher.publishInventoryChanged(
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
}
