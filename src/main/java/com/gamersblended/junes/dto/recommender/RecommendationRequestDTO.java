package com.gamersblended.junes.dto.recommender;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RecommendationRequestDTO {
    List<ProductSignalDTO> signalList;
    Integer maxResult;
}
