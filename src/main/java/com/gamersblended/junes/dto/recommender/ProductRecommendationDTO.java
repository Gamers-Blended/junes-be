package com.gamersblended.junes.dto.recommender;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductRecommendationDTO {

    @JsonProperty("product_id")
    String productID;

    String name;

    String slug;

    String platform;

    String region;

    String edition;

    BigDecimal price;

    @JsonProperty("product_image_url")
    String productImageUrl;
}
