package com.gamersblended.junes.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CartItemDTO {

    private String productID;
    private String name;
    private BigDecimal price;
    private String platform;
    private String region;
    private String edition;
    private String productImageUrl;
    private Integer quantity;
    private Long userID;
    private LocalDateTime createdOn;
}
