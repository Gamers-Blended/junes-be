package com.gamersblended.junes.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionItemDTO {

    private String name;
    private String slug;
    private BigDecimal price;
    private String platform;
    private String region;
    private String edition;
    private String productImageUrl;
    private Integer quantity;
    private String productID;
}
