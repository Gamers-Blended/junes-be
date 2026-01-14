package com.gamersblended.junes.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionDetailsDTO {

    private String orderNumber;
    private LocalDateTime orderDate;
    private LocalDateTime shippedDate;
    private BigDecimal totalAmount;
    private List<TransactionItemDTO> transactionItemDTOList;
    private AddressDTO shippingAddress;
    private BigDecimal shippingCost;
    private BigDecimal shippingWeight;
    private String trackingNumber;
}
