package com.gamersblended.junes.dto.event;

import lombok.Data;

@Data
public class InventoryChangedEvent extends BaseEvent {

    private Long productID;
    private Integer previousStock;
    private Integer currentStock;
    private Integer quantityChanged;
    private String reason;

    public InventoryChangedEvent() {
        this.setEventType("INVENTORY_CHANGED");
    }
}
