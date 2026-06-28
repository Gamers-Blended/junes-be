package com.gamersblended.junes.dto.recommender;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class RecommendationResponseDTO {
    List<ProductRecommendationDTO> products;
    Integer total;
    LocalDateTime servedAt;
}
