package com.gamersblended.junes.dto.request;

import com.gamersblended.junes.dto.OrderItemDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlaceOrderRequest {

    private UUID addressID;
    private UUID paymentMethodID;
    private List<OrderItemDTO> orderItemDTOList;
    private BigDecimal shippingCost;
}
