package com.gamersblended.junes.dto.recommender;

import lombok.Value;

import java.time.LocalDateTime;

@Value
public class OrderEvent {
    String productID;
    LocalDateTime createdOn;
}
