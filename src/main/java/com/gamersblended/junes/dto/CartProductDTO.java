package com.gamersblended.junes.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CartProductDTO {

    private String productID;
    private String name;
    private Double price;
    private String platform;
    private String region;
    private String edition;
    private String productImageUrl;
    private Integer quantity;
    private Integer userID;
    private LocalDate createdOn;
}
