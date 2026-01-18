package com.gamersblended.junes.dto.event;

import com.gamersblended.junes.dto.TransactionItemDTO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class OrderPlacedEvent extends BaseEvent {

    private UUID transactionID;
    private UUID userID;
    private BigDecimal totalAmount;
    private List<TransactionItemDTO> itemList;

    public OrderPlacedEvent() {
        this.setEventType("ORDER_PLACED");
    }
}
