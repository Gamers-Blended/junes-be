package com.gamersblended.junes.service;

import com.gamersblended.junes.dto.OrderItemDTO;
import com.gamersblended.junes.dto.event.InventoryChangedEvent;
import com.gamersblended.junes.dto.event.OrderPlacedEvent;
import com.gamersblended.junes.model.Transaction;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public EventPublisher(KafkaTemplate kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderPlaced(Transaction transaction,
                                   Map<String, Integer> productMap) {
        OrderPlacedEvent event = new OrderPlacedEvent();
        event.setTransactionID(transaction.getTransactionID());
        event.setUserID(transaction.getUserID());
        event.setTotalAmount(transaction.getTotalAmount());

        List<OrderItemDTO> orderItemList = productMap.entrySet().stream()
                .map(entry -> {
                    OrderItemDTO item = new OrderItemDTO();
                    item.setProductID(entry.getKey());
                    item.setQuantity(entry.getValue());

                    return item;
                })
                .toList();

        event.setItemList(orderItemList);

        kafkaTemplate.send("order-events",
                String.valueOf(transaction.getTransactionID()), event);
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
