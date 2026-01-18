package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.event.InventoryChangedEvent;
import com.gamersblended.junes.model.Transaction;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EventPublisher {

    private KafkaTemplate<String, Object> kafkaTemplate;

    public EventPublisher(KafkaTemplate kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderPlaced(Transaction transaction,
                                   Map<String, Integer> items) {
        // TODO
    }

    public void publishInventoryChanged(String productID,
                                        Integer previousStock,
                                        Integer currentStock,
                                        String reason) {
        InventoryChangedEvent event = new InventoryChangedEvent();
        event.setProductID(productID);
        event.setPreviousStock(previousStock);
        event.setCurrentStock(currentStock);
        event.setQuantityChanged(currentStock - previousStock);
        event.setReason(reason);

        kafkaTemplate.send("inventory-events",
                productID,
                event);
    }
}
