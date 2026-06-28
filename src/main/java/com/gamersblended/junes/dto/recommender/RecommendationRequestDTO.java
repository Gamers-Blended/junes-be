package com.gamersblended.junes.dto.recommender;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RecommendationRequestDTO {
    @JsonProperty("signal_list")
    List<ProductSignalDTO> signalList;

    @JsonProperty("max_result")
    Integer maxResult;
}
