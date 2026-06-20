package com.gamersblended.junes.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

@Getter
@Setter
public class ProductVariantDTO {

    private String productID;
    private String name;
    private BigDecimal price;
    private String platform;
    private String region;
    private String edition;
    private LocalDate releaseDate;
    private Set<String> languages;
    private Integer stock;
    private String productImageUrl;
    private String editionNotes;
}
