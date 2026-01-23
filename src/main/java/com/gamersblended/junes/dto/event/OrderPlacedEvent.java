package com.gamersblended.junes.dto.event;

import com.gamersblended.junes.dto.OrderItemDTO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.gamersblended.junes.constant.KafkaConstants.ORDER_PLACED;

@Data
public class OrderPlacedEvent extends BaseEvent {

    private UUID transactionID;
    private UUID userID;
    private BigDecimal totalAmount;
    private List<OrderItemDTO> itemList;

    public OrderPlacedEvent() {
        this.setEventType(ORDER_PLACED);
    }
}
