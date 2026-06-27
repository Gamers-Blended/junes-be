package com.gamersblended.junes.dto.recommender;

import com.gamersblended.junes.constant.SignalTypeEnums;
import lombok.Value;

import java.time.LocalDateTime;

@Value
public class ProductSignalDTO {
    String productID;
    SignalTypeEnums type;
    LocalDateTime timestamp;
}
