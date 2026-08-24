package com.gamersblended.junes.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class ProductInWishlistDTO {

    private String id;
    private String name;
    private String slug;
    private BigDecimal price;
    private String platform;
    private String region;
    private String edition;
    private String productImageUrl;
    private LocalDateTime createdOn;

}
