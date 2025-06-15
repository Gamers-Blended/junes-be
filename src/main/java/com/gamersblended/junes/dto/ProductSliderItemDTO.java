package com.gamersblended.junes.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductSliderItemDTO {

    private String id;
    private String name;
    private String slug;
    private BigDecimal price;
    private String productImageUrl;
}
