package com.gamersblended.junes.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionItemEmailDTO {

    private String name;
    private BigDecimal price;
    private Integer quantity;
    private String productImageUrl;
}
