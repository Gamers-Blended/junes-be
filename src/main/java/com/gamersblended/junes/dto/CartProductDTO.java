package com.gamersblended.junes.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CartProductDTO {

    private String id;
    private String name;
    private Double price;
    private String platform;
    private String region;
    private String edition;
    private String productImageUrl;
    private Integer quantity;
    private Integer userID;
    private LocalDate createdAt;
}
