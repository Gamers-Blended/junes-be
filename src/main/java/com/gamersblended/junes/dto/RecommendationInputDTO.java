package com.gamersblended.junes.dto;

import com.gamersblended.junes.constant.SignalEnums;
import lombok.Value;

import java.time.LocalDateTime;

@Value
public class RecommendationInputDTO {
    String productID;
    LocalDateTime timestamp;
    SignalEnums signal;
}
