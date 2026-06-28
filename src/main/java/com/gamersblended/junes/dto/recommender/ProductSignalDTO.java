package com.gamersblended.junes.dto.recommender;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.time.LocalDateTime;

@Value
public class ProductSignalDTO {
    @JsonProperty("product_id")
    String productID;

    String type;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    LocalDateTime timestamp;
}
