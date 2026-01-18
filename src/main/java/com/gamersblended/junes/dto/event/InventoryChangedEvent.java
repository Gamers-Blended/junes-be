package com.gamersblended.junes.dto.event;

import lombok.Data;

import static com.gamersblended.junes.constant.KafkaConstants.INVENTORY_CHANGED;

@Data
public class InventoryChangedEvent extends BaseEvent {

    private String productID;
    private Integer previousStock;
    private Integer currentStock;
    private Integer quantityChanged;
    private String reason;

    public InventoryChangedEvent() {
        this.setEventType(INVENTORY_CHANGED);
    }
}
