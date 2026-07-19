package com.gamersblended.junes.dto.event;

import com.gamersblended.junes.dto.OrderItemDTO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.gamersblended.junes.constant.KafkaConstants.ORDER_CREATED;

@Data
public class OrderCreatedEvent extends BaseEvent {

    private UUID transactionID;
    private String orderNumber;
    private UUID userID;
    private UUID sessionID;
    private UUID paymentMethodID;
    private BigDecimal totalAmount;
    private List<OrderItemDTO> itemList;

    public OrderCreatedEvent() {
        this.setEventType(ORDER_CREATED);
    }
}
