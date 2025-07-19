package com.gamersblended.junes.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductVariantDTO {

    private String platform;
    private String region;
    private String edition;
    private BigDecimal price;
    private String productImageUrl;
}
