package com.gamersblended.junes.dto.recommender;

import lombok.Value;

import java.time.LocalDateTime;

@Value
public class ProductSignalDTO {
    String productID;
    String type;
    LocalDateTime timestamp;
}
